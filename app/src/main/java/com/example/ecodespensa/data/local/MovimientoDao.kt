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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(movimiento: Movimiento)

    /** Cuenta cuántos movimientos antiguos no tienen propietario (`usuarioUid = ''`). */
    @Query("SELECT COUNT(*) FROM movimientos WHERE usuarioUid = ''")
    fun contarSinPropietario(): Flow<Int>
}
