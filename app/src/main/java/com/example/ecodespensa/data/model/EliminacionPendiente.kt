package com.example.ecodespensa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room (tombstone) que registra de forma persistente la eliminación pendiente
 * de un alimento para sincronizarla con Cloud Firestore (`usuarios/{uid}/alimentos/{remoteId}`).
 *
 * Permite:
 * 1. Conservar el estado de eliminación pendiente incluso si se cierra o reinicia la app sin Internet.
 * 2. Reintentar la eliminación desde Mi Perfil cuando vuelva la conexión.
 * 3. Evitar que la recuperación manual desde Firestore vuelva a insertar un alimento eliminado localmente.
 * 4. Detectar conflictos si el documento remoto fue modificado o recreado con posterioridad.
 */
@Entity(tableName = "eliminaciones_pendientes")
data class EliminacionPendiente(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String = "",
    val usuarioUid: String = "",
    val nombreAlimento: String = "",
    val fechaEliminacion: Long = 0L,
    val baseRemotaActualizadoEn: Long = 0L
)

