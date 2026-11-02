package com.example.ecodespensa.data.local

import androidx.room.*
import com.example.ecodespensa.data.model.Alimento
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones CRUD de alimentos.
 * Los resultados se emiten como Flow para que la UI reaccione automáticamente a cambios.
 */
@Dao
interface AlimentoDao {

    @Query("SELECT * FROM alimentos ORDER BY fechaVencimiento ASC")
    fun obtenerTodos(): Flow<List<Alimento>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(alimento: Alimento)

    /** Inserta y devuelve el rowId generado (útil para transacciones atómicas). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarYRetornarId(alimento: Alimento): Long

    @Update
    suspend fun actualizar(alimento: Alimento)

    @Delete
    suspend fun eliminar(alimento: Alimento)

    @Query("DELETE FROM alimentos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)
}
