package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entidad Room que representa un alimento en la despensa.
 * Se persiste en la base de datos local EcoDespensaDatabase.
 *
 * @param remoteId Identificador remoto único (UUID) generado una sola vez al crear el alimento
 *                 y persistido en Room. No cambia al editar, consumir ni ajustar stock.
 */
@Entity(tableName = "alimentos")
data class Alimento(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val categoria: String = "",
    val cantidadActual: String = "",
    val unidad: String = "",
    val fechaCompra: String = "",
    val fechaVencimiento: String = "",
    val usuarioUid: String = "",
    val pendienteSincronizacion: Boolean = false,
    val actualizadoEn: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)
