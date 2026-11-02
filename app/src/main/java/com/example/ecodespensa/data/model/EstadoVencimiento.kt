package com.example.ecodespensa.data.model

/**
 * Estados posibles de vencimiento de un alimento.
 * - NORMAL: más de 3 días hasta la fecha de vencimiento (o sin fecha).
 * - PROXIMO: vence en 3 días o menos, pero aún no ha vencido.
 * - VENCIDO: la fecha de vencimiento ya pasó.
 */
enum class EstadoVencimiento {
    NORMAL,
    PROXIMO,
    VENCIDO
}
