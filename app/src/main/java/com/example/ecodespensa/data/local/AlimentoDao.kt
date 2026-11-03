package com.example.ecodespensa.data.local

import androidx.room.*
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EliminacionPendiente
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones CRUD de alimentos.
 * Los resultados se emiten como Flow para que la UI reaccione automáticamente a cambios.
 */
@Dao
interface AlimentoDao {

    @Query("SELECT * FROM alimentos ORDER BY fechaVencimiento ASC")
    fun obtenerTodos(): Flow<List<Alimento>>

    /** Obtiene únicamente los alimentos pertenecientes al [usuarioUid] autenticado. */
    @Query("SELECT * FROM alimentos WHERE usuarioUid = :usuarioUid ORDER BY fechaVencimiento ASC")
    fun obtenerPorUsuario(usuarioUid: String): Flow<List<Alimento>>

    /** Obtiene un alimento específico verificando que pertenezca a [usuarioUid]. */
    @Query("SELECT * FROM alimentos WHERE id = :id AND usuarioUid = :usuarioUid LIMIT 1")
    suspend fun obtenerPorIdYUsuario(id: Int, usuarioUid: String): Alimento?

    /** Obtiene un alimento por su identificador remoto persistente [remoteId] y [usuarioUid]. */
    @Query("SELECT * FROM alimentos WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid LIMIT 1")
    suspend fun obtenerPorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Alimento?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(alimento: Alimento)

    /**
     * Inserta un alimento únicamente si no entra en conflicto con una fila existente.
     * Devuelve `-1L` si la inserción fue ignorada.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(alimento: Alimento): Long

    /** Inserta y devuelve el rowId generado (útil para transacciones atómicas). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarYRetornarId(alimento: Alimento): Long

    @Update
    suspend fun actualizar(alimento: Alimento)

    /**
     * Actualiza un alimento únicamente si su `id` y `usuarioUid` coinciden con el propietario,
     * marcando además `pendienteSincronizacion = 1` y `actualizadoEn` para que ninguna actualización
     * omita el estado de sincronización.
     * @return Número de filas afectadas (1 si se actualizó, 0 si no pertenece al usuario).
     */
    @Query(
        """
        UPDATE alimentos
        SET nombre = :nombre,
            categoria = :categoria,
            cantidadActual = :cantidadActual,
            unidad = :unidad,
            fechaCompra = :fechaCompra,
            fechaVencimiento = :fechaVencimiento,
            pendienteSincronizacion = 1,
            actualizadoEn = :actualizadoEn
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    suspend fun actualizarPorUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        categoria: String,
        cantidadActual: String,
        unidad: String,
        fechaCompra: String,
        fechaVencimiento: String,
        actualizadoEn: Long = System.currentTimeMillis()
    ): Int

    /**
     * Marca un alimento del usuario como sincronizado con Cloud Firestore,
     * desactivando `pendienteSincronizacion` y guardando la marca de tiempo remota confirmada.
     */
    @Query(
        """
        UPDATE alimentos
        SET pendienteSincronizacion = 0,
            actualizadoEn = :actualizadoEn,
            baseRemotaActualizadoEn = :baseRemotaActualizadoEn
        WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid
        """
    )
    suspend fun marcarSincronizadoPorRemoteId(
        remoteId: String,
        usuarioUid: String,
        actualizadoEn: Long,
        baseRemotaActualizadoEn: Long
    ): Int

    /**
     * Registra la marca de tiempo base remota de un alimento si aún no la tenía,
     * sin alterar sus datos ni marcarlo como pendiente.
     */
    @Query(
        """
        UPDATE alimentos
        SET baseRemotaActualizadoEn = :baseRemotaActualizadoEn
        WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid AND baseRemotaActualizadoEn = 0
        """
    )
    suspend fun registrarBaseRemotaSiCero(
        remoteId: String,
        usuarioUid: String,
        baseRemotaActualizadoEn: Long
    ): Int

    @Delete
    suspend fun eliminar(alimento: Alimento)

    @Query("DELETE FROM alimentos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    /**
     * Elimina un alimento por su [id] verificando que pertenezca a [usuarioUid].
     * @return Número de filas eliminadas.
     */
    @Query("DELETE FROM alimentos WHERE id = :id AND usuarioUid = :usuarioUid")
    suspend fun eliminarPorIdYUsuario(id: Int, usuarioUid: String): Int

    /**
     * Obtiene la lista reactiva de eliminaciones pendientes (tombstones) del [usuarioUid] autenticado.
     */
    @Query("SELECT * FROM eliminaciones_pendientes WHERE usuarioUid = :usuarioUid ORDER BY fechaEliminacion ASC")
    fun obtenerEliminacionesPendientesPorUsuario(usuarioUid: String): Flow<List<EliminacionPendiente>>

    /**
     * Consulta si existe una eliminación pendiente para [remoteId] y [usuarioUid].
     */
    @Query("SELECT * FROM eliminaciones_pendientes WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid LIMIT 1")
    suspend fun obtenerEliminacionPendientePorRemoteIdYUsuario(
        remoteId: String,
        usuarioUid: String
    ): EliminacionPendiente?

    /**
     * Limpia el registro de eliminación pendiente una vez confirmada su baja en Cloud Firestore.
     */
    @Query("DELETE FROM eliminaciones_pendientes WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid")
    suspend fun eliminarTombstonePorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Int

    /** Cuenta cuántos alimentos antiguos no tienen propietario (`usuarioUid = ''`). */
    @Query("SELECT COUNT(*) FROM alimentos WHERE usuarioUid = ''")
    fun contarSinPropietario(): Flow<Int>

    /**
     * Emite `true` si existen alimentos o movimientos antiguos sin propietario (`usuarioUid = ''`).
     */
    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM alimentos WHERE usuarioUid = '') +
            (SELECT COUNT(*) FROM movimientos WHERE usuarioUid = '')
        ) > 0
        """
    )
    fun existenDatosSinPropietario(): Flow<Boolean>
}
