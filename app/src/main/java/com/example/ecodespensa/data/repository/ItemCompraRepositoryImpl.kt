package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.EstadoIntegracionItemCompraRemoto
import com.example.ecodespensa.data.local.ItemCompraDao
import com.example.ecodespensa.data.model.EliminacionItemCompraPendiente
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.ItemCompraFirestore
import com.example.ecodespensa.data.model.OrigenItemCompra
import kotlinx.coroutines.flow.Flow

/**
 * Implementación Room de [ItemCompraRepository].
 */
class ItemCompraRepositoryImpl(
    private val dao: ItemCompraDao
) : ItemCompraRepository {

    override fun observarItemsPorUsuario(usuarioUid: String): Flow<List<ItemCompra>> =
        dao.observarItemsPorUsuario(usuarioUid.trim())

    override suspend fun obtenerListaPorUsuario(usuarioUid: String): List<ItemCompra> =
        dao.obtenerListaPorUsuario(usuarioUid.trim())

    override suspend fun agregarItemManual(
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String,
        alimentoRemoteId: String,
        usuarioUid: String
    ): ItemCompra {
        val item = ItemCompra(
            usuarioUid = usuarioUid.trim(),
            nombre = nombre.trim(),
            cantidad = cantidad,
            unidad = unidad.trim(),
            comprado = false,
            origen = OrigenItemCompra.MANUAL.name,
            alimentoRemoteId = alimentoRemoteId.trim(),
            notaOrigen = nota.trim()
        )
        return dao.agregarOUnificarItem(
            nuevo = item,
            usuarioUid = usuarioUid.trim(),
            sumarSiExiste = true
        )
    }

    override suspend fun agregarDesdeSugerencia(
        nombre: String,
        cantidadFaltante: Double,
        unidad: String,
        alimentoRemoteId: String,
        detalleExplicacion: String,
        usuarioUid: String
    ): ItemCompra {
        val item = ItemCompra(
            usuarioUid = usuarioUid.trim(),
            nombre = nombre.trim(),
            cantidad = cantidadFaltante,
            unidad = unidad.trim(),
            comprado = false,
            origen = OrigenItemCompra.SUGERENCIA_PLANIFICADOR.name,
            alimentoRemoteId = alimentoRemoteId.trim(),
            notaOrigen = detalleExplicacion.trim()
        )
        return dao.agregarOUnificarItem(
            nuevo = item,
            usuarioUid = usuarioUid.trim(),
            sumarSiExiste = false
        )
    }

    override suspend fun editarItem(
        id: Int,
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String,
        usuarioUid: String
    ): Boolean =
        dao.editarItemPorUsuario(
            id = id,
            usuarioUid = usuarioUid.trim(),
            nombre = nombre,
            cantidad = cantidad,
            unidad = unidad,
            notaOrigen = nota
        )

    override suspend fun marcarComprado(
        id: Int,
        comprado: Boolean,
        usuarioUid: String
    ): Boolean =
        dao.cambiarEstadoComprado(
            id = id,
            usuarioUid = usuarioUid.trim(),
            comprado = comprado
        )

    override suspend fun eliminarItem(id: Int, usuarioUid: String): Boolean =
        dao.eliminarPorUsuario(id = id, usuarioUid = usuarioUid.trim())

    override suspend fun eliminarComprados(usuarioUid: String): Int =
        dao.limpiarCompradosPorUsuario(usuarioUid.trim())

    override fun observarEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): Flow<List<EliminacionItemCompraPendiente>> =
        dao.observarEliminacionesPendientesPorUsuario(usuarioUid.trim())

    override suspend fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): List<EliminacionItemCompraPendiente> =
        dao.obtenerEliminacionesPendientesPorUsuario(usuarioUid.trim())

    override suspend fun marcarItemSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    ) {
        dao.marcarItemSincronizado(
            remoteId = remoteId.trim(),
            usuarioUid = usuarioUid.trim(),
            actualizadoEnConfirmado = actualizadoEnConfirmado
        )
    }

    override suspend fun limpiarEliminacionItemPendiente(
        remoteId: String,
        usuarioUid: String
    ) {
        dao.limpiarEliminacionItemPendiente(
            remoteId = remoteId.trim(),
            usuarioUid = usuarioUid.trim()
        )
    }

    override suspend fun integrarItemRecuperadoDeNube(
        itemRemoto: ItemCompraFirestore,
        usuarioUid: String
    ): EstadoIntegracionItemCompraRemoto =
        dao.integrarItemRecuperadoDeNube(
            itemRemoto = itemRemoto,
            usuarioUid = usuarioUid.trim()
        )
}
