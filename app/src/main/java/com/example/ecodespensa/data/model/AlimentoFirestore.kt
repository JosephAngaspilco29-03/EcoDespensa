package com.example.ecodespensa.data.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.UUID

/**
 * Modelo de datos para persistir y consultar un [Alimento] en Cloud Firestore
 * dentro de la ruta `usuarios/{uid}/alimentos/{remoteId}`.
 *
 * Utiliza el [remoteId] (UUID único generado una sola vez y persistido en Room)
 * como identificador del documento remoto, sin depender del nombre, categoría,
 * fechas ni del `id` autoincremental local de Room.
 */
data class AlimentoFirestore(
    val remoteId: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val cantidadActual: String = "",
    val unidad: String = "",
    val fechaCompra: String = "",
    val fechaVencimiento: String = "",
    val usuarioUid: String = "",
    val actualizadoEn: Long = 0L
) {

    /**
     * Convierte este modelo en un mapa clave-valor compatible con Firestore.
     */
    fun toMap(): Map<String, Any> = mapOf(
        FIELD_REMOTE_ID to remoteId,
        FIELD_NOMBRE to nombre,
        FIELD_CATEGORIA to categoria,
        FIELD_CANTIDAD_ACTUAL to cantidadActual,
        FIELD_UNIDAD to unidad,
        FIELD_FECHA_COMPRA to fechaCompra,
        FIELD_FECHA_VENCIMIENTO to fechaVencimiento,
        FIELD_USUARIO_UID to usuarioUid,
        FIELD_ACTUALIZADO_EN to actualizadoEn
    )

    companion object {
        const val FIELD_REMOTE_ID = "remoteId"
        const val FIELD_NOMBRE = "nombre"
        const val FIELD_CATEGORIA = "categoria"
        const val FIELD_CANTIDAD_ACTUAL = "cantidadActual"
        const val FIELD_UNIDAD = "unidad"
        const val FIELD_FECHA_COMPRA = "fechaCompra"
        const val FIELD_FECHA_VENCIMIENTO = "fechaVencimiento"
        const val FIELD_USUARIO_UID = "usuarioUid"
        const val FIELD_ACTUALIZADO_EN = "actualizadoEn"

        /**
         * Construye un [AlimentoFirestore] a partir de un [DocumentSnapshot] de Firestore.
         * El `remoteId` se toma del campo `remoteId` o del ID del propio documento.
         */
        fun fromSnapshot(snapshot: DocumentSnapshot): AlimentoFirestore? {
            if (!snapshot.exists()) return null
            val remoteId = snapshot.getString(FIELD_REMOTE_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: snapshot.id.trim()
            if (remoteId.isEmpty()) return null

            val nombre = snapshot.getString(FIELD_NOMBRE)?.trim().orEmpty()
            if (nombre.isEmpty()) return null

            val actualizadoLong = snapshot.getLong(FIELD_ACTUALIZADO_EN) ?: 0L

            return AlimentoFirestore(
                remoteId = remoteId,
                nombre = nombre,
                categoria = snapshot.getString(FIELD_CATEGORIA).orEmpty(),
                cantidadActual = snapshot.getString(FIELD_CANTIDAD_ACTUAL).orEmpty(),
                unidad = snapshot.getString(FIELD_UNIDAD).orEmpty(),
                fechaCompra = snapshot.getString(FIELD_FECHA_COMPRA).orEmpty(),
                fechaVencimiento = snapshot.getString(FIELD_FECHA_VENCIMIENTO).orEmpty(),
                usuarioUid = snapshot.getString(FIELD_USUARIO_UID).orEmpty(),
                actualizadoEn = actualizadoLong
            )
        }
    }
}

/**
 * Convierte una entidad local [Alimento] de Room al modelo [AlimentoFirestore],
 * conservando intacto su [Alimento.remoteId] persistente.
 */
fun Alimento.toFirestoreModel(
    uidOverride: String? = null,
    actualizadoEn: Long = System.currentTimeMillis()
): AlimentoFirestore {
    val propietarioUid = (uidOverride ?: usuarioUid).trim()
    val idRemoto = remoteId.trim()
    require(idRemoto.isNotEmpty()) {
        "El alimento debe tener un remoteId persistido antes de convertirse a AlimentoFirestore."
    }

    return AlimentoFirestore(
        remoteId = idRemoto,
        nombre = nombre.trim(),
        categoria = categoria.trim(),
        cantidadActual = cantidadActual.trim(),
        unidad = unidad.trim(),
        fechaCompra = fechaCompra.trim(),
        fechaVencimiento = fechaVencimiento.trim(),
        usuarioUid = propietarioUid,
        actualizadoEn = actualizadoEn
    )
}

/**
 * Convierte un [AlimentoFirestore] remoto a la entidad local [Alimento] de Room,
 * preservando su [AlimentoFirestore.remoteId] único.
 * Si [idLocalExistente] se proporciona (cuando el `remoteId` ya existe en Room),
 * conserva ese `id` local para actualizar la fila sin crear duplicados.
 */
fun AlimentoFirestore.toRoomEntity(idLocalExistente: Int = 0): Alimento {
    val idRemoto = remoteId.trim().takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
    return Alimento(
        id = idLocalExistente.coerceAtLeast(0),
        remoteId = idRemoto,
        nombre = nombre.trim(),
        categoria = categoria.trim(),
        cantidadActual = cantidadActual.trim(),
        unidad = unidad.trim(),
        fechaCompra = fechaCompra.trim(),
        fechaVencimiento = fechaVencimiento.trim(),
        usuarioUid = usuarioUid.trim()
    )
}
