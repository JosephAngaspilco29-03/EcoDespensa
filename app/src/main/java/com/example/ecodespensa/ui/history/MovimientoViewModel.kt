package com.example.ecodespensa.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.repository.AuthRepository
import com.example.ecodespensa.data.repository.MovimientoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Historial.
 * Expone la lista de movimientos del usuario autenticado como StateFlow
 * y permite registrar nuevos movimientos asociados a su UID.
 */
class MovimientoViewModel(
    private val repository: MovimientoRepository,
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

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
