package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.EliminacionItemCompraPendiente
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.ItemCompraFirestore
import com.example.ecodespensa.data.model.OrigenItemCompra
import com.example.ecodespensa.data.model.toFirestoreModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

enum class EstadoSubidaItemCompra {
    SUBIDO,
    OMITIDO_YA_EXISTE,
    CONFLICTO_REMOTO_MAS_RECIENTE
}

data class RespuestaSubidaItemCompra(
    val estado: EstadoSubidaItemCompra,
    val actualizadoEnConfirmado: Long = 0L
)

enum class EstadoEliminacionItemCompra {
    ELIMINADO_CONFIRMADO,
    CONFLICTO_CAMBIO_REMOTO_POSTERIOR
}

data class ResultadoSubidaListaCompras(
    val total: Int,
    val subidos: Int,
    val eliminados: Int,
    val omitidos: Int,
    val conflictos: Int,
    val errores: Int,
    val detalleError: String? = null
)

data class ResultadoDescargaListaCompras(
    val totalRemotos: Int,
    val descargados: Int,
    val omitidos: Int,
    val conflictos: Int,
    val errores: Int,
    val detalleError: String? = null
)

data class LoteDescargaItemsCompraFirestore(
    val totalDocumentos: Int,
    val validos: List<ItemCompraFirestore>,
    val erroresValidacion: Int,
    val detalleUltimoError: String? = null
)

/**
 * Repositorio para sincronizar la Lista Inteligente de Compras con Cloud Firestore bajo
 * `usuarios/{uid}/items_compra/{remoteId}`.
 *
 * - Verifica siempre que `uid` coincida con el usuario autenticado en Firebase.
 * - Conserva el `remoteId` UUID persistente de cada producto.
 * - Sincroniza nombre, cantidad, unidad, comprado, origen, alimentoRemoteId, notaOrigen y actualizadoEn.
 * - Detecta conflictos remotos (`actualizadoEn` vs `baseRemotaActualizadoEn`) al subir ediciones,
 *   cambios de `comprado` o eliminaciones pendientes (tombstones).
 */
class FirestoreItemCompraRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Sube o actualiza un [ItemCompra] en `usuarios/{uid}/items_compra/{remoteId}`:
     * - Si el producto local NO tiene cambios pendientes (`pendienteSincronizacion == false`) y ya existe
     *   en Firestore, devuelve [EstadoSubidaItemCompra.OMITIDO_YA_EXISTE].
     * - Si tiene cambios pendientes (`pendienteSincronizacion == true`), verifica que el documento
     *   remoto no tenga una marca `actualizadoEn` más reciente que `baseRemotaActualizadoEn`
     *   para proteger cambios remotos en conflicto.
     */
    suspend fun sincronizarItemCompra(
        uid: String,
        item: ItemCompra
    ): Result<RespuestaSubidaItemCompra> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (item.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("El producto de la lista no pertenece al usuario autenticado actual.")
        }
        val remoteIdLimpio = item.remoteId.trim()
        require(remoteIdLimpio.isNotEmpty()) { "El producto no tiene un remoteId válido." }

        val docRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_ITEMS_COMPRA)
            .document(remoteIdLimpio)

        // 1. Lectura en servidor real para comprobar conectividad y estado remoto actual
        val previo = withTimeout(6_000L) { docRef.get(Source.SERVER).await() }

        if (!item.pendienteSincronizacion && previo.exists()) {
            val marcaRemota = previo.getLong(ItemCompraFirestore.FIELD_ACTUALIZADO_EN)
                ?: item.actualizadoEn
            return@runCatching RespuestaSubidaItemCompra(
                estado = EstadoSubidaItemCompra.OMITIDO_YA_EXISTE,
                actualizadoEnConfirmado = marcaRemota
            )
        }

        val marcaEscritura = item.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val modelo = item.toFirestoreModel(
            uidVerificado = uidVerificado,
            marcaActualizacion = marcaEscritura
        )

        val resultadoTx = withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val actualSnap = transaction.get(docRef)
                if (actualSnap.exists()) {
                    val uidRemoto = actualSnap.getString(ItemCompraFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
                    if (uidRemoto.isNotEmpty() && uidRemoto != uidVerificado) {
                        throw SecurityException("El documento remoto no pertenece al usuario autenticado.")
                    }
                    val remotoActualizadoEn =
                        actualSnap.getLong(ItemCompraFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                    val baseConocida = item.baseRemotaActualizadoEn

                    val hayConflicto = if (baseConocida > 0L) {
                        remotoActualizadoEn > baseConocida && remotoActualizadoEn > marcaEscritura
                    } else {
                        remotoActualizadoEn > marcaEscritura
                    }

                    if (hayConflicto) {
                        return@runTransaction EstadoSubidaItemCompra.CONFLICTO_REMOTO_MAS_RECIENTE
                    }
                }
                transaction.set(docRef, modelo.toMap())
                EstadoSubidaItemCompra.SUBIDO
            }.await()
        }

        RespuestaSubidaItemCompra(
            estado = resultadoTx,
            actualizadoEnConfirmado = marcaEscritura
        )
    }

    /**
     * Sincroniza la eliminación pendiente (tombstone) de un producto en
     * `usuarios/{uid}/items_compra/{remoteId}` verificando antes que el documento remoto
     * no tenga cambios posteriores.
     */
    suspend fun sincronizarEliminacionItemCompra(
        uid: String,
        tombstone: EliminacionItemCompraPendiente
    ): Result<EstadoEliminacionItemCompra> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        if (tombstone.usuarioUid.trim() != uidVerificado) {
            throw SecurityException("La eliminación pendiente no pertenece al usuario autenticado.")
        }
        val remoteIdLimpio = tombstone.remoteId.trim()
        require(remoteIdLimpio.isNotEmpty()) { "El remoteId de la eliminación no es válido." }

        val docRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_ITEMS_COMPRA)
            .document(remoteIdLimpio)

        val previo = withTimeout(6_000L) { docRef.get(Source.SERVER).await() }
        if (!previo.exists()) {
            return@runCatching EstadoEliminacionItemCompra.ELIMINADO_CONFIRMADO
        }

        withTimeout(8_000L) {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (!snap.exists()) {
                    return@runTransaction EstadoEliminacionItemCompra.ELIMINADO_CONFIRMADO
                }
                val remotoActualizadoEn =
                    snap.getLong(ItemCompraFirestore.FIELD_ACTUALIZADO_EN) ?: 0L
                val limiteConocido = maxOf(tombstone.baseRemotaActualizadoEn, tombstone.fechaEliminacion)
                if (remotoActualizadoEn > limiteConocido) {
                    EstadoEliminacionItemCompra.CONFLICTO_CAMBIO_REMOTO_POSTERIOR
                } else {
                    transaction.delete(docRef)
                    EstadoEliminacionItemCompra.ELIMINADO_CONFIRMADO
                }
            }.await()
        }
    }

    /**
     * Lee y valida `usuarios/{uid}/items_compra` del usuario autenticado desde `Source.SERVER`.
     */
    suspend fun descargarItemsCompraValidados(
        uid: String
    ): Result<LoteDescargaItemsCompraFirestore> = runCatching {
        val uidVerificado = verificarUidAutenticado(uid)
        val colRef = firestore.collection(COLLECTION_USUARIOS)
            .document(uidVerificado)
            .collection(COLLECTION_ITEMS_COMPRA)

        val snapshot = withTimeout(10_000L) {
            colRef.get(Source.SERVER).await()
        }

        val validos = mutableListOf<ItemCompraFirestore>()
        val remoteIdsVistos = mutableSetOf<String>()
        var errores = 0
        var ultimoError: String? = null

        for (doc in snapshot.documents) {
            val remoteIdCampo = doc.getString(ItemCompraFirestore.FIELD_REMOTE_ID)?.trim().orEmpty()
            val remoteIdEfectivo = remoteIdCampo.ifEmpty { doc.id.trim() }

            if (remoteIdEfectivo.isEmpty()) {
                errores++
                ultimoError = "Documento de compra sin remoteId válido."
                continue
            }
            if (remoteIdCampo.isNotEmpty() && remoteIdCampo != doc.id.trim()) {
                errores++
                ultimoError = "El remoteId del producto ${doc.id} no coincide con su clave."
                continue
            }
            if (!remoteIdsVistos.add(remoteIdEfectivo)) {
                errores++
                ultimoError = "Producto duplicado con remoteId $remoteIdEfectivo."
                continue
            }

            val uidDoc = doc.getString(ItemCompraFirestore.FIELD_USUARIO_UID)?.trim().orEmpty()
            if (uidDoc.isEmpty() || uidDoc != uidVerificado) {
                errores++
                ultimoError = "El producto ${doc.id} no pertenece al usuario autenticado."
                continue
            }

            val nombre = doc.getString(ItemCompraFirestore.FIELD_NOMBRE)?.trim().orEmpty()
            if (nombre.isEmpty()) {
                errores++
                ultimoError = "El producto ${doc.id} no tiene nombre."
                continue
            }

            val cantidad = doc.getDouble(ItemCompraFirestore.FIELD_CANTIDAD) ?: 0.0
            if (!cantidad.isFinite() || cantidad <= 0.0) {
                errores++
                ultimoError = "El producto ${doc.id} tiene cantidad inválida ($cantidad)."
                continue
            }

            val unidad = doc.getString(ItemCompraFirestore.FIELD_UNIDAD)?.trim().orEmpty()
            val comprado = doc.getBoolean(ItemCompraFirestore.FIELD_COMPRADO) ?: false
            val origenRaw = doc.getString(ItemCompraFirestore.FIELD_ORIGEN)?.trim().orEmpty()
            val origenValido = runCatching { OrigenItemCompra.valueOf(origenRaw).name }
                .getOrDefault(OrigenItemCompra.MANUAL.name)
            val alimentoRemoteId = doc.getString(ItemCompraFirestore.FIELD_ALIMENTO_REMOTE_ID)?.trim().orEmpty()
            val notaOrigen = doc.getString(ItemCompraFirestore.FIELD_NOTA_ORIGEN)?.trim().orEmpty()
            val actualizadoEn = doc.getLong(ItemCompraFirestore.FIELD_ACTUALIZADO_EN) ?: 0L

            validos.add(
                ItemCompraFirestore(
                    remoteId = remoteIdEfectivo,
                    usuarioUid = uidVerificado,
                    nombre = nombre,
                    cantidad = cantidad,
                    unidad = unidad,
                    comprado = comprado,
                    origen = origenValido,
                    alimentoRemoteId = alimentoRemoteId,
                    notaOrigen = notaOrigen,
                    actualizadoEn = actualizadoEn
                )
            )
        }

        LoteDescargaItemsCompraFirestore(
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
        private const val COLLECTION_ITEMS_COMPRA = "items_compra"
    }
}

