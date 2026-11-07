package com.example.ecodespensa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.ecodespensa.data.model.EliminacionItemCompraPendiente
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.ItemCompraFirestore
import com.example.ecodespensa.util.UnidadConversionUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class EstadoIntegracionItemCompraRemoto {
    INSERTADO_NUEVO,
    ACTUALIZADO,
    OMITIDO_SIN_CAMBIOS,
    OMITIDO_ELIMINADO_LOCALMENTE,
    CONFLICTO_LOCAL_PENDIENTE
}

/**
 * DAO para la Lista Inteligente de Compras (`items_compra`) y sus eliminaciones pendientes
 * (`eliminaciones_items_compra_pendientes`).
 * Todas las operaciones filtran y verifican `usuarioUid`.
 */
@Dao
abstract class ItemCompraDao {

    @Query(
        """
        SELECT * FROM items_compra
        WHERE usuarioUid = :usuarioUid
        ORDER BY comprado ASC, actualizadoEn DESC, id DESC
        """
    )
    abstract fun observarItemsPorUsuario(usuarioUid: String): Flow<List<ItemCompra>>

    @Query("SELECT * FROM items_compra WHERE usuarioUid = :usuarioUid")
    abstract suspend fun obtenerListaPorUsuario(usuarioUid: String): List<ItemCompra>

    @Query("SELECT * FROM items_compra WHERE id = :id AND usuarioUid = :usuarioUid LIMIT 1")
    abstract suspend fun obtenerPorIdYUsuario(id: Int, usuarioUid: String): ItemCompra?

    @Query("SELECT * FROM items_compra WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid LIMIT 1")
    abstract suspend fun obtenerPorRemoteIdYUsuario(remoteId: String, usuarioUid: String): ItemCompra?

    @Query("SELECT * FROM items_compra WHERE usuarioUid = :usuarioUid AND comprado = 1")
    abstract suspend fun obtenerCompradosPorUsuario(usuarioUid: String): List<ItemCompra>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertar(item: ItemCompra): Long

    @Query(
        """
        UPDATE items_compra
        SET nombre = :nombre,
            cantidad = :cantidad,
            unidad = :unidad,
            notaOrigen = :notaOrigen,
            pendienteSincronizacion = 1,
            actualizadoEn = :actualizadoEn
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun _actualizarDatosPorUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        cantidad: Double,
        unidad: String,
        notaOrigen: String,
        actualizadoEn: Long
    ): Int

    @Query(
        """
        UPDATE items_compra
        SET comprado = :comprado,
            pendienteSincronizacion = 1,
            actualizadoEn = :actualizadoEn
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun _actualizarCompradoPorUsuario(
        id: Int,
        usuarioUid: String,
        comprado: Boolean,
        actualizadoEn: Long
    ): Int

    @Query(
        """
        UPDATE items_compra
        SET nombre = :nombre,
            cantidad = :cantidad,
            unidad = :unidad,
            comprado = :comprado,
            origen = :origen,
            alimentoRemoteId = :alimentoRemoteId,
            notaOrigen = :notaOrigen,
            pendienteSincronizacion = 0,
            actualizadoEn = :actualizadoEn,
            baseRemotaActualizadoEn = :baseRemotaActualizadoEn
        WHERE id = :id AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun _actualizarDesdeNubePorIdYUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        cantidad: Double,
        unidad: String,
        comprado: Boolean,
        origen: String,
        alimentoRemoteId: String,
        notaOrigen: String,
        actualizadoEn: Long,
        baseRemotaActualizadoEn: Long
    ): Int

    @Query(
        """
        UPDATE items_compra
        SET pendienteSincronizacion = 0,
            actualizadoEn = :actualizadoEnConfirmado,
            baseRemotaActualizadoEn = :actualizadoEnConfirmado
        WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun marcarItemSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    ): Int

    @Query("DELETE FROM items_compra WHERE id = :id AND usuarioUid = :usuarioUid")
    abstract suspend fun _eliminarPorIdYUsuario(id: Int, usuarioUid: String): Int

    @Query("DELETE FROM items_compra WHERE usuarioUid = :usuarioUid AND comprado = 1")
    abstract suspend fun _eliminarCompradosPorUsuario(usuarioUid: String): Int

    // ── Eliminaciones pendientes (Tombstones) ───────────────────────────────

    @Query("SELECT * FROM eliminaciones_items_compra_pendientes WHERE usuarioUid = :usuarioUid ORDER BY fechaEliminacion ASC")
    abstract fun observarEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): Flow<List<EliminacionItemCompraPendiente>>

    @Query("SELECT * FROM eliminaciones_items_compra_pendientes WHERE usuarioUid = :usuarioUid ORDER BY fechaEliminacion ASC")
    abstract suspend fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): List<EliminacionItemCompraPendiente>

    @Query(
        """
        SELECT * FROM eliminaciones_items_compra_pendientes
        WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid
        LIMIT 1
        """
    )
    abstract suspend fun obtenerEliminacionPendientePorRemoteId(
        remoteId: String,
        usuarioUid: String
    ): EliminacionItemCompraPendiente?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarEliminacionPendiente(
        eliminacion: EliminacionItemCompraPendiente
    ): Long

    @Query("DELETE FROM eliminaciones_items_compra_pendientes WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid")
    abstract suspend fun limpiarEliminacionItemPendiente(
        remoteId: String,
        usuarioUid: String
    ): Int

    /**
     * Inserta un producto en la lista de compras o, si ya existe un producto pendiente (`comprado = false`)
     * del mismo usuario, mismo alimento/nombre y unidad compatible:
     * - Si [sumarSiExiste] es `true` (ingreso manual), suma la cantidad en la unidad compatible.
     * - Si [sumarSiExiste] es `false` (agregar desde sugerencia), asegura cubrir al menos la cantidad faltante
     *   sin duplicar el registro.
     */
    @Transaction
    open suspend fun agregarOUnificarItem(
        nuevo: ItemCompra,
        usuarioUid: String,
        sumarSiExiste: Boolean
    ): ItemCompra {
        val uidLimpio = usuarioUid.trim()
        require(uidLimpio.isNotEmpty()) { "El usuarioUid no puede estar vacío." }

        val nombreLimpio = nuevo.nombre.trim()
        require(nombreLimpio.isNotEmpty() && nuevo.cantidad > 0.0) { "Datos de compra inválidos." }

        val ahora = System.currentTimeMillis()
        val pendientes = obtenerListaPorUsuario(uidLimpio).filter { !it.comprado }

        val coincidente = pendientes.firstOrNull { existente ->
            val mismoAlimento = (nuevo.alimentoRemoteId.isNotBlank() &&
                existente.alimentoRemoteId.equals(nuevo.alimentoRemoteId.trim(), ignoreCase = true)) ||
                existente.nombre.trim().equals(nombreLimpio, ignoreCase = true)

            mismoAlimento && UnidadConversionUtils.sonCompatibles(existente.unidad, nuevo.unidad)
        }

        if (coincidente != null) {
            val cantExistenteBase = UnidadConversionUtils.aCantidadBase(coincidente.cantidad, coincidente.unidad)
            val cantNuevaBase = UnidadConversionUtils.aCantidadBase(nuevo.cantidad, nuevo.unidad)

            val totalBase = if (sumarSiExiste) {
                cantExistenteBase + cantNuevaBase
            } else {
                maxOf(cantExistenteBase, cantNuevaBase)
            }

            val unidadFinal = coincidente.unidad.ifBlank { nuevo.unidad.trim() }
            val cantidadFinal = UnidadConversionUtils.desdeCantidadBase(totalBase, unidadFinal)
            val notaFinal = nuevo.notaOrigen.trim().ifEmpty { coincidente.notaOrigen }

            _actualizarDatosPorUsuario(
                id = coincidente.id,
                usuarioUid = uidLimpio,
                nombre = coincidente.nombre,
                cantidad = cantidadFinal,
                unidad = unidadFinal,
                notaOrigen = notaFinal,
                actualizadoEn = ahora
            )
            return coincidente.copy(
                cantidad = cantidadFinal,
                unidad = unidadFinal,
                notaOrigen = notaFinal,
                pendienteSincronizacion = true,
                actualizadoEn = ahora
            )
        }

        val remoteIdFinal = nuevo.remoteId.trim().ifEmpty { UUID.randomUUID().toString() }
        limpiarEliminacionItemPendiente(remoteIdFinal, uidLimpio)

        val aInsertar = nuevo.copy(
            id = 0,
            remoteId = remoteIdFinal,
            usuarioUid = uidLimpio,
            nombre = nombreLimpio,
            unidad = nuevo.unidad.trim(),
            alimentoRemoteId = nuevo.alimentoRemoteId.trim(),
            notaOrigen = nuevo.notaOrigen.trim(),
            pendienteSincronizacion = true,
            actualizadoEn = ahora
        )
        val rowId = _insertar(aInsertar).toInt()
        return aInsertar.copy(id = rowId)
    }

    suspend fun editarItemPorUsuario(
        id: Int,
        usuarioUid: String,
        nombre: String,
        cantidad: Double,
        unidad: String,
        notaOrigen: String
    ): Boolean {
        val uidLimpio = usuarioUid.trim()
        val nombreLimpio = nombre.trim()
        if (uidLimpio.isEmpty() || nombreLimpio.isEmpty() || cantidad <= 0.0) return false
        val filas = _actualizarDatosPorUsuario(
            id = id,
            usuarioUid = uidLimpio,
            nombre = nombreLimpio,
            cantidad = cantidad,
            unidad = unidad.trim(),
            notaOrigen = notaOrigen.trim(),
            actualizadoEn = System.currentTimeMillis()
        )
        return filas > 0
    }

    suspend fun cambiarEstadoComprado(
        id: Int,
        usuarioUid: String,
        comprado: Boolean
    ): Boolean {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return false
        return _actualizarCompradoPorUsuario(
            id = id,
            usuarioUid = uidLimpio,
            comprado = comprado,
            actualizadoEn = System.currentTimeMillis()
        ) > 0
    }

    /**
     * Registra un tombstone persistente en `eliminaciones_items_compra_pendientes` antes de
     * eliminar el producto de `items_compra`, para que la baja pueda sincronizarse con
     * Cloud Firestore y el producto no reaparezca al recuperar desde la nube.
     */
    @Transaction
    open suspend fun eliminarPorUsuario(id: Int, usuarioUid: String): Boolean {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return false
        val existente = obtenerPorIdYUsuario(id, uidLimpio) ?: return false
        val remoteIdLimpio = existente.remoteId.trim()
        val ahora = System.currentTimeMillis()

        if (remoteIdLimpio.isNotEmpty()) {
            limpiarEliminacionItemPendiente(remoteIdLimpio, uidLimpio)
            _insertarEliminacionPendiente(
                EliminacionItemCompraPendiente(
                    remoteId = remoteIdLimpio,
                    usuarioUid = uidLimpio,
                    nombre = existente.nombre,
                    fechaEliminacion = ahora,
                    baseRemotaActualizadoEn = maxOf(existente.baseRemotaActualizadoEn, existente.actualizadoEn)
                )
            )
        }

        return _eliminarPorIdYUsuario(id, uidLimpio) > 0
    }

    /**
     * Registra tombstones persistentes para todos los productos comprados del usuario antes de
     * retirarlos de `items_compra`, evitando que reaparezcan al recuperar la lista desde Firestore.
     */
    @Transaction
    open suspend fun limpiarCompradosPorUsuario(usuarioUid: String): Int {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isEmpty()) return 0
        val comprados = obtenerCompradosPorUsuario(uidLimpio)
        if (comprados.isEmpty()) return 0

        val ahora = System.currentTimeMillis()
        for (item in comprados) {
            val remoteIdLimpio = item.remoteId.trim()
            if (remoteIdLimpio.isNotEmpty()) {
                limpiarEliminacionItemPendiente(remoteIdLimpio, uidLimpio)
                _insertarEliminacionPendiente(
                    EliminacionItemCompraPendiente(
                        remoteId = remoteIdLimpio,
                        usuarioUid = uidLimpio,
                        nombre = item.nombre,
                        fechaEliminacion = ahora,
                        baseRemotaActualizadoEn = maxOf(item.baseRemotaActualizadoEn, item.actualizadoEn)
                    )
                )
            }
        }

        return _eliminarCompradosPorUsuario(uidLimpio)
    }

    /**
     * Integra de forma segura un [ItemCompraFirestore] descargado desde Cloud Firestore:
     * 1. Si existe un tombstone local (`eliminaciones_items_compra_pendientes`) para ese `remoteId`,
     *    NO lo re-inserta ([EstadoIntegracionItemCompraRemoto.OMITIDO_ELIMINADO_LOCALMENTE]).
     * 2. Si existe localmente con `pendienteSincronizacion == true`, protege los cambios locales
     *    ([EstadoIntegracionItemCompraRemoto.CONFLICTO_LOCAL_PENDIENTE]).
     * 3. Si existe localmente sin cambios pendientes y la versión remota es más reciente, actualiza
     *    sus datos conservando su `id` local y `remoteId` ([EstadoIntegracionItemCompraRemoto.ACTUALIZADO]).
     * 4. Si existe localmente y ya está al día, lo omite ([EstadoIntegracionItemCompraRemoto.OMITIDO_SIN_CAMBIOS]).
     * 5. Si no existe localmente, lo inserta con `pendienteSincronizacion = false`
     *    ([EstadoIntegracionItemCompraRemoto.INSERTADO_NUEVO]).
     */
    @Transaction
    open suspend fun integrarItemRecuperadoDeNube(
        itemRemoto: ItemCompraFirestore,
        usuarioUid: String
    ): EstadoIntegracionItemCompraRemoto {
        val uidLimpio = usuarioUid.trim()
        val remoteIdLimpio = itemRemoto.remoteId.trim()
        require(uidLimpio.isNotEmpty() && itemRemoto.usuarioUid.trim() == uidLimpio) {
            "El producto descargado no pertenece al usuario autenticado."
        }
        require(remoteIdLimpio.isNotEmpty()) {
            "El producto descargado no tiene un remoteId válido."
        }

        // 1. Verificar si fue eliminado localmente y está pendiente de borrar en la nube
        val tombstone = obtenerEliminacionPendientePorRemoteId(remoteIdLimpio, uidLimpio)
        if (tombstone != null) {
            return EstadoIntegracionItemCompraRemoto.OMITIDO_ELIMINADO_LOCALMENTE
        }

        val marcaRemota = itemRemoto.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val existente = obtenerPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)

        if (existente != null) {
            if (existente.pendienteSincronizacion) {
                return EstadoIntegracionItemCompraRemoto.CONFLICTO_LOCAL_PENDIENTE
            }
            if (marcaRemota <= existente.actualizadoEn) {
                return EstadoIntegracionItemCompraRemoto.OMITIDO_SIN_CAMBIOS
            }
            _actualizarDesdeNubePorIdYUsuario(
                id = existente.id,
                usuarioUid = uidLimpio,
                nombre = itemRemoto.nombre.trim(),
                cantidad = itemRemoto.cantidad,
                unidad = itemRemoto.unidad.trim(),
                comprado = itemRemoto.comprado,
                origen = itemRemoto.origen.trim(),
                alimentoRemoteId = itemRemoto.alimentoRemoteId.trim(),
                notaOrigen = itemRemoto.notaOrigen.trim(),
                actualizadoEn = marcaRemota,
                baseRemotaActualizadoEn = marcaRemota
            )
            return EstadoIntegracionItemCompraRemoto.ACTUALIZADO
        }

        _insertar(
            ItemCompra(
                id = 0,
                remoteId = remoteIdLimpio,
                usuarioUid = uidLimpio,
                nombre = itemRemoto.nombre.trim(),
                cantidad = itemRemoto.cantidad,
                unidad = itemRemoto.unidad.trim(),
                comprado = itemRemoto.comprado,
                origen = itemRemoto.origen.trim(),
                alimentoRemoteId = itemRemoto.alimentoRemoteId.trim(),
                notaOrigen = itemRemoto.notaOrigen.trim(),
                pendienteSincronizacion = false,
                actualizadoEn = marcaRemota,
                baseRemotaActualizadoEn = marcaRemota
            )
        )
        return EstadoIntegracionItemCompraRemoto.INSERTADO_NUEVO
    }
}
