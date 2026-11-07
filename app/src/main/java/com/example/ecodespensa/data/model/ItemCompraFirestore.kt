package com.example.ecodespensa.data.model

/**
 * Modelo remoto de un producto de la Lista Inteligente de Compras en Cloud Firestore
 * bajo `usuarios/{uid}/items_compra/{remoteId}`.
 */
data class ItemCompraFirestore(
    val remoteId: String = "",
    val usuarioUid: String = "",
    val nombre: String = "",
    val cantidad: Double = 1.0,
    val unidad: String = "",
    val comprado: Boolean = false,
    val origen: String = OrigenItemCompra.MANUAL.name,
    val alimentoRemoteId: String = "",
    val notaOrigen: String = "",
    val actualizadoEn: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        FIELD_REMOTE_ID to remoteId,
        FIELD_USUARIO_UID to usuarioUid,
        FIELD_NOMBRE to nombre,
        FIELD_CANTIDAD to cantidad,
        FIELD_UNIDAD to unidad,
        FIELD_COMPRADO to comprado,
        FIELD_ORIGEN to origen,
        FIELD_ALIMENTO_REMOTE_ID to alimentoRemoteId,
        FIELD_NOTA_ORIGEN to notaOrigen,
        FIELD_ACTUALIZADO_EN to actualizadoEn
    )

    companion object {
        const val FIELD_REMOTE_ID = "remoteId"
        const val FIELD_USUARIO_UID = "usuarioUid"
        const val FIELD_NOMBRE = "nombre"
        const val FIELD_CANTIDAD = "cantidad"
        const val FIELD_UNIDAD = "unidad"
        const val FIELD_COMPRADO = "comprado"
        const val FIELD_ORIGEN = "origen"
        const val FIELD_ALIMENTO_REMOTE_ID = "alimentoRemoteId"
        const val FIELD_NOTA_ORIGEN = "notaOrigen"
        const val FIELD_ACTUALIZADO_EN = "actualizadoEn"
    }
}

/**
 * Convierte un [ItemCompra] local al modelo de documento remoto [ItemCompraFirestore].
 */
fun ItemCompra.toFirestoreModel(
    uidVerificado: String,
    marcaActualizacion: Long = if (actualizadoEn > 0L) actualizadoEn else System.currentTimeMillis()
): ItemCompraFirestore = ItemCompraFirestore(
    remoteId = remoteId.trim(),
    usuarioUid = uidVerificado,
    nombre = nombre.trim(),
    cantidad = cantidad,
    unidad = unidad.trim(),
    comprado = comprado,
    origen = origen.trim().ifEmpty { OrigenItemCompra.MANUAL.name },
    alimentoRemoteId = alimentoRemoteId.trim(),
    notaOrigen = notaOrigen.trim(),
    actualizadoEn = marcaActualizacion
)

