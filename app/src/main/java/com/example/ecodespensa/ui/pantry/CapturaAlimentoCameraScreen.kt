package com.example.ecodespensa.ui.pantry

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ecodespensa.ml.ImagenEntradaReconocimiento
import com.example.ecodespensa.ml.ProcesadorImagenCamara
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Pantalla Compose con CameraX para capturar la fotografía de un alimento (Etapa 7A).
 *
 * - Solicita y valida el permiso `CAMERA` en tiempo de ejecución.
 * - Detecta disponibilidad real de cámaras (trasera y frontal) tanto en dispositivos físicos
 *   como en emuladores, mostrando un aviso claro si el emulador no tiene cámara funcional.
 * - Guarda la captura temporalmente en el almacenamiento privado (`cacheDir/capturas_alimentos`)
 *   sin requerir permisos de almacenamiento.
 * - Normaliza orientación EXIF y dimensiones antes de mostrar la vista previa de la foto capturada.
 * - Permite ver la fotografía, repetirla, confirmarla o regresar al formulario sin perder datos.
 * - Libera correctamente los casos de uso de CameraX y su `ExecutorService` al salir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaAlimentoCameraScreen(
    onConfirmarCaptura: (ImagenEntradaReconocimiento) -> Unit,
    onCancelarYVolver: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var tienePermisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permisoSolicitadoUnaVez by remember { mutableStateOf(false) }

    val launcherPermisoCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermisoCamara = concedido
        permisoSolicitadoUnaVez = true
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoCamara && !permisoSolicitadoUnaVez) {
            permisoSolicitadoUnaVez = true
            launcherPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    // Estado de cámara y captura
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var tieneCamaraTrasera by remember { mutableStateOf(false) }
    var tieneCamaraFrontal by remember { mutableStateOf(false) }
    var usarCamaraFrontal by remember { mutableStateOf(false) }
    var errorCamara by remember { mutableStateOf<String?>(null) }
    var intentoReinicioCamara by remember { mutableIntStateOf(0) }

    var capturandoFoto by remember { mutableStateOf(false) }
    var mensajeErrorCaptura by remember { mutableStateOf<String?>(null) }
    var capturaTemporalActual by remember { mutableStateOf<ImagenEntradaReconocimiento?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val cancelarLimpiandoTemporal = {
        capturaTemporalActual?.let { noConfirmada ->
            ProcesadorImagenCamara.eliminarArchivoTemporalSeguro(noConfirmada.rutaArchivoTemporal)
        }
        capturaTemporalActual = null
        onCancelarYVolver()
    }

    BackHandler {
        cancelarLimpiandoTemporal()
    }

    // Liberar recursos de CameraX y Executor al destruir el Composable
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            runCatching { cameraExecutor.shutdown() }
        }
    }

    // Inicializar ProcessCameraProvider cuando el permiso CAMERA está concedido
    LaunchedEffect(tienePermisoCamara, intentoReinicioCamara) {
        if (!tienePermisoCamara) return@LaunchedEffect
        errorCamara = null

        val tieneFeatureCamara = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
        if (!tieneFeatureCamara) {
            errorCamara =
                "Este emulador o dispositivo no reporta hardware de cámara disponible. " +
                    "Puedes configurar una cámara virtual (Emulated / Webcam0) en el AVD Manager " +
                    "de Android Studio, probar en un dispositivo físico o volver al formulario sin perder tus datos."
            return@LaunchedEffect
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    val traseraDisponible =
                        runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }
                            .getOrDefault(false)
                    val frontalDisponible =
                        runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }
                            .getOrDefault(false)

                    tieneCamaraTrasera = traseraDisponible
                    tieneCamaraFrontal = frontalDisponible
                    cameraProvider = provider

                    when {
                        !traseraDisponible && !frontalDisponible -> {
                            errorCamara =
                                "No se detectó ninguna cámara funcional en este emulador o dispositivo. " +
                                    "En el emulador de Android Studio, abre los ajustes del AVD y configura " +
                                    "Camera (Front/Back) en 'Emulated' o 'Webcam0', o utiliza un dispositivo físico."
                        }
                        !traseraDisponible && frontalDisponible -> {
                            usarCamaraFrontal = true
                        }
                        traseraDisponible && !frontalDisponible -> {
                            usarCamaraFrontal = false
                        }
                    }
                }.onFailure { e ->
                    errorCamara =
                        "No se pudo inicializar el proveedor CameraX: ${e.localizedMessage ?: "error desconocido"}."
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (capturaTemporalActual != null) "Revisar fotografía"
                        else "Reconocer alimento con cámara"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = cancelarLimpiandoTemporal) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al formulario"
                        )
                    }
                },
                actions = {
                    if (tienePermisoCamara &&
                        capturaTemporalActual == null &&
                        tieneCamaraTrasera &&
                        tieneCamaraFrontal
                    ) {
                        IconButton(
                            onClick = { usarCamaraFrontal = !usarCamaraFrontal },
                            enabled = !capturandoFoto
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cambiar entre cámara trasera y frontal"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. Sin permiso de cámara concedido
                !tienePermisoCamara -> {
                    VistaPermisoCamaraRequerido(
                        onSolicitarPermiso = {
                            launcherPermisoCamara.launch(Manifest.permission.CAMERA)
                        },
                        onVolverFormulario = cancelarLimpiandoTemporal
                    )
                }

                // 2. Fotografía ya capturada: verla, repetirla o confirmarla
                capturaTemporalActual != null -> {
                    val foto = capturaTemporalActual!!
                    VistaRevisionFotografiaCapturada(
                        entrada = foto,
                        onRepetir = {
                            ProcesadorImagenCamara.eliminarArchivoTemporalSeguro(
                                foto.rutaArchivoTemporal
                            )
                            capturaTemporalActual = null
                            mensajeErrorCaptura = null
                        },
                        onConfirmar = {
                            val confirmada = foto
                            capturaTemporalActual = null
                            onConfirmarCaptura(confirmada)
                        },
                        onCancelarYVolver = cancelarLimpiandoTemporal
                    )
                }

                // 3. Error de disponibilidad de cámara (por ejemplo, emulador sin cámara configurada)
                errorCamara != null -> {
                    VistaAvisoSinCamaraFuncional(
                        mensaje = errorCamara!!,
                        onReintentar = { intentoReinicioCamara++ },
                        onVolverFormulario = cancelarLimpiandoTemporal
                    )
                }

                // 4. Vista previa activa de CameraX lista para tomar fotografía
                cameraProvider != null -> {
                    VistaPreviaActivaCameraX(
                        cameraProvider = cameraProvider!!,
                        usarCamaraFrontal = usarCamaraFrontal,
                        capturandoFoto = capturandoFoto,
                        mensajeErrorCaptura = mensajeErrorCaptura,
                        onIniciarCaptura = { imageCapture ->
                            if (capturandoFoto) return@VistaPreviaActivaCameraX
                            capturandoFoto = true
                            mensajeErrorCaptura = null

                            val archivoSalida =
                                ProcesadorImagenCamara.crearArchivoTemporalCaptura(context)
                            val outputOptions = ImageCapture.OutputFileOptions
                                .Builder(archivoSalida)
                                .build()

                            imageCapture.takePicture(
                                outputOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        coroutineScope.launch {
                                            val resultado =
                                                ProcesadorImagenCamara.procesarCapturaTemporal(
                                                    archivoSalida
                                                )
                                            capturandoFoto = false
                                            resultado.fold(
                                                onSuccess = { entradaProcesada ->
                                                    capturaTemporalActual = entradaProcesada
                                                },
                                                onFailure = { err ->
                                                    ProcesadorImagenCamara.eliminarArchivoTemporalSeguro(
                                                        archivoSalida.absolutePath
                                                    )
                                                    mensajeErrorCaptura =
                                                        err.localizedMessage
                                                            ?: "No se pudo procesar la fotografía capturada."
                                                }
                                            )
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        ProcesadorImagenCamara.eliminarArchivoTemporalSeguro(
                                            archivoSalida.absolutePath
                                        )
                                        coroutineScope.launch {
                                            capturandoFoto = false
                                            mensajeErrorCaptura =
                                                "No se pudo capturar la fotografía (${exception.localizedMessage ?: "error de cámara"}). " +
                                                    "Verifica que la cámara del dispositivo o emulador esté activa."
                                        }
                                    }
                                }
                            )
                        },
                        onErrorEnlaceCamara = { msg ->
                            errorCamara = msg
                        },
                        onVolverFormulario = cancelarLimpiandoTemporal,
                        lifecycleOwner = lifecycleOwner
                    )
                }

                // 5. Cargando proveedor de cámara
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Inicializando cámara con CameraX...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VistaPreviaActivaCameraX(
    cameraProvider: ProcessCameraProvider,
    usarCamaraFrontal: Boolean,
    capturandoFoto: Boolean,
    mensajeErrorCaptura: String?,
    onIniciarCaptura: (ImageCapture) -> Unit,
    onErrorEnlaceCamara: (String) -> Unit,
    onVolverFormulario: () -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember(usarCamaraFrontal) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
    }

    DisposableEffect(cameraProvider, lifecycleOwner, usarCamaraFrontal) {
        val selector = if (usarCamaraFrontal) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder()
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture
            )
        }.onFailure { e ->
            onErrorEnlaceCamara(
                "No se pudo enlazar la vista previa de la cámara: " +
                    "${e.localizedMessage ?: "cámara no disponible en este entorno"}. " +
                    "Si usas un emulador, verifica que la cámara virtual esté habilitada o prueba en un dispositivo físico."
            )
        }

        onDispose {
            runCatching { cameraProvider.unbindAll() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Enfoca el alimento para capturar una fotografía clara. Tus datos del formulario se conservan intactos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            if (capturandoFoto) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Capturando y normalizando imagen...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (!mensajeErrorCaptura.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mensajeErrorCaptura,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = {
                imageCapture.targetRotation =
                    previewView.display?.rotation ?: Surface.ROTATION_0
                onIniciarCaptura(imageCapture)
            },
            enabled = !capturandoFoto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (capturandoFoto) "Capturando..." else "Tomar fotografía")
        }

        OutlinedButton(
            onClick = onVolverFormulario,
            enabled = !capturandoFoto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Regresar sin tomar fotografía")
        }
    }
}

@Composable
private fun VistaRevisionFotografiaCapturada(
    entrada: ImagenEntradaReconocimiento,
    onRepetir: () -> Unit,
    onConfirmar: () -> Unit,
    onCancelarYVolver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Revisa la fotografía capturada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Guardada temporalmente en almacenamiento privado de la app • " +
                "Dimensiones listas: ${entrada.anchoProcesadoPx}×${entrada.altoProcesadoPx} px" +
                if (entrada.rotacionAplicadaGrados != 0) {
                    " (rotación EXIF corregida: ${entrada.rotacionAplicadaGrados}°)"
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 380.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Image(
                bitmap = entrada.bitmap.asImageBitmap(),
                contentDescription = "Fotografía capturada del alimento",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color.Black),
                contentScale = ContentScale.Fit
            )
        }

        Button(
            onClick = onConfirmar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirmar fotografía")
        }

        OutlinedButton(
            onClick = onRepetir,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Repetir fotografía")
        }

        TextButton(
            onClick = onCancelarYVolver,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Descartar y volver al formulario")
        }
    }
}

@Composable
private fun VistaPermisoCamaraRequerido(
    onSolicitarPermiso: () -> Unit,
    onVolverFormulario: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Permiso de cámara requerido",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Para capturar la imagen del alimento necesitamos acceso a la cámara. " +
                "La fotografía se guarda únicamente de forma temporal en el almacenamiento privado " +
                "de EcoDespensa y no requiere permisos de archivos.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSolicitarPermiso,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conceder permiso de cámara")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onVolverFormulario,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver al formulario sin tomar foto")
        }
    }
}

@Composable
private fun VistaAvisoSinCamaraFuncional(
    mensaje: String,
    onReintentar: () -> Unit,
    onVolverFormulario: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Cámara no disponible",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onReintentar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reintentar cámara")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onVolverFormulario,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Regresar al formulario sin perder datos")
        }
    }
}

