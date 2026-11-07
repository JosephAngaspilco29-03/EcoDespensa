package com.example.ecodespensa.util

/**
 * Familias de unidades para comparar cantidades entre el Planificador Semanal,
 * Mi Despensa y la Lista de Compras sin realizar conversiones inválidas.
 */
enum class FamiliaUnidad {
    MASA,       // base: gramos (g)
    VOLUMEN,    // base: mililitros (ml)
    CONTEO,     // base: unidad
    PERSONALIZADA
}

data class UnidadNormalizada(
    val familia: FamiliaUnidad,
    val claveFamilia: String,
    val factorABase: Double,
    val etiquetaPreferida: String
)

object UnidadConversionUtils {

    /**
     * Analiza una cadena de unidad y devuelve su familia y factor hacia la unidad base de esa familia.
     * Si no pertenece a masa, volumen o conteo estándar, se clasifica como [FamiliaUnidad.PERSONALIZADA]
     * y solo será compatible con el mismo texto de unidad normalizado.
     */
    fun analizarUnidad(unidadRaw: String): UnidadNormalizada {
        val limpia = unidadRaw.trim().lowercase()
        return when (limpia) {
            "g", "gr", "grs", "gramo", "gramos" ->
                UnidadNormalizada(FamiliaUnidad.MASA, "MASA", 1.0, "g")
            "kg", "kilo", "kilos", "kilogramo", "kilogramos" ->
                UnidadNormalizada(FamiliaUnidad.MASA, "MASA", 1000.0, "kg")
            "ml", "mililitro", "mililitros", "cc" ->
                UnidadNormalizada(FamiliaUnidad.VOLUMEN, "VOLUMEN", 1.0, "ml")
            "l", "lt", "lts", "litro", "litros" ->
                UnidadNormalizada(FamiliaUnidad.VOLUMEN, "VOLUMEN", 1000.0, "L")
            "", "u", "ud", "uds", "unid", "unidad", "unidades", "pieza", "piezas" ->
                UnidadNormalizada(FamiliaUnidad.CONTEO, "CONTEO", 1.0, unidadRaw.trim().ifEmpty { "unid." })
            else ->
                UnidadNormalizada(FamiliaUnidad.PERSONALIZADA, "CUSTOM:$limpia", 1.0, unidadRaw.trim())
        }
    }

    /**
     * Indica si dos unidades pueden compararse y sumarse/restarse de forma segura.
     */
    fun sonCompatibles(unidadA: String, unidadB: String): Boolean {
        val normA = analizarUnidad(unidadA)
        val normB = analizarUnidad(unidadB)
        return normA.claveFamilia == normB.claveFamilia
    }

    /**
     * Convierte [cantidad] expresada en [unidadOrigen] a la unidad base de su familia.
     */
    fun aCantidadBase(cantidad: Double, unidadOrigen: String): Double {
        val norm = analizarUnidad(unidadOrigen)
        return cantidad * norm.factorABase
    }

    /**
     * Convierte una [cantidadEnBase] de vuelta a [unidadDestino] (deben pertenecer a la misma familia).
     */
    fun desdeCantidadBase(cantidadEnBase: Double, unidadDestino: String): Double {
        val norm = analizarUnidad(unidadDestino)
        return if (norm.factorABase > 0.0) cantidadEnBase / norm.factorABase else cantidadEnBase
    }
}

