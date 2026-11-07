package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tipos de movimiento registrados en el historial de la despensa.
 */
enum class TipoMovimiento {
    COMPRA,
    CONSUMO,
    DESPERDICIO,
    DONACION,
    AJUSTE
}

/**
 * Entidad Room que representa un movimiento de stock en la despensa.
 * Cada movimiento queda registrado con el alimento, tipo, cantidad y fecha.
 *
 * @param remoteId Identificador remoto único (UUID) generado una sola vez y persistido en Room.
 *                 No depende del ID autoincremental local; se usa como ID del documento en
 *                 `usuarios/{uid}/movimientos/{remoteId}`.
 */
@Entity(tableName = "movimientos")
data class Movimiento(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val alimentoId: Int,
    val alimentoNombre: String,   // desnormalizado para mostrar en historial sin JOIN
    val tipo: TipoMovimiento,
    val cantidad: Double,
    val fecha: String,            // ISO-8601: yyyy-MM-dd HH:mm
    val usuarioUid: String = "",
    val remoteId: String = UUID.randomUUID().toString()
)
