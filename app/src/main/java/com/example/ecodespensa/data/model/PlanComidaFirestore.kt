package com.example.ecodespensa.data.model

/**
 * Modelo de ingrediente embebido dentro de un documento `usuarios/{uid}/planes_comida/{remoteId}`
 * en Cloud Firestore. Conserva su `remoteId` UUID y el `alimentoRemoteId` de Mi Despensa.
 */
data class IngredientePlanificadoFirestore(
    val remoteId: String = "",
    val alimentoRemoteId: String = "",
    val alimentoNombre: String = "",
    val cantidadPlanificada: Double = 0.0,
    val unidad: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        FIELD_REMOTE_ID to remoteId,
        FIELD_ALIMENTO_REMOTE_ID to alimentoRemoteId,
        FIELD_ALIMENTO_NOMBRE to alimentoNombre,
        FIELD_CANTIDAD_PLANIFICADA to cantidadPlanificada,
        FIELD_UNIDAD to unidad
    )

    companion object {
        const val FIELD_REMOTE_ID = "remoteId"
        const val FIELD_ALIMENTO_REMOTE_ID = "alimentoRemoteId"
        const val FIELD_ALIMENTO_NOMBRE = "alimentoNombre"
        const val FIELD_CANTIDAD_PLANIFICADA = "cantidadPlanificada"
        const val FIELD_UNIDAD = "unidad"
    }
}

/**
 * Modelo de datos para persistir un [PlanComidaConIngredientes] en Cloud Firestore dentro de
 * `usuarios/{uid}/planes_comida/{remoteId}`.
 */
data class PlanComidaFirestore(
    val remoteId: String = "",
    val usuarioUid: String = "",
    val fechaInicioSemana: String = "",
    val diaSemana: Int = 1,
    val tipoComida: String = "",
    val nombrePlato: String = "",
    val actualizadoEn: Long = 0L,
    val ingredientes: List<IngredientePlanificadoFirestore> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        FIELD_REMOTE_ID to remoteId,
        FIELD_USUARIO_UID to usuarioUid,
        FIELD_FECHA_INICIO_SEMANA to fechaInicioSemana,
        FIELD_DIA_SEMANA to diaSemana,
        FIELD_TIPO_COMIDA to tipoComida,
        FIELD_NOMBRE_PLATO to nombrePlato,
        FIELD_ACTUALIZADO_EN to actualizadoEn,
        FIELD_INGREDIENTES to ingredientes.map { it.toMap() }
    )

    companion object {
        const val FIELD_REMOTE_ID = "remoteId"
        const val FIELD_USUARIO_UID = "usuarioUid"
        const val FIELD_FECHA_INICIO_SEMANA = "fechaInicioSemana"
        const val FIELD_DIA_SEMANA = "diaSemana"
        const val FIELD_TIPO_COMIDA = "tipoComida"
        const val FIELD_NOMBRE_PLATO = "nombrePlato"
        const val FIELD_ACTUALIZADO_EN = "actualizadoEn"
        const val FIELD_INGREDIENTES = "ingredientes"
    }
}

fun PlanComidaConIngredientes.toFirestoreModel(
    uidVerificado: String,
    marcaActualizacion: Long
): PlanComidaFirestore {
    val idRemoto = plan.remoteId.trim()
    require(idRemoto.isNotEmpty()) { "El plan de comida debe tener un remoteId válido." }
    return PlanComidaFirestore(
        remoteId = idRemoto,
        usuarioUid = uidVerificado.trim(),
        fechaInicioSemana = plan.fechaInicioSemana.trim(),
        diaSemana = plan.diaSemana,
        tipoComida = plan.tipoComida.trim(),
        nombrePlato = plan.nombrePlato.trim(),
        actualizadoEn = marcaActualizacion,
        ingredientes = ingredientes.map { ing ->
            IngredientePlanificadoFirestore(
                remoteId = ing.remoteId.trim(),
                alimentoRemoteId = ing.alimentoRemoteId.trim(),
                alimentoNombre = ing.alimentoNombre.trim(),
                cantidadPlanificada = ing.cantidadPlanificada,
                unidad = ing.unidad.trim()
            )
        }
    )
}

