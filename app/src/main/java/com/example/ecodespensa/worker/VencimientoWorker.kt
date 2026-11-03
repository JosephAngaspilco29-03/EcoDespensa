package com.example.ecodespensa.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ecodespensa.data.local.EcoDespensaDatabase
import com.example.ecodespensa.util.parsearFecha
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Worker que revisa los alimentos en Room del usuario autenticado
 * y emite notificaciones de vencimiento.
 *
 * Reglas de negocio:
 *  - Si no hay sesión activa en Firebase, no consulta datos ni emite notificaciones.
 *  - Consulta únicamente los alimentos cuyo `usuarioUid` coincide con el UID autenticado.
 *  - Solo notifica alimentos con stock > 0.
 *  - Solo notifica si el alimento vence HOY (0 días), MAÑANA (1 día) o en 3 DÍAS.
 *  - No notifica alimentos ya vencidos (días < 0).
 */
class VencimientoWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.crearCanal(context)

        // Verificar sesión activa en Firebase; si no hay usuario, limpiar alertas previas y salir
        val uidActual = FirebaseAuth.getInstance().currentUser?.uid?.trim()
        if (uidActual.isNullOrEmpty()) {
            NotificationHelper.cancelarTodas(context)
            return Result.success()
        }

        // Limpiar notificaciones previas para no mezclar alertas si cambió la cuenta
        NotificationHelper.cancelarTodas(context)

        val db = EcoDespensaDatabase.getInstance(context)
        val alimentos = db.alimentoDao().obtenerPorUsuario(uidActual).first()

        val hoy = LocalDate.now()

        alimentos.forEach { alimento ->
            // Garantizar que el alimento pertenezca al usuario autenticado
            if (alimento.usuarioUid != uidActual) return@forEach

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

            // ID único por usuario + alimento + días restantes
            val notifId = ((uidActual.hashCode() and 0x7FFFFFFF) xor (alimento.id * 10 + diasRestantes.toInt()))

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
