package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room (tombstone) que registra de forma persistente la eliminación pendiente
 * de un [PlanComida] para sincronizarla con Cloud Firestore y evitar que un plan
 * eliminado localmente reaparezca al recuperar el planificador de la nube.
 */
@Entity(tableName = "eliminaciones_planes_pendientes")
data class EliminacionPlanPendiente(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = "",
    val usuarioUid: String = "",
    val fechaInicioSemana: String = "",
    val diaSemana: Int = 1,
    val tipoComida: String = "",
    val nombrePlato: String = "",
    val fechaEliminacion: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)

