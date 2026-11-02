package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.AlimentoDao
import com.example.ecodespensa.data.local.DespensaTransactionDao
import com.example.ecodespensa.data.model.Alimento
import kotlinx.coroutines.flow.Flow

/**
 * Implementación de [AlimentoRepository] usando Room (persistencia local).
 * Delega operaciones simples al [AlimentoDao] y las transaccionales
 * (insertar+COMPRA, ajustar+AJUSTE) al [DespensaTransactionDao].
 */
class AlimentoRepositoryImpl(
    private val dao: AlimentoDao,
    private val transactionDao: DespensaTransactionDao
) : AlimentoRepository {

    override fun obtenerAlimentos(): Flow<List<Alimento>> = dao.obtenerTodos()

    override suspend fun agregarAlimento(alimento: Alimento) {
        dao.insertar(alimento)
    }

    /**
     * Inserta el alimento y registra automáticamente un movimiento COMPRA
     * en una única transacción Room. Si algo falla, ninguno se persiste.
     */
    override suspend fun agregarAlimentoConCompra(alimento: Alimento) {
        transactionDao.insertarAlimentoConCompra(alimento)
    }

    /**
     * Actualiza el stock del alimento y registra un movimiento AJUSTE
     * en una única transacción Room.
     */
    override suspend fun ajustarStock(alimento: Alimento, cantidadAjuste: Double) {
        transactionDao.ajustarStockConMovimiento(alimento, cantidadAjuste)
    }

    override suspend fun actualizarAlimento(alimento: Alimento) {
        dao.actualizar(alimento)
    }

    /**
     * Edita los datos generales del alimento y registra AJUSTE solo si
     * cambió la cantidad. Delega al [DespensaTransactionDao] para atomicidad.
     */
    override suspend fun editarAlimento(alimento: Alimento, cantidadAnterior: Double) {
        transactionDao.editarAlimentoConAjuste(alimento, cantidadAnterior)
    }

    override suspend fun eliminarAlimento(id: Int) {
        dao.eliminarPorId(id)
    }
}
