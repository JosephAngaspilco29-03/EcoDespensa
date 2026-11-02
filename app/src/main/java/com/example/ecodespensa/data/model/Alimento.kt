package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que representa un alimento en la despensa.
 * Se persiste en la base de datos local EcoDespensaDatabase.
 */
@Entity(tableName = "alimentos")
data class Alimento(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nombre: String = "",
    val categoria: String = "",
    val cantidadActual: String = "",
    val unidad: String = "",
    val fechaCompra: String = "",
    val fechaVencimiento: String = ""
)
