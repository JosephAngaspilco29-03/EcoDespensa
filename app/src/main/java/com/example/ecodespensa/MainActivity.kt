package com.example.ecodespensa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ecodespensa.data.local.EcoDespensaDatabase
import com.example.ecodespensa.data.repository.AlimentoRepositoryImpl
import com.example.ecodespensa.data.repository.MovimientoRepositoryImpl
import com.example.ecodespensa.ui.history.MovimientoViewModel
import com.example.ecodespensa.ui.navigation.NavGraph
import com.example.ecodespensa.ui.pantry.AlimentoViewModel
import com.example.ecodespensa.ui.theme.EcoDespensaTheme
import com.example.ecodespensa.worker.NotificationHelper
import com.example.ecodespensa.worker.VencimientoWorker
import java.util.concurrent.TimeUnit

/**
 * Actividad principal de EcoDespensa.
 * Configura la base de datos Room, los repositorios, los ViewModels
 * y lanza el grafo de navegación.
 *
 * Además programa la revisión periódica diaria de vencimientos con WorkManager
 * y solicita el permiso POST_NOTIFICATIONS en Android 13+.
 */
class MainActivity : ComponentActivity() {

    private val db by lazy { EcoDespensaDatabase.getInstance(applicationContext) }

    private val alimentoViewModel: AlimentoViewModel by viewModels {
        AlimentoViewModel.Factory(
            repository = AlimentoRepositoryImpl(
                dao = db.alimentoDao(),
                transactionDao = db.despensaTransactionDao()
            ),
            movimientoRepository = MovimientoRepositoryImpl(db.movimientoDao())
        )
    }

    private val movimientoViewModel: MovimientoViewModel by viewModels {
        MovimientoViewModel.Factory(MovimientoRepositoryImpl(db.movimientoDao()))
    }

    /** Lanzador para solicitar el permiso de notificaciones (Android 13+). */
    private val pedirPermisoNotificacion =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Si se otorga, programamos el Worker; si no, simplemente no notificamos.
            programarWorkerVencimientos()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Crear canal antes de cualquier notificación
        NotificationHelper.crearCanal(this)

        // Solicitar permiso en Android 13+ o programar directamente en versiones anteriores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permiso = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED) {
                programarWorkerVencimientos()
            } else {
                pedirPermisoNotificacion.launch(permiso)
            }
        } else {
            programarWorkerVencimientos()
        }

        setContent {
            EcoDespensaTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    alimentoViewModel = alimentoViewModel,
                    movimientoViewModel = movimientoViewModel
                )
            }
        }
    }

    /**
     * Programa una revisión periódica diaria con WorkManager.
     * Usa [ExistingPeriodicWorkPolicy.KEEP] para no reprogramar si ya existe,
     * evitando notificaciones duplicadas al reiniciar la app.
     */
    private fun programarWorkerVencimientos() {
        val request = PeriodicWorkRequestBuilder<VencimientoWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            VencimientoWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}