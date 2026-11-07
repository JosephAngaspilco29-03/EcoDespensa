package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.MovimientoFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de movimientos de stock.
 */
interface MovimientoRepository {
    fun obtenerMovimientos(): Flow<List<Movimiento>>
    fun obtenerMovimientosPorUsuario(usuarioUid: String): Flow<List<Movimiento>>
    suspend fun registrarMovimiento(movimiento: Movimiento)
    suspend fun registrarMovimiento(movimiento: Movimiento, usuarioUid: String)

    /** Lectura puntual de los movimientos de [usuarioUid] (solo lectura; no genera movimientos). */
    suspend fun obtenerListaMovimientosPorUsuario(usuarioUid: String): List<Movimiento>

    /**
     * Inserta en Room un movimiento recuperado de Cloud Firestore únicamente si su `remoteId`
     * aún no existe para [usuarioUid].
     * - No sobrescribe movimientos existentes.
     * - Autogenera un nuevo ID local (`id = 0`) sin usar IDs locales remotos (`alimentoId = 0`).
     * - No altera el stock de alimentos ni recrea alimentos eliminados.
     *
     * @return `true` si se insertó como nuevo, `false` si ya existía y fue omitido.
     */
    suspend fun insertarMovimientoRecuperadoDeNube(
        movimientoRemoto: MovimientoFirestore,
        usuarioUid: String
    ): Boolean
}
