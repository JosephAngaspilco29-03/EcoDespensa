package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.EstadoIntegracionItemCompraRemoto
import com.example.ecodespensa.data.model.EliminacionItemCompraPendiente
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.ItemCompraFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio para la Lista Inteligente de Compras.
 * Todas las operaciones están aisladas por `usuarioUid` y no modifican el stock de Mi Despensa.
 */
interface ItemCompraRepository {
    fun observarItemsPorUsuario(usuarioUid: String): Flow<List<ItemCompra>>

    suspend fun obtenerListaPorUsuario(usuarioUid: String): List<ItemCompra>

    suspend fun agregarItemManual(
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String,
        alimentoRemoteId: String,
        usuarioUid: String
    ): ItemCompra

    suspend fun agregarDesdeSugerencia(
        nombre: String,
        cantidadFaltante: Double,
        unidad: String,
        alimentoRemoteId: String,
        detalleExplicacion: String,
        usuarioUid: String
    ): ItemCompra

    suspend fun editarItem(
        id: Int,
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String,
        usuarioUid: String
    ): Boolean

    suspend fun marcarComprado(
        id: Int,
        comprado: Boolean,
        usuarioUid: String
    ): Boolean

    suspend fun eliminarItem(id: Int, usuarioUid: String): Boolean

    suspend fun eliminarComprados(usuarioUid: String): Int

    fun observarEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): Flow<List<EliminacionItemCompraPendiente>>

    suspend fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): List<EliminacionItemCompraPendiente>

    suspend fun marcarItemSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    )

    suspend fun limpiarEliminacionItemPendiente(
        remoteId: String,
        usuarioUid: String
    )

    suspend fun integrarItemRecuperadoDeNube(
        itemRemoto: ItemCompraFirestore,
        usuarioUid: String
    ): EstadoIntegracionItemCompraRemoto
}
