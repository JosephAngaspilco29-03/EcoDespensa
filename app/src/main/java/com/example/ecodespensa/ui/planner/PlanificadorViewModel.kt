package com.example.ecodespensa.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.local.EstadoIntegracionPlanRemoto
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.DiaSemana
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.PlanComida
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.TipoComida
import com.example.ecodespensa.data.repository.AlimentoRepository
import com.example.ecodespensa.data.repository.AuthRepository
import com.example.ecodespensa.data.repository.EstadoEliminacionPlanComida
import com.example.ecodespensa.data.repository.EstadoSubidaPlanComida
import com.example.ecodespensa.data.repository.FirestorePlanComidaRepository
import com.example.ecodespensa.data.repository.PlanComidaRepository
import com.example.ecodespensa.data.repository.ResultadoDescargaPlanificador
import com.example.ecodespensa.data.repository.ResultadoSubidaPlanificador
import com.example.ecodespensa.util.calcularEstado
import com.example.ecodespensa.util.diasParaVencer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

sealed class SubidaPlanificadorUiState {
    data object Idle : SubidaPlanificadorUiState()
    data class Subiendo(val procesados: Int, val total: Int) : SubidaPlanificadorUiState()
    data class Resultado(val resumen: ResultadoSubidaPlanificador) : SubidaPlanificadorUiState()
    data class ErrorBloqueante(val mensaje: String) : SubidaPlanificadorUiState()
}

sealed class DescargaPlanificadorUiState {
    data object Idle : DescargaPlanificadorUiState()
    data object Descargando : DescargaPlanificadorUiState()
    data class Resultado(val resumen: ResultadoDescargaPlanificador) : DescargaPlanificadorUiState()
    data class ErrorBloqueante(val mensaje: String) : DescargaPlanificadorUiState()
}

/**
 * ViewModel del Planificador Semanal de Comidas (Etapas 5A + 5B + 5C).
 *
 * - Permite navegar entre semanas usando el lunes ISO (`yyyy-MM-dd`) como `fechaInicioSemana`.
 * - Aísla todos los planes e ingredientes por el `uid` autenticado en Firebase.
 * - Expone los alimentos de Mi Despensa priorizando primero los próximos a vencer con stock > 0,
 *   separando los vencidos o agotados para no sugerirlos como disponibles.
 * - Sincroniza manualmente el planificador con Cloud Firestore (`usuarios/{uid}/planes_comida/{remoteId}`)
 *   sin duplicar casillas, sin sobrescribir cambios locales pendientes y respetando tombstones de eliminación.
 * - Planificar o sincronizar comidas NO descuenta stock ni registra movimientos de consumo.
 */
class PlanificadorViewModel(
    private val planRepository: PlanComidaRepository,
    private val alimentoRepository: AlimentoRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val firestorePlanRepository: FirestorePlanComidaRepository = FirestorePlanComidaRepository()
) : ViewModel() {

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _lunesSemanaSeleccionada = MutableStateFlow(lunesDe(LocalDate.now()))
    val lunesSemanaSeleccionada: StateFlow<LocalDate> = _lunesSemanaSeleccionada.asStateFlow()

    private val _subidaPlanificadorState =
        MutableStateFlow<SubidaPlanificadorUiState>(SubidaPlanificadorUiState.Idle)
    val subidaPlanificadorState: StateFlow<SubidaPlanificadorUiState> =
        _subidaPlanificadorState.asStateFlow()

    private val _descargaPlanificadorState =
        MutableStateFlow<DescargaPlanificadorUiState>(DescargaPlanificadorUiState.Idle)
    val descargaPlanificadorState: StateFlow<DescargaPlanificadorUiState> =
        _descargaPlanificadorState.asStateFlow()

    val fechaInicioSemanaIso: StateFlow<String> = _lunesSemanaSeleccionada
        .map { it.format(isoFormatter) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _lunesSemanaSeleccionada.value.format(isoFormatter)
        )

    fun semanaAnterior() {
        _lunesSemanaSeleccionada.value = _lunesSemanaSeleccionada.value.minusWeeks(1)
    }

    fun semanaSiguiente() {
        _lunesSemanaSeleccionada.value = _lunesSemanaSeleccionada.value.plusWeeks(1)
    }

    fun irASemanaActual() {
        _lunesSemanaSeleccionada.value = lunesDe(LocalDate.now())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalPlanesCuenta: StateFlow<Int> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) flowOf(0)
            else planRepository.observarTotalPlanesPorUsuario(uidLimpio)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalEliminacionesPlanCuenta: StateFlow<Int> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) flowOf(0)
            else planRepository.observarTotalEliminacionesPorUsuario(uidLimpio)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Planes de comida (con ingredientes) de la cuenta autenticada y la semana seleccionada.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val planesSemana: StateFlow<List<PlanComidaConIngredientes>> = combine(
        authRepository.uidActualFlow,
        _lunesSemanaSeleccionada
    ) { uid, lunes ->
        Pair(uid?.trim().orEmpty(), lunes.format(isoFormatter))
    }.flatMapLatest { (uid, fechaInicio) ->
        if (uid.isEmpty()) {
            flowOf(emptyList())
        } else {
            planRepository.observarPlanesSemana(uid, fechaInicio).map { lista ->
                lista
                    .filter { it.plan.usuarioUid == uid }
                    .map { item ->
                        item.copy(
                            ingredientes = item.ingredientes.filter { ing -> ing.usuarioUid == uid }
                        )
                    }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val alimentosUsuario: StateFlow<List<Alimento>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) {
                flowOf(emptyList())
            } else {
                alimentoRepository.obtenerAlimentosPorUsuario(uidLimpio)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Alimentos de Mi Despensa DISPONIBLES para planificar:
     * - Tienen stock > 0 y NO están vencidos.
     * - Ordenados dando prioridad a los próximos a vencer (`PROXIMO`, fecha más cercana primero).
     */
    val alimentosDisponiblesPriorizados: StateFlow<List<Alimento>> = alimentosUsuario
        .map { lista ->
            val hoy = LocalDate.now()
            lista
                .filter { alimento ->
                    parsearStock(alimento.cantidadActual) > 0.0 &&
                        calcularEstado(alimento, hoy) != EstadoVencimiento.VENCIDO
                }
                .sortedWith { a, b ->
                    val estadoA = calcularEstado(a, hoy)
                    val estadoB = calcularEstado(b, hoy)
                    val proximoA = estadoA == EstadoVencimiento.PROXIMO
                    val proximoB = estadoB == EstadoVencimiento.PROXIMO
                    if (proximoA != proximoB) {
                        if (proximoA) -1 else 1
                    } else {
                        val diasA = diasParaVencer(a, hoy)
                        val diasB = diasParaVencer(b, hoy)
                        when {
                            diasA != null && diasB != null -> {
                                val cmp = diasA.compareTo(diasB)
                                if (cmp != 0) cmp else a.nombre.compareTo(b.nombre, ignoreCase = true)
                            }
                            diasA != null -> -1
                            diasB != null -> 1
                            else -> a.nombre.compareTo(b.nombre, ignoreCase = true)
                        }
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val alimentosVencidosOSinStock: StateFlow<List<Alimento>> = alimentosUsuario
        .map { lista ->
            val hoy = LocalDate.now()
            lista.filter { alimento ->
                parsearStock(alimento.cantidadActual) <= 0.0 ||
                    calcularEstado(alimento, hoy) == EstadoVencimiento.VENCIDO
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun guardarComida(
        diaSemana: DiaSemana,
        tipoComida: TipoComida,
        nombrePlato: String,
        ingredientes: List<IngredientePlanificado>,
        planExistente: PlanComida? = null,
        onGuardado: () -> Unit = {}
    ) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val platoLimpio = nombrePlato.trim()
        if (platoLimpio.isEmpty()) return

        val fechaInicio = _lunesSemanaSeleccionada.value.format(isoFormatter)
        val plan = PlanComida(
            id = planExistente?.id ?: 0,
            remoteId = planExistente?.remoteId?.takeIf { it.isNotBlank() }
                ?: java.util.UUID.randomUUID().toString(),
            usuarioUid = uid,
            fechaInicioSemana = fechaInicio,
            diaSemana = diaSemana.numero,
            tipoComida = tipoComida.name,
            nombrePlato = platoLimpio,
            pendienteSincronizacion = true,
            actualizadoEn = System.currentTimeMillis(),
            baseRemotaActualizadoEn = planExistente?.baseRemotaActualizadoEn ?: 0L
        )

        viewModelScope.launch {
            planRepository.guardarComidaPlanificada(
                plan = plan,
                ingredientes = ingredientes,
                usuarioUid = uid
            )
            onGuardado()
        }
    }

    fun eliminarComida(planRemoteId: String, onEliminado: () -> Unit = {}) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (planRemoteId.isBlank()) return
        viewModelScope.launch {
            planRepository.eliminarComidaPlanificada(planRemoteId, uid)
            onEliminado()
        }
    }

    /**
     * Sube manualmente a `usuarios/{uid}/planes_comida/{remoteId}` los planes y eliminaciones pendientes
     * del usuario autenticado, detectando conflictos y sin generar movimientos ni descontar stock.
     */
    fun subirMiPlanificadorALaNube() {
        if (_subidaPlanificadorState.value is SubidaPlanificadorUiState.Subiendo) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _subidaPlanificadorState.value = SubidaPlanificadorUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para subir tu planificador."
            )
            return
        }

        viewModelScope.launch {
            try {
                val planes = planRepository.obtenerTodosPlanesPorUsuario(uid)
                    .filter { it.plan.usuarioUid == uid }
                    .map { item ->
                        item.copy(
                            ingredientes = item.ingredientes.filter { ing -> ing.usuarioUid == uid }
                        )
                    }

                val eliminaciones = planRepository.obtenerEliminacionesPendientesPorUsuario(uid)
                    .filter { it.usuarioUid == uid }

                val total = planes.size + eliminaciones.size
                _subidaPlanificadorState.value = SubidaPlanificadorUiState.Subiendo(0, total)

                var subidos = 0
                var eliminados = 0
                var omitidos = 0
                var conflictos = 0
                var errores = 0
                var ultimoError: String? = null
                var procesados = 0

                for (item in planes) {
                    val uidActual = authRepository.usuarioActual?.uid?.trim()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la subida del planificador."
                        break
                    }

                    firestorePlanRepository.sincronizarPlanComida(uid, item).fold(
                        onSuccess = { respuesta ->
                            when (respuesta.estado) {
                                EstadoSubidaPlanComida.SUBIDO -> {
                                    planRepository.marcarPlanSincronizado(
                                        remoteId = item.plan.remoteId,
                                        usuarioUid = uid,
                                        actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                                    )
                                    subidos++
                                }
                                EstadoSubidaPlanComida.OMITIDO_YA_EXISTE -> {
                                    omitidos++
                                }
                                EstadoSubidaPlanComida.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                                    conflictos++
                                    ultimoError =
                                        "Conflicto en '${item.plan.nombrePlato}': la nube tiene una versión más reciente y no se sobrescribió."
                                }
                            }
                        },
                        onFailure = { e ->
                            errores++
                            ultimoError =
                                e.localizedMessage ?: "Error al sincronizar '${item.plan.nombrePlato}' con Firestore."
                        }
                    )

                    procesados++
                    _subidaPlanificadorState.value =
                        SubidaPlanificadorUiState.Subiendo(procesados, total)
                }

                for (tombstone in eliminaciones) {
                    val uidActual = authRepository.usuarioActual?.uid?.trim()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la subida del planificador."
                        break
                    }

                    firestorePlanRepository.sincronizarEliminacionPlan(uid, tombstone).fold(
                        onSuccess = { estado ->
                            when (estado) {
                                EstadoEliminacionPlanComida.ELIMINADO_CONFIRMADO -> {
                                    planRepository.limpiarEliminacionPlanPendiente(
                                        remoteId = tombstone.remoteId,
                                        usuarioUid = uid
                                    )
                                    eliminados++
                                }
                                EstadoEliminacionPlanComida.CONFLICTO_CAMBIO_REMOTO_POSTERIOR -> {
                                    conflictos++
                                    ultimoError =
                                        "Conflicto al eliminar '${tombstone.nombrePlato}': el plan en la nube tiene cambios posteriores."
                                }
                            }
                        },
                        onFailure = { e ->
                            errores++
                            ultimoError =
                                e.localizedMessage ?: "No se pudo eliminar '${tombstone.nombrePlato}' en Firestore."
                        }
                    )

                    procesados++
                    _subidaPlanificadorState.value =
                        SubidaPlanificadorUiState.Subiendo(procesados, total)
                }

                _subidaPlanificadorState.value = SubidaPlanificadorUiState.Resultado(
                    ResultadoSubidaPlanificador(
                        total = total,
                        subidos = subidos,
                        eliminados = eliminados,
                        omitidos = omitidos,
                        conflictos = conflictos,
                        errores = errores,
                        detalleError = ultimoError
                    )
                )
            } catch (e: Exception) {
                _subidaPlanificadorState.value = SubidaPlanificadorUiState.ErrorBloqueante(
                    e.localizedMessage ?: "No se pudo completar la subida del planificador."
                )
            }
        }
    }

    fun limpiarEstadoSubidaPlanificador() {
        if (_subidaPlanificadorState.value !is SubidaPlanificadorUiState.Subiendo) {
            _subidaPlanificadorState.value = SubidaPlanificadorUiState.Idle
        }
    }

    /**
     * Recupera manualmente desde `usuarios/{uid}/planes_comida` los planes de comida del usuario
     * autenticado e integra en Room únicamente los válidos sin sobrescribir cambios locales pendientes
     * ni reinsertar planes con eliminación local pendiente (tombstones).
     */
    fun recuperarMiPlanificadorDeLaNube() {
        if (_descargaPlanificadorState.value is DescargaPlanificadorUiState.Descargando) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _descargaPlanificadorState.value = DescargaPlanificadorUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para recuperar tu planificador."
            )
            return
        }

        viewModelScope.launch {
            _descargaPlanificadorState.value = DescargaPlanificadorUiState.Descargando
            firestorePlanRepository.descargarPlanesValidados(uid).fold(
                onSuccess = { lote ->
                    var descargados = 0
                    var omitidos = 0
                    var conflictos = 0
                    var errores = lote.erroresValidacion
                    var ultimoError = lote.detalleUltimoError

                    for (remoto in lote.validos) {
                        val uidActual = authRepository.usuarioActual?.uid?.trim()
                        if (uidActual == null || uidActual != uid) {
                            errores++
                            ultimoError = "La sesión cambió o se cerró durante la recuperación."
                            break
                        }

                        runCatching {
                            planRepository.integrarPlanRecuperadoDeNube(remoto, uid)
                        }.fold(
                            onSuccess = { estado ->
                                when (estado) {
                                    EstadoIntegracionPlanRemoto.DESCARGADO -> descargados++
                                    EstadoIntegracionPlanRemoto.OMITIDO -> omitidos++
                                    EstadoIntegracionPlanRemoto.CONFLICTO_CAMBIO_LOCAL_PENDIENTE -> {
                                        conflictos++
                                        ultimoError =
                                            "Se conservaron tus cambios locales pendientes sin sobrescribirlos."
                                    }
                                }
                            },
                            onFailure = { e ->
                                errores++
                                ultimoError = e.localizedMessage ?: "Error al guardar plan en Room."
                            }
                        )
                    }

                    _descargaPlanificadorState.value = DescargaPlanificadorUiState.Resultado(
                        ResultadoDescargaPlanificador(
                            totalRemotos = lote.totalDocumentos,
                            descargados = descargados,
                            omitidos = omitidos,
                            conflictos = conflictos,
                            errores = errores,
                            detalleError = ultimoError
                        )
                    )
                },
                onFailure = { e ->
                    _descargaPlanificadorState.value = DescargaPlanificadorUiState.ErrorBloqueante(
                        e.localizedMessage
                            ?: "No se pudo consultar tu planificador en Cloud Firestore. Verifica tu conexión a Internet."
                    )
                }
            )
        }
    }

    fun limpiarEstadoDescargaPlanificador() {
        if (_descargaPlanificadorState.value !is DescargaPlanificadorUiState.Descargando) {
            _descargaPlanificadorState.value = DescargaPlanificadorUiState.Idle
        }
    }

    private fun lunesDe(fecha: LocalDate): LocalDate =
        fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun parsearStock(valor: String): Double =
        valor.replace(',', '.')
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toDoubleOrNull() ?: 0.0

    class Factory(
        private val planRepository: PlanComidaRepository,
        private val alimentoRepository: AlimentoRepository,
        private val authRepository: AuthRepository = AuthRepository(),
        private val firestorePlanRepository: FirestorePlanComidaRepository = FirestorePlanComidaRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlanificadorViewModel::class.java)) {
                return PlanificadorViewModel(
                    planRepository = planRepository,
                    alimentoRepository = alimentoRepository,
                    authRepository = authRepository,
                    firestorePlanRepository = firestorePlanRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
