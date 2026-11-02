package com.example.ecodespensa.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento
import com.example.ecodespensa.data.repository.AlimentoRepository
import com.example.ecodespensa.data.repository.MovimientoRepository
import com.example.ecodespensa.util.calcularEstado
import com.example.ecodespensa.util.parsearFecha
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    private val movimientoRepository: MovimientoRepository
) : ViewModel() {

    /** Lista reactiva de alimentos ordenados por fecha de vencimiento más próxima. */
    val alimentos: StateFlow<List<Alimento>> = repository.obtenerAlimentos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Alimentos para "Consumir primero".
     * Reglas:
     *  - Excluye alimentos vencidos (fecha < hoy).
     *  - Excluye alimentos que vencen HOY (dias == 0).
     *  - Excluye alimentos con stock ≤ 0.
     *  - Ordena por fecha de vencimiento más próxima (ascendente).
     *    Sin fecha al final.
     */
    val alimentosConsumiPrimero: StateFlow<List<Alimento>> = repository.obtenerAlimentos()
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
     * Agrega el alimento a Room y registra automáticamente un movimiento COMPRA.
     * La operación es atómica: si falla, ninguno de los dos se persiste.
     */
    fun agregar(alimento: Alimento) {
        viewModelScope.launch { repository.agregarAlimentoConCompra(alimento) }
    }

    fun actualizar(alimento: Alimento) {
        viewModelScope.launch { repository.actualizarAlimento(alimento) }
    }

    /**
     * Edita los datos generales del alimento.
     * Si cambia [cantidadAnterior] respecto a la nueva cantidad del [alimento],
     * registra automáticamente un movimiento AJUSTE con la diferencia.
     * NO registra COMPRA. NO permite cantidad negativa.
     * Llama a [onError] si la nueva cantidad es negativa.
     */
    fun editarAlimento(
        alimento: Alimento,
        cantidadAnterior: Double,
        onError: () -> Unit = {}
    ) {
        val cantidadNueva = alimento.cantidadActual
            .replace(',', '.')
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toDoubleOrNull() ?: 0.0
        if (cantidadNueva < 0) { onError(); return }
        viewModelScope.launch { repository.editarAlimento(alimento, cantidadAnterior) }
    }

    fun eliminar(id: Int) {
        viewModelScope.launch { repository.eliminarAlimento(id) }
    }

    /**
     * Registra un consumo del alimento y reduce su cantidadActual.
     * Llama a [onError] si la cantidad solicitada supera el stock actual.
     */
    fun consumir(alimento: Alimento, cantidad: Double, onError: () -> Unit = {}) {
        viewModelScope.launch {
            val stockActual = alimento.cantidadActual.toDoubleOrNull() ?: 0.0
            if (cantidad <= 0 || cantidad > stockActual) { onError(); return@launch }
            val nuevo = alimento.copy(cantidadActual = (stockActual - cantidad).toString())
            repository.actualizarAlimento(nuevo)
            movimientoRepository.registrarMovimiento(
                Movimiento(
                    alimentoId = alimento.id,
                    alimentoNombre = alimento.nombre,
                    tipo = TipoMovimiento.CONSUMO,
                    cantidad = cantidad,
                    fecha = fechaActual()
                )
            )
        }
    }

    /**
     * Registra un desperdicio del alimento y reduce su cantidadActual.
     * Llama a [onError] si la cantidad solicitada supera el stock actual.
     */
    fun desperdiciar(alimento: Alimento, cantidad: Double, onError: () -> Unit = {}) {
        viewModelScope.launch {
            val stockActual = alimento.cantidadActual.toDoubleOrNull() ?: 0.0
            if (cantidad <= 0 || cantidad > stockActual) { onError(); return@launch }
            val nuevo = alimento.copy(cantidadActual = (stockActual - cantidad).toString())
            repository.actualizarAlimento(nuevo)
            movimientoRepository.registrarMovimiento(
                Movimiento(
                    alimentoId = alimento.id,
                    alimentoNombre = alimento.nombre,
                    tipo = TipoMovimiento.DESPERDICIO,
                    cantidad = cantidad,
                    fecha = fechaActual()
                )
            )
        }
    }

    /**
     * Aumenta manualmente el stock del alimento y registra un movimiento AJUSTE.
     * La operación es atómica: si falla, ninguno de los dos se persiste.
     * Llama a [onError] si la cantidad es inválida (≤ 0).
     */
    fun ajustarStock(alimento: Alimento, cantidadExtra: Double, onError: () -> Unit = {}) {
        viewModelScope.launch {
            if (cantidadExtra <= 0) { onError(); return@launch }
            val stockActual = alimento.cantidadActual.toDoubleOrNull() ?: 0.0
            val nuevoStock = stockActual + cantidadExtra
            val actualizado = alimento.copy(cantidadActual = nuevoStock.toString())
            repository.ajustarStock(actualizado, cantidadExtra)
        }
    }

    private fun fechaActual(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    /** Factory para instanciar el ViewModel con los repositorios inyectados. */
    class Factory(
        private val repository: AlimentoRepository,
        private val movimientoRepository: MovimientoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlimentoViewModel::class.java)) {
                return AlimentoViewModel(repository, movimientoRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
