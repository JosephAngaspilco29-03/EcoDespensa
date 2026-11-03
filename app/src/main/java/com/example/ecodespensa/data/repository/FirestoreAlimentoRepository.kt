package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.AlimentoFirestore
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.toFirestoreModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Resultado individual al intentar subir un alimento sin sobrescribir documentos existentes.
 */
enum class EstadoSubidaAlimento {
    /** El documento no existía en Firestore y fue creado y confirmado en el servidor. */
    SUBIDO,
    /** El documento ya existía en Firestore y se omitió sin sobrescribirlo. */
    OMITIDO_YA_EXISTE
}

/**
 * Estado al sincronizar una edición (o cambio pendiente) de un alimento con Cloud Firestore.
 */
enum class EstadoSincronizacionEdicion {
    /** El documento remoto fue actualizado (o creado si no existía) y confirmado en el servidor. */
    SINCRONIZADO,
    /** El documento remoto tiene una marca `actualizadoEn` más reciente; se evitó sobrescribirlo. */
    CONFLICTO_REMOTO_MAS_RECIENTE
}

/**
 * Resultado detallado de la sincronización de una edición en Cloud Firestore.
 */
data class ResultadoSincronizacionEdicion(
    val estado: EstadoSincronizacionEdicion,
    val actualizadoEnConfirmado: Long,
    val actualizadoEnRemoto: Long = 0L
)

/**
 * Estado al sincronizar una eliminación pendiente (tombstone) con Cloud Firestore (Etapa 4C.3F).
 */
enum class EstadoSincronizacionEliminacion {
    /** El documento fue eliminado en el servidor (o ya no existía) y se puede limpiar el pendiente local. */
    ELIMINADO_CONFIRMADO,
    /** El documento remoto tiene cambios posteriores a la eliminación/base local; no se borró. */
    CONFLICTO_CAMBIO_REMOTO_POSTERIOR
}

/**
 * Resumen final de una operación manual de subida/sincronización de despensa a Cloud Firestore.
 */
data class ResultadoSubidaDespensa(
    val total: Int,
    val subidos: Int,
    val eliminados: Int = 0,
    val omitidos: Int,
    val errores: Int,
    val detalleError: String? = null
)

/**
 * Resumen final de una operación manual de recuperación de alimentos desde Cloud Firestore.
 */
data class ResultadoDescargaDespensa(
    val totalRemotos: Int,
    val descargados: Int,
    val omitidos: Int,
    val errores: Int,
    val detalleError: String? = null
)

/**
 * Resultado de la lectura y validación de documentos en `usuarios/{uid}/alimentos`.
 */
data class LoteDescargaFirestore(
    val totalDocumentos: Int,
    val validos: List<AlimentoFirestore>,
    val erroresValidacion: Int,
    val detalleUltimoError: String? = null
)

/**
 * Repositorio para gestionar la persistencia remota de [Alimento] en Cloud Firestore
 * bajo la colección `usuarios/{uid}/alimentos/{remoteId}`.
 *
 * - Usa exclusivamente [Alimento.remoteId] (UUID único generado una sola vez y persistido en Room)
 *   como ID del documento en Firestore (`{documentId}` == `alimento.remoteId`).
 * - No depende del nombre, categoría, fechas ni del `id` autoincremental local de Room.
 * - Editar, consumir, desperdiciar o ajustar stock conserva siempre el mismo `remoteId`.
 * - Verifica en todas las operaciones que el `uid` solicitado coincida con el usuario autenticado.
 */
class FirestoreAlimentoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Sube un [Alimento] a `usuarios/{uid}/alimentos/{remoteId}` únicamente si el documento
     * aún NO existe en el servidor de Cloud Firestore.
     *
     * Utiliza una transacción de Firestore (`runTransaction`), lo que garantiza:
     * 1. Que la verificación de existencia y la escritura ocurran contra el servidor real
     *    (no declara éxito si solo queda en caché offline sin confirmar).
     * 2. Que si el documento ya existe en la nube, NO se sobrescriba y devuelva
     *    [EstadoSubidaAlimento.OMITIDO_YA_EXISTE].
     * 3. Que no se genere un nuevo `remoteId` para alimentos existentes.
     */
    suspend fun subirAlimentoSiNoExiste(
        uid: String,
        alimento: Alimento
    ): Result<EstadoSubidaAlimento> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (alimento.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El alimento no pertenece al usuario autenticado actual.")
        }
        validarPropietarioYRemoteId(alimento, uidVerificado)

        val marcaSubida = alimento.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val modeloRemoto = alimento.toFirestoreModel(
            uidOverride = uidVerificado,
            actualizadoEn = marcaSubida
        )
        val docRef = coleccionAlimentos(uidVerificado).document(modeloRemoto.remoteId)

        // 1. Verificar contra el servidor real (falla de inmediato si no hay Internet)
        val snapshotPrevio = withTimeout(5_000L) {
            docRef.get(Source.SERVER).await()
        }
        if (snapshotPrevio.exists()) {
            return@runCatching EstadoSubidaAlimento.OMITIDO_YA_EXISTE
        }

        // 2. Crear en una transacción confirmada por el servidor sin sobrescribir si ya existe
        val fueCreado = withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    false
                } else {
                    transaction.set(docRef, modeloRemoto.toMap())
                    true
                }
            }.await()
        }

        if (fueCreado) {
            EstadoSubidaAlimento.SUBIDO
        } else {
            EstadoSubidaAlimento.OMITIDO_YA_EXISTE
        }
    }

    /**
     * Sincroniza la edición de un [Alimento] existente en `usuarios/{uid}/alimentos/{remoteId}`:
     * - Conserva siempre el `remoteId` original del alimento.
     * - Verifica que pertenezca al usuario autenticado (`uid`).
     * - Comprueba contra el servidor real (`Source.SERVER` + `runTransaction`) si el documento
     *   remoto tiene cambios más recientes (`actualizadoEn` remoto mayor que el local o mayor
     *   que `baseRemotaActualizadoEn`) para evitar sobrescribir cambios remotos sin detectar conflicto.
     */
    suspend fun sincronizarEdicionAlimento(
        uid: String,
        alimento: Alimento
    ): Result<ResultadoSincronizacionEdicion> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (alimento.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El alimento no pertenece al usuario autenticado actual.")
        }
        validarPropietarioYRemoteId(alimento, uidVerificado)

        val marcaLocal = alimento.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val modeloActualizado = alimento.toFirestoreModel(
            uidOverride = uidVerificado,
            actualizadoEn = marcaLocal
        )
        val docRef = coleccionAlimentos(uidVerificado).document(modeloActualizado.remoteId)

        // 1. Sondeo rápido contra el servidor real para fallar de inmediato si no hay Internet
        val snapshotServidor = withTimeout(5_000L) {
            docRef.get(Source.SERVER).await()
        }
        if (snapshotServidor.exists()) {
            val uidRemotoPrevio = snapshotServidor.getString(AlimentoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidRemotoPrevio.isNotEmpty() && uidRemotoPrevio != uidVerificado) {
                throw SecurityException("El documento remoto pertenece a otra cuenta.")
            }
            val remotoActualizadoPrevio = snapshotServidor.getLong(AlimentoFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
            val conflictoBasePrevio =
                alimento.baseRemotaActualizadoEn > 0L &&
                    remotoActualizadoPrevio > alimento.baseRemotaActualizadoEn
            val conflictoTimestampPrevio = remotoActualizadoPrevio > marcaLocal

            if (conflictoBasePrevio || conflictoTimestampPrevio) {
                return@runCatching ResultadoSincronizacionEdicion(
                    estado = EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE,
                    actualizadoEnConfirmado = marcaLocal,
                    actualizadoEnRemoto = remotoActualizadoPrevio
                )
            }
        }

        // 2. Transacción atómica en el servidor para verificar conflicto y actualizar el mismo remoteId
        withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val uidRemoto = snapshot.getString(AlimentoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
                    if (uidRemoto.isNotEmpty() && uidRemoto != uidVerificado) {
                        throw SecurityException("El documento remoto pertenece a otra cuenta.")
                    }
                    val remotoActualizadoEn = snapshot.getLong(AlimentoFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                    val hayConflictoPorBase =
                        alimento.baseRemotaActualizadoEn > 0L &&
                            remotoActualizadoEn > alimento.baseRemotaActualizadoEn
                    val hayConflictoPorTimestamp = remotoActualizadoEn > marcaLocal

                    if (hayConflictoPorBase || hayConflictoPorTimestamp) {
                        return@runTransaction ResultadoSincronizacionEdicion(
                            estado = EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE,
                            actualizadoEnConfirmado = marcaLocal,
                            actualizadoEnRemoto = remotoActualizadoEn
                        )
                    }
                }

                transaction.set(docRef, modeloActualizado.toMap())
                ResultadoSincronizacionEdicion(
                    estado = EstadoSincronizacionEdicion.SINCRONIZADO,
                    actualizadoEnConfirmado = marcaLocal,
                    actualizadoEnRemoto = marcaLocal
                )
            }.await()
        }
    }

    /**
     * Guarda un [Alimento] en `usuarios/{uid}/alimentos/{remoteId}` usando su
     * identificador remoto persistente.
     */
    suspend fun guardarAlimento(
        uid: String,
        alimento: Alimento
    ): Result<AlimentoFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        validarPropietarioYRemoteId(alimento, uidVerificado)

        val modeloRemoto = alimento.toFirestoreModel(
            uidOverride = uidVerificado,
            actualizadoEn = System.currentTimeMillis()
        )

        coleccionAlimentos(uidVerificado)
            .document(modeloRemoto.remoteId)
            .set(modeloRemoto.toMap(), SetOptions.merge())
            .await()

        modeloRemoto
    }

    /**
     * Consulta todos los alimentos del usuario en `usuarios/{uid}/alimentos`.
     */
    suspend fun obtenerAlimentos(uid: String): Result<List<AlimentoFirestore>> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val snapshot = coleccionAlimentos(uidVerificado)
            .get()
            .await()

        snapshot.documents
            .mapNotNull { doc -> AlimentoFirestore.fromSnapshot(doc) }
            .filter { item -> item.usuarioUid.isBlank() || item.usuarioUid == uidVerificado }
    }

    /**
     * Lee desde el servidor de Cloud Firestore exclusivamente los documentos de
     * `usuarios/{uid}/alimentos` del usuario autenticado y valida la integridad
     * y pertenencia de cada documento antes de insertarlo en Room.
     */
    suspend fun descargarAlimentosValidados(uid: String): Result<LoteDescargaFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val snapshot = coleccionAlimentos(uidVerificado)
            .get(Source.SERVER)
            .await()

        val validos = mutableListOf<AlimentoFirestore>()
        val remoteIdsVistos = mutableSetOf<String>()
        var erroresValidacion = 0
        var ultimoError: String? = null

        for (doc in snapshot.documents) {
            if (!doc.exists()) continue

            val remoteIdDoc = doc.getString(AlimentoFirestore.FIELD_REMOTE_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: doc.id.trim()
            if (remoteIdDoc.isEmpty()) {
                erroresValidacion++
                ultimoError = "Se encontró un documento sin remoteId válido."
                continue
            }

            val uidDoc = doc.getString(AlimentoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidDoc.isNotEmpty() && uidDoc != uidVerificado) {
                erroresValidacion++
                ultimoError = "Se rechazó un documento cuyo usuarioUid no coincide con la cuenta actual."
                continue
            }

            val nombreDoc = doc.getString(AlimentoFirestore.FIELD_NOMBRE)?.trim().orEmpty()
            if (nombreDoc.isEmpty()) {
                erroresValidacion++
                ultimoError = "Se encontró un documento sin nombre de alimento."
                continue
            }

            val cantidadDoc = doc.getString(AlimentoFirestore.FIELD_CANTIDAD_ACTUAL)?.trim().orEmpty()
            if (cantidadDoc.isNotEmpty()) {
                val numero = cantidadDoc
                    .replace(',', '.')
                    .split(Regex("\\s+"))
                    .firstOrNull()
                    ?.toDoubleOrNull()
                if (numero == null || numero < 0.0) {
                    erroresValidacion++
                    ultimoError = "Cantidad inválida en el alimento '$nombreDoc'."
                    continue
                }
            }

            // Evitar procesar dos veces el mismo remoteId en un mismo lote
            if (!remoteIdsVistos.add(remoteIdDoc)) {
                continue
            }

            validos.add(
                AlimentoFirestore(
                    remoteId = remoteIdDoc,
                    nombre = nombreDoc,
                    categoria = doc.getString(AlimentoFirestore.FIELD_CATEGORIA)?.trim().orEmpty(),
                    cantidadActual = cantidadDoc,
                    unidad = doc.getString(AlimentoFirestore.FIELD_UNIDAD)?.trim().orEmpty(),
                    fechaCompra = doc.getString(AlimentoFirestore.FIELD_FECHA_COMPRA)?.trim().orEmpty(),
                    fechaVencimiento = doc.getString(AlimentoFirestore.FIELD_FECHA_VENCIMIENTO)?.trim().orEmpty(),
                    usuarioUid = uidVerificado,
                    actualizadoEn = doc.getLong(AlimentoFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                )
            )
        }

        LoteDescargaFirestore(
            totalDocumentos = snapshot.documents.size,
            validos = validos,
            erroresValidacion = erroresValidacion,
            detalleUltimoError = ultimoError
        )
    }

    /**
     * Observa en tiempo real los documentos de `usuarios/{uid}/alimentos`.
     * Libera el listener automáticamente al cancelar el flujo.
     */
    fun observarAlimentos(uid: String): Flow<List<AlimentoFirestore>> = callbackFlow {
        val uidVerificado = try {
            verificarUidAutenticado(uid)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val registration = coleccionAlimentos(uidVerificado)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val lista = snapshot?.documents
                    ?.mapNotNull { doc -> AlimentoFirestore.fromSnapshot(doc) }
                    ?.filter { item -> item.usuarioUid.isBlank() || item.usuarioUid == uidVerificado }
                    .orEmpty()
                trySend(lista)
            }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Actualiza un [Alimento] existente en `usuarios/{uid}/alimentos/{remoteId}`
     * utilizando directamente su [Alimento.remoteId] persistente, sin cambiar su identificador
     * aunque cambien el nombre, la categoría, las fechas o la cantidad.
     */
    suspend fun actualizarAlimento(
        uid: String,
        alimento: Alimento
    ): Result<AlimentoFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        validarPropietarioYRemoteId(alimento, uidVerificado)

        val modeloActualizado = alimento.toFirestoreModel(
            uidOverride = uidVerificado,
            actualizadoEn = System.currentTimeMillis()
        )

        coleccionAlimentos(uidVerificado)
            .document(modeloActualizado.remoteId)
            .set(modeloActualizado.toMap(), SetOptions.merge())
            .await()

        modeloActualizado
    }

    /**
     * Sincroniza de forma segura una eliminación pendiente ([EliminacionPendiente]) en
     * `usuarios/{uid}/alimentos/{remoteId}` (Etapa 4C.3F):
     * - Verifica que el `uid` solicitado y el del tombstone coincidan con el usuario autenticado.
     * - Sondea el servidor real (`Source.SERVER`) para fallar rápido si no hay Internet.
     * - Si el documento remoto tiene cambios posteriores (`actualizadoEn` > `fechaEliminacion`
     *   o > `baseRemotaActualizadoEn`), detecta el conflicto y NO elimina el documento remoto.
     * - Si no hay conflicto, elimina el documento dentro de una transacción confirmada por el servidor.
     */
    suspend fun sincronizarEliminacionAlimento(
        uid: String,
        eliminacion: EliminacionPendiente
    ): Result<EstadoSincronizacionEliminacion> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (eliminacion.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("La eliminación pendiente no pertenece al usuario autenticado.")
        }
        val idRemoto = eliminacion.remoteId.trim()
        require(idRemoto.isNotEmpty()) { "El remoteId de la eliminación pendiente no puede estar vacío." }

        val docRef = coleccionAlimentos(uidVerificado).document(idRemoto)

        // 1. Sondeo contra el servidor real (falla de inmediato si no hay Internet)
        val snapshotServidor = withTimeout(5_000L) {
            docRef.get(Source.SERVER).await()
        }
        if (snapshotServidor.exists()) {
            val uidRemotoPrevio = snapshotServidor.getString(AlimentoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidRemotoPrevio.isNotEmpty() && uidRemotoPrevio != uidVerificado) {
                throw SecurityException("El documento remoto pertenece a otra cuenta.")
            }
            val remotoActualizadoPrevio = snapshotServidor.getLong(AlimentoFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
            val conflictoBasePrevio =
                eliminacion.baseRemotaActualizadoEn > 0L &&
                    remotoActualizadoPrevio > eliminacion.baseRemotaActualizadoEn
            val conflictoFechaEliminacionPrevio =
                eliminacion.fechaEliminacion > 0L &&
                    remotoActualizadoPrevio > eliminacion.fechaEliminacion

            if (conflictoBasePrevio || conflictoFechaEliminacionPrevio) {
                return@runCatching EstadoSincronizacionEliminacion.CONFLICTO_CAMBIO_REMOTO_POSTERIOR
            }
        }

        // 2. Transacción atómica en el servidor para confirmar la eliminación sin borrar cambios más recientes
        withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    return@runTransaction EstadoSincronizacionEliminacion.ELIMINADO_CONFIRMADO
                }

                val uidRemoto = snapshot.getString(AlimentoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
                if (uidRemoto.isNotEmpty() && uidRemoto != uidVerificado) {
                    throw SecurityException("El documento remoto pertenece a otra cuenta.")
                }

                val remotoActualizadoEn = snapshot.getLong(AlimentoFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                val hayConflictoPorBase =
                    eliminacion.baseRemotaActualizadoEn > 0L &&
                        remotoActualizadoEn > eliminacion.baseRemotaActualizadoEn
                val hayConflictoPorFechaEliminacion =
                    eliminacion.fechaEliminacion > 0L &&
                        remotoActualizadoEn > eliminacion.fechaEliminacion

                if (hayConflictoPorBase || hayConflictoPorFechaEliminacion) {
                    return@runTransaction EstadoSincronizacionEliminacion.CONFLICTO_CAMBIO_REMOTO_POSTERIOR
                }

                transaction.delete(docRef)
                EstadoSincronizacionEliminacion.ELIMINADO_CONFIRMADO
            }.await()
        }
    }

    /**
     * Elimina un alimento en `usuarios/{uid}/alimentos/{remoteId}` a partir de su [remoteId].
     */
    suspend fun eliminarAlimentoPorRemoteId(
        uid: String,
        remoteId: String
    ): Result<Unit> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val idRemoto = remoteId.trim()
        require(idRemoto.isNotEmpty()) { "El remoteId no puede estar vacío." }

        coleccionAlimentos(uidVerificado)
            .document(idRemoto)
            .delete()
            .await()
    }

    /**
     * Elimina en Firestore el documento correspondiente al [alimento] usando su [Alimento.remoteId].
     */
    suspend fun eliminarAlimento(
        uid: String,
        alimento: Alimento
    ): Result<Unit> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        validarPropietarioYRemoteId(alimento, uidVerificado)

        coleccionAlimentos(uidVerificado)
            .document(alimento.remoteId.trim())
            .delete()
            .await()
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Verifica que haya un usuario autenticado en Firebase y que su `uid`
     * coincida con [uidSolicitado].
     */
    private fun verificarUidAutenticado(uidSolicitado: String): String {
        val uidActual = auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("No hay sesión activa en Firebase Authentication.")
        val uidLimpio = uidSolicitado.trim()
        if (uidLimpio.isEmpty() || uidActual != uidLimpio) {
            throw SecurityException("El UID solicitado no coincide con el usuario autenticado.")
        }
        return uidActual
    }

    /**
     * Valida que el alimento tenga un `remoteId` persistente no vacío y que su `usuarioUid`
     * (si está presente) coincida con [uidVerificado].
     */
    private fun validarPropietarioYRemoteId(alimento: Alimento, uidVerificado: String) {
        require(alimento.remoteId.isNotBlank()) {
            "El alimento no tiene un remoteId válido."
        }
        if (alimento.usuarioUid.isNotBlank() && alimento.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El alimento pertenece a otro usuario.")
        }
    }

    /**
     * Referencia a la subcolección `usuarios/{uid}/alimentos`.
     */
    private fun coleccionAlimentos(uid: String): CollectionReference =
        firestore.collection(COLLECTION_USUARIOS)
            .document(uid)
            .collection(COLLECTION_ALIMENTOS)

    companion object {
        private const val COLLECTION_USUARIOS = "usuarios"
        private const val COLLECTION_ALIMENTOS = "alimentos"
    }
}
