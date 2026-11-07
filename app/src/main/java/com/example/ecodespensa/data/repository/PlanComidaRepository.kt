package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.EstadoIntegracionPlanRemoto
import com.example.ecodespensa.data.model.EliminacionPlanPendiente
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.PlanComida
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.PlanComidaFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio del Planificador Semanal de Comidas.
 * Todas las lecturas y escrituras se aíslan por `usuarioUid` y `fechaInicioSemana`.
 */
interface PlanComidaRepository {

    fun observarPlanesSemana(
        usuarioUid: String,
        fechaInicioSemana: String
    ): Flow<List<PlanComidaConIngredientes>>

    fun observarPlanesActivos(
        usuarioUid: String,
        fechaInicioSemanaMinima: String
    ): Flow<List<PlanComidaConIngredientes>>

    fun observarTotalPlanesPorUsuario(usuarioUid: String): Flow<Int>

    fun observarTotalEliminacionesPorUsuario(usuarioUid: String): Flow<Int>

    suspend fun obtenerTodosPlanesPorUsuario(usuarioUid: String): List<PlanComidaConIngredientes>

    suspend fun obtenerEliminacionesPendientesPorUsuario(usuarioUid: String): List<EliminacionPlanPendiente>

    suspend fun guardarComidaPlanificada(
        plan: PlanComida,
        ingredientes: List<IngredientePlanificado>,
        usuarioUid: String
    ): PlanComida

    suspend fun eliminarComidaPlanificada(
        planRemoteId: String,
        usuarioUid: String
    ): Boolean

    suspend fun marcarPlanSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    )

    suspend fun limpiarEliminacionPlanPendiente(
        remoteId: String,
        usuarioUid: String
    )

    suspend fun integrarPlanRecuperadoDeNube(
        planRemoto: PlanComidaFirestore,
        usuarioUid: String
    ): EstadoIntegracionPlanRemoto
}
