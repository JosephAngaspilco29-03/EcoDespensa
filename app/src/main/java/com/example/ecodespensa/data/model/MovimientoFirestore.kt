package com.example.ecodespensa.data.model

/**
 * Modelo de datos para persistir un [Movimiento] en Cloud Firestore dentro de
 * `usuarios/{uid}/movimientos/{remoteId}`.
 *
 * Usa el [remoteId] (UUID persistente en Room) como ID del documento; no incluye
 * el ID autoincremental local ni el `alimentoId` local (no es globalmente único).
 */
data class MovimientoFirestore(
    val remoteId: String = "",
    val alimentoNombre: String = "",
    val tipo: String = "",
    val cantidad: Double = 0.0,
    val fecha: String = "",
    val usuarioUid: String = "",
    val subidoEn: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        FIELD_REMOTE_ID to remoteId,
        FIELD_ALIMENTO_NOMBRE to alimentoNombre,
        FIELD_TIPO to tipo,
        FIELD_CANTIDAD to cantidad,
        FIELD_FECHA to fecha,
        FIELD_USUARIO_UID to usuarioUid,
        FIELD_SUBIDO_EN to subidoEn
    )

    companion object {
        const val FIELD_REMOTE_ID = "remoteId"
        const val FIELD_ALIMENTO_NOMBRE = "alimentoNombre"
        const val FIELD_TIPO = "tipo"
        const val FIELD_CANTIDAD = "cantidad"
        const val FIELD_FECHA = "fecha"
        const val FIELD_USUARIO_UID = "usuarioUid"
        const val FIELD_SUBIDO_EN = "subidoEn"
    }
}

/** Convierte un [Movimiento] de Room al modelo Firestore conservando su `remoteId` persistente. */
fun Movimiento.toFirestoreModel(uidOverride: String? = null): MovimientoFirestore {
    val idRemoto = remoteId.trim()
    require(idRemoto.isNotEmpty()) { "El movimiento debe tener un remoteId persistido." }
    return MovimientoFirestore(
        remoteId = idRemoto,
        alimentoNombre = alimentoNombre.trim(),
        tipo = tipo.name,
        cantidad = cantidad,
        fecha = fecha,
        usuarioUid = (uidOverride ?: usuarioUid).trim(),
        subidoEn = System.currentTimeMillis()
    )
}

/**
 * Convierte un [MovimientoFirestore] validado en una entidad [Movimiento] de Room:
 * - Asigna `id = 0` para que Room autogenere un nuevo ID local.
 * - Asigna `alimentoId = 0` (no depende de IDs locales de otro dispositivo ni requiere recrear alimentos eliminados).
 * - Conserva `remoteId`, `alimentoNombre`, `tipo`, `cantidad`, `fecha` y `usuarioUid`.
 */
fun MovimientoFirestore.toRoomEntity(uidVerificado: String): Movimiento {
    val idRemoto = remoteId.trim()
    require(idRemoto.isNotEmpty()) { "El movimiento remoto debe tener un remoteId válido." }
    return Movimiento(
        id = 0,
        alimentoId = 0,
        alimentoNombre = alimentoNombre.trim(),
        tipo = TipoMovimiento.valueOf(tipo.trim()),
        cantidad = cantidad,
        fecha = fecha.trim(),
        usuarioUid = uidVerificado.trim(),
        remoteId = idRemoto
    )
}


