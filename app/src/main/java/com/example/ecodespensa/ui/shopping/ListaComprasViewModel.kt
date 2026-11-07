package com.example.ecodespensa.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.local.EstadoIntegracionItemCompraRemoto
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.SugerenciaCompraAutomatica
import com.example.ecodespensa.data.repository.AlimentoRepository
import com.example.ecodespensa.data.repository.AuthRepository
import com.example.ecodespensa.data.repository.EstadoEliminacionItemCompra
import com.example.ecodespensa.data.repository.EstadoSubidaItemCompra
import com.example.ecodespensa.data.repository.FirestoreItemCompraRepository
import com.example.ecodespensa.data.repository.ItemCompraRepository
import com.example.ecodespensa.data.repository.PlanComidaRepository
import com.example.ecodespensa.data.repository.ResultadoDescargaListaCompras
import com.example.ecodespensa.data.repository.ResultadoSubidaListaCompras
import com.example.ecodespensa.util.UnidadConversionUtils
import com.example.ecodespensa.util.calcularEstado
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
import kotlin.math.abs

data class ResumenSugerenciasCompras(
    val sugerencias: List<SugerenciaCompraAutomatica> = emptyList(),
    val ingredientesCubiertosPorDespensa: Int = 0
)

sealed interface SubidaListaComprasUiState {
    data object Idle : SubidaListaComprasUiState
    data class Subiendo(val procesados: Int, val total: Int) : SubidaListaComprasUiState
    data class Resultado(val resumen: ResultadoSubidaListaCompras) : SubidaListaComprasUiState
    data class ErrorBloqueante(val mensaje: String) : SubidaListaComprasUiState
}

sealed interface DescargaListaComprasUiState {
    data object Idle : DescargaListaComprasUiState
    data object Descargando : DescargaListaComprasUiState
    data class Resultado(val resumen: ResultadoDescargaListaCompras) : DescargaListaComprasUiState
    data class ErrorBloqueante(val mensaje: String) : DescargaListaComprasUiState
}

/**
 * ViewModel de la Lista Inteligente de Compras.
 *
 * - Aísla todos los productos y sugerencias por `usuarioUid`.
 * - Cruza los ingredientes del Planificador Semanal con el stock vigente de Mi Despensa.
 * - Agrupa ingredientes repetidos, compara unidades compatibles sin conversiones inválidas,
 *   prioriza aprovechar el stock existente y sugiere únicamente las cantidades faltantes.
 * - Marcar un producto como comprado NO altera el stock de Mi Despensa ni genera movimientos COMPRA.
 * - Sincroniza manualmente los productos y sus eliminaciones (tombstones) con
 *   `usuarios/{uid}/items_compra/{remoteId}` en Cloud Firestore.
 */
class ListaComprasViewModel(
    private val itemCompraRepository: ItemCompraRepository,
    private val planComidaRepository: PlanComidaRepository,
    private val alimentoRepository: AlimentoRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val firestoreItemCompraRepository: FirestoreItemCompraRepository = FirestoreItemCompraRepository()
) : ViewModel() {

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _subidaListaComprasState =
        MutableStateFlow<SubidaListaComprasUiState>(SubidaListaComprasUiState.Idle)
    val subidaListaComprasState: StateFlow<SubidaListaComprasUiState> =
        _subidaListaComprasState.asStateFlow()

    private val _descargaListaComprasState =
        MutableStateFlow<DescargaListaComprasUiState>(DescargaListaComprasUiState.Idle)
    val descargaListaComprasState: StateFlow<DescargaListaComprasUiState> =
        _descargaListaComprasState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val itemsCompra: StateFlow<List<ItemCompra>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) {
                flowOf(emptyList())
            } else {
                itemCompraRepository.observarItemsPorUsuario(uidLimpio)
                    .map { lista -> lista.filter { it.usuarioUid == uidLimpio } }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalEliminacionesCompraCuenta: StateFlow<Int> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) {
                flowOf(0)
            } else {
                itemCompraRepository.observarEliminacionesPendientesPorUsuario(uidLimpio)
                    .map { lista -> lista.count { it.usuarioUid == uidLimpio } }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val alimentosUsuario: StateFlow<List<Alimento>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            val uidLimpio = uid?.trim().orEmpty()
            if (uidLimpio.isEmpty()) {
                flowOf(emptyList())
            } else {
                alimentoRepository.obtenerAlimentosPorUsuario(uidLimpio)
                    .map { lista -> lista.filter { it.usuarioUid == uidLimpio } }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Alimentos vigentes con stock > 0 en Mi Despensa (para avisar al usuario si intenta comprar
     * manualmente algo que ya tiene disponible en casa).
     */
    val alimentosVigentesEnDespensa: StateFlow<List<Alimento>> = alimentosUsuario
        .map { lista ->
            val hoy = LocalDate.now()
            lista.filter { alimento ->
                parsearCantidad(alimento.cantidadActual) > 0.0 &&
                    calcularEstado(alimento, hoy) != EstadoVencimiento.VENCIDO
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val planesActivosUsuario: StateFlow<List<PlanComidaConIngredientes>> =
        authRepository.uidActualFlow
            .flatMapLatest { uid ->
                val uidLimpio = uid?.trim().orEmpty()
                if (uidLimpio.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val lunesActual = LocalDate.now()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .format(isoFormatter)
                    planComidaRepository.observarPlanesActivos(uidLimpio, lunesActual)
                        .map { lista ->
                            lista
                                .filter { it.plan.usuarioUid == uidLimpio }
                                .map { item ->
                                    item.copy(
                                        ingredientes = item.ingredientes.filter { ing ->
                                            ing.usuarioUid == uidLimpio
                                        }
                                    )
                                }
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Calcula reactivamente las sugerencias automáticas de compra:
     * 1. Agrupa ingredientes repetidos de las comidas planificadas por alimento y familia de unidad compatible.
     * 2. Descuenta primero el stock vigente (no vencido y > 0) disponible en Mi Despensa si las unidades son compatibles.
     * 3. Si las unidades no son compatibles, NO realiza conversiones incorrectas e informa la diferencia.
     * 4. Sugiere comprar únicamente la cantidad faltante (> 0).
     */
    val resumenSugerencias: StateFlow<ResumenSugerenciasCompras> = combine(
        planesActivosUsuario,
        alimentosVigentesEnDespensa,
        itemsCompra
    ) { planes, despensaVigente, listaActual ->
        calcularSugerenciasFaltantes(
            planes = planes,
            despensaVigente = despensaVigente,
            itemsEnLista = listaActual
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumenSugerenciasCompras()
    )

    fun agregarManual(
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String,
        alimentoRemoteId: String = ""
    ) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (nombre.isBlank() || cantidad <= 0.0) return
        viewModelScope.launch {
            itemCompraRepository.agregarItemManual(
                nombre = nombre,
                cantidad = cantidad,
                unidad = unidad,
                nota = nota,
                alimentoRemoteId = alimentoRemoteId,
                usuarioUid = uid
            )
        }
    }

    fun agregarSugerenciaALista(sugerencia: SugerenciaCompraAutomatica) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (sugerencia.cantidadFaltante <= 0.0) return
        viewModelScope.launch {
            itemCompraRepository.agregarDesdeSugerencia(
                nombre = sugerencia.nombre,
                cantidadFaltante = sugerencia.cantidadFaltante,
                unidad = sugerencia.unidad,
                alimentoRemoteId = sugerencia.alimentoRemoteId,
                detalleExplicacion = sugerencia.detalleExplicacion,
                usuarioUid = uid
            )
        }
    }

    fun editarItem(
        id: Int,
        nombre: String,
        cantidad: Double,
        unidad: String,
        nota: String
    ) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (nombre.isBlank() || cantidad <= 0.0) return
        viewModelScope.launch {
            itemCompraRepository.editarItem(
                id = id,
                nombre = nombre,
                cantidad = cantidad,
                unidad = unidad,
                nota = nota,
                usuarioUid = uid
            )
        }
    }

    /**
     * Marca o desmarca un producto como comprado en la lista.
     * NO modifica el stock de Mi Despensa ni genera movimientos COMPRA.
     */
    fun cambiarComprado(item: ItemCompra, comprado: Boolean) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (item.usuarioUid != uid) return
        viewModelScope.launch {
            itemCompraRepository.marcarComprado(
                id = item.id,
                comprado = comprado,
                usuarioUid = uid
            )
        }
    }

    fun eliminarItem(item: ItemCompra) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (item.usuarioUid != uid) return
        viewModelScope.launch {
            itemCompraRepository.eliminarItem(id = item.id, usuarioUid = uid)
        }
    }

    fun limpiarComprados() {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            itemCompraRepository.eliminarComprados(uid)
        }
    }

    /**
     * Sube manualmente a `usuarios/{uid}/items_compra/{remoteId}` los productos y eliminaciones
     * pendientes (tombstones) del usuario autenticado, detectando conflictos y sin modificar
     * Mi Despensa ni generar movimientos COMPRA.
     */
    fun subirMiListaComprasALaNube() {
        if (_subidaListaComprasState.value is SubidaListaComprasUiState.Subiendo) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _subidaListaComprasState.value = SubidaListaComprasUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para subir tu lista de compras."
            )
            return
        }

        viewModelScope.launch {
            try {
                val items = itemCompraRepository.obtenerListaPorUsuario(uid)
                    .filter { it.usuarioUid == uid }
                val eliminaciones = itemCompraRepository.obtenerEliminacionesPendientesPorUsuario(uid)
                    .filter { it.usuarioUid == uid }

                val total = items.size + eliminaciones.size
                _subidaListaComprasState.value = SubidaListaComprasUiState.Subiendo(0, total)

                var subidos = 0
                var eliminados = 0
                var omitidos = 0
                var conflictos = 0
                var errores = 0
                var ultimoError: String? = null
                var procesados = 0

                for (item in items) {
                    val uidActual = authRepository.usuarioActual?.uid?.trim()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la subida de la lista de compras."
                        break
                    }

                    firestoreItemCompraRepository.sincronizarItemCompra(uid, item).fold(
                        onSuccess = { respuesta ->
                            when (respuesta.estado) {
                                EstadoSubidaItemCompra.SUBIDO -> {
                                    itemCompraRepository.marcarItemSincronizado(
                                        remoteId = item.remoteId,
                                        usuarioUid = uid,
                                        actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                                    )
                                    subidos++
                                }
                                EstadoSubidaItemCompra.OMITIDO_YA_EXISTE -> {
                                    omitidos++
                                }
                                EstadoSubidaItemCompra.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                                    conflictos++
                                    ultimoError =
                                        "Conflicto en '${item.nombre}': la nube tiene una versión más reciente y no se sobrescribió."
                                }
                            }
                        },
                        onFailure = { e ->
                            errores++
                            ultimoError =
                                e.localizedMessage ?: "Error al sincronizar '${item.nombre}' con Firestore."
                        }
                    )

                    procesados++
                    _subidaListaComprasState.value =
                        SubidaListaComprasUiState.Subiendo(procesados, total)
                }

                for (tombstone in eliminaciones) {
                    val uidActual = authRepository.usuarioActual?.uid?.trim()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la subida de la lista de compras."
                        break
                    }

                    firestoreItemCompraRepository.sincronizarEliminacionItemCompra(uid, tombstone).fold(
                        onSuccess = { estado ->
                            when (estado) {
                                EstadoEliminacionItemCompra.ELIMINADO_CONFIRMADO -> {
                                    itemCompraRepository.limpiarEliminacionItemPendiente(
                                        remoteId = tombstone.remoteId,
                                        usuarioUid = uid
                                    )
                                    eliminados++
                                }
                                EstadoEliminacionItemCompra.CONFLICTO_CAMBIO_REMOTO_POSTERIOR -> {
                                    conflictos++
                                    ultimoError =
                                        "Conflicto al eliminar '${tombstone.nombre}': el producto en la nube tiene cambios posteriores."
                                }
                            }
                        },
                        onFailure = { e ->
                            errores++
                            ultimoError =
                                e.localizedMessage ?: "No se pudo eliminar '${tombstone.nombre}' en Firestore."
                        }
                    )

                    procesados++
                    _subidaListaComprasState.value =
                        SubidaListaComprasUiState.Subiendo(procesados, total)
                }

                _subidaListaComprasState.value = SubidaListaComprasUiState.Resultado(
                    ResultadoSubidaListaCompras(
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
                _subidaListaComprasState.value = SubidaListaComprasUiState.ErrorBloqueante(
                    e.localizedMessage ?: "No se pudo completar la subida de la lista de compras."
                )
            }
        }
    }

    fun limpiarEstadoSubidaListaCompras() {
        if (_subidaListaComprasState.value !is SubidaListaComprasUiState.Subiendo) {
            _subidaListaComprasState.value = SubidaListaComprasUiState.Idle
        }
    }

    /**
     * Recupera manualmente desde `usuarios/{uid}/items_compra` los productos de la lista de compras
     * del usuario autenticado e integra en Room únicamente los válidos sin sobrescribir cambios
     * locales pendientes ni reinsertar productos con eliminación pendiente (tombstones).
     * NO modifica Mi Despensa ni genera movimientos COMPRA.
     */
    fun recuperarMiListaComprasDeLaNube() {
        if (_descargaListaComprasState.value is DescargaListaComprasUiState.Descargando) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _descargaListaComprasState.value = DescargaListaComprasUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para recuperar tu lista de compras."
            )
            return
        }

        viewModelScope.launch {
            _descargaListaComprasState.value = DescargaListaComprasUiState.Descargando
            firestoreItemCompraRepository.descargarItemsCompraValidados(uid).fold(
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
                            itemCompraRepository.integrarItemRecuperadoDeNube(remoto, uid)
                        }.fold(
                            onSuccess = { estado ->
                                when (estado) {
                                    EstadoIntegracionItemCompraRemoto.INSERTADO_NUEVO,
                                    EstadoIntegracionItemCompraRemoto.ACTUALIZADO -> descargados++
                                    EstadoIntegracionItemCompraRemoto.OMITIDO_SIN_CAMBIOS,
                                    EstadoIntegracionItemCompraRemoto.OMITIDO_ELIMINADO_LOCALMENTE -> omitidos++
                                    EstadoIntegracionItemCompraRemoto.CONFLICTO_LOCAL_PENDIENTE -> {
                                        conflictos++
                                        ultimoError =
                                            "Se conservaron tus cambios locales pendientes en '${remoto.nombre}' sin sobrescribirlos."
                                    }
                                }
                            },
                            onFailure = { e ->
                                errores++
                                ultimoError = e.localizedMessage ?: "Error al guardar producto en Room."
                            }
                        )
                    }

                    _descargaListaComprasState.value = DescargaListaComprasUiState.Resultado(
                        ResultadoDescargaListaCompras(
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
                    _descargaListaComprasState.value = DescargaListaComprasUiState.ErrorBloqueante(
                        e.localizedMessage
                            ?: "No se pudo consultar tu lista de compras en Cloud Firestore. Verifica tu conexión a Internet."
                    )
                }
            )
        }
    }

    fun limpiarEstadoDescargaListaCompras() {
        if (_descargaListaComprasState.value !is DescargaListaComprasUiState.Descargando) {
            _descargaListaComprasState.value = DescargaListaComprasUiState.Idle
        }
    }

    private fun calcularSugerenciasFaltantes(
        planes: List<PlanComidaConIngredientes>,
        despensaVigente: List<Alimento>,
        itemsEnLista: List<ItemCompra>
    ): ResumenSugerenciasCompras {
        val todosIngredientes: List<IngredientePlanificado> = planes.flatMap { it.ingredientes }
            .filter { it.alimentoNombre.isNotBlank() && it.cantidadPlanificada > 0.0 }

        if (todosIngredientes.isEmpty()) {
            return ResumenSugerenciasCompras()
        }

        // Agrupar ingredientes repetidos por alimento (remoteId o nombre normalizado) + familia de unidad compatible
        val grupos = todosIngredientes.groupBy { ing ->
            val claveAlimento = ing.alimentoRemoteId.trim().lowercase()
                .ifEmpty { ing.alimentoNombre.trim().lowercase() }
            val familia = UnidadConversionUtils.analizarUnidad(ing.unidad).claveFamilia
            "$claveAlimento|$familia"
        }

        val sugerencias = mutableListOf<SugerenciaCompraAutomatica>()
        var cubiertosPorDespensa = 0
        val pendientesEnLista = itemsEnLista.filter { !it.comprado }

        for ((claveGrupo, listaIng) in grupos) {
            val primero = listaIng.first()
            val unidadRepresentativa = listaIng
                .map { it.unidad.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()

            val totalPlanificadoBase = listaIng.sumOf { ing ->
                UnidadConversionUtils.aCantidadBase(ing.cantidadPlanificada, ing.unidad)
            }
            val totalPlanificadoEnUnidad = UnidadConversionUtils.desdeCantidadBase(
                totalPlanificadoBase,
                unidadRepresentativa
            )

            // Buscar alimentos vigentes en Mi Despensa que correspondan a este ingrediente
            val coincidenciasDespensa = despensaVigente.filter { al ->
                (primero.alimentoRemoteId.isNotBlank() &&
                    al.remoteId.equals(primero.alimentoRemoteId.trim(), ignoreCase = true)) ||
                    al.nombre.trim().equals(primero.alimentoNombre.trim(), ignoreCase = true)
            }

            val compatiblesDespensa = coincidenciasDespensa.filter { al ->
                UnidadConversionUtils.sonCompatibles(al.unidad, unidadRepresentativa)
            }
            val incompatiblesDespensa = coincidenciasDespensa.filter { al ->
                !UnidadConversionUtils.sonCompatibles(al.unidad, unidadRepresentativa)
            }

            val disponibleBase = compatiblesDespensa.sumOf { al ->
                UnidadConversionUtils.aCantidadBase(parsearCantidad(al.cantidadActual), al.unidad)
            }
            val disponibleEnUnidad = UnidadConversionUtils.desdeCantidadBase(
                disponibleBase,
                unidadRepresentativa
            )

            // Si el stock compatible de Mi Despensa cubre todo lo planificado, se aprovecha y NO se sugiere comprar
            if (disponibleBase >= totalPlanificadoBase - 1e-6) {
                cubiertosPorDespensa++
                continue
            }

            val faltanteBase = (totalPlanificadoBase - disponibleBase).coerceAtLeast(0.0)
            val faltanteEnUnidad = UnidadConversionUtils.desdeCantidadBase(
                faltanteBase,
                unidadRepresentativa
            )

            val tieneIncompatibles = incompatiblesDespensa.isNotEmpty() && compatiblesDespensa.isEmpty()
            val resumenIncompatibles = incompatiblesDespensa.joinToString(", ") { al ->
                "${al.cantidadActual} ${al.unidad}".trim()
            }

            val unidadTexto = if (unidadRepresentativa.isNotBlank()) " $unidadRepresentativa" else ""
            val detalle = when {
                tieneIncompatibles ->
                    "Planificado: ${formatearNumero(totalPlanificadoEnUnidad)}$unidadTexto • " +
                        "En despensa tienes $resumenIncompatibles (unidades no compatibles; revisa antes de comprar)"
                disponibleBase > 0.0 ->
                    "Planificado: ${formatearNumero(totalPlanificadoEnUnidad)}$unidadTexto • " +
                        "En despensa: ${formatearNumero(disponibleEnUnidad)}$unidadTexto • " +
                        "Faltan ${formatearNumero(faltanteEnUnidad)}$unidadTexto"
                else ->
                    "Planificado: ${formatearNumero(totalPlanificadoEnUnidad)}$unidadTexto • " +
                        "Sin stock disponible en Mi Despensa"
            }

            // Verificar si el usuario ya agregó al menos esta cantidad faltante a su lista pendiente
            val totalEnListaBase = pendientesEnLista
                .filter { item ->
                    val mismoAlimento = (primero.alimentoRemoteId.isNotBlank() &&
                        item.alimentoRemoteId.equals(primero.alimentoRemoteId.trim(), ignoreCase = true)) ||
                        item.nombre.trim().equals(primero.alimentoNombre.trim(), ignoreCase = true)
                    mismoAlimento && UnidadConversionUtils.sonCompatibles(item.unidad, unidadRepresentativa)
                }
                .sumOf { item ->
                    UnidadConversionUtils.aCantidadBase(item.cantidad, item.unidad)
                }

            val yaCubierto = totalEnListaBase >= faltanteBase - 1e-6

            sugerencias.add(
                SugerenciaCompraAutomatica(
                    claveAgrupacion = claveGrupo,
                    alimentoRemoteId = primero.alimentoRemoteId,
                    nombre = primero.alimentoNombre,
                    cantidadFaltante = redondearLimpio(faltanteEnUnidad),
                    unidad = unidadRepresentativa,
                    cantidadPlanificadaTotal = redondearLimpio(totalPlanificadoEnUnidad),
                    cantidadDisponibleDespensa = redondearLimpio(disponibleEnUnidad),
                    unidadDespensa = unidadRepresentativa,
                    unidadesIncompatibles = tieneIncompatibles,
                    detalleExplicacion = detalle,
                    yaCubiertoEnLista = yaCubierto
                )
            )
        }

        return ResumenSugerenciasCompras(
            sugerencias = sugerencias.sortedWith(
                compareBy<SugerenciaCompraAutomatica> { it.yaCubiertoEnLista }
                    .thenBy { it.nombre.lowercase() }
            ),
            ingredientesCubiertosPorDespensa = cubiertosPorDespensa
        )
    }

    private fun parsearCantidad(valor: String): Double =
        valor.replace(',', '.')
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toDoubleOrNull() ?: 0.0

    private fun redondearLimpio(valor: Double): Double =
        if (abs(valor - Math.round(valor)) < 1e-4) Math.round(valor).toDouble()
        else Math.round(valor * 100.0) / 100.0

    private fun formatearNumero(valor: Double): String {
        val limpio = redondearLimpio(valor)
        return if (limpio % 1.0 == 0.0) limpio.toLong().toString() else limpio.toString()
    }

    class Factory(
        private val itemCompraRepository: ItemCompraRepository,
        private val planComidaRepository: PlanComidaRepository,
        private val alimentoRepository: AlimentoRepository,
        private val authRepository: AuthRepository = AuthRepository(),
        private val firestoreItemCompraRepository: FirestoreItemCompraRepository = FirestoreItemCompraRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListaComprasViewModel::class.java)) {
                return ListaComprasViewModel(
                    itemCompraRepository = itemCompraRepository,
                    planComidaRepository = planComidaRepository,
                    alimentoRepository = alimentoRepository,
                    authRepository = authRepository,
                    firestoreItemCompraRepository = firestoreItemCompraRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
