package com.example.ecodespensa.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.repository.MovimientoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla Historial.
 * Expone la lista de movimientos como StateFlow y permite registrar nuevos.
 */
class MovimientoViewModel(
    private val repository: MovimientoRepository
) : ViewModel() {

    /** Lista reactiva de movimientos, más reciente primero. */
    val movimientos: StateFlow<List<Movimiento>> = repository.obtenerMovimientos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun registrar(movimiento: Movimiento) {
        viewModelScope.launch { repository.registrarMovimiento(movimiento) }
    }

    class Factory(private val repository: MovimientoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MovimientoViewModel::class.java)) {
                return MovimientoViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
