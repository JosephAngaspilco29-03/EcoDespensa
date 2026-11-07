package com.example.ecodespensa.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.repository.AuthRepository
import com.example.ecodespensa.data.repository.EstadoSubidaMovimiento
import com.example.ecodespensa.data.repository.FirestoreMovimientoRepository
import com.example.ecodespensa.data.repository.MovimientoRepository
import com.example.ecodespensa.data.repository.ResultadoDescargaHistorial
import com.example.ecodespensa.data.repository.ResultadoSubidaHistorial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado de la subida manual del historial de movimientos a Cloud Firestore.
 */
sealed class SubidaHistorialUiState {
    data object Idle : SubidaHistorialUiState()
    data class Subiendo(val procesados: Int, val total: Int) : SubidaHistorialUiState()
    data class Resultado(val resumen: ResultadoSubidaHistorial) : SubidaHistorialUiState()
    data class ErrorBloqueante(val mensaje: String) : SubidaHistorialUiState()
}

/**
 * Estado de la recuperación manual del historial de movimientos desde Cloud Firestore hacia Room.
 */
sealed class DescargaHistorialUiState {
    data object Idle : DescargaHistorialUiState()
    data object Descargando : DescargaHistorialUiState()
    data class Resultado(val resumen: ResultadoDescargaHistorial) : DescargaHistorialUiState()
    data class ErrorBloqueante(val mensaje: String) : DescargaHistorialUiState()
}

/**
 * ViewModel de la pantalla Historial.
 * Expone la lista de movimientos del usuario autenticado como StateFlow
 * y permite registrar nuevos movimientos asociados a su UID.
 */
class MovimientoViewModel(
    private val repository: MovimientoRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val firestoreMovimientoRepository: FirestoreMovimientoRepository = FirestoreMovimientoRepository()
) : ViewModel() {

    private val _subidaHistorialState =
        MutableStateFlow<SubidaHistorialUiState>(SubidaHistorialUiState.Idle)
    val subidaHistorialState: StateFlow<SubidaHistorialUiState> = _subidaHistorialState.asStateFlow()

    private val _descargaHistorialState =
        MutableStateFlow<DescargaHistorialUiState>(DescargaHistorialUiState.Idle)
    val descargaHistorialState: StateFlow<DescargaHistorialUiState> = _descargaHistorialState.asStateFlow()

    /**
     * Sube manualmente a `usuarios/{uid}/movimientos/{remoteId}` únicamente los movimientos de Room
     * del usuario autenticado. Omite los existentes sin sobrescribir y NO genera movimientos nuevos.
     */
    fun subirMiHistorialALaNube() {
        if (_subidaHistorialState.value is SubidaHistorialUiState.Subiendo) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _subidaHistorialState.value = SubidaHistorialUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para subir tu historial."
            )
            return
        }

        viewModelScope.launch {
            try {
                val movimientos = repository.obtenerListaMovimientosPorUsuario(uid)
                    .filter { it.usuarioUid == uid }
                val total = movimientos.size
                _subidaHistorialState.value = SubidaHistorialUiState.Subiendo(0, total)

                var subidos = 0
                var omitidos = 0
                var errores = 0
                var ultimoError: String? = null

                movimientos.forEachIndexed { index, movimiento ->
                    val uidActual = authRepository.usuarioActual?.uid?.trim()
                    if (uidActual == null || uidActual != uid) {
                        errores += (total - index)
                        ultimoError = "La sesión cambió o se cerró durante la subida."
                        _subidaHistorialState.value = SubidaHistorialUiState.Resultado(
                            ResultadoSubidaHistorial(total, subidos, omitidos, errores, ultimoError)
                        )
                        return@launch
                    }

                    firestoreMovimientoRepository.subirMovimientoSiNoExiste(uid, movimiento).fold(
                        onSuccess = { estado ->
                            when (estado) {
                                EstadoSubidaMovimiento.SUBIDO -> subidos++
                                EstadoSubidaMovimiento.OMITIDO_YA_EXISTE -> omitidos++
                            }
                        },
                        onFailure = { e ->
                            errores++
                            ultimoError = e.localizedMessage ?: "Error al confirmar escritura en Firestore"
                        }
                    )
                    _subidaHistorialState.value =
                        SubidaHistorialUiState.Subiendo(index + 1, total)
                }

                _subidaHistorialState.value = SubidaHistorialUiState.Resultado(
                    ResultadoSubidaHistorial(total, subidos, omitidos, errores, ultimoError)
                )
            } catch (e: Exception) {
                _subidaHistorialState.value = SubidaHistorialUiState.ErrorBloqueante(
                    e.localizedMessage ?: "No se pudo completar la subida del historial."
                )
            }
        }
    }

    fun limpiarEstadoSubidaHistorial() {
        if (_subidaHistorialState.value !is SubidaHistorialUiState.Subiendo) {
            _subidaHistorialState.value = SubidaHistorialUiState.Idle
        }
    }

    /**
     * Recupera manualmente desde `usuarios/{uid}/movimientos` los movimientos del usuario autenticado
     * y los inserta en Room solo si su `remoteId` no existe aún localmente.
     * - No sobrescribe movimientos existentes ni genera movimientos adicionales.
     * - No altera el stock ni recrea alimentos eliminados (como JUGO o GALLETAS).
     */
    fun recuperarMiHistorialDeLaNube() {
        if (_descargaHistorialState.value is DescargaHistorialUiState.Descargando) return

        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid == null) {
            _descargaHistorialState.value = DescargaHistorialUiState.ErrorBloqueante(
                "No hay una sesión activa en Firebase. Inicia sesión para recuperar tu historial."
            )
            return
        }

        viewModelScope.launch {
            _descargaHistorialState.value = DescargaHistorialUiState.Descargando
            try {
                val loteResult = firestoreMovimientoRepository.descargarMovimientosValidados(uid)
                loteResult.fold(
                    onSuccess = { lote ->
                        var descargados = 0
                        var omitidos = 0
                        var errores = lote.erroresValidacion
                        var ultimoError = lote.detalleUltimoError

                        for (movRemoto in lote.validos) {
                            val uidActual = authRepository.usuarioActual?.uid?.trim()
                            if (uidActual == null || uidActual != uid) {
                                errores++
                                ultimoError = "La sesión cambió o se cerró durante la recuperación."
                                break
                            }
                            runCatching {
                                repository.insertarMovimientoRecuperadoDeNube(movRemoto, uid)
                            }.fold(
                                onSuccess = { insertado ->
                                    if (insertado) descargados++ else omitidos++
                                },
                                onFailure = { e ->
                                    errores++
                                    ultimoError = e.localizedMessage ?: "Error al guardar movimiento en Room."
                                }
                            )
                        }

                        _descargaHistorialState.value = DescargaHistorialUiState.Resultado(
                            ResultadoDescargaHistorial(
                                totalRemotos = lote.totalDocumentos,
                                descargados = descargados,
                                omitidos = omitidos,
                                errores = errores,
                                detalleError = ultimoError
                            )
                        )
                    },
                    onFailure = { e ->
                        _descargaHistorialState.value = DescargaHistorialUiState.ErrorBloqueante(
                            e.localizedMessage
                                ?: "No se pudo consultar tu historial en Cloud Firestore. Verifica tu conexión a Internet."
                        )
                    }
                )
            } catch (e: Exception) {
                _descargaHistorialState.value = DescargaHistorialUiState.ErrorBloqueante(
                    e.localizedMessage ?: "Error inesperado al recuperar el historial."
                )
            }
        }
    }

    fun limpiarEstadoDescargaHistorial() {
        if (_descargaHistorialState.value !is DescargaHistorialUiState.Descargando) {
            _descargaHistorialState.value = DescargaHistorialUiState.Idle
        }
    }

    /**
     * Lista reactiva de movimientos del usuario autenticado, más reciente primero.
     * Al cerrar sesión (`uid == null`) emite una lista vacía; al cambiar de cuenta, emite solo sus datos.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val movimientos: StateFlow<List<Movimiento>> = authRepository.uidActualFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.obtenerMovimientosPorUsuario(uid)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun registrar(movimiento: Movimiento) {
        val uid = authRepository.usuarioActual?.uid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            repository.registrarMovimiento(movimiento, uid)
        }
    }

    class Factory(
        private val repository: MovimientoRepository,
        private val authRepository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MovimientoViewModel::class.java)) {
                return MovimientoViewModel(repository, authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
