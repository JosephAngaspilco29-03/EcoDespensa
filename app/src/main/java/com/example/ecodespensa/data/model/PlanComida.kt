package com.example.ecodespensa.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * Días de la semana del planificador (Lunes = 1 .. Domingo = 7).
 */
enum class DiaSemana(val numero: Int, val etiqueta: String) {
    LUNES(1, "Lunes"),
    MARTES(2, "Martes"),
    MIERCOLES(3, "Miércoles"),
    JUEVES(4, "Jueves"),
    VIERNES(5, "Viernes"),
    SABADO(6, "Sábado"),
    DOMINGO(7, "Domingo");

    companion object {
        fun fromNumero(numero: Int): DiaSemana =
            entries.firstOrNull { it.numero == numero } ?: LUNES
    }
}

/**
 * Momentos de comida diarios soportados en el planificador semanal.
 */
enum class TipoComida(val orden: Int, val etiqueta: String) {
    DESAYUNO(1, "Desayuno"),
    ALMUERZO(2, "Almuerzo"),
    CENA(3, "Cena");

    companion object {
        fun fromNombre(nombre: String): TipoComida =
            entries.firstOrNull { it.name.equals(nombre.trim(), ignoreCase = true) } ?: ALMUERZO
    }
}

/**
 * Entidad Room que representa una comida planificada (Desayuno, Almuerzo o Cena)
 * para un día específico dentro de una semana identificada por [fechaInicioSemana] (lunes ISO `yyyy-MM-dd`).
 *
 * Incluye [usuarioUid] y [remoteId] UUID persistente para futura sincronización con Cloud Firestore.
 */
@Entity(tableName = "planes_comida")
data class PlanComida(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val usuarioUid: String = "",
    val fechaInicioSemana: String = "",
    val diaSemana: Int = DiaSemana.LUNES.numero,
    val tipoComida: String = TipoComida.DESAYUNO.name,
    val nombrePlato: String = "",
    val pendienteSincronizacion: Boolean = true,
    val actualizadoEn: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)

/**
 * Entidad Room que representa un ingrediente opcional de Mi Despensa asignado a un [PlanComida].
 *
 * Se vincula tanto con el plan mediante [planRemoteId] (y [planId] local) como con el alimento de
 * Mi Despensa mediante [alimentoRemoteId] (UUID persistente) y [alimentoNombre] desnormalizado.
 * Planificar ingredientes NO descuenta el stock real de `alimentos`.
 */
@Entity(tableName = "ingredientes_planificados")
data class IngredientePlanificado(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val planId: Int = 0,
    val planRemoteId: String = "",
    val usuarioUid: String = "",
    val alimentoRemoteId: String = "",
    val alimentoNombre: String = "",
    val cantidadPlanificada: Double = 0.0,
    val unidad: String = ""
)

/**
 * Proyección relacional de Room que agrupa un [PlanComida] junto con su lista de [IngredientePlanificado].
 */
data class PlanComidaConIngredientes(
    @Embedded
    val plan: PlanComida,
    @Relation(
        parentColumn = "remoteId",
        entityColumn = "planRemoteId"
    )
    val ingredientes: List<IngredientePlanificado> = emptyList()
)

