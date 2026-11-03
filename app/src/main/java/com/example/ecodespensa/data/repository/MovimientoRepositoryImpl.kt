package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.MovimientoDao
import com.example.ecodespensa.data.model.Movimiento
import kotlinx.coroutines.flow.Flow

/**
 * Implementación de [MovimientoRepository] usando Room.
 * Delega operaciones al [MovimientoDao].
 */
class MovimientoRepositoryImpl(
    private val dao: MovimientoDao
) : MovimientoRepository {

    override fun obtenerMovimientos(): Flow<List<Movimiento>> = dao.obtenerTodos()

    override fun obtenerMovimientosPorUsuario(usuarioUid: String): Flow<List<Movimiento>> =
        dao.obtenerPorUsuario(usuarioUid)

    override suspend fun registrarMovimiento(movimiento: Movimiento) {
        dao.insertar(movimiento)
    }

    override suspend fun registrarMovimiento(movimiento: Movimiento, usuarioUid: String) {
        dao.insertar(movimiento.copy(usuarioUid = usuarioUid))
    }
}
