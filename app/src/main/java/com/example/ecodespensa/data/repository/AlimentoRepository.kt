package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.Alimento
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de alimentos.
 * Define el contrato de acceso a datos para los alimentos de la despensa.
 *
 * Preparado para ser implementado con:
 * - AlimentoRepositoryImpl (Room para persistencia local)
 * - Sincronización con Cloud Firestore en etapas posteriores
 */
interface AlimentoRepository {
    fun obtenerAlimentos(): Flow<List<Alimento>>
    /** Agrega un alimento y registra automáticamente un movimiento COMPRA (operación atómica). */
    suspend fun agregarAlimento(alimento: Alimento)
    /** Registra alimento + movimiento COMPRA en una única transacción Room. */
    suspend fun agregarAlimentoConCompra(alimento: Alimento)
    /** Actualiza el alimento y registra un movimiento AJUSTE en una única transacción Room. */
    suspend fun ajustarStock(alimento: Alimento, cantidadAjuste: Double)
    suspend fun actualizarAlimento(alimento: Alimento)
    /**
     * Edita los datos generales del alimento y registra AJUSTE solo si
     * cambió [cantidadAnterior]. NO registra movimiento COMPRA.
     */
    suspend fun editarAlimento(alimento: Alimento, cantidadAnterior: Double)
    suspend fun eliminarAlimento(id: Int)
}
