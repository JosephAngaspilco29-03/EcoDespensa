package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Origen de un producto dentro de la Lista de Compras persistida en Room.
 */
enum class OrigenItemCompra {
    MANUAL,
    SUGERENCIA_PLANIFICADOR
}

/**
 * Entidad Room que representa un producto en la Lista de Compras del usuario.
 *
 * - Aislado por [usuarioUid] y con [remoteId] UUID persistente.
 * - Marcar [comprado] = `true` NO altera el stock de `alimentos` ni genera registros en `movimientos`.
 */
@Entity(tableName = "items_compra")
data class ItemCompra(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val usuarioUid: String = "",
    val nombre: String = "",
    val cantidad: Double = 1.0,
    val unidad: String = "",
    val comprado: Boolean = false,
    val origen: String = OrigenItemCompra.MANUAL.name,
    val alimentoRemoteId: String = "",
    val notaOrigen: String = "",
    val pendienteSincronizacion: Boolean = true,
    val actualizadoEn: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)

/**
 * Modelo de dominio para una sugerencia automática calculada cruzando el Planificador Semanal
 * con el stock vigente disponible en Mi Despensa.
 */
data class SugerenciaCompraAutomatica(
    val claveAgrupacion: String,
    val alimentoRemoteId: String,
    val nombre: String,
    val cantidadFaltante: Double,
    val unidad: String,
    val cantidadPlanificadaTotal: Double,
    val cantidadDisponibleDespensa: Double,
    val unidadDespensa: String,
    val unidadesIncompatibles: Boolean,
    val detalleExplicacion: String,
    val yaCubiertoEnLista: Boolean
)

