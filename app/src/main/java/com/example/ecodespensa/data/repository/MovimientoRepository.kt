package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.Movimiento
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de movimientos de stock.
 */
interface MovimientoRepository {
    fun obtenerMovimientos(): Flow<List<Movimiento>>
    suspend fun registrarMovimiento(movimiento: Movimiento)
}
