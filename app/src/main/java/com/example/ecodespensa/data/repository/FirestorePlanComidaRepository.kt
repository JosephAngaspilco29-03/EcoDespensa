package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.EliminacionPlanPendiente
import com.example.ecodespensa.data.model.IngredientePlanificadoFirestore
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.PlanComidaFirestore
import com.example.ecodespensa.data.model.TipoComida
import com.example.ecodespensa.data.model.toFirestoreModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

enum class EstadoSubidaPlanComida {
    SUBIDO,
    OMITIDO_YA_EXISTE,
    CONFLICTO_REMOTO_MAS_RECIENTE
}

data class RespuestaSubidaPlanComida(
    val estado: EstadoSubidaPlanComida,
    val actualizadoEnConfirmado: Long = 0L
)

enum class EstadoEliminacionPlanComida {
    ELIMINADO_CONFIRMADO,
    CONFLICTO_CAMBIO_REMOTO_POSTERIOR
}

data class ResultadoSubidaPlanificador(
    val total: Int,
    val subidos: Int,
    val eliminados: Int,
    val omitidos: Int,
    val conflictos: Int,
    val errores: Int,
    val detalleError: String? = null
)

data class ResultadoDescargaPlanificador(
    val totalRemotos: Int,
    val descargados: Int,
    val omitidos: Int,
    val conflictos: Int,
    val errores: Int,
    val detalleError: String? = null
)

data class LoteDescargaPlanesFirestore(
    val totalDocumentos: Int,
    val validos: List<PlanComidaFirestore>,
    val erroresValidacion: Int,
    val detalleUltimoError: String? = null
)

/**
 * Repositorio para sincronizar el Planificador Semanal con Cloud Firestore bajo
 * `usuarios/{uid}/planes_comida/{remoteId}`.
 *
 * - Verifica siempre que `uid` coincida con el usuario autenticado en Firebase.
 * - Conserva los `remoteId` UUID del plan y de sus ingredientes.
 * - Detecta conflictos remotos al subir cambios o eliminaciones pendientes.
 * - Evita duplicar comidas en la misma casilla (`fechaInicioSemana`, `diaSemana`, `tipoComida`).
 */
class FirestorePlanComidaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Sube o actualiza un [PlanComidaConIngredientes] en `usuarios/{uid}/planes_comida/{remoteId}`:
     * - Si el plan local NO tiene cambios pendientes (`pendienteSincronizacion == false`) y ya existe
     *   en Firestore, devuelve [EstadoSubidaPlanComida.OMITIDO_YA_EXISTE].
     * - Si tiene cambios pendientes (`pendienteSincronizacion == true`), verifica que el documento
     *   remoto no tenga una marca `actualizadoEn` más reciente que `baseRemotaActualizadoEn`
     *   para no sobrescribir cambios remotos en conflicto.
     */
    suspend fun sincronizarPlanComida(
        uid: String,
        item: PlanComidaConIngredientes
    ): Result<RespuestaSubidaPlanComida> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val plan = item.plan
        if (plan.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El plan no pertenece al usuario autenticado actual.")
        }
        val remoteIdLimpio = plan.remoteId.trim()
        require(remoteIdLimpio.isNotEmpty()) { "El plan no tiene un remoteId válido." }

        val colRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_PLANES_COMIDA)
        val docRef = colRef.document(remoteIdLimpio)

        // 1. Lectura en servidor real para comprobar conectividad y estado remoto actual
        val previo = withTimeout(6_000L) { docRef.get(Source.SERVER).await() }

        if (!plan.pendienteSincronizacion && previo.exists()) {
            val marcaRemota = previo.getLong(PlanComidaFirestore.FIELD_ACTUALIZADO_EN)
                ?: plan.actualizadoEn
            return@runCatching RespuestaSubidaPlanComida(
                estado = EstadoSubidaPlanComida.OMITIDO_YA_EXISTE,
                actualizadoEnConfirmado = marcaRemota
            )
        }

        // Comprobar si existe otro documento distinto en la misma casilla (semana, día y tipo)
        val duplicadosCasilla = withTimeout(6_000L) {
            colRef
                .whereEqualTo(PlanComidaFirestore.FIELD_FECHA_INICIO_SEMANA, plan.fechaInicioSemana.trim())
                .whereEqualTo(PlanComidaFirestore.FIELD_DIA_SEMANA, plan.diaSemana)
                .whereEqualTo(PlanComidaFirestore.FIELD_TIPO_COMIDA, plan.tipoComida.trim())
                .get(Source.SERVER)
                .await()
        }
        val otroEnMismaCasilla = duplicadosCasilla.documents.firstOrNull { it.id != remoteIdLimpio }
        if (otroEnMismaCasilla != null) {
            val marcaOtro = otroEnMismaCasilla.getLong(PlanComidaFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
            if (marcaOtro > plan.actualizadoEn) {
                return@runCatching RespuestaSubidaPlanComida(
                    estado = EstadoSubidaPlanComida.CONFLICTO_REMOTO_MAS_RECIENTE,
                    actualizadoEnConfirmado = marcaOtro
                )
            }
        }

        val marcaEscritura = plan.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val modelo = item.toFirestoreModel(
            uidVerificado = uidVerificado,
            marcaActualizacion = marcaEscritura
        )

        val resultadoTx = withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val actualSnap = transaction.get(docRef)
                if (actualSnap.exists()) {
                    val uidRemoto = actualSnap.getString(PlanComidaFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
                    if (uidRemoto.isNotEmpty() && uidRemoto != uidVerificado) {
                        throw SecurityException("El documento remoto no pertenece al usuario autenticado.")
                    }
                    val remotoActualizadoEn =
                        actualSnap.getLong(PlanComidaFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                    val baseConocida = plan.baseRemotaActualizadoEn

                    val hayConflicto = if (baseConocida > 0L) {
                        remotoActualizadoEn > baseConocida && remotoActualizadoEn > marcaEscritura
                    } else {
                        remotoActualizadoEn > marcaEscritura
                    }

                    if (hayConflicto) {
                        return@runTransaction EstadoSubidaPlanComida.CONFLICTO_REMOTO_MAS_RECIENTE
                    }
                }
                // Si había otro documento más antiguo en la misma casilla, reemplazarlo limpiamente
                if (otroEnMismaCasilla != null) {
                    transaction.delete(otroEnMismaCasilla.reference)
                }
                transaction.set(docRef, modelo.toMap())
                EstadoSubidaPlanComida.SUBIDO
            }.await()
        }

        RespuestaSubidaPlanComida(
            estado = resultadoTx,
            actualizadoEnConfirmado = marcaEscritura
        )
    }

    /**
     * Sincroniza la eliminación pendiente (tombstone) de un plan en `usuarios/{uid}/planes_comida/{remoteId}`
     * verificando antes que el documento remoto no tenga cambios posteriores.
     */
    suspend fun sincronizarEliminacionPlan(
        uid: String,
        tombstone: EliminacionPlanPendiente
    ): Result<EstadoEliminacionPlanComida> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (tombstone.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("La eliminación pendiente no pertenece al usuario autenticado.")
        }
        val remoteIdLimpio = tombstone.remoteId.trim()
        require(remoteIdLimpio.isNotEmpty()) { "El remoteId de la eliminación no es válido." }

        val docRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_PLANES_COMIDA)
            .document(remoteIdLimpio)

        val previo = withTimeout(6_000L) { docRef.get(Source.SERVER).await() }
        if (!previo.exists()) {
            return@runCatching EstadoEliminacionPlanComida.ELIMINADO_CONFIRMADO
        }

        withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (!snap.exists()) {
                    return@runTransaction EstadoEliminacionPlanComida.ELIMINADO_CONFIRMADO
                }
                val remotoActualizadoEn =
                    snap.getLong(PlanComidaFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                val limiteConocido = maxOf(tombstone.baseRemotaActualizadoEn, tombstone.fechaEliminacion)
                if (remotoActualizadoEn > limiteConocido) {
                    EstadoEliminacionPlanComida.CONFLICTO_CAMBIO_REMOTO_POSTERIOR
                } else {
                    transaction.delete(docRef)
                    EstadoEliminacionPlanComida.ELIMINADO_CONFIRMADO
                }
            }.await()
        }
    }

    /**
     * Lee y valida `usuarios/{uid}/planes_comida` del usuario autenticado desde `Source.SERVER`.
     */
    suspend fun descargarPlanesValidados(
        uid: String
    ): Result<LoteDescargaPlanesFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val colRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_PLANES_COMIDA)

        val snapshot = withTimeout(10_000L) {
            colRef.get(Source.SERVER).await()
        }

        val validosPorCasilla = linkedMapOf<String, PlanComidaFirestore>()
        val remoteIdsVistos = mutableSetOf<String>()
        var errores = 0
        var ultimoError: String? = null

        for (doc in snapshot.documents) {
            val remoteIdCampo = doc.getString(PlanComidaFirestore.FIELD_REMOTE_ID)?.trim().orEmpty()
            val remoteIdEfectivo = remoteIdCampo.ifEmpty { doc.id.trim() }

            if (remoteIdEfectivo.isEmpty()) {
                errores++
                ultimoError = "Documento de plan sin remoteId válido."
                continue
            }
            if (remoteIdCampo.isNotEmpty() && remoteIdCampo != doc.id.trim()) {
                errores++
                ultimoError = "El remoteId del plan ${doc.id} no coincide con su clave."
                continue
            }
            if (!remoteIdsVistos.add(remoteIdEfectivo)) {
                errores++
                ultimoError = "Plan duplicado con remoteId $remoteIdEfectivo."
                continue
            }

            val uidDoc = doc.getString(PlanComidaFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidDoc.isEmpty() || uidDoc != uidVerificado) {
                errores++
                ultimoError = "El plan ${doc.id} no pertenece al usuario autenticado."
                continue
            }

            val fechaInicioSemana =
                doc.getString(PlanComidaFirestore.FIELD_FECHA_INICIO_SEMANA)?.trim().orEmpty()
            if (!REGEX_FECHA_ISO.matches(fechaInicioSemana)) {
                errores++
                ultimoError = "El plan ${doc.id} tiene fechaInicioSemana inválida ($fechaInicioSemana)."
                continue
            }

            val diaSemana = doc.getLong(PlanComidaFirestore.FIELD_DIA_SEMANA)?.toInt() ?: 0
            if (diaSemana !in 1..7) {
                errores++
                ultimoError = "El plan ${doc.id} tiene diaSemana fuera de rango ($diaSemana)."
                continue
            }

            val tipoComidaStr = doc.getString(PlanComidaFirestore.FIELD_TIPO_COMIDA)?.trim().orEmpty()
            val tipoValido = runCatching { TipoComida.valueOf(tipoComidaStr) }.getOrNull()
            if (tipoValido == null) {
                errores++
                ultimoError = "El plan ${doc.id} tiene tipoComida inválido ($tipoComidaStr)."
                continue
            }

            val nombrePlato = doc.getString(PlanComidaFirestore.FIELD_NOMBRE_PLATO)?.trim().orEmpty()
            if (nombrePlato.isEmpty()) {
                errores++
                ultimoError = "El plan ${doc.id} no tiene nombre de plato."
                continue
            }

            val actualizadoEn = doc.getLong(PlanComidaFirestore.FIELD_ACTUALIZADO_EN) ?: 0L

            val ingredientesRaw = doc.get(PlanComidaFirestore.FIELD_INGREDIENTES) as? List<*>
            val ingredientesValidos = mutableListOf<IngredientePlanificadoFirestore>()
            var ingredienteInvalido = false

            if (ingredientesRaw != null) {
                for (item in ingredientesRaw) {
                    val mapa = item as? Map<*, *> ?: run {
                        ingredienteInvalido = true
                        break
                    }
                    val ingRemoteId =
                        (mapa[IngredientePlanificadoFirestore.FIELD_REMOTE_ID] as? String)?.trim()
                            .orEmpty()
                            .ifEmpty { java.util.UUID.randomUUID().toString() }
                    val alRemoteId =
                        (mapa[IngredientePlanificadoFirestore.FIELD_ALIMENTO_REMOTE_ID] as? String)?.trim()
                            .orEmpty()
                    val alNombre =
                        (mapa[IngredientePlanificadoFirestore.FIELD_ALIMENTO_NOMBRE] as? String)?.trim()
                            .orEmpty()
                    val cantNum =
                        (mapa[IngredientePlanificadoFirestore.FIELD_CANTIDAD_PLANIFICADA] as? Number)?.toDouble()
                    val unidad =
                        (mapa[IngredientePlanificadoFirestore.FIELD_UNIDAD] as? String)?.trim()
                            .orEmpty()

                    if (alNombre.isEmpty() || cantNum == null || !cantNum.isFinite() || cantNum <= 0.0) {
                        ingredienteInvalido = true
                        break
                    }

                    ingredientesValidos.add(
                        IngredientePlanificadoFirestore(
                            remoteId = ingRemoteId,
                            alimentoRemoteId = alRemoteId,
                            alimentoNombre = alNombre,
                            cantidadPlanificada = cantNum,
                            unidad = unidad
                        )
                    )
                }
            }

            if (ingredienteInvalido) {
                errores++
                ultimoError = "El plan ${doc.id} contiene ingredientes con datos inválidos."
                continue
            }

            val planRemoto = PlanComidaFirestore(
                remoteId = remoteIdEfectivo,
                usuarioUid = uidVerificado,
                fechaInicioSemana = fechaInicioSemana,
                diaSemana = diaSemana,
                tipoComida = tipoValido.name,
                nombrePlato = nombrePlato,
                actualizadoEn = actualizadoEn,
                ingredientes = ingredientesValidos
            )

            // Evitar duplicados remotos en la misma semana, día y tipo de comida (conservar el más reciente)
            val claveCasilla = "$fechaInicioSemana|$diaSemana|${tipoValido.name}"
            val previoCasilla = validosPorCasilla[claveCasilla]
            if (previoCasilla == null || planRemoto.actualizadoEn >= previoCasilla.actualizadoEn) {
                validosPorCasilla[claveCasilla] = planRemoto
            }
        }

        LoteDescargaPlanesFirestore(
            totalDocumentos = snapshot.size(),
            validos = validosPorCasilla.values.toList(),
            erroresValidacion = errores,
            detalleUltimoError = ultimoError
        )
    }

    private fun verificarUidAutenticado(uidSolicitado: String): String {
        val uidActual = auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("No hay sesión activa en Firebase Authentication.")
        val uidLimpio = uidSolicitado.trim()
        if (uidLimpio.isEmpty() || uidActual != uidLimpio) {
            throw SecurityException("El UID solicitado no coincide con el usuario autenticado.")
        }
        return uidActual
    }

    companion object {
        private const val COLLECTION_USUARIOS = "usuarios"
        private const val COLLECTION_PLANES_COMIDA = "planes_comida"
        private val REGEX_FECHA_ISO = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

