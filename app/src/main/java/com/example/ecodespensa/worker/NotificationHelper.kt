package com.example.ecodespensa.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Helper para crear el canal de notificaciones y mostrar alertas de vencimiento.
 */
object NotificationHelper {

    const val CHANNEL_ID = "ecodespensa_vencimientos"
    private const val CHANNEL_NAME = "Alertas de Vencimiento"
    private const val CHANNEL_DESC = "Notifica cuando un alimento está próximo a vencer"

    /**
     * Crea el NotificationChannel (solo necesario en Android 8+).
     * Es seguro llamarlo múltiples veces; si el canal ya existe, no hace nada.
     */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(canal)
        }
    }

    /**
     * Muestra una notificación de vencimiento para un alimento.
     *
     * @param context    Contexto de la aplicación.
     * @param notifId    ID único de notificación (evita duplicados con el mismo ID).
     * @param nombre     Nombre del alimento.
     * @param diasRestantes Días hasta el vencimiento (0 = hoy, 1 = mañana, etc.).
     */
    fun mostrarNotificacion(
        context: Context,
        notifId: Int,
        nombre: String,
        diasRestantes: Long
    ) {
        val texto = when (diasRestantes) {
            0L -> "$nombre vence hoy"
            1L -> "$nombre vence mañana"
            else -> "$nombre vence en $diasRestantes días"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Alimento próximo a vencer")
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // El usuario no otorgó el permiso POST_NOTIFICATIONS
        }
    }

    /**
     * Cancela todas las notificaciones activas de la aplicación.
     * Se utiliza al cerrar sesión o cambiar de usuario para evitar mostrar alertas de otra cuenta.
     */
    fun cancelarTodas(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
