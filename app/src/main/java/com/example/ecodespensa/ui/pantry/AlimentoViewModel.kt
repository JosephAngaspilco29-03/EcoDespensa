package com.example.ecodespensa.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento
import com.example.ecodespensa.data.repository.AlimentoRepository
import com.example.ecodespensa.data.repository.AuthRepository
import com.example.ecodespensa.data.repository.EstadoSincronizacionEdicion
import com.example.ecodespensa.data.repository.EstadoSincronizacionEliminacion
import com.example.ecodespensa.data.repository.EstadoSubidaAlimento
import com.example.ecodespensa.data.repository.FirestoreAlimentoRepository
import com.example.ecodespensa.data.repository.MovimientoRepository
import com.example.ecodespensa.data.repository.ResultadoDescargaDespensa
import com.example.ecodespensa.data.repository.ResultadoSubidaDespensa
import com.example.ecodespensa.util.calcularEstado
import com.example.ecodespensa.util.parsearFecha
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Estado de la operación manual de subida de alimentos a Cloud Firestore.
 */
sealed class SubidaNubeUiState {
    /** Sin operación en curso. */
    data object Idle : SubidaNubeUiState()

    /** Subida en progreso: indica cuántos alimentos se han procesado del total. */
    data class Subiendo(val procesados: Int, val total: Int) : SubidaNubeUiState()

    /** Operación terminada con el conteo de subidos, omitidos y errores. */
    data class Resultado(val resumen: ResultadoSubidaDespensa) : SubidaNubeUiState()

    /** Operación bloqueada o fallida antes de procesar (por ejemplo, sin sesión activa). */
    data class ErrorBloqueante(val mensaje: String) : SubidaNubeUiState()
}

/**
 * Estado de la operación manual de recuperación de alimentos desde Cloud Firestore hacia Room.
 */
sealed class DescargaNubeUiState {
    /** Sin operación en curso. */
    data object Idle : DescargaNubeUiState()

    /** Descarga y comparación en progreso. */
    data object Descargando : DescargaNubeUiState()

    /** Operación terminada con el resumen de descargados, omitidos y errores. */
    data class Resultado(val resumen: ResultadoDescargaDespensa) : DescargaNubeUiState()

    /** Operación bloqueada o fallida (por ejemplo, sin sesión activa o sin conexión). */
    data class ErrorBloqueante(val mensaje: String) : DescargaNubeUiState()
}

/**
 * Estado de la sincronización automática al crear o editar un alimento (Etapas 4C.3A y 4C.3B).
 */
sealed class SincronizacionCreacionUiState {
    /** Sin aviso activo. */
    data object Idle : SincronizacionCreacionUiState()

    /** Guardado en Room e intentando sincronizar con Cloud Firestore. */
    data class SubiendoNuevo(
        val nombreAlimento: String,
        val mensaje: String = "Guardado en Room. Sincronizando '$nombreAlimento' con la nube..."
    ) : SincronizacionCreacionUiState()

    /** Confirmado en Room y en el servidor de Cloud Firestore. */
    data class Sincronizado(
        val nombreAlimento: String,
        val mensaje: String = "✓ '$nombreAlimento' guardado en Room y sincronizado en la nube."
    ) : SincronizacionCreacionUiState()

    /** Guardado en Room, pero pendiente de sincronizar con la nube (por ejemplo, sin Internet). */
    data class PendienteDeSubir(
        val nombreAlimento: String,
        val mensaje: String = "⚠ '$nombreAlimento' se guardó en Room y está pendiente de sincronizar con la nube. Puedes reintentar en Mi Perfil."
    ) : SincronizacionCreacionUiState()

    /** Error o conflicto detectado al intentar sincronizar con Cloud Firestore. */
    data class ErrorSincronizacion(
        val nombreAlimento: String,
        val mensaje: String
    ) : SincronizacionCreacionUiState()
}

/**
 * ViewModel de Mi Despensa.
 * Expone la lista de alimentos como StateFlow y ofrece operaciones CRUD
 * más las acciones de consumir, desperdiciar y ajustar stock, que actualizan
 * el stock y registran el movimiento correspondiente en Room.
 *
 * Al agregar un alimento nuevo se registra automáticamente un movimiento COMPRA
 * dentro de una transacción atómica Room (ver [AlimentoRepository.agregarAlimentoConCompra]).
 */
class AlimentoViewModel(
    private val repository: AlimentoRepository,
    private val movimientoRepository: MovimientoRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val firestoreAlimentoRepository: FirestoreAlimentoRepository = FirestoreAlimentoRepository()
) : ViewModel() {

    private val _vinculandoDatos = MutableStateFlow(false)

    private val _subidaNubeState = MutableStateFlow<SubidaNubeUiState>(SubidaNubeUiState.Idle)
    val subidaNubeState: StateFlow<SubidaNubeUiState> = _subidaNubeState.asStateFlow()

    private val _descargaNubeState = MutableStateFlow<DescargaNubeUiState>(DescargaNubeUiState.Idle)
    val descargaNubeState: StateFlow<DescargaNubeUiState> = _descargaNubeState.asStateFlow()

    private val _sincronizacionCreacionState =
        MutableStateFlow<SincronizacionCreacionUiState>(SincronizacionCreacionUiState.Idle)
    val sincronizacionCreacionState: StateFlow<SincronizacionCreacionUiState> =
        _sincronizacionCreacionState.asStateFlow()

    /**
     * Indica si debe mostrarse la opción "Vincular datos antiguos a mi cuenta".
     * Solo es `true` cuando existe una sesión Firebase activa y hay registros con `usuarioUid = ""`.
     */
    val mostrarOpcionVincularDatosAntiguos: StateFlow<Boolean> = combine(
        authRepository.uidActualFlow,
        repository.existenDatosSinPropietario(),
        _vinculandoDatos
    ) { uid, existenSinPropietario, vinculando ->
        !uid.isNullOrBlank() && existenSinPropietario && !vinculando
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    /**
     * Actualiza en una única transacción Room todos los alimentos y movimientos cuyo
     * `usuarioUid` sea `""` para asignarlos al UID autenticado actual.
     * Evita asignaciones duplicadas o llamadas concurrentes.
     */
    fun vincularDatosAntiguosAMiCuenta() {
        if (_vinculandoDatos.value) return
        val uid = authRepository.usuarioActual?.uid?.trim().orEmpty()
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _vinculandoDatos.value = true
            try {
                repository.vincularDatosAntiguos(uid)
            } finally {
                _vinculandoDatos.value = false
            }
        }
    }

    /**
     * Lista reactiva de alimentos del usuario autenticado, ordenados por fecha de vencimiento.
     * Al cerrar sesión (`uid == null`) emite una lista vacía; al cambiar de cuenta, emite solo sus datos.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alimentos: StateFlow<List<Alimento>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.obtenerAlimentosPorUsuario(uid)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Conjunto reactivo y persistente (desde Room) de `remoteId` de alimentos del usuario actual
     * que tienen cambios pendientes de sincronizar con Cloud Firestore.
     */
    val remoteIdsPendientesDeSubir: StateFlow<Set<String>> = alimentos
        .map { lista ->
            lista.asSequence()
                .filter { it.pendienteSincronizacion }
                .map { it.remoteId }
                .toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    /**
     * Lista reactiva y persistente de eliminaciones pendientes (tombstones) del usuario autenticado.
     * Se conserva en Room incluso tras reiniciar la app y se limpia al confirmar en Firestore.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val eliminacionesPendientes: StateFlow<List<EliminacionPendiente>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.obtenerEliminacionesPendientesPorUsuario(uid)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Alimentos para "Consumir primero" pertenecientes al usuario autenticado.
     * Reglas:
     *  - Excluye alimentos vencidos (fecha < hoy).
     *  - Excluye alimentos que vencen HOY (dias == 0).
     *  - Excluye alimentos con stock ≤ 0.
     *  - Ordena por fecha de vencimiento más próxima (ascendente).
     *    Sin fecha al final.
     */
    val alimentosConsumiPrimero: StateFlow<List<Alimento>> = alimentos
        .map { lista ->
            val hoy = LocalDate.now()
            lista
                // 1. Solo con stock > 0
                .filter { alimento ->
                    alimento.cantidadActual
                        .replace(',', '.')
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.toDoubleOrNull()
                        ?.let { v -> v > 0 } == true
                }
                // 2. Excluir vencidos y vence-hoy (dias <= 0)
                .filter { alimento ->
                    val dias = parsearFecha(alimento.fechaVencimiento)
                        ?.let { fecha -> java.time.temporal.ChronoUnit.DAYS.between(hoy, fecha) }
                    // Si no tiene fecha la dejamos pasar (sin fecha al final)
                    dias == null || dias > 0
                }
                // 3. Ordenar por fecha más cercana primero; sin fecha al final
                .sortedWith(Comparator { a, b ->
                    val fa = parsearFecha(a.fechaVencimiento)
                    val fb = parsearFecha(b.fechaVencimiento)
                    when {
                        fa == null && fb == null -> 0
                        fa == null -> 1
                        fb == null -> -1
                        else -> fa.compareTo(fb)
                    }
                })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Total de alimentos con stock > 0. */
    val totalDisponibles: StateFlow<Int> = alimentos
        .map { lista ->
            lista.count {
                it.cantidadActual.replace(',', '.').trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.let { v -> v > 0 } == true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Alimentos próximos a vencer (≤3 días) con stock > 0. */
    val proximosAVencer: StateFlow<Int> = alimentos
        .map { lista ->
            val hoy = LocalDate.now()
            lista.count {
                calcularEstado(it, hoy) == EstadoVencimiento.PROXIMO &&
                it.cantidadActual.replace(',', '.').trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.let { v -> v > 0 } == true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Alimentos vencidos con stock > 0. */
    val vencidos: StateFlow<Int> = alimentos
        .map { lista ->
            val hoy = LocalDate.now()
            lista.count {
                calcularEstado(it, hoy) == EstadoVencimiento.VENCIDO &&
                it.cantidadActual.replace(',', '.').trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.let { v -> v > 0 } == true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Agrega el alimento nuevo:
     * 1. Lo guarda primero en Room con su `remoteId` persistente, `usuarioUid` y `pendienteSincronizacion = true`, junto con su movimiento COMPRA.
     * 2. Intenta subirlo automáticamente a `usuarios/{uid}/alimentos/{remoteId}` sin sobrescribir si ya existe.
     * 3. Si se confirma en Firestore, limpia `pendienteSincronizacion` en Room; si no hay Internet, conserva el estado pendiente persistido en Room.
     */
    fun agregar(alimento: Alimento) {
        val uid = obtenerUidActivo() ?: return
        val remoteIdPersistente = alimento.remoteId.trim().ifEmpty {
            java.util.UUID.randomUUID().toString()
        }
        val ahora = System.currentTimeMillis()
        val alimentoNuevo = alimento.copy(
            remoteId = remoteIdPersistente,
            usuarioUid = uid,
            pendienteSincronizacion = true,
            actualizadoEn = ahora,
            baseRemotaActualizadoEn = 0L
        )

        viewModelScope.launch {
            // 1. Guardar primero en Room (fuente de verdad local, marcado pendiente hasta confirmar nube)
            repository.agregarAlimentoConCompra(alimentoNuevo, uid)

            // 2. Verificar que la sesión siga activa con el mismo UID antes de subir a Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = alimentoNuevo.nombre,
                        mensaje = "⚠ '${alimentoNuevo.nombre}' se guardó en Room y quedó pendiente de sincronizar."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = alimentoNuevo.nombre,
                    mensaje = "Guardado en Room. Subiendo '${alimentoNuevo.nombre}' a la nube..."
                )

            // 3. Intentar subir a Cloud Firestore usando su remoteId persistente
            val resultado = firestoreAlimentoRepository.subirAlimentoSiNoExiste(uidVerificado, alimentoNuevo)
            resultado.fold(
                onSuccess = {
                    repository.marcarAlimentoSincronizado(
                        remoteId = remoteIdPersistente,
                        usuarioUid = uidVerificado,
                        actualizadoEnConfirmado = ahora
                    )
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.Sincronizado(
                            nombreAlimento = alimentoNuevo.nombre,
                            mensaje = "✓ '${alimentoNuevo.nombre}' guardado en Room y subido a la nube."
                        )
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = alimentoNuevo.nombre,
                            mensaje = "⚠ '${alimentoNuevo.nombre}' se guardó en Room y está pendiente de sincronizar (sin conexión). Puedes reintentar en Mi Perfil."
                        )
                }
            )
        }
    }

    /** Limpia el aviso de sincronización de creación o edición. */
    fun limpiarAvisoSincronizacionCreacion() {
        if (_sincronizacionCreacionState.value !is SincronizacionCreacionUiState.SubiendoNuevo) {
            _sincronizacionCreacionState.value = SincronizacionCreacionUiState.Idle
        }
    }

    fun actualizar(alimento: Alimento) {
        val uid = obtenerUidActivo() ?: return
        if (alimento.usuarioUid != uid) return
        viewModelScope.launch {
            repository.actualizarAlimentoPorUsuario(alimento, uid)
        }
    }

    /**
     * Edita los datos generales de un alimento existente (Etapa 4C.3B):
     * 1. Verifica que el alimento pertenezca al usuario autenticado y conserva su `remoteId` original.
     * 2. Guarda primero los cambios en Room marcando `pendienteSincronizacion = true` de forma persistente
     *    (y registra movimiento AJUSTE si cambió [cantidadAnterior]).
     * 3. Intenta actualizar el mismo documento en Firestore: `usuarios/{uid}/alimentos/{remoteId}`,
     *    evitando sobrescribir cambios remotos más recientes si se detecta un conflicto.
     * 4. Si se confirma en el servidor, marca `pendienteSincronizacion = false` en Room;
     *    si falla Internet o hay conflicto, conserva el estado pendiente persistido en Room.
     */
    fun editarAlimento(
        alimento: Alimento,
        cantidadAnterior: Double,
        onError: () -> Unit = {}
    ) {
        val uid = obtenerUidActivo() ?: run { onError(); return }
        if (alimento.usuarioUid.trim() != uid) { onError(); return }
        if (alimento.remoteId.isBlank()) { onError(); return }
        val cantidadNueva = parsearCantidad(alimento.cantidadActual)
        if (cantidadNueva < 0) { onError(); return }

        viewModelScope.launch {
            // Verificar que el alimento exista en Room para este usuario y conservar su remoteId original
            val actualEnRoom = repository.obtenerAlimentoPorIdYUsuario(alimento.id, uid)
            if (actualEnRoom == null || actualEnRoom.usuarioUid.trim() != uid) {
                onError()
                return@launch
            }

            val remoteIdOriginal = actualEnRoom.remoteId.trim()
            if (remoteIdOriginal.isEmpty()) {
                onError()
                return@launch
            }

            val marcaEdicion = System.currentTimeMillis()
            val alimentoEditado = alimento.copy(
                id = actualEnRoom.id,
                remoteId = remoteIdOriginal,
                usuarioUid = uid,
                pendienteSincronizacion = true,
                actualizadoEn = marcaEdicion,
                baseRemotaActualizadoEn = actualEnRoom.baseRemotaActualizadoEn
            )

            // 1. Guardar primero en Room (persiste pendienteSincronizacion = true incluso si se cierra la app)
            val guardadoEnRoom = repository.editarAlimento(alimentoEditado, cantidadAnterior, uid)
            if (!guardadoEnRoom) {
                onError()
                return@launch
            }

            // 2. Comprobar que la sesión siga activa con el mismo UID antes de actualizar en Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = alimentoEditado.nombre,
                        mensaje = "⚠ Cambios de '${alimentoEditado.nombre}' guardados en Room. Quedó pendiente de sincronizar con la nube."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = alimentoEditado.nombre,
                    mensaje = "Guardado en Room. Sincronizando edición de '${alimentoEditado.nombre}' en la nube..."
                )

            // 3. Actualizar el mismo documento en usuarios/{uid}/alimentos/{remoteId} verificando conflictos
            val resultado = firestoreAlimentoRepository.sincronizarEdicionAlimento(
                uid = uidVerificado,
                alimento = alimentoEditado
            )

            resultado.fold(
                onSuccess = { respuesta ->
                    when (respuesta.estado) {
                        EstadoSincronizacionEdicion.SINCRONIZADO -> {
                            repository.marcarAlimentoSincronizado(
                                remoteId = remoteIdOriginal,
                                usuarioUid = uidVerificado,
                                actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                            )
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.Sincronizado(
                                    nombreAlimento = alimentoEditado.nombre,
                                    mensaje = "✓ Cambios de '${alimentoEditado.nombre}' guardados en Room y sincronizados en la nube."
                                )
                        }
                        EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.ErrorSincronizacion(
                                    nombreAlimento = alimentoEditado.nombre,
                                    mensaje = "❌ Conflicto en '${alimentoEditado.nombre}': la nube tiene cambios más recientes y no se sobrescribió el documento remoto. Tus cambios locales siguen guardados en Room."
                                )
                        }
                    }
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = alimentoEditado.nombre,
                            mensaje = "⚠ Cambios de '${alimentoEditado.nombre}' guardados en Room. Sin conexión: quedó pendiente de sincronizar (puedes reintentar desde Mi Perfil)."
                        )
                }
            )
        }
    }

    /**
     * Elimina un alimento de forma segura y sincronizada (Etapa 4C.3F):
     * 1. Registra primero la eliminación pendiente (tombstone) en Room y retira el alimento
     *    de la despensa visible dentro de una misma transacción Room, sin borrar su historial.
     * 2. Intenta eliminar el documento `usuarios/{uid}/alimentos/{remoteId}` en Cloud Firestore,
     *    verificando antes que no existan cambios remotos posteriores (conflicto).
     * 3. Si Firebase confirma la eliminación, limpia el tombstone local; si no hay Internet,
     *    conserva el pendiente persistido en Room para reintentar desde Mi Perfil.
     */
    fun eliminar(id: Int) {
        val uid = obtenerUidActivo() ?: return
        viewModelScope.launch {
            val marcaEliminacion = System.currentTimeMillis()

            // 1. Transacción atómica en Room: guardar tombstone + retirar alimento visible
            val eliminacion = repository.registrarEliminacionPendienteYBorrarLocal(
                alimentoId = id,
                usuarioUid = uid,
                fechaEliminacion = marcaEliminacion
            ) ?: return@launch

            // 2. Verificar que la sesión siga activa con el mismo UID antes de ir a Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = eliminacion.nombreAlimento,
                        mensaje = "⚠ '${eliminacion.nombreAlimento}' se retiró de Room. Eliminación en la nube pendiente."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = eliminacion.nombreAlimento,
                    mensaje = "Eliminado en Room. Sincronizando eliminación de '${eliminacion.nombreAlimento}' en la nube..."
                )

            // 3. Intentar eliminar usuarios/{uid}/alimentos/{remoteId} verificando conflictos
            val resultado = firestoreAlimentoRepository.sincronizarEliminacionAlimento(
                uid = uidVerificado,
                eliminacion = eliminacion
            )

            resultado.fold(
                onSuccess = { estado ->
                    when (estado) {
                        EstadoSincronizacionEliminacion.ELIMINADO_CONFIRMADO -> {
                            repository.limpiarEliminacionPendiente(
                                remoteId = eliminacion.remoteId,
                                usuarioUid = uidVerificado
                            )
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.Sincronizado(
                                    nombreAlimento = eliminacion.nombreAlimento,
                                    mensaje = "✓ '${eliminacion.nombreAlimento}' eliminado de Room y de la nube."
                                )
                        }
                        EstadoSincronizacionEliminacion.CONFLICTO_CAMBIO_REMOTO_POSTERIOR -> {
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.ErrorSincronizacion(
                                    nombreAlimento = eliminacion.nombreAlimento,
                                    mensaje = "❌ Conflicto al eliminar '${eliminacion.nombreAlimento}' en la nube: el documento remoto tiene cambios posteriores y no se eliminó."
                                )
                        }
                    }
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = eliminacion.nombreAlimento,
                            mensaje = "⚠ '${eliminacion.nombreAlimento}' se eliminó en Room. Sin conexión: eliminación en la nube pendiente (puedes reintentar desde Mi Perfil)."
                        )
                }
            )
        }
    }

    /**
     * Registra un consumo del alimento (Etapa 4C.3C):
     * 1. Verifica que el UID del propietario coincida con el usuario autenticado y que la cantidad
     *    solicitada sea mayor a 0 y no supere el stock disponible.
     * 2. Ejecuta en una única transacción Room la reducción de `cantidadActual`, el marcado de
     *    `pendienteSincronizacion = true` y `actualizadoEn`, y el registro del movimiento `CONSUMO`
     *    en el historial local.
     * 3. Intenta sincronizar la nueva cantidad con Cloud Firestore en `usuarios/{uid}/alimentos/{remoteId}`
     *    reutilizando la detección de conflictos de la Etapa 4C.3B (sin sincronizar movimientos).
     * 4. Si no hay Internet, conserva el consumo y su historial en Room con estado pendiente persistente
     *    para poder reintentar desde Mi Perfil sin duplicar movimientos.
     */
    fun consumir(alimento: Alimento, cantidad: Double, onError: () -> Unit = {}) {
        val uid = obtenerUidActivo() ?: run { onError(); return }
        if (alimento.usuarioUid.trim() != uid) { onError(); return }
        if (alimento.remoteId.isBlank()) { onError(); return }
        val stockActual = parsearCantidad(alimento.cantidadActual)
        if (cantidad <= 0 || cantidad > stockActual) { onError(); return }

        viewModelScope.launch {
            val marcaConsumo = System.currentTimeMillis()

            // 1. Transacción atómica en Room: actualizar cantidad + pendienteSincronizacion + movimiento CONSUMO
            val alimentoConsumido = repository.consumirAlimento(
                alimentoId = alimento.id,
                cantidad = cantidad,
                usuarioUid = uid,
                marcaActualizacion = marcaConsumo
            )
            if (alimentoConsumido == null) {
                onError()
                return@launch
            }

            // 2. Comprobar que la sesión siga activa con el mismo UID antes de sincronizar con Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = alimentoConsumido.nombre,
                        mensaje = "⚠ Consumo de '${alimentoConsumido.nombre}' guardado en Room. Quedó pendiente de sincronizar con la nube."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = alimentoConsumido.nombre,
                    mensaje = "Consumo registrado en Room. Sincronizando '${alimentoConsumido.nombre}' en la nube..."
                )

            // 3. Sincronizar la cantidad actualizada en usuarios/{uid}/alimentos/{remoteId} con detección de conflictos
            val resultado = firestoreAlimentoRepository.sincronizarEdicionAlimento(
                uid = uidVerificado,
                alimento = alimentoConsumido
            )

            resultado.fold(
                onSuccess = { respuesta ->
                    when (respuesta.estado) {
                        EstadoSincronizacionEdicion.SINCRONIZADO -> {
                            repository.marcarAlimentoSincronizado(
                                remoteId = alimentoConsumido.remoteId,
                                usuarioUid = uidVerificado,
                                actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                            )
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.Sincronizado(
                                    nombreAlimento = alimentoConsumido.nombre,
                                    mensaje = "✓ Consumo de '${alimentoConsumido.nombre}' registrado en Room y sincronizado en la nube."
                                )
                        }
                        EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.ErrorSincronizacion(
                                    nombreAlimento = alimentoConsumido.nombre,
                                    mensaje = "❌ Conflicto al sincronizar consumo de '${alimentoConsumido.nombre}': la nube tiene cambios más recientes y no se sobrescribió el documento remoto."
                                )
                        }
                    }
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = alimentoConsumido.nombre,
                            mensaje = "⚠ Consumo de '${alimentoConsumido.nombre}' guardado en Room. Sin conexión: quedó pendiente de sincronizar (puedes reintentar desde Mi Perfil)."
                        )
                }
            )
        }
    }

    /**
     * Registra un desperdicio del alimento (Etapa 4C.3D):
     * 1. Verifica que el UID del propietario coincida con el usuario autenticado y que la cantidad
     *    desperdiciada sea mayor a 0 y no supere el stock disponible.
     * 2. Ejecuta en una única transacción Room la reducción de `cantidadActual`, el marcado de
     *    `pendienteSincronizacion = true` y `actualizadoEn`, y el registro del movimiento `DESPERDICIO`
     *    en el historial local.
     * 3. Intenta sincronizar la nueva cantidad con Cloud Firestore en `usuarios/{uid}/alimentos/{remoteId}`
     *    reutilizando la detección de conflictos existente (sin sincronizar movimientos).
     * 4. Si no hay Internet, conserva el cambio y su historial en Room con estado pendiente persistente
     *    para poder reintentar desde Mi Perfil sin duplicar movimientos.
     */
    fun desperdiciar(alimento: Alimento, cantidad: Double, onError: () -> Unit = {}) {
        val uid = obtenerUidActivo() ?: run { onError(); return }
        if (alimento.usuarioUid.trim() != uid) { onError(); return }
        if (alimento.remoteId.isBlank()) { onError(); return }
        val stockActual = parsearCantidad(alimento.cantidadActual)
        if (cantidad <= 0 || cantidad > stockActual) { onError(); return }

        viewModelScope.launch {
            val marcaDesperdicio = System.currentTimeMillis()

            // 1. Transacción atómica en Room: actualizar cantidad + pendienteSincronizacion + movimiento DESPERDICIO
            val alimentoDesperdiciado = repository.desperdiciarAlimento(
                alimentoId = alimento.id,
                cantidad = cantidad,
                usuarioUid = uid,
                marcaActualizacion = marcaDesperdicio
            )
            if (alimentoDesperdiciado == null) {
                onError()
                return@launch
            }

            // 2. Comprobar que la sesión siga activa con el mismo UID antes de sincronizar con Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = alimentoDesperdiciado.nombre,
                        mensaje = "⚠ Desperdicio de '${alimentoDesperdiciado.nombre}' guardado en Room. Quedó pendiente de sincronizar con la nube."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = alimentoDesperdiciado.nombre,
                    mensaje = "Desperdicio registrado en Room. Sincronizando '${alimentoDesperdiciado.nombre}' en la nube..."
                )

            // 3. Sincronizar la cantidad actualizada en usuarios/{uid}/alimentos/{remoteId} con detección de conflictos
            val resultado = firestoreAlimentoRepository.sincronizarEdicionAlimento(
                uid = uidVerificado,
                alimento = alimentoDesperdiciado
            )

            resultado.fold(
                onSuccess = { respuesta ->
                    when (respuesta.estado) {
                        EstadoSincronizacionEdicion.SINCRONIZADO -> {
                            repository.marcarAlimentoSincronizado(
                                remoteId = alimentoDesperdiciado.remoteId,
                                usuarioUid = uidVerificado,
                                actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                            )
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.Sincronizado(
                                    nombreAlimento = alimentoDesperdiciado.nombre,
                                    mensaje = "✓ Desperdicio de '${alimentoDesperdiciado.nombre}' registrado en Room y sincronizado en la nube."
                                )
                        }
                        EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.ErrorSincronizacion(
                                    nombreAlimento = alimentoDesperdiciado.nombre,
                                    mensaje = "❌ Conflicto al sincronizar desperdicio de '${alimentoDesperdiciado.nombre}': la nube tiene cambios más recientes y no se sobrescribió el documento remoto."
                                )
                        }
                    }
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = alimentoDesperdiciado.nombre,
                            mensaje = "⚠ Desperdicio de '${alimentoDesperdiciado.nombre}' guardado en Room. Sin conexión: quedó pendiente de sincronizar (puedes reintentar desde Mi Perfil)."
                        )
                }
            )
        }
    }

    /**
     * Aumenta manualmente el stock del alimento (Etapa 4C.3E):
     * 1. Verifica que el UID del propietario coincida con el usuario autenticado, que la cantidad
     *    adicional sea mayor a 0 y que el stock no sea negativo.
     * 2. Ejecuta en una única transacción Room la actualización de `cantidadActual`, el marcado de
     *    `pendienteSincronizacion = true` y `actualizadoEn`, y el registro del movimiento `AJUSTE`
     *    en el historial local.
     * 3. Intenta sincronizar el alimento actualizado con Cloud Firestore en `usuarios/{uid}/alimentos/{remoteId}`
     *    reutilizando el mecanismo existente de timestamps y detección de conflictos.
     * 4. Si no hay Internet, conserva el ajuste y su movimiento en Room con estado pendiente persistente
     *    para poder reintentar desde Mi Perfil sin duplicar movimientos.
     */
    fun ajustarStock(alimento: Alimento, cantidadExtra: Double, onError: () -> Unit = {}) {
        val uid = obtenerUidActivo() ?: run { onError(); return }
        if (alimento.usuarioUid.trim() != uid) { onError(); return }
        if (alimento.remoteId.isBlank()) { onError(); return }
        if (cantidadExtra <= 0) { onError(); return }
        val stockActual = parsearCantidad(alimento.cantidadActual)
        if (stockActual < 0 || stockActual + cantidadExtra < 0) { onError(); return }

        viewModelScope.launch {
            val marcaAjuste = System.currentTimeMillis()

            // 1. Transacción atómica en Room: actualizar cantidad + pendienteSincronizacion + movimiento AJUSTE
            val alimentoAjustado = repository.ajustarStock(
                alimento = alimento,
                cantidadAjuste = cantidadExtra,
                usuarioUid = uid,
                marcaActualizacion = marcaAjuste
            )
            if (alimentoAjustado == null) {
                onError()
                return@launch
            }

            // 2. Comprobar que la sesión siga activa con el mismo UID antes de sincronizar con Firestore
            val uidVerificado = obtenerUidActivo()
            if (uidVerificado == null || uidVerificado != uid) {
                _sincronizacionCreacionState.value =
                    SincronizacionCreacionUiState.PendienteDeSubir(
                        nombreAlimento = alimentoAjustado.nombre,
                        mensaje = "⚠ Ajuste de stock de '${alimentoAjustado.nombre}' guardado en Room. Quedó pendiente de sincronizar con la nube."
                    )
                return@launch
            }

            _sincronizacionCreacionState.value =
                SincronizacionCreacionUiState.SubiendoNuevo(
                    nombreAlimento = alimentoAjustado.nombre,
                    mensaje = "Ajuste registrado en Room. Sincronizando '${alimentoAjustado.nombre}' en la nube..."
                )

            // 3. Sincronizar el alimento actualizado en usuarios/{uid}/alimentos/{remoteId} con detección de conflictos
            val resultado = firestoreAlimentoRepository.sincronizarEdicionAlimento(
                uid = uidVerificado,
                alimento = alimentoAjustado
            )

            resultado.fold(
                onSuccess = { respuesta ->
                    when (respuesta.estado) {
                        EstadoSincronizacionEdicion.SINCRONIZADO -> {
                            repository.marcarAlimentoSincronizado(
                                remoteId = alimentoAjustado.remoteId,
                                usuarioUid = uidVerificado,
                                actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                            )
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.Sincronizado(
                                    nombreAlimento = alimentoAjustado.nombre,
                                    mensaje = "✓ Ajuste de stock de '${alimentoAjustado.nombre}' registrado en Room y sincronizado en la nube."
                                )
                        }
                        EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                            _sincronizacionCreacionState.value =
                                SincronizacionCreacionUiState.ErrorSincronizacion(
                                    nombreAlimento = alimentoAjustado.nombre,
                                    mensaje = "❌ Conflicto al sincronizar ajuste de '${alimentoAjustado.nombre}': la nube tiene cambios más recientes y no se sobrescribió el documento remoto."
                                )
                        }
                    }
                },
                onFailure = {
                    _sincronizacionCreacionState.value =
                        SincronizacionCreacionUiState.PendienteDeSubir(
                            nombreAlimento = alimentoAjustado.nombre,
                            mensaje = "⚠ Ajuste de stock de '${alimentoAjustado.nombre}' guardado en Room. Sin conexión: quedó pendiente de sincronizar (puedes reintentar desde Mi Perfil)."
                        )
                }
            )
        }
    }

    /**
     * Sube manualmente a Cloud Firestore (`usuarios/{uid}/alimentos/{remoteId}`) únicamente
     * los alimentos en Room cuyo `usuarioUid` coincide con el usuario actualmente autenticado.
     *
     * - Si no hay sesión activa, bloquea la operación.
     * - Si el documento remoto ya existe, lo omite sin sobrescribirlo.
     * - No genera nuevos `remoteId` ni modifica Room.
     * - Reporta progreso y conteo exacto de subidos, omitidos y errores.
     */
    fun subirMiDespensaALaNube() {
        if (_subidaNubeState.value is SubidaNubeUiState.Subiendo) return

        val uid = obtenerUidActivo()
        if (uid == null) {
            _subidaNubeState.value = SubidaNubeUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para subir tu despensa."
            )
            return
        }

        viewModelScope.launch {
            try {
                // Leer únicamente los alimentos y eliminaciones pendientes del UID autenticado
                val alimentosDelUsuario = repository.obtenerAlimentosPorUsuario(uid)
                    .first()
                    .filter { it.usuarioUid == uid }

                val eliminacionesDelUsuario = repository.obtenerEliminacionesPendientesPorUsuario(uid)
                    .first()
                    .filter { it.usuarioUid == uid }

                val total = alimentosDelUsuario.size + eliminacionesDelUsuario.size
                _subidaNubeState.value = SubidaNubeUiState.Subiendo(procesados = 0, total = total)

                if (total == 0) {
                    _subidaNubeState.value = SubidaNubeUiState.Resultado(
                        ResultadoSubidaDespensa(
                            total = 0,
                            subidos = 0,
                            eliminados = 0,
                            omitidos = 0,
                            errores = 0
                        )
                    )
                    return@launch
                }

                var subidos = 0
                var eliminados = 0
                var omitidos = 0
                var errores = 0
                var ultimoError: String? = null
                var procesados = 0

                alimentosDelUsuario.forEachIndexed { index, alimento ->
                    // Verificar que la sesión siga siendo la misma durante el proceso
                    val uidActual = obtenerUidActivo()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la sincronización."
                        _subidaNubeState.value = SubidaNubeUiState.Resultado(
                            ResultadoSubidaDespensa(
                                total = total,
                                subidos = subidos,
                                eliminados = eliminados,
                                omitidos = omitidos,
                                errores = errores,
                                detalleError = ultimoError
                            )
                        )
                        return@launch
                    }

                    if (alimento.pendienteSincronizacion) {
                        // Alimento con creación o edición pendiente: NO omitir aunque ya exista en Firestore
                        val resultadoEdicion = firestoreAlimentoRepository.sincronizarEdicionAlimento(uid, alimento)
                        resultadoEdicion.fold(
                            onSuccess = { respuesta ->
                                when (respuesta.estado) {
                                    EstadoSincronizacionEdicion.SINCRONIZADO -> {
                                        repository.marcarAlimentoSincronizado(
                                            remoteId = alimento.remoteId,
                                            usuarioUid = uid,
                                            actualizadoEnConfirmado = respuesta.actualizadoEnConfirmado
                                        )
                                        subidos++
                                    }
                                    EstadoSincronizacionEdicion.CONFLICTO_REMOTO_MAS_RECIENTE -> {
                                        errores++
                                        ultimoError =
                                            "Conflicto en '${alimento.nombre}': el documento en la nube tiene cambios más recientes y no se sobrescribió."
                                    }
                                }
                            },
                            onFailure = { error ->
                                errores++
                                ultimoError =
                                    error.localizedMessage ?: "Error al sincronizar '${alimento.nombre}' con Firestore"
                            }
                        )
                    } else {
                        // Alimento sin cambios pendientes: solo subir si aún no existe en Firestore
                        val resultado = firestoreAlimentoRepository.subirAlimentoSiNoExiste(uid, alimento)
                        resultado.fold(
                            onSuccess = { estado ->
                                when (estado) {
                                    EstadoSubidaAlimento.SUBIDO -> {
                                        val marca = alimento.actualizadoEn.takeIf { it > 0L }
                                            ?: System.currentTimeMillis()
                                        repository.marcarAlimentoSincronizado(
                                            remoteId = alimento.remoteId,
                                            usuarioUid = uid,
                                            actualizadoEnConfirmado = marca
                                        )
                                        subidos++
                                    }
                                    EstadoSubidaAlimento.OMITIDO_YA_EXISTE -> {
                                        omitidos++
                                    }
                                }
                            },
                            onFailure = { error ->
                                errores++
                                ultimoError = error.localizedMessage ?: "Error al confirmar escritura en Firestore"
                            }
                        )
                    }

                    procesados++
                    _subidaNubeState.value = SubidaNubeUiState.Subiendo(
                        procesados = procesados,
                        total = total
                    )
                }

                // Reintentar eliminaciones pendientes (tombstones) de la cuenta actual
                eliminacionesDelUsuario.forEach { eliminacion ->
                    val uidActual = obtenerUidActivo()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - procesados)
                        ultimoError = "La sesión cambió o se cerró durante la sincronización."
                        _subidaNubeState.value = SubidaNubeUiState.Resultado(
                            ResultadoSubidaDespensa(
                                total = total,
                                subidos = subidos,
                                eliminados = eliminados,
                                omitidos = omitidos,
                                errores = errores,
                                detalleError = ultimoError
                            )
                        )
                        return@launch
                    }

                    val resultadoEliminacion = firestoreAlimentoRepository.sincronizarEliminacionAlimento(
                        uid = uid,
                        eliminacion = eliminacion
                    )
                    resultadoEliminacion.fold(
                        onSuccess = { estado ->
                            when (estado) {
                                EstadoSincronizacionEliminacion.ELIMINADO_CONFIRMADO -> {
                                    repository.limpiarEliminacionPendiente(
                                        remoteId = eliminacion.remoteId,
                                        usuarioUid = uid
                                    )
                                    eliminados++
                                }
                                EstadoSincronizacionEliminacion.CONFLICTO_CAMBIO_REMOTO_POSTERIOR -> {
                                    errores++
                                    ultimoError =
                                        "Conflicto al eliminar '${eliminacion.nombreAlimento}': el documento remoto tiene cambios posteriores y no se eliminó."
                                }
                            }
                        },
                        onFailure = { error ->
                            errores++
                            ultimoError =
                                error.localizedMessage ?: "No se pudo eliminar '${eliminacion.nombreAlimento}' en Firestore"
                        }
                    )

                    procesados++
                    _subidaNubeState.value = SubidaNubeUiState.Subiendo(
                        procesados = procesados,
                        total = total
                    )
                }

                _subidaNubeState.value = SubidaNubeUiState.Resultado(
                    ResultadoSubidaDespensa(
                        total = total,
                        subidos = subidos,
                        eliminados = eliminados,
                        omitidos = omitidos,
                        errores = errores,
                        detalleError = ultimoError
                    )
                )
                if (errores == 0 &&
                    _sincronizacionCreacionState.value is SincronizacionCreacionUiState.PendienteDeSubir
                ) {
                    _sincronizacionCreacionState.value = SincronizacionCreacionUiState.Idle
                }
            } catch (e: Exception) {
                _subidaNubeState.value = SubidaNubeUiState.ErrorBloqueante(
                    e.localizedMessage ?: "No se pudo completar la subida a la nube."
                )
            }
        }
    }

    /** Limpia el estado de resultado/error de la subida manual a la nube. */
    fun limpiarEstadoSubidaNube() {
        if (_subidaNubeState.value !is SubidaNubeUiState.Subiendo) {
            _subidaNubeState.value = SubidaNubeUiState.Idle
        }
    }

    /**
     * Descarga manualmente los alimentos de `usuarios/{uid}/alimentos` desde Cloud Firestore
     * y los inserta en Room únicamente si su `remoteId` aún no existe localmente:
     *
     * - Si el `remoteId` ya existe en Room, se omite sin sobrescribirlo.
     * - Si no existe, se inserta con `id = 0` (nuevo ID local autogenerado por Room),
     *   conservando su `remoteId` y `usuarioUid`.
     * - NO genera movimientos de COMPRA.
     * - Valida la pertenencia (`usuarioUid == uid`) y datos de cada documento.
     */
    fun recuperarAlimentosDeLaNube() {
        if (_descargaNubeState.value is DescargaNubeUiState.Descargando) return

        val uid = obtenerUidActivo()
        if (uid == null) {
            _descargaNubeState.value = DescargaNubeUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para recuperar tus alimentos."
            )
            return
        }

        viewModelScope.launch {
            _descargaNubeState.value = DescargaNubeUiState.Descargando
            val resultadoRemoto = firestoreAlimentoRepository.descargarAlimentosValidados(uid)

            resultadoRemoto.fold(
                onSuccess = { lote ->
                    var descargados = 0
                    var omitidos = 0
                    var errores = lote.erroresValidacion
                    var ultimoError = lote.detalleUltimoError

                    for (remoto in lote.validos) {
                        val uidActual = obtenerUidActivo()
                        if (uidActual == null || uidActual != uid) {
                            errores++
                            ultimoError = "La sesión cambió o se cerró durante la recuperación."
                            continue
                        }

                        runCatching {
                            repository.insertarAlimentoRecuperadoDeNube(remoto, uid)
                        }.fold(
                            onSuccess = { insertado ->
                                if (insertado) {
                                    descargados++
                                } else {
                                    omitidos++
                                }
                            },
                            onFailure = { e ->
                                errores++
                                ultimoError = e.localizedMessage ?: "Error al insertar en Room"
                            }
                        )
                    }

                    _descargaNubeState.value = DescargaNubeUiState.Resultado(
                        ResultadoDescargaDespensa(
                            totalRemotos = lote.totalDocumentos,
                            descargados = descargados,
                            omitidos = omitidos,
                            errores = errores,
                            detalleError = ultimoError
                        )
                    )
                },
                onFailure = { error ->
                    _descargaNubeState.value = DescargaNubeUiState.ErrorBloqueante(
                        error.localizedMessage ?: "No se pudieron consultar los alimentos en Cloud Firestore."
                    )
                }
            )
        }
    }

    /** Limpia el estado de resultado/error de la recuperación manual desde la nube. */
    fun limpiarEstadoDescargaNube() {
        if (_descargaNubeState.value !is DescargaNubeUiState.Descargando) {
            _descargaNubeState.value = DescargaNubeUiState.Idle
        }
    }

    private fun obtenerUidActivo(): String? =
        authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }

    private fun parsearCantidad(valor: String): Double =
        valor.replace(',', '.')
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toDoubleOrNull() ?: 0.0

    private fun fechaActual(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    /** Factory para instanciar el ViewModel con los repositorios inyectados. */
    class Factory(
        private val repository: AlimentoRepository,
        private val movimientoRepository: MovimientoRepository,
        private val authRepository: AuthRepository = AuthRepository(),
        private val firestoreAlimentoRepository: FirestoreAlimentoRepository = FirestoreAlimentoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlimentoViewModel::class.java)) {
                return AlimentoViewModel(
                    repository = repository,
                    movimientoRepository = movimientoRepository,
                    authRepository = authRepository,
                    firestoreAlimentoRepository = firestoreAlimentoRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
