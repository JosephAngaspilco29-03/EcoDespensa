package com.example.ecodespensa.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecodespensa.ui.auth.AuthViewModel
import com.example.ecodespensa.ui.history.DescargaHistorialUiState
import com.example.ecodespensa.ui.history.MovimientoViewModel
import com.example.ecodespensa.ui.history.SubidaHistorialUiState
import com.example.ecodespensa.ui.pantry.AlimentoViewModel
import com.example.ecodespensa.ui.pantry.DescargaNubeUiState
import com.example.ecodespensa.ui.pantry.SubidaNubeUiState

/**
 * Pantalla Mi Perfil.
 * Muestra nombre y correo del usuario autenticado en Firebase.
 * Incluye botón "Subir mi despensa a la nube", opción para vincular datos antiguos
 * sin propietario y botón para cerrar sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiPerfilScreen(
    onVolver: () -> Unit,
    onCerrarSesion: () -> Unit,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory()),
    alimentoViewModel: AlimentoViewModel? = null,
    movimientoViewModel: MovimientoViewModel? = null
) {
    val usuario = authViewModel.usuarioActual
    val haySesionActiva = usuario?.uid?.isNotBlank() == true
    val nombre = usuario?.displayName?.ifBlank { "Sin nombre" } ?: "Sin nombre"
    val correo = usuario?.email ?: "Sin correo"

    val alimentosCuenta = alimentoViewModel
        ?.alimentos
        ?.collectAsState()
        ?.value
        .orEmpty()

    val eliminacionesPendientes = alimentoViewModel
        ?.eliminacionesPendientes
        ?.collectAsState()
        ?.value
        .orEmpty()

    val mostrarVincularDatos = alimentoViewModel
        ?.mostrarOpcionVincularDatosAntiguos
        ?.collectAsState()
        ?.value ?: false

    val subidaState = alimentoViewModel
        ?.subidaNubeState
        ?.collectAsState()
        ?.value ?: SubidaNubeUiState.Idle

    val descargaState = alimentoViewModel
        ?.descargaNubeState
        ?.collectAsState()
        ?.value ?: DescargaNubeUiState.Idle

    var mostrarDialogo by remember { mutableStateOf(false) }
    var mensajeSinSesion by remember { mutableStateOf<String?>(null) }
    var mostrarConfirmacionHistorial by remember { mutableStateOf(false) }
    var mostrarConfirmacionDescargaHistorial by remember { mutableStateOf(false) }

    val historialState = movimientoViewModel
        ?.subidaHistorialState
        ?.collectAsState()
        ?.value ?: SubidaHistorialUiState.Idle

    val descargaHistorialState = movimientoViewModel
        ?.descargaHistorialState
        ?.collectAsState()
        ?.value ?: DescargaHistorialUiState.Idle

    val movimientosCuenta = movimientoViewModel
        ?.movimientos
        ?.collectAsState()
        ?.value
        .orEmpty()

    // Confirmación antes de recuperar el historial desde la nube
    if (mostrarConfirmacionDescargaHistorial) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionDescargaHistorial = false },
            title = { Text("Recuperar mi historial de la nube") },
            text = {
                Text(
                    "Se consultarán los movimientos almacenados en Cloud Firestore para tu cuenta actual.\n\n" +
                        "• Solo se agregarán a Room los movimientos cuyo remoteId aún no exista localmente.\n" +
                        "• Tu historial local existente no será borrado ni sobrescrito.\n" +
                        "• No se alterará el stock ni se recrearán alimentos eliminados.\n" +
                        "• No se generarán movimientos de compra, consumo ni ajuste adicionales.\n\n" +
                        "¿Deseas confirmar la recuperación?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionDescargaHistorial = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            movimientoViewModel?.recuperarMiHistorialDeLaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionDescargaHistorial = false }) { Text("Cancelar") }
            }
        )
    }

    // Resultado o error de la recuperación del historial desde la nube
    when (val estadoDescargaH = descargaHistorialState) {
        is DescargaHistorialUiState.Resultado -> {
            val res = estadoDescargaH.resumen
            AlertDialog(
                onDismissRequest = { movimientoViewModel?.limpiarEstadoDescargaHistorial() },
                title = {
                    Text(
                        if (res.errores > 0) "Recuperación de historial con observaciones"
                        else "Resultado de recuperación del historial"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Movimientos encontrados en tu nube: ${res.totalRemotos}")
                        Text("• Descargados (nuevos en Room): ${res.descargados}")
                        Text("• Omitidos (ya existían localmente): ${res.omitidos}")
                        Text("• Errores: ${res.errores}")
                        if (res.errores > 0 && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { movimientoViewModel?.limpiarEstadoDescargaHistorial() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is DescargaHistorialUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { movimientoViewModel?.limpiarEstadoDescargaHistorial() },
                title = { Text("No se pudo recuperar el historial") },
                text = { Text(estadoDescargaH.mensaje) },
                confirmButton = {
                    TextButton(onClick = { movimientoViewModel?.limpiarEstadoDescargaHistorial() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    // Confirmación antes de subir el historial
    if (mostrarConfirmacionHistorial) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionHistorial = false },
            title = { Text("Subir mi historial a la nube") },
            text = {
                Text(
                    "Tienes ${movimientosCuenta.size} movimiento(s) en tu cuenta actual.\n\n" +
                        "• Se subirán a Cloud Firestore usando su identificador único.\n" +
                        "• Los que ya existan en la nube se omitirán sin sobrescribirlos.\n" +
                        "• No se generarán movimientos nuevos ni se modificará tu historial local.\n\n" +
                        "¿Deseas confirmar la subida?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionHistorial = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            movimientoViewModel?.subirMiHistorialALaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionHistorial = false }) { Text("Cancelar") }
            }
        )
    }

    // Resultado o error de la subida del historial
    when (val estadoH = historialState) {
        is SubidaHistorialUiState.Resultado -> {
            val res = estadoH.resumen
            AlertDialog(
                onDismissRequest = { movimientoViewModel?.limpiarEstadoSubidaHistorial() },
                title = {
                    Text(if (res.errores > 0) "Subida de historial con errores" else "Resultado de subida del historial")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Movimientos evaluados de tu cuenta: ${res.total}")
                        Text("• Subidos: ${res.subidos}")
                        Text("• Omitidos (ya existían): ${res.omitidos}")
                        Text("• Errores: ${res.errores}")
                        if (res.errores > 0 && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { movimientoViewModel?.limpiarEstadoSubidaHistorial() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is SubidaHistorialUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { movimientoViewModel?.limpiarEstadoSubidaHistorial() },
                title = { Text("No se pudo subir el historial") },
                text = { Text(estadoH.mensaje) },
                confirmButton = {
                    TextButton(onClick = { movimientoViewModel?.limpiarEstadoSubidaHistorial() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }
    var mostrarDialogoVincular by remember { mutableStateOf(false) }
    var mostrarConfirmacionSubida by remember { mutableStateOf(false) }
    var mostrarConfirmacionDescarga by remember { mutableStateOf(false) }

    // Diálogo cuando se intenta operar sin sesión activa
    if (mensajeSinSesion != null) {
        AlertDialog(
            onDismissRequest = { mensajeSinSesion = null },
            title = { Text("Operación bloqueada") },
            text = { Text(mensajeSinSesion!!) },
            confirmButton = {
                TextButton(onClick = { mensajeSinSesion = null }) {
                    Text("Aceptar")
                }
            }
        )
    }

    // Diálogo de confirmación antes de subir la despensa a Cloud Firestore
    if (mostrarConfirmacionSubida) {
        val cantidadAlimentos = alimentosCuenta.size
        val cantidadPendientes = alimentosCuenta.count { it.pendienteSincronizacion }
        val cantidadEliminaciones = eliminacionesPendientes.size
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionSubida = false },
            title = { Text("Subir mi despensa a la nube") },
            text = {
                Text(
                    "Tienes $cantidadAlimentos alimento(s) en tu cuenta actual" +
                        (if (cantidadPendientes > 0) " ($cantidadPendientes cambio(s) pendiente(s))" else "") +
                        (if (cantidadEliminaciones > 0) " y $cantidadEliminaciones eliminación(es) pendiente(s)" else "") +
                        ".\n\n" +
                        "• Se subirán los alimentos nuevos usando su remoteId único.\n" +
                        "• Se actualizarán en Cloud Firestore los alimentos con cambios pendientes.\n" +
                        "• Se ejecutarán las eliminaciones pendientes verificando conflictos.\n" +
                        "• Se omitirán los que ya estén sincronizados sin cambios pendientes.\n\n" +
                        "¿Deseas confirmar la sincronización?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionSubida = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            alimentoViewModel?.subirMiDespensaALaNube()
                        }
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionSubida = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación antes de recuperar alimentos desde Cloud Firestore
    if (mostrarConfirmacionDescarga) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionDescarga = false },
            title = { Text("Recuperar alimentos de la nube") },
            text = {
                Text(
                    "Se consultarán los alimentos almacenados en Cloud Firestore para tu cuenta actual.\n\n" +
                        "• Solo se agregarán a Room los alimentos cuyo remoteId aún no exista localmente.\n" +
                        "• Los alimentos locales existentes no serán borrados ni sobrescritos.\n" +
                        "• No se generarán movimientos de compra duplicados.\n\n" +
                        "¿Deseas confirmar la recuperación?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionDescarga = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            alimentoViewModel?.recuperarAlimentosDeLaNube()
                        }
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionDescarga = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo con el resultado o error de la recuperación desde la nube
    when (val estadoDescarga = descargaState) {
        is DescargaNubeUiState.Resultado -> {
            val res = estadoDescarga.resumen
            val tituloDescarga = if (res.errores > 0) {
                "Recuperación completada con observaciones"
            } else {
                "Resultado de recuperación de la nube"
            }
            AlertDialog(
                onDismissRequest = { alimentoViewModel?.limpiarEstadoDescargaNube() },
                title = { Text(tituloDescarga) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Documentos encontrados en tu nube: ${res.totalRemotos}")
                        Text("• Descargados (nuevos en Room): ${res.descargados}")
                        Text("• Omitidos (ya existían localmente): ${res.omitidos}")
                        Text("• Errores: ${res.errores}")
                        if (res.errores > 0 && !res.detalleError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { alimentoViewModel?.limpiarEstadoDescargaNube() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is DescargaNubeUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { alimentoViewModel?.limpiarEstadoDescargaNube() },
                title = { Text("No se pudo recuperar de la nube") },
                text = { Text(estadoDescarga.mensaje) },
                confirmButton = {
                    TextButton(onClick = { alimentoViewModel?.limpiarEstadoDescargaNube() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    // Diálogo con el resultado o error de la subida a la nube
    when (val estado = subidaState) {
        is SubidaNubeUiState.Resultado -> {
            val res = estado.resumen
            val tituloResultado = when {
                res.errores > 0 && res.subidos == 0 && res.eliminados == 0 && res.omitidos == 0 ->
                    "No se pudo subir la despensa"
                res.errores > 0 ->
                    "Sincronización completada con observaciones"
                else ->
                    "Resultado de subida a la nube"
            }
            AlertDialog(
                onDismissRequest = { alimentoViewModel?.limpiarEstadoSubidaNube() },
                title = { Text(tituloResultado) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Operaciones evaluadas de tu cuenta: ${res.total}")
                        Text("• Subidos / actualizados: ${res.subidos}")
                        Text("• Eliminaciones sincronizadas: ${res.eliminados}")
                        Text("• Omitidos (ya existían sin cambios pendientes): ${res.omitidos}")
                        Text("• Errores / conflictos: ${res.errores}")
                        if (res.errores > 0 && !res.detalleError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { alimentoViewModel?.limpiarEstadoSubidaNube() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is SubidaNubeUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { alimentoViewModel?.limpiarEstadoSubidaNube() },
                title = { Text("No se pudo subir a la nube") },
                text = { Text(estado.mensaje) },
                confirmButton = {
                    TextButton(onClick = { alimentoViewModel?.limpiarEstadoSubidaNube() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    if (mostrarDialogoVincular) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoVincular = false },
            title = { Text("Vincular datos antiguos a mi cuenta") },
            text = {
                Text(
                    "Todos los registros antiguos sin propietario (alimentos y movimientos) " +
                        "pasarán a pertenecer a tu cuenta actual. Los registros que ya tengan " +
                        "propietario no serán modificados."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDialogoVincular = false
                        alimentoViewModel?.vincularDatosAntiguosAMiCuenta()
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoVincular = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación
    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que quieres cerrar sesión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDialogo = false
                        authViewModel.cerrarSesion()
                        onCerrarSesion()
                    }
                ) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi perfil") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarDialogo = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesión"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Avatar de usuario",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = correo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Información del hogar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilaInfo(etiqueta = "Personas en el hogar", valor = "2")
            FilaInfo(etiqueta = "Preferencia alimenticia", valor = "Sin restricciones")
            FilaInfo(etiqueta = "Miembro desde", valor = "Octubre 2026")

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Estadísticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilaInfo(etiqueta = "Alimentos registrados", valor = alimentosCuenta.size.toString())
            FilaInfo(
                etiqueta = "Pendientes de sincronizar",
                valor = (alimentosCuenta.count { it.pendienteSincronizacion } + eliminacionesPendientes.size).toString()
            )
            FilaInfo(
                etiqueta = "Eliminaciones pendientes",
                valor = eliminacionesPendientes.size.toString()
            )
            FilaInfo(etiqueta = "Alimentos aprovechados", valor = "0")
            FilaInfo(etiqueta = "Desperdicio evitado", valor = "0 kg")

            Spacer(modifier = Modifier.height(32.dp))

            // Progreso de subida en curso
            if (subidaState is SubidaNubeUiState.Subiendo) {
                val progreso = if (subidaState.total > 0) {
                    subidaState.procesados.toFloat() / subidaState.total.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Subiendo alimentos a la nube (${subidaState.procesados}/${subidaState.total})...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val operacionNubeEnCurso =
                subidaState is SubidaNubeUiState.Subiendo ||
                    descargaState is DescargaNubeUiState.Descargando ||
                    historialState is SubidaHistorialUiState.Subiendo ||
                    descargaHistorialState is DescargaHistorialUiState.Descargando

            if (historialState is SubidaHistorialUiState.Subiendo) {
                val progresoH = if (historialState.total > 0) {
                    historialState.procesados.toFloat() / historialState.total.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progresoH },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Subiendo historial (${historialState.procesados}/${historialState.total})...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (descargaHistorialState is DescargaHistorialUiState.Descargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recuperando historial desde la nube...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (descargaState is DescargaNubeUiState.Descargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recuperando alimentos desde la nube...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botón manual de subida a Cloud Firestore
            Button(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para subir tu despensa."
                    } else {
                        mostrarConfirmacionSubida = true
                    }
                },
                enabled = !operacionNubeEnCurso && alimentoViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir mi despensa a la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de recuperación desde Cloud Firestore hacia Room
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para recuperar tus alimentos."
                    } else {
                        mostrarConfirmacionDescarga = true
                    }
                },
                enabled = !operacionNubeEnCurso && alimentoViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recuperar alimentos de la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de subida del historial de movimientos
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para subir tu historial."
                    } else {
                        mostrarConfirmacionHistorial = true
                    }
                },
                enabled = !operacionNubeEnCurso && movimientoViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir mi historial a la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de recuperación del historial desde Cloud Firestore hacia Room
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para recuperar tu historial."
                    } else {
                        mostrarConfirmacionDescargaHistorial = true
                    }
                },
                enabled = !operacionNubeEnCurso && movimientoViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recuperar mi historial de la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (mostrarVincularDatos) {
                Button(
                    onClick = { mostrarDialogoVincular = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vincular datos antiguos a mi cuenta")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { mostrarDialogo = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar sesión")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilaInfo(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
