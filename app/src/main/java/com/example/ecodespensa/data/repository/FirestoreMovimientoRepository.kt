package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.MovimientoFirestore
import com.example.ecodespensa.data.model.TipoMovimiento
import com.example.ecodespensa.data.model.toFirestoreModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Resultado individual al subir un movimiento sin sobrescribir documentos existentes. */
enum class EstadoSubidaMovimiento {
    SUBIDO,
    OMITIDO_YA_EXISTE
}

/** Resumen final de la subida manual del historial de movimientos. */
data class ResultadoSubidaHistorial(
    val total: Int,
    val subidos: Int,
    val omitidos: Int,
    val errores: Int,
    val detalleError: String? = null
)

/** Resumen final de la recuperación manual del historial desde Cloud Firestore hacia Room. */
data class ResultadoDescargaHistorial(
    val totalRemotos: Int,
    val descargados: Int,
    val omitidos: Int,
    val errores: Int,
    val detalleError: String? = null
)

/** Resultado intermedio de la lectura y validación de movimientos remotos. */
data class LoteDescargaMovimientosFirestore(
    val totalDocumentos: Int,
    val validos: List<MovimientoFirestore>,
    val erroresValidacion: Int,
    val detalleUltimoError: String? = null
)

/**
 * Repositorio para guardar y recuperar [Movimiento] en Cloud Firestore bajo
 * `usuarios/{uid}/movimientos/{remoteId}`.
 *
 * - Verifica que el `uid` solicitado coincida con el usuario autenticado.
 * - Nunca sobrescribe: si el documento ya existe, lo omite.
 */
class FirestoreMovimientoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun subirMovimientoSiNoExiste(
        uid: String,
        movimiento: Movimiento
    ): Result<EstadoSubidaMovimiento> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (movimiento.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El movimiento no pertenece al usuario autenticado actual.")
        }
        require(movimiento.remoteId.isNotBlank()) { "El movimiento no tiene un remoteId válido." }

        val modelo = movimiento.toFirestoreModel(uidOverride = uidVerificado)
        val docRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_MOVIMIENTOS)
            .document(modelo.remoteId)

        // 1. Verificar contra el servidor real (falla de inmediato sin Internet)
        val previo = withTimeout(5_000L) { docRef.get(Source.SERVER).await() }
        if (previo.exists()) {
            return@runCatching EstadoSubidaMovimiento.OMITIDO_YA_EXISTE
        }

        // 2. Crear en transacción (confirmada por el servidor) sin sobrescribir
        val creado = withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                if (transaction.get(docRef).exists()) {
                    false
                } else {
                    transaction.set(docRef, modelo.toMap())
                    true
                }
            }.await()
        }

        if (creado) EstadoSubidaMovimiento.SUBIDO else EstadoSubidaMovimiento.OMITIDO_YA_EXISTE
    }

    /**
     * Descarga y valida únicamente `usuarios/{uid}/movimientos` del usuario autenticado.
     * - Verifica que `usuarioUid` del documento coincida con el usuario en sesión.
     * - Valida `remoteId`, `alimentoNombre`, `tipo`, `cantidad` y `fecha`.
     * - Nunca utiliza IDs locales de otro dispositivo.
     */
    suspend fun descargarMovimientosValidados(
        uid: String
    ): Result<LoteDescargaMovimientosFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val colRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_MOVIMIENTOS)

        val snapshot = withTimeout(10_000L) {
            colRef.get(Source.SERVER).await()
        }

        val validos = mutableListOf<MovimientoFirestore>()
        val remoteIdsVistos = mutableSetOf<String>()
        var errores = 0
        var ultimoError: String? = null

        for (doc in snapshot.documents) {
            val remoteIdCampo = doc.getString(MovimientoFirestore.FIELD_REMOTE_ID)?.trim().orEmpty()
            val remoteIdEfectivo = remoteIdCampo.ifEmpty { doc.id.trim() }

            if (remoteIdEfectivo.isEmpty()) {
                errores++
                ultimoError = "Documento sin remoteId válido."
                continue
            }
            if (remoteIdCampo.isNotEmpty() && remoteIdCampo != doc.id.trim()) {
                errores++
                ultimoError = "El remoteId del documento ${doc.id} no coincide con su clave."
                continue
            }
            if (!remoteIdsVistos.add(remoteIdEfectivo)) {
                errores++
                ultimoError = "Documento duplicado con remoteId $remoteIdEfectivo."
                continue
            }

            val uidDoc = doc.getString(MovimientoFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidDoc.isEmpty() || uidDoc != uidVerificado) {
                errores++
                ultimoError = "Documento ${doc.id} no pertenece al usuario autenticado."
                continue
            }

            val alimentoNombre = doc.getString(MovimientoFirestore.FIELD_ALIMENTO_NOMBRE)?.trim().orEmpty()
            if (alimentoNombre.isEmpty()) {
                errores++
                ultimoError = "Movimiento ${doc.id} sin nombre de alimento."
                continue
            }

            val tipoStr = doc.getString(MovimientoFirestore.FIELD_TIPO)?.trim().orEmpty()
            val tipoValido = runCatching { TipoMovimiento.valueOf(tipoStr) }.getOrNull()
            if (tipoValido == null) {
                errores++
                ultimoError = "Movimiento ${doc.id} con tipo inválido ($tipoStr)."
                continue
            }

            val cantidad = doc.getDouble(MovimientoFirestore.FIELD_CANTIDAD)
            if (cantidad == null || !cantidad.isFinite() || cantidad == 0.0) {
                errores++
                ultimoError = "Movimiento ${doc.id} con cantidad inválida."
                continue
            }

            val fecha = doc.getString(MovimientoFirestore.FIELD_FECHA)?.trim().orEmpty()
            if (fecha.isEmpty()) {
                errores++
                ultimoError = "Movimiento ${doc.id} sin fecha válida."
                continue
            }

            val subidoEn = doc.getLong(MovimientoFirestore.FIELD_SUBIDO_EN) ?: 0L

            validos.add(
                MovimientoFirestore(
                    remoteId = remoteIdEfectivo,
                    alimentoNombre = alimentoNombre,
                    tipo = tipoValido.name,
                    cantidad = cantidad,
                    fecha = fecha,
                    usuarioUid = uidVerificado,
                    subidoEn = subidoEn
                )
            )
        }

        LoteDescargaMovimientosFirestore(
            totalDocumentos = snapshot.size(),
            validos = validos,
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
        private const val COLLECTION_MOVIMIENTOS = "movimientos"
    }
}

