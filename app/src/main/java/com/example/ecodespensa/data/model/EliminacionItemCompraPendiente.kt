package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro persistente (tombstone) de una eliminación pendiente de un producto de la
 * Lista Inteligente de Compras para sincronizar con Cloud Firestore
 * (`usuarios/{uid}/items_compra/{remoteId}`).
 *
 * Evita que los productos eliminados individualmente o limpiados como comprados
 * reaparezcan al recuperar la lista desde la nube.
 */
@Entity(tableName = "eliminaciones_items_compra_pendientes")
data class EliminacionItemCompraPendiente(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = "",
    val usuarioUid: String = "",
    val nombre: String = "",
    val fechaEliminacion: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)

