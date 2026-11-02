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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(movimiento: Movimiento)
}
