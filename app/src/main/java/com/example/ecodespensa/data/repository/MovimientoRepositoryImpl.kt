package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.MovimientoDao
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.MovimientoFirestore
import com.example.ecodespensa.data.model.toRoomEntity
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

    override suspend fun obtenerListaMovimientosPorUsuario(usuarioUid: String): List<Movimiento> =
        dao.obtenerListaPorUsuario(usuarioUid)

    override suspend fun insertarMovimientoRecuperadoDeNube(
        movimientoRemoto: MovimientoFirestore,
        usuarioUid: String
    ): Boolean {
        val uidLimpio = usuarioUid.trim()
        require(uidLimpio.isNotEmpty()) { "El usuarioUid no puede estar vacío." }
        val remoteIdLimpio = movimientoRemoto.remoteId.trim()
        require(remoteIdLimpio.isNotEmpty()) { "El remoteId del movimiento no puede estar vacío." }

        val existente = dao.obtenerPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)
        if (existente != null) {
            return false
        }

        val entidadNueva = movimientoRemoto.toRoomEntity(uidVerificado = uidLimpio)
        val rowId = dao.insertarSiNoExiste(entidadNueva)
        return rowId != -1L
    }
}
