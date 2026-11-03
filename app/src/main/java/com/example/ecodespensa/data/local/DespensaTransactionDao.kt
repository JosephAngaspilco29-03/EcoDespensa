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
    abstract suspend fun _insertarMovimiento(movimiento: Movimiento)

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
     * con la cantidad inicial registrada y el mismo `usuarioUid` del alimento.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun insertarAlimentoConCompra(alimento: Alimento) {
        val alimentoFinal = if (alimento.remoteId.isBlank()) {
            alimento.copy(remoteId = java.util.UUID.randomUUID().toString())
        } else {
            alimento
        }
        val nuevoId = _insertarAlimento(alimentoFinal)
        val cantidad = parsearCantidad(alimentoFinal.cantidadActual)
        _insertarMovimiento(
            Movimiento(
                alimentoId = nuevoId.toInt(),
                alimentoNombre = alimentoFinal.nombre,
                tipo = TipoMovimiento.COMPRA,
                cantidad = cantidad,
                fecha = fechaActual(),
                usuarioUid = alimentoFinal.usuarioUid
            )
        )
    }

    /**
     * Actualiza el [alimento] verificando su [usuarioUid], marca `pendienteSincronizacion = true`
     * y `actualizadoEn`, y registra un movimiento AJUSTE con el mismo `usuarioUid`.
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
    ): Alimento? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty() || cantidadAjuste <= 0.0) return null

        val actual = _obtenerAlimentoPorIdYUsuario(alimento.id, uidLimpio) ?: return null
        if (actual.usuarioUid.trim() != uidLimpio || actual.remoteId.isBlank()) return null

        val stockActual = parsearCantidad(actual.cantidadActual)
        if (stockActual < 0.0) return null

        val nuevoStock = stockActual + cantidadAjuste
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

        _insertarMovimiento(
            Movimiento(
                alimentoId = actual.id,
                alimentoNombre = actual.nombre,
                tipo = TipoMovimiento.AJUSTE,
                cantidad = cantidadAjuste,
                fecha = fechaActual(),
                usuarioUid = uidLimpio
            )
        )

        return actual.copy(
            cantidadActual = nuevaCantidadTexto,
            usuarioUid = uidLimpio,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
    }

    /**
     * Actualiza los datos generales del [alimentoActualizado] verificando su [usuarioUid],
     * conserva su `remoteId` original, lo marca como pendiente de sincronización en Room y,
     * si la cantidad cambió respecto a [cantidadAnterior], registra
     * automáticamente un movimiento AJUSTE con la diferencia y el mismo `usuarioUid`.
     * NO registra movimiento COMPRA. Impide stock negativo.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun editarAlimentoConAjuste(
        alimentoActualizado: Alimento,
        cantidadAnterior: Double,
        usuarioUid: String = alimentoActualizado.usuarioUid
    ): Boolean {
        val cantidadNueva = parsearCantidad(alimentoActualizado.cantidadActual)
        if (cantidadNueva < 0.0) return false

        val marcaActualizacion = alimentoActualizado.actualizadoEn.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val filasActualizadas = _editarAlimentoMarcandoPendientePorUsuario(
            id = alimentoActualizado.id,
            usuarioUid = usuarioUid,
            nombre = alimentoActualizado.nombre,
            categoria = alimentoActualizado.categoria,
            cantidadActual = alimentoActualizado.cantidadActual,
            unidad = alimentoActualizado.unidad,
            fechaCompra = alimentoActualizado.fechaCompra,
            fechaVencimiento = alimentoActualizado.fechaVencimiento,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
        if (filasActualizadas > 0) {
            val diferencia = cantidadNueva - cantidadAnterior
            if (diferencia != 0.0) {
                _insertarMovimiento(
                    Movimiento(
                        alimentoId = alimentoActualizado.id,
                        alimentoNombre = alimentoActualizado.nombre,
                        tipo = TipoMovimiento.AJUSTE,
                        cantidad = diferencia,
                        fecha = fechaActual(),
                        usuarioUid = usuarioUid
                    )
                )
            }
            return true
        }
        return false
    }

    /**
     * Registra el consumo de un alimento en una única transacción atómica de Room (Etapa 4C.3C):
     * 1. Verifica que el alimento exista y pertenezca a [usuarioUid], que tenga `remoteId` válido
     *    y que [cantidadConsumida] sea mayor a 0 y no supere el stock actual.
     * 2. Actualiza la cantidad local marcando `pendienteSincronizacion = true` y [marcaActualizacion],
     *    conservando intactos su `remoteId` original y su `baseRemotaActualizadoEn`.
     * 3. Registra el movimiento de tipo [TipoMovimiento.CONSUMO] en el historial local de Room.
     *
     * @return El [Alimento] con su cantidad y estado actualizados, o `null` si la operación no es válida.
     */
    @Transaction
    open suspend fun consumirAlimentoConMovimiento(
        alimentoId: Int,
        cantidadConsumida: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): Alimento? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty() || cantidadConsumida <= 0.0) return null

        val actual = _obtenerAlimentoPorIdYUsuario(alimentoId, uidLimpio) ?: return null
        if (actual.usuarioUid.trim() != uidLimpio || actual.remoteId.isBlank()) return null

        val stockActual = parsearCantidad(actual.cantidadActual)
        if (cantidadConsumida > stockActual) return null

        val nuevoStock = stockActual - cantidadConsumida
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

        _insertarMovimiento(
            Movimiento(
                alimentoId = actual.id,
                alimentoNombre = actual.nombre,
                tipo = TipoMovimiento.CONSUMO,
                cantidad = cantidadConsumida,
                fecha = fechaActual(),
                usuarioUid = uidLimpio
            )
        )

        return actual.copy(
            cantidadActual = nuevaCantidadTexto,
            usuarioUid = uidLimpio,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
    }

    /**
     * Registra el desperdicio de un alimento en una única transacción atómica de Room (Etapa 4C.3D):
     * 1. Verifica que el alimento exista y pertenezca a [usuarioUid], que tenga `remoteId` válido
     *    y que [cantidadDesperdiciada] sea mayor a 0 y no supere el stock disponible.
     * 2. Actualiza la cantidad local marcando `pendienteSincronizacion = true` y [marcaActualizacion],
     *    conservando intactos su `remoteId` original y su `baseRemotaActualizadoEn`.
     * 3. Registra el movimiento de tipo [TipoMovimiento.DESPERDICIO] en el historial local de Room.
     *
     * @return El [Alimento] con su cantidad y estado actualizados, o `null` si la operación no es válida.
     */
    @Transaction
    open suspend fun desperdiciarAlimentoConMovimiento(
        alimentoId: Int,
        cantidadDesperdiciada: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): Alimento? {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty() || cantidadDesperdiciada <= 0.0) return null

        val actual = _obtenerAlimentoPorIdYUsuario(alimentoId, uidLimpio) ?: return null
        if (actual.usuarioUid.trim() != uidLimpio || actual.remoteId.isBlank()) return null

        val stockActual = parsearCantidad(actual.cantidadActual)
        if (cantidadDesperdiciada > stockActual) return null

        val nuevoStock = stockActual - cantidadDesperdiciada
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

        _insertarMovimiento(
            Movimiento(
                alimentoId = actual.id,
                alimentoNombre = actual.nombre,
                tipo = TipoMovimiento.DESPERDICIO,
                cantidad = cantidadDesperdiciada,
                fecha = fechaActual(),
                usuarioUid = uidLimpio
            )
        )

        return actual.copy(
            cantidadActual = nuevaCantidadTexto,
            usuarioUid = uidLimpio,
            pendienteSincronizacion = true,
            actualizadoEn = marcaActualizacion
        )
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
