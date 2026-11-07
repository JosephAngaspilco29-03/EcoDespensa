package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.EstadoIntegracionPlanRemoto
import com.example.ecodespensa.data.local.PlanComidaDao
import com.example.ecodespensa.data.model.EliminacionPlanPendiente
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.PlanComida
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.PlanComidaFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Implementación Room de [PlanComidaRepository].
 */
class PlanComidaRepositoryImpl(
    private val dao: PlanComidaDao
) : PlanComidaRepository {

    override fun observarPlanesSemana(
        usuarioUid: String,
        fechaInicioSemana: String
    ): Flow<List<PlanComidaConIngredientes>> =
        dao.observarPlanesPorSemanaYUsuario(
            usuarioUid = usuarioUid.trim(),
            fechaInicioSemana = fechaInicioSemana.trim()
        )

    override fun observarPlanesActivos(
        usuarioUid: String,
        fechaInicioSemanaMinima: String
    ): Flow<List<PlanComidaConIngredientes>> =
        dao.observarPlanesActivosPorUsuario(
            usuarioUid = usuarioUid.trim(),
            fechaInicioSemanaMinima = fechaInicioSemanaMinima.trim()
        )

    override fun observarTotalPlanesPorUsuario(usuarioUid: String): Flow<Int> =
        dao.observarTotalPlanesPorUsuario(usuarioUid.trim())

    override fun observarTotalEliminacionesPorUsuario(usuarioUid: String): Flow<Int> =
        dao.observarTotalEliminacionesPorUsuario(usuarioUid.trim())

    override suspend fun obtenerTodosPlanesPorUsuario(
        usuarioUid: String
    ): List<PlanComidaConIngredientes> =
        dao.obtenerTodosPorUsuario(usuarioUid.trim())

    override suspend fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): List<EliminacionPlanPendiente> =
        dao.obtenerEliminacionesPendientesPorUsuario(usuarioUid.trim())

    override suspend fun guardarComidaPlanificada(
        plan: PlanComida,
        ingredientes: List<IngredientePlanificado>,
        usuarioUid: String
    ): PlanComida =
        dao.guardarPlanConIngredientes(
            plan = plan,
            ingredientes = ingredientes,
            usuarioUid = usuarioUid.trim()
        )

    override suspend fun eliminarComidaPlanificada(
        planRemoteId: String,
        usuarioUid: String
    ): Boolean =
        dao.eliminarPlanConIngredientes(
            planRemoteId = planRemoteId.trim(),
            usuarioUid = usuarioUid.trim()
        )

    override suspend fun marcarPlanSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    ) {
        dao.marcarPlanSincronizado(
            remoteId = remoteId.trim(),
            usuarioUid = usuarioUid.trim(),
            actualizadoEnConfirmado = actualizadoEnConfirmado
        )
    }

    override suspend fun limpiarEliminacionPlanPendiente(
        remoteId: String,
        usuarioUid: String
    ) {
        dao.eliminarTombstonePorRemoteIdYUsuario(
            remoteId = remoteId.trim(),
            usuarioUid = usuarioUid.trim()
        )
    }

    override suspend fun integrarPlanRecuperadoDeNube(
        planRemoto: PlanComidaFirestore,
        usuarioUid: String
    ): EstadoIntegracionPlanRemoto =
        dao.integrarPlanRecuperadoDeNube(
            planRemoto = planRemoto,
            usuarioUid = usuarioUid.trim()
        )
}
