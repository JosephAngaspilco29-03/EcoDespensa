package com.example.ecodespensa.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ecodespensa.data.local.EcoDespensaDatabase
import com.example.ecodespensa.util.parsearFecha
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Worker que revisa los alimentos en Room y emite notificaciones de vencimiento.
 *
 * Reglas de negocio:
 *  - Solo notifica alimentos con stock > 0.
 *  - Solo notifica si el alimento vence HOY (0 días), MAÑANA (1 día) o en 3 DÍAS.
 *  - No notifica alimentos ya vencidos (días < 0).
 *  - Usa un ID de notificación derivado del ID del alimento para evitar duplicados.
 */
class VencimientoWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.crearCanal(context)

        val db = EcoDespensaDatabase.getInstance(context)
        val alimentos = db.alimentoDao().obtenerTodos().first()

        val hoy = LocalDate.now()

        alimentos.forEach { alimento ->
            // Ignorar stock <= 0
            val stock = alimento.cantidadActual
                .replace(',', '.')
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.toDoubleOrNull() ?: 0.0
            if (stock <= 0) return@forEach

            // Ignorar sin fecha de vencimiento
            val fechaVenc = parsearFecha(alimento.fechaVencimiento) ?: return@forEach

            val diasRestantes = ChronoUnit.DAYS.between(hoy, fechaVenc)

            // Solo notificar: hoy (0), mañana (1) o en 3 días (3)
            if (diasRestantes !in listOf(0L, 1L, 3L)) return@forEach

            // ID único por alimento (base 1000) + offset por días para no solapar
            val notifId = alimento.id * 10 + diasRestantes.toInt()

            NotificationHelper.mostrarNotificacion(
                context = context,
                notifId = notifId,
                nombre = alimento.nombre,
                diasRestantes = diasRestantes
            )
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "VencimientoWorkerPeriodico"
    }
}
