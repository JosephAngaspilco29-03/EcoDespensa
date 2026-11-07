package com.example.ecodespensa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.ecodespensa.data.model.EliminacionPlanPendiente
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.PlanComida
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.PlanComidaFirestore
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class EstadoIntegracionPlanRemoto {
    DESCARGADO,
    OMITIDO,
    CONFLICTO_CAMBIO_LOCAL_PENDIENTE
}

/**
 * DAO para el Planificador Semanal de Comidas y sus eliminaciones pendientes (tombstones).
 * Todas las operaciones filtran y verifican `usuarioUid` para aislar los planes por cuenta.
 */
@Dao
abstract class PlanComidaDao {

    /**
     * Observa reactivamente las comidas planificadas (con sus ingredientes) del [usuarioUid]
     * para la semana cuyo lunes es [fechaInicioSemana].
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planes_comida
        WHERE usuarioUid = :usuarioUid AND fechaInicioSemana = :fechaInicioSemana
        ORDER BY diaSemana ASC, id ASC
        """
    )
    abstract fun observarPlanesPorSemanaYUsuario(
        usuarioUid: String,
        fechaInicioSemana: String
    ): Flow<List<PlanComidaConIngredientes>>

    /**
     * Observa reactivamente las comidas planificadas (con sus ingredientes) del [usuarioUid]
     * desde la semana [fechaInicioSemanaMinima] en adelante (para cruzar con la Lista de Compras).
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planes_comida
        WHERE usuarioUid = :usuarioUid AND fechaInicioSemana >= :fechaInicioSemanaMinima
        ORDER BY fechaInicioSemana ASC, diaSemana ASC, id ASC
        """
    )
    abstract fun observarPlanesActivosPorUsuario(
        usuarioUid: String,
        fechaInicioSemanaMinima: String
    ): Flow<List<PlanComidaConIngredientes>>

    /**
     * Obtiene todos los planes (con sus ingredientes) del [usuarioUid] para sincronizar con Firestore.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planes_comida
        WHERE usuarioUid = :usuarioUid
        ORDER BY fechaInicioSemana ASC, diaSemana ASC, id ASC
        """
    )
    abstract suspend fun obtenerTodosPorUsuario(
        usuarioUid: String
    ): List<PlanComidaConIngredientes>

    @Query("SELECT COUNT(*) FROM planes_comida WHERE usuarioUid = :usuarioUid")
    abstract fun observarTotalPlanesPorUsuario(usuarioUid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM eliminaciones_planes_pendientes WHERE usuarioUid = :usuarioUid")
    abstract fun observarTotalEliminacionesPorUsuario(usuarioUid: String): Flow<Int>

    @Query("SELECT * FROM eliminaciones_planes_pendientes WHERE usuarioUid = :usuarioUid ORDER BY fechaEliminacion ASC")
    abstract suspend fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): List<EliminacionPlanPendiente>

    @Query(
        """
        SELECT * FROM planes_comida
        WHERE usuarioUid = :usuarioUid
          AND fechaInicioSemana = :fechaInicioSemana
          AND diaSemana = :diaSemana
          AND tipoComida = :tipoComida
        LIMIT 1
        """
    )
    abstract suspend fun obtenerPlanPorCasillaYUsuario(
        usuarioUid: String,
        fechaInicioSemana: String,
        diaSemana: Int,
        tipoComida: String
    ): PlanComida?

    @Query("SELECT * FROM planes_comida WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid LIMIT 1")
    abstract suspend fun obtenerPlanPorRemoteIdYUsuario(
        remoteId: String,
        usuarioUid: String
    ): PlanComida?

    @Query(
        """
        SELECT * FROM eliminaciones_planes_pendientes
        WHERE usuarioUid = :usuarioUid
          AND (remoteId = :remoteId
               OR (fechaInicioSemana = :fechaInicioSemana AND diaSemana = :diaSemana AND tipoComida = :tipoComida))
        LIMIT 1
        """
    )
    abstract suspend fun obtenerTombstonePorRemoteIdOCasilla(
        usuarioUid: String,
        remoteId: String,
        fechaInicioSemana: String,
        diaSemana: Int,
        tipoComida: String
    ): EliminacionPlanPendiente?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarPlan(plan: PlanComida): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarIngredientes(ingredientes: List<IngredientePlanificado>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarTombstone(tombstone: EliminacionPlanPendiente): Long

    @Query("DELETE FROM ingredientes_planificados WHERE planRemoteId = :planRemoteId AND usuarioUid = :usuarioUid")
    abstract suspend fun _eliminarIngredientesPorPlanYUsuario(planRemoteId: String, usuarioUid: String)

    @Query("DELETE FROM planes_comida WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid")
    abstract suspend fun _eliminarPlanPorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Int

    @Query("DELETE FROM eliminaciones_planes_pendientes WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid")
    abstract suspend fun eliminarTombstonePorRemoteIdYUsuario(remoteId: String, usuarioUid: String): Int

    @Query(
        """
        DELETE FROM eliminaciones_planes_pendientes
        WHERE usuarioUid = :usuarioUid
          AND fechaInicioSemana = :fechaInicioSemana
          AND diaSemana = :diaSemana
          AND tipoComida = :tipoComida
        """
    )
    abstract suspend fun _eliminarTombstonePorCasillaYUsuario(
        usuarioUid: String,
        fechaInicioSemana: String,
        diaSemana: Int,
        tipoComida: String
    ): Int

    @Query(
        """
        UPDATE planes_comida
        SET pendienteSincronizacion = 0,
            actualizadoEn = :actualizadoEnConfirmado,
            baseRemotaActualizadoEn = :actualizadoEnConfirmado
        WHERE remoteId = :remoteId AND usuarioUid = :usuarioUid
        """
    )
    abstract suspend fun marcarPlanSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    ): Int

    /**
     * Crea o actualiza de forma atómica una comida planificada y su lista de ingredientes
     * para la casilla (`fechaInicioSemana`, `diaSemana`, `tipoComida`) del [usuarioUid].
     *
     * - Si ya existía un plan para esa casilla o con el mismo `remoteId`, conserva su `id` local,
     *   su `remoteId` UUID original y su `baseRemotaActualizadoEn`.
     * - Reemplaza limpiamente sus ingredientes planificados conservando `usuarioUid` y `planRemoteId`.
     * - NO modifica la tabla `alimentos` ni genera movimientos en `movimientos`.
     */
    @Transaction
    open suspend fun guardarPlanConIngredientes(
        plan: PlanComida,
        ingredientes: List<IngredientePlanificado>,
        usuarioUid: String
    ): PlanComida {
        val uidLimpio = usuarioUid.trim()
        require(uidLimpio.isNotEmpty()) { "El usuarioUid no puede estar vacío." }

        val existente = obtenerPlanPorCasillaYUsuario(
            usuarioUid = uidLimpio,
            fechaInicioSemana = plan.fechaInicioSemana.trim(),
            diaSemana = plan.diaSemana,
            tipoComida = plan.tipoComida.trim()
        ) ?: plan.remoteId.trim().takeIf { it.isNotEmpty() }?.let { idRemoto ->
            obtenerPlanPorRemoteIdYUsuario(idRemoto, uidLimpio)
        }

        val remoteIdFinal = existente?.remoteId?.takeIf { it.isNotBlank() }
            ?: plan.remoteId.trim().ifEmpty { UUID.randomUUID().toString() }

        val ahora = System.currentTimeMillis()
        val planAGuardar = PlanComida(
            id = existente?.id ?: 0,
            remoteId = remoteIdFinal,
            usuarioUid = uidLimpio,
            fechaInicioSemana = plan.fechaInicioSemana.trim(),
            diaSemana = plan.diaSemana,
            tipoComida = plan.tipoComida.trim(),
            nombrePlato = plan.nombrePlato.trim(),
            pendienteSincronizacion = true,
            actualizadoEn = ahora,
            baseRemotaActualizadoEn = existente?.baseRemotaActualizadoEn ?: plan.baseRemotaActualizadoEn
        )

        val nuevoId = _insertarPlan(planAGuardar).toInt()
        val idLocalEfectivo = if (planAGuardar.id != 0) planAGuardar.id else nuevoId

        // Limpiar cualquier tombstone previo para este remoteId o para esta misma casilla
        eliminarTombstonePorRemoteIdYUsuario(remoteIdFinal, uidLimpio)
        _eliminarTombstonePorCasillaYUsuario(
            usuarioUid = uidLimpio,
            fechaInicioSemana = planAGuardar.fechaInicioSemana,
            diaSemana = planAGuardar.diaSemana,
            tipoComida = planAGuardar.tipoComida
        )

        _eliminarIngredientesPorPlanYUsuario(remoteIdFinal, uidLimpio)

        val ingredientesNormalizados = ingredientes
            .filter { it.alimentoNombre.isNotBlank() && it.cantidadPlanificada > 0.0 }
            .map { ing ->
                IngredientePlanificado(
                    id = 0,
                    remoteId = ing.remoteId.trim().ifEmpty { UUID.randomUUID().toString() },
                    planId = idLocalEfectivo,
                    planRemoteId = remoteIdFinal,
                    usuarioUid = uidLimpio,
                    alimentoRemoteId = ing.alimentoRemoteId.trim(),
                    alimentoNombre = ing.alimentoNombre.trim(),
                    cantidadPlanificada = ing.cantidadPlanificada,
                    unidad = ing.unidad.trim()
                )
            }

        if (ingredientesNormalizados.isNotEmpty()) {
            _insertarIngredientes(ingredientesNormalizados)
        }

        return planAGuardar.copy(id = idLocalEfectivo)
    }

    /**
     * Elimina de forma atómica una comida planificada y sus ingredientes asociados,
     * registrando primero un tombstone persistente en `eliminaciones_planes_pendientes`
     * para sincronizar la eliminación con Firestore y evitar que reaparezca al recuperar de la nube.
     */
    @Transaction
    open suspend fun eliminarPlanConIngredientes(
        planRemoteId: String,
        usuarioUid: String
    ): Boolean {
        val uidLimpio = usuarioUid.trim()
        val remoteIdLimpio = planRemoteId.trim()
        if (uidLimpio.isEmpty() || remoteIdLimpio.isEmpty()) return false

        val actual = obtenerPlanPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio) ?: return false

        val baseConocida = if (actual.baseRemotaActualizadoEn > 0L) {
            actual.baseRemotaActualizadoEn
        } else {
            actual.actualizadoEn
        }

        eliminarTombstonePorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)
        _insertarTombstone(
            EliminacionPlanPendiente(
                remoteId = remoteIdLimpio,
                usuarioUid = uidLimpio,
                fechaInicioSemana = actual.fechaInicioSemana,
                diaSemana = actual.diaSemana,
                tipoComida = actual.tipoComida,
                nombrePlato = actual.nombrePlato,
                fechaEliminacion = System.currentTimeMillis(),
                baseRemotaActualizadoEn = baseConocida
            )
        )

        _eliminarIngredientesPorPlanYUsuario(remoteIdLimpio, uidLimpio)
        val borrados = _eliminarPlanPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)
        return borrados > 0
    }

    /**
     * Integra en Room un plan descargado desde Cloud Firestore respetando:
     * 1. Si existe una eliminación local pendiente (tombstone), NO vuelve a insertar el plan borrado.
     * 2. Si existe un plan local con cambios pendientes (`pendienteSincronizacion == true`), NO lo sobrescribe.
     * 3. Evita duplicar comidas en la misma semana, día y tipo de comida.
     */
    @Transaction
    open suspend fun integrarPlanRecuperadoDeNube(
        planRemoto: PlanComidaFirestore,
        usuarioUid: String
    ): EstadoIntegracionPlanRemoto {
        val uidLimpio = usuarioUid.trim()
        val remoteIdLimpio = planRemoto.remoteId.trim()
        if (uidLimpio.isEmpty() || remoteIdLimpio.isEmpty()) {
            return EstadoIntegracionPlanRemoto.OMITIDO
        }

        // 1. Verificar si el plan fue eliminado localmente (tombstone pendiente)
        val tombstone = obtenerTombstonePorRemoteIdOCasilla(
            usuarioUid = uidLimpio,
            remoteId = remoteIdLimpio,
            fechaInicioSemana = planRemoto.fechaInicioSemana.trim(),
            diaSemana = planRemoto.diaSemana,
            tipoComida = planRemoto.tipoComida.trim()
        )
        if (tombstone != null) {
            val limiteLocal = maxOf(tombstone.fechaEliminacion, tombstone.baseRemotaActualizadoEn)
            return if (planRemoto.actualizadoEn > limiteLocal) {
                EstadoIntegracionPlanRemoto.CONFLICTO_CAMBIO_LOCAL_PENDIENTE
            } else {
                EstadoIntegracionPlanRemoto.OMITIDO
            }
        }

        // 2. Comprobar si ya existe localmente por remoteId o en la misma casilla (semana, día, tipo)
        val existentePorId = obtenerPlanPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)
        val existentePorCasilla = obtenerPlanPorCasillaYUsuario(
            usuarioUid = uidLimpio,
            fechaInicioSemana = planRemoto.fechaInicioSemana.trim(),
            diaSemana = planRemoto.diaSemana,
            tipoComida = planRemoto.tipoComida.trim()
        )
        val existente = existentePorId ?: existentePorCasilla

        if (existente != null) {
            // Nunca sobrescribir un plan local con cambios pendientes de subir
            if (existente.pendienteSincronizacion) {
                return EstadoIntegracionPlanRemoto.CONFLICTO_CAMBIO_LOCAL_PENDIENTE
            }
            // Si el plan local ya está sincronizado y es igual o más reciente, o tiene otro remoteId en esa casilla, omitir
            if (existente.remoteId != remoteIdLimpio || planRemoto.actualizadoEn <= existente.actualizadoEn) {
                return EstadoIntegracionPlanRemoto.OMITIDO
            }
        }

        val marcaRemota = planRemoto.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        val planLocal = PlanComida(
            id = existente?.id ?: 0,
            remoteId = remoteIdLimpio,
            usuarioUid = uidLimpio,
            fechaInicioSemana = planRemoto.fechaInicioSemana.trim(),
            diaSemana = planRemoto.diaSemana,
            tipoComida = planRemoto.tipoComida.trim(),
            nombrePlato = planRemoto.nombrePlato.trim(),
            pendienteSincronizacion = false,
            actualizadoEn = marcaRemota,
            baseRemotaActualizadoEn = marcaRemota
        )

        val nuevoId = _insertarPlan(planLocal).toInt()
        val idLocalEfectivo = if (planLocal.id != 0) planLocal.id else nuevoId

        _eliminarIngredientesPorPlanYUsuario(remoteIdLimpio, uidLimpio)

        val ingredientesLocales = planRemoto.ingredientes.map { ing ->
            IngredientePlanificado(
                id = 0,
                remoteId = ing.remoteId.trim().ifEmpty { UUID.randomUUID().toString() },
                planId = idLocalEfectivo,
                planRemoteId = remoteIdLimpio,
                usuarioUid = uidLimpio,
                alimentoRemoteId = ing.alimentoRemoteId.trim(),
                alimentoNombre = ing.alimentoNombre.trim(),
                cantidadPlanificada = ing.cantidadPlanificada,
                unidad = ing.unidad.trim()
            )
        }

        if (ingredientesLocales.isNotEmpty()) {
            _insertarIngredientes(ingredientesLocales)
        }

        return EstadoIntegracionPlanRemoto.DESCARGADO
    }
}
