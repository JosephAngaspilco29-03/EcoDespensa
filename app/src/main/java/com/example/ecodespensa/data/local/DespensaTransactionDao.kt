package com.example.ecodespensa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.Update
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * DAO de operaciones transaccionales atómicas de EcoDespensa.
 * Al ser una clase abstracta de Room, puede tener métodos concretos anotados con @Transaction
 * que combinan múltiples operaciones DAO de forma atómica.
 *
 * Garantiza que el alimento y su movimiento COMPRA se persistan juntos,
 * o que ninguno se guarde si ocurre un error.
 */
@Dao
abstract class DespensaTransactionDao {

    // ── Operaciones internas (duplicadas aquí para evitar dependencia de inyección) ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarAlimento(alimento: Alimento): Long

    @Update
    abstract suspend fun _actualizarAlimento(alimento: Alimento)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _insertarMovimiento(movimiento: Movimiento)

    // ── Operaciones transaccionales públicas ──────────────────────────────────────

    /**
     * Inserta el [alimento] y crea automáticamente un movimiento de tipo COMPRA
     * con la cantidad inicial registrada.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun insertarAlimentoConCompra(alimento: Alimento) {
        val nuevoId = _insertarAlimento(alimento)
        val cantidad = alimento.cantidadActual.toDoubleOrNull() ?: 0.0
        _insertarMovimiento(
            Movimiento(
                alimentoId = nuevoId.toInt(),
                alimentoNombre = alimento.nombre,
                tipo = TipoMovimiento.COMPRA,
                cantidad = cantidad,
                fecha = fechaActual()
            )
        )
    }

    /**
     * Actualiza el [alimento] con el nuevo stock y registra un movimiento AJUSTE.
     * [cantidadAjuste] es la cantidad que se incrementó (siempre positiva).
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun ajustarStockConMovimiento(alimento: Alimento, cantidadAjuste: Double) {
        _actualizarAlimento(alimento)
        _insertarMovimiento(
            Movimiento(
                alimentoId = alimento.id,
                alimentoNombre = alimento.nombre,
                tipo = TipoMovimiento.AJUSTE,
                cantidad = cantidadAjuste,
                fecha = fechaActual()
            )
        )
    }

    /**
     * Actualiza los datos generales del [alimentoActualizado] y,
     * si la cantidad cambió respecto a [cantidadAnterior], registra
     * automáticamente un movimiento AJUSTE con la diferencia.
     * NO registra movimiento COMPRA.
     * Todo ocurre dentro de una única transacción Room.
     */
    @Transaction
    open suspend fun editarAlimentoConAjuste(
        alimentoActualizado: Alimento,
        cantidadAnterior: Double
    ) {
        _actualizarAlimento(alimentoActualizado)
        val cantidadNueva = alimentoActualizado.cantidadActual.toDoubleOrNull() ?: 0.0
        val diferencia = cantidadNueva - cantidadAnterior
        if (diferencia != 0.0) {
            _insertarMovimiento(
                Movimiento(
                    alimentoId = alimentoActualizado.id,
                    alimentoNombre = alimentoActualizado.nombre,
                    tipo = TipoMovimiento.AJUSTE,
                    cantidad = diferencia,
                    fecha = fechaActual()
                )
            )
        }
    }

    private fun fechaActual(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
