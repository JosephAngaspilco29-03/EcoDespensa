package com.example.ecodespensa.data.local

import androidx.room.*
import com.example.ecodespensa.data.model.Movimiento
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de historial de movimientos.
 * Los resultados se emiten como Flow para reactividad en la UI.
 */
@Dao
interface MovimientoDao {

    @Query("SELECT * FROM movimientos ORDER BY fecha DESC")
    fun obtenerTodos(): Flow<List<Movimiento>>

    /** Obtiene únicamente los movimientos pertenecientes al [usuarioUid] autenticado. */
    @Query("SELECT * FROM movimientos WHERE usuarioUid = :usuarioUid ORDER BY fecha DESC")
    fun obtenerPorUsuario(usuarioUid: String): Flow<List<Movimiento>>

    /** Obtiene una lista puntual (no reactiva) de los movimientos del [usuarioUid], más antiguo primero. */
    @Query("SELECT * FROM movimientos WHERE usuarioUid = :usuarioUid ORDER BY fecha ASC, id ASC")
    suspend fun obtenerListaPorUsuario(usuarioUid: String): List<Movimiento>

    /** Busca un movimiento en Room por su [remoteId] UUID y [usuarioUid] para evitar duplicados o sobrescrituras. */
    @Query("SELECT * FROM movimientos WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid LIMIT 1")
    suspend fun obtenerPorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Movimiento?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(movimiento: Movimiento)

    /** Inserta un movimiento solo si no entra en conflicto, devolviendo su nuevo ID local o -1L si se omitió. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(movimiento: Movimiento): Long

    /** Cuenta cuántos movimientos antiguos no tienen propietario (`usuarioUid = ''`). */
    @Query("SELECT COUNT(*) FROM movimientos WHERE usuarioUid = ''")
    fun contarSinPropietario(): Flow<Int>
}
