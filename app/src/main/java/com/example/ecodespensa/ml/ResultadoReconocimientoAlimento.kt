package com.example.ecodespensa.ml

/**
 * Predicción unitaria devuelta por el modelo TensorFlow Lite de reconocimiento de alimentos.
 *
 * - [etiqueta]: nombre sugerido del alimento en español (siempre editable por el usuario).
 * - [confianza]: probabilidad real devuelta por el tensor de salida en el rango `0.0f..1.0f`.
 * - [categoriaSugerida]: categoría sugerida para el formulario (p. ej. "Fruta", "Vegetal", "Verdura").
 * - [etiquetaOriginalModelo]: etiqueta original del índice del modelo (p. ej. "Apple", "Banana").
 */
data class PrediccionAlimento(
    val etiqueta: String,
    val confianza: Float,
    val categoriaSugerida: String? = null,
    val etiquetaOriginalModelo: String = etiqueta
) {
    val porcentajeConfianza: Int
        get() = (confianza.coerceIn(0f, 1f) * 100f).toInt()

    val porcentajeFormateado: String
        get() = String.format(java.util.Locale.US, "%.1f%%", confianza.coerceIn(0f, 1f) * 100f)
}

/**
 * Resultado desacoplado del análisis de una fotografía de alimento mediante TensorFlow Lite.
 */
sealed interface ResultadoReconocimientoAlimento {
    /**
     * Inferencia exitosa cuya predicción principal supera el umbral mínimo de confianza.
     * Incluye hasta 3 predicciones ordenadas de mayor a menor probabilidad real.
     */
    data class Exito(
        val prediccionPrincipal: PrediccionAlimento,
        val alternativas: List<PrediccionAlimento> = emptyList(),
        val tiempoInferenciaMs: Long = 0L,
        val descripcionTensor: String = ""
    ) : ResultadoReconocimientoAlimento {
        val topTresPredicciones: List<PrediccionAlimento>
            get() = (listOf(prediccionPrincipal) + alternativas).take(3)
    }

    /**
     * Cuando la probabilidad máxima del modelo es inferior al umbral de confianza configurado
     * o la imagen no corresponde claramente a las clases admitidas.
     * Conserva [candidatos] internamente para diagnóstico/decisiones sin mostrarlos en la UI.
     */
    data class BajaConfianza(
        val mensaje: String = MENSAJE_BAJA_CONFIANZA_DEFECTO,
        val candidatos: List<PrediccionAlimento> = emptyList(),
        val tiempoInferenciaMs: Long = 0L
    ) : ResultadoReconocimientoAlimento

    data class SinModeloConfigurado(
        val mensaje: String
    ) : ResultadoReconocimientoAlimento

    data class Error(
        val mensaje: String
    ) : ResultadoReconocimientoAlimento

    companion object {
        const val MENSAJE_BAJA_CONFIANZA_DEFECTO =
            "No se pudo identificar el alimento. Ingresa sus datos manualmente"
    }
}
