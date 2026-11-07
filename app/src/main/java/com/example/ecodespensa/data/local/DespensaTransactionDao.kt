package com.example.ecodespensa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Resultado atómico de una operación transaccional en Room que actualiza un [Alimento]
 * y opcionalmente registra un [Movimiento] en el historial local (con su `remoteId` UUID persistente).
 */
data class ResultadoOperacionStock(
    val alimentoActualizado: Alimento,
    val movimientoRegistrado: Movimiento? = null
)

/**
 * DAO de operaciones transaccionales atómicas de EcoDespensa.
 * Al ser una clase abstracta de Room, puede tener métodos concretos anotados con @Transaction
 * que combinan múltiples operaciones DAO de forma atómica.
 *
 * Garantiza que el alimento y su movimiento se persistan juntos conservando el `usuarioUid`,
 * o que ninguno se guarde si ocurre un error.
 */
@Dao
abstract class DespensaTransactionDao {

    // ── Operaciones internas (duplicadas aquí para evitar dependencia de inyección) ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarAlimento(alimento: Alimento): Long

    @Query("SELECT * FROM alimentos WHERE id = :id AND usuarioUid = :usuarioUid LIMIT 1")
    abstract suspend fun _obtenerAlimentoPorIdYUsuario(id: Int, usuarioUid: String): Alimento?

    @Update
    abstract suspend fun _actualizarAlimento(alimento: Alimento)

    @Query(
        """
        UPDATE alimentos
        SET nombre = :nombre,
            categoria = :categoria,
            cantidadActual = :cantidadActual,
            unidad = :unidad,
            fechaCompra = :fechaCompra,
            fechaVencimiento = :fechaVencimiento
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun _actualizarAlimentoPorUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        categoria: String,
        cantidadActual: String,
        unidad: String,
        fechaCompra: String,
        fechaVencimiento: String
    ): Int

    /**
     * Actualiza los campos editables de un alimento verificando su propietario (`id` y `usuarioUid`),
     * conservando intacto su `remoteId` y `baseRemotaActualizadoEn`, y registrando de forma
     * persistente su estado `pendienteSincronizacion` y la marca `actualizadoEn`.
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
            pendienteSincronizacion = :pendienteSincronizacion,
            actualizadoEn = :actualizadoEn
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun _editarAlimentoMarcandoPendientePorUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        categoria: String,
        cantidadActual: String,
        unidad: String,
        fechaCompra: String,
        fechaVencimiento: String,
        pendienteSincronizacion: Boolean,
        actualizadoEn: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarMovimiento(movimiento: Movimiento): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarEliminacionPendiente(eliminacion: EliminacionPendiente): Long

    @Query("DELETE FROM eliminaciones_pendientes WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid")
    abstract suspend fun _eliminarTombstonePorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Int

    @Query("DELETE FROM alimentos WHERE id = :id AND usuarioUid = :usuarioUid")
    abstract suspend fun _eliminarAlimentoPorIdYUsuario(id: Int, usuarioUid: String): Int

    @Query("UPDATE alimentos SET usuarioUid = :usuarioUid WHERE usuarioUid = ''")
    abstract suspend fun _vincularAlimentosSinPropietario(usuarioUid: String): Int

    @Query("UPDATE movimientos SET usuarioUid = :usuarioUid WHERE usuarioUid = ''")
    abstract suspend fun _vincularMovimientosSinPropietario(usuarioUid: String): Int

    // ── Operaciones transaccionales públicas ──────────────────────────────────────

    /**
     * Vincula en una única transacción Room todos los alimentos y movimientos antiguos
     * cuyo `usuarioUid` sea `""` al [usuarioUid] autenticado.
     * No modifica registros que ya tienen propietario.
     */
    @Transaction
    open suspend fun vincularDatosAntiguosAUsuario(usuarioUid: String) {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return
        _vincularAlimentosSinPropietario(uidLimpio)
        _vincularMovimientosSinPropietario(uidLimpio)
    }

    /**
     * Inserta el [alimento] y crea automáticamente un movimiento de tipo COMPRA
     * con la cantidad inicial registrada, el mismo `usuarioUid` del alimento y un
     * `remoteId` UUID único y persistente.
     * Todo ocurre dentro de una única transacción Room.
     *
     * @return El [Movimiento] de tipo COMPRA persistido en Room (con su `id` local y `remoteId` UUID).
     */
    @Transaction
    open suspend fun insertarAlimentoConCompra(alimento: Alimento): Movimiento {
        val alimentoFinal = if (alimento.remoteId.isBlank()) {
            alimento.copy(remoteId = java.util.UUID.randomUUID().toString())
        } else {
            alimento
        }
        val nuevoId = _insertarAlimento(alimentoFinal)
        val cantidad = parsearCantidad(alimentoFinal.cantidadActual)
        return registrarMovimientoLocal(
            alimentoId = nuevoId.toInt(),
            alimentoNombre = alimentoFinal.nombre,
            tipo = TipoMovimiento.COMPRA,
            cantidad = cantidad,
            usuarioUid = alimentoFinal.usuarioUid
        )
    }

    /**
     * Actualiza el [alimento] verificando su [usuarioUid], marca `pendienteSincronizacion = true`
     * y `actualizadoEn`, y registra un movimiento AJUSTE con el mismo `usuarioUid` y `remoteId` UUID.
     * [cantidadAjuste] es la cantidad que se incrementó (siempre positiva).
     * Impide stock negativo y conserva el `remoteId` original.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun ajustarStockConMovimiento(
        alimento: Alimento,
        cantidadAjuste: Double,
        usuarioUid: String = alimento.usuarioUid,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock? = modificarStockConMovimiento(
        alimentoId = alimento.id,
        usuarioUid = usuarioUid,
        deltaStock = cantidadAjuste,
        cantidadMovimiento = cantidadAjuste,
        tipoMovimiento = TipoMovimiento.AJUSTE,
        marcaActualizacion = marcaActualizacion
    )

    /**
     * Actualiza los datos generales del [alimentoActualizado] verificando su [usuarioUid],
     * conserva su `remoteId` original, lo marca como pendiente de sincronización en Room y,
     * si la cantidad cambió respecto a [cantidadAnterior], registra
     * automáticamente un movimiento AJUSTE con la diferencia, el mismo `usuarioUid` y su `remoteId` UUID.
     * NO registra movimiento COMPRA. Impide stock negativo.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun editarAlimentoConAjuste(
        alimentoActualizado: Alimento,
        cantidadAnterior: Double,
        usuarioUid: String = alimentoActualizado.usuarioUid
    ): ResultadoOperacionStock? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return null

        val cantidadNueva = parsearCantidad(alimentoActualizado.cantidadActual)
        if (cantidadNueva < 0.0) return null

        val marcaActualizacion = alimentoActualizado.actualizadoEn.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val filasActualizadas = _editarAlimentoMarcandoPendientePorUsuario(
            id = alimentoActualizado.id,
            usuarioUid = uidLimpio,
            nombre = alimentoActualizado.nombre,
            categoria = alimentoActualizado.categoria,
            cantidadActual = alimentoActualizado.cantidadActual,
            unidad = alimentoActualizado.unidad,
            fechaCompra = alimentoActualizado.fechaCompra,
            fechaVencimiento = alimentoActualizado.fechaVencimiento,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
        if (filasActualizadas <= 0) return null

        val diferencia = cantidadNueva - cantidadAnterior
        val movimientoAjuste = if (diferencia != 0.0) {
            registrarMovimientoLocal(
                alimentoId = alimentoActualizado.id,
                alimentoNombre = alimentoActualizado.nombre,
                tipo = TipoMovimiento.AJUSTE,
                cantidad = diferencia,
                usuarioUid = uidLimpio
            )
        } else {
            null
        }

        return ResultadoOperacionStock(
            alimentoActualizado = alimentoActualizado.copy(
                usuarioUid = uidLimpio,
                pendienteSincronizacion = true,
                actualizadoEn = marcaActualizacion
            ),
            movimientoRegistrado = movimientoAjuste
        )
    }

    /**
     * Registra el consumo de un alimento en una única transacción atómica de Room:
     * actualiza `cantidadActual`, marca `pendienteSincronizacion = true` y [marcaActualizacion],
     * e inserta el movimiento [TipoMovimiento.CONSUMO] con su `remoteId` UUID persistente.
     */
    @Transaction
    open suspend fun consumirAlimentoConMovimiento(
        alimentoId: Int,
        cantidadConsumida: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock? = modificarStockConMovimiento(
        alimentoId = alimentoId,
        usuarioUid = usuarioUid,
        deltaStock = -cantidadConsumida,
        cantidadMovimiento = cantidadConsumida,
        tipoMovimiento = TipoMovimiento.CONSUMO,
        marcaActualizacion = marcaActualizacion
    )

    /**
     * Registra el desperdicio de un alimento en una única transacción atómica de Room:
     * actualiza `cantidadActual`, marca `pendienteSincronizacion = true` y [marcaActualizacion],
     * e inserta el movimiento [TipoMovimiento.DESPERDICIO] con su `remoteId` UUID persistente.
     */
    @Transaction
    open suspend fun desperdiciarAlimentoConMovimiento(
        alimentoId: Int,
        cantidadDesperdiciada: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock? = modificarStockConMovimiento(
        alimentoId = alimentoId,
        usuarioUid = usuarioUid,
        deltaStock = -cantidadDesperdiciada,
        cantidadMovimiento = cantidadDesperdiciada,
        tipoMovimiento = TipoMovimiento.DESPERDICIO,
        marcaActualizacion = marcaActualizacion
    )

    /**
     * Lógica común reutilizada por CONSUMO, DESPERDICIO y AJUSTE dentro de una transacción Room:
     * valida propietario, `remoteId` y límites de stock, actualiza el alimento marcando
     * `pendienteSincronizacion = true` y registra el movimiento con su UUID persistente.
     */
    private suspend fun modificarStockConMovimiento(
        alimentoId: Int,
        usuarioUid: String,
        deltaStock: Double,
        cantidadMovimiento: Double,
        tipoMovimiento: TipoMovimiento,
        marcaActualizacion: Long
    ): ResultadoOperacionStock? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty() || cantidadMovimiento <= 0.0) return null

        val actual = _obtenerAlimentoPorIdYUsuario(alimentoId, uidLimpio) ?: return null
        if (actual.usuarioUid.trim() != uidLimpio || actual.remoteId.isBlank()) return null

        val stockActual = parsearCantidad(actual.cantidadActual)
        if (stockActual < 0.0) return null

        val nuevoStock = stockActual + deltaStock
        if (nuevoStock < 0.0) return null

        val nuevaCantidadTexto = formatearCantidad(nuevoStock)

        val filasActualizadas = _editarAlimentoMarcandoPendientePorUsuario(
            id = actual.id,
            usuarioUid = uidLimpio,
            nombre = actual.nombre,
            categoria = actual.categoria,
            cantidadActual = nuevaCantidadTexto,
            unidad = actual.unidad,
            fechaCompra = actual.fechaCompra,
            fechaVencimiento = actual.fechaVencimiento,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
        if (filasActualizadas <= 0) return null

        val movimiento = registrarMovimientoLocal(
            alimentoId = actual.id,
            alimentoNombre = actual.nombre,
            tipo = tipoMovimiento,
            cantidad = cantidadMovimiento,
            usuarioUid = uidLimpio
        )

        return ResultadoOperacionStock(
            alimentoActualizado = actual.copy(
                cantidadActual = nuevaCantidadTexto,
                usuarioUid = uidLimpio,
                pendienteSincronizacion = true,
                actualizadoEn = marcaActualizacion
            ),
            movimientoRegistrado = movimiento
        )
    }

    private suspend fun registrarMovimientoLocal(
        alimentoId: Int,
        alimentoNombre: String,
        tipo: TipoMovimiento,
        cantidad: Double,
        usuarioUid: String
    ): Movimiento {
        val movimiento = Movimiento(
            alimentoId = alimentoId,
            alimentoNombre = alimentoNombre,
            tipo = tipo,
            cantidad = cantidad,
            fecha = fechaActual(),
            usuarioUid = usuarioUid,
            remoteId = java.util.UUID.randomUUID().toString()
        )
        val nuevoId = _insertarMovimiento(movimiento)
        return movimiento.copy(id = nuevoId.toInt())
    }

    /**
     * Registra de forma persistente una eliminación pendiente (tombstone) en `eliminaciones_pendientes`
     * y retira el alimento de la tabla `alimentos` dentro de una misma transacción Room (Etapa 4C.3F).
     *
     * - Verifica que el alimento exista y pertenezca a [usuarioUid].
     * - Conserva intacto el historial de movimientos (NO borra registros de `movimientos`).
     * - Persiste `remoteId`, `usuarioUid`, `nombreAlimento`, [fechaEliminacion] y `baseRemotaActualizadoEn`.
     *
     * @return El registro [EliminacionPendiente] creado, o `null` si el alimento no existe o no pertenece al usuario.
     */
    @Transaction
    open suspend fun registrarEliminacionPendienteYBorrarLocal(
        alimentoId: Int,
        usuarioUid: String,
        fechaEliminacion: Long = System.currentTimeMillis()
    ): EliminacionPendiente? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return null

        val actual = _obtenerAlimentoPorIdYUsuario(alimentoId, uidLimpio) ?: return null
        if (actual.usuarioUid.trim() != uidLimpio) return null

        val remoteIdOriginal = actual.remoteId.trim()
        if (remoteIdOriginal.isEmpty()) return null

        val baseConocida = if (actual.baseRemotaActualizadoEn > 0L) {
            actual.baseRemotaActualizadoEn
        } else {
            actual.actualizadoEn
        }

        // Reemplazar cualquier tombstone previo para el mismo remoteId y usuarioUid
        _eliminarTombstonePorRemoteIdYUsuario(remoteIdOriginal, uidLimpio)

        val tombstone = EliminacionPendiente(
            remoteId = remoteIdOriginal,
            usuarioUid = uidLimpio,
            nombreAlimento = actual.nombre,
            fechaEliminacion = fechaEliminacion,
            baseRemotaActualizadoEn = baseConocida
        )
        val nuevoTombstoneId = _insertarEliminacionPendiente(tombstone)

        val filasBorradas = _eliminarAlimentoPorIdYUsuario(actual.id, uidLimpio)
        if (filasBorradas <= 0) {
            throw IllegalStateException("No se pudo retirar el alimento local durante la transacción.")
        }

        return tombstone.copy(id = nuevoTombstoneId.toInt())
    }

    private fun formatearCantidad(valor: Double): String =
        if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()

    private fun parsearCantidad(valor: String): Double =
        valor.replace(',', '.')
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toDoubleOrNull() ?: 0.0

    private fun fechaActual(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
