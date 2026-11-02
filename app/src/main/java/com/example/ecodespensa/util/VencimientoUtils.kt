package com.example.ecodespensa.util

import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EstadoVencimiento
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val FORMATOS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("MM/dd/yyyy")
)

fun parsearFecha(texto: String): LocalDate? {
    if (texto.isBlank()) return null
    for (fmt in FORMATOS) {
        try {
            return LocalDate.parse(texto.trim(), fmt)
        } catch (_: DateTimeParseException) { }
    }
    return null
}

fun calcularEstado(alimento: Alimento, hoy: LocalDate = LocalDate.now()): EstadoVencimiento {
    val fecha = parsearFecha(alimento.fechaVencimiento) ?: return EstadoVencimiento.NORMAL
    return when {
        !hoy.isBefore(fecha.plusDays(1)) -> EstadoVencimiento.VENCIDO
        ChronoUnit.DAYS.between(hoy, fecha) <= 3 -> EstadoVencimiento.PROXIMO
        else -> EstadoVencimiento.NORMAL
    }
}

fun diasParaVencer(alimento: Alimento, hoy: LocalDate = LocalDate.now()): Long? {
    val fecha = parsearFecha(alimento.fechaVencimiento) ?: return null
    return ChronoUnit.DAYS.between(hoy, fecha)
}
