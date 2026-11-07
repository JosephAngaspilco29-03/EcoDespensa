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
import com.example.ecodespensa.ui.planner.DescargaPlanificadorUiState
import com.example.ecodespensa.ui.planner.PlanificadorViewModel
import com.example.ecodespensa.ui.planner.SubidaPlanificadorUiState
import com.example.ecodespensa.ui.shopping.DescargaListaComprasUiState
import com.example.ecodespensa.ui.shopping.ListaComprasViewModel
import com.example.ecodespensa.ui.shopping.SubidaListaComprasUiState

/**
 * Pantalla Mi Perfil.
 * Muestra nombre y correo del usuario autenticado en Firebase.
 * Incluye botones para sincronizar la despensa, el historial, el planificador semanal y la lista de compras con la nube,
 * opción para vincular datos antiguos sin propietario y botón para cerrar sesión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiPerfilScreen(
    onVolver: () -> Unit,
    onCerrarSesion: () -> Unit,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory()),
    alimentoViewModel: AlimentoViewModel? = null,
    movimientoViewModel: MovimientoViewModel? = null,
    planificadorViewModel: PlanificadorViewModel? = null,
    listaComprasViewModel: ListaComprasViewModel? = null
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

    var mostrarConfirmacionSubidaPlanificador by remember { mutableStateOf(false) }
    var mostrarConfirmacionDescargaPlanificador by remember { mutableStateOf(false) }

    val subidaPlanificadorState = planificadorViewModel
        ?.subidaPlanificadorState
        ?.collectAsState()
        ?.value ?: SubidaPlanificadorUiState.Idle

    val descargaPlanificadorState = planificadorViewModel
        ?.descargaPlanificadorState
        ?.collectAsState()
        ?.value ?: DescargaPlanificadorUiState.Idle

    val totalPlanesCuenta = planificadorViewModel
        ?.totalPlanesCuenta
        ?.collectAsState()
        ?.value ?: 0

    val totalEliminacionesPlanCuenta = planificadorViewModel
        ?.totalEliminacionesPlanCuenta
        ?.collectAsState()
        ?.value ?: 0

    var mostrarConfirmacionSubidaListaCompras by remember { mutableStateOf(false) }
    var mostrarConfirmacionDescargaListaCompras by remember { mutableStateOf(false) }

    val subidaListaComprasState = listaComprasViewModel
        ?.subidaListaComprasState
        ?.collectAsState()
        ?.value ?: SubidaListaComprasUiState.Idle

    val descargaListaComprasState = listaComprasViewModel
        ?.descargaListaComprasState
        ?.collectAsState()
        ?.value ?: DescargaListaComprasUiState.Idle

    val itemsCompraCuenta = listaComprasViewModel
        ?.itemsCompra
        ?.collectAsState()
        ?.value
        .orEmpty()

    val totalEliminacionesCompraCuenta = listaComprasViewModel
        ?.totalEliminacionesCompraCuenta
        ?.collectAsState()
        ?.value ?: 0

    // Confirmación antes de subir la lista de compras a Cloud Firestore
    if (mostrarConfirmacionSubidaListaCompras) {
        val pendientesCompra = itemsCompraCuenta.count { it.pendienteSincronizacion }
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionSubidaListaCompras = false },
            title = { Text("Subir mi lista de compras a la nube") },
            text = {
                Text(
                    "Tienes ${itemsCompraCuenta.size} producto(s) en tu lista de compras" +
                        (if (pendientesCompra > 0) " ($pendientesCompra cambio(s) pendiente(s))" else "") +
                        (if (totalEliminacionesCompraCuenta > 0) " y $totalEliminacionesCompraCuenta eliminación(es) pendiente(s)" else "") +
                        ".\n\n" +
                        "• Se guardarán en Cloud Firestore conservando su identificador único.\n" +
                        "• Se sincronizarán ediciones, productos marcados como comprados y eliminaciones verificando conflictos.\n" +
                        "• No se modificará Mi Despensa ni se generarán movimientos de compra.\n\n" +
                        "¿Deseas confirmar la subida?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionSubidaListaCompras = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            listaComprasViewModel?.subirMiListaComprasALaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionSubidaListaCompras = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación antes de recuperar la lista de compras desde Cloud Firestore
    if (mostrarConfirmacionDescargaListaCompras) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionDescargaListaCompras = false },
            title = { Text("Recuperar mi lista de compras de la nube") },
            text = {
                Text(
                    "Se consultarán los productos de tu lista de compras en Cloud Firestore para tu cuenta actual.\n\n" +
                        "• No se sobrescribirán productos locales con cambios pendientes de subir.\n" +
                        "• Los productos eliminados o limpiados localmente no volverán a insertarse.\n" +
                        "• Se evitarán duplicados comparando cada producto por su remoteId.\n" +
                        "• No se modificará Mi Despensa ni se generarán movimientos de compra.\n\n" +
                        "¿Deseas confirmar la recuperación?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionDescargaListaCompras = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            listaComprasViewModel?.recuperarMiListaComprasDeLaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionDescargaListaCompras = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Resultado o error de la subida de la lista de compras
    when (val estadoSubidaLC = subidaListaComprasState) {
        is SubidaListaComprasUiState.Resultado -> {
            val res = estadoSubidaLC.resumen
            AlertDialog(
                onDismissRequest = { listaComprasViewModel?.limpiarEstadoSubidaListaCompras() },
                title = {
                    Text(
                        if (res.errores > 0 || res.conflictos > 0) "Subida de lista de compras con observaciones"
                        else "Resultado de subida de lista de compras"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Operaciones evaluadas: ${res.total}")
                        Text("• Subidos / actualizados: ${res.subidos}")
                        Text("• Eliminaciones sincronizadas: ${res.eliminados}")
                        Text("• Omitidos (ya existían sin cambios): ${res.omitidos}")
                        Text("• Conflictos detectados: ${res.conflictos}")
                        Text("• Errores: ${res.errores}")
                        if ((res.errores > 0 || res.conflictos > 0) && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { listaComprasViewModel?.limpiarEstadoSubidaListaCompras() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is SubidaListaComprasUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { listaComprasViewModel?.limpiarEstadoSubidaListaCompras() },
                title = { Text("No se pudo subir la lista de compras") },
                text = { Text(estadoSubidaLC.mensaje) },
                confirmButton = {
                    TextButton(onClick = { listaComprasViewModel?.limpiarEstadoSubidaListaCompras() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    // Resultado o error de la recuperación de la lista de compras
    when (val estadoDescargaLC = descargaListaComprasState) {
        is DescargaListaComprasUiState.Resultado -> {
            val res = estadoDescargaLC.resumen
            AlertDialog(
                onDismissRequest = { listaComprasViewModel?.limpiarEstadoDescargaListaCompras() },
                title = {
                    Text(
                        if (res.errores > 0 || res.conflictos > 0) "Recuperación de lista de compras con observaciones"
                        else "Resultado de recuperación de lista de compras"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Productos encontrados en tu nube: ${res.totalRemotos}")
                        Text("• Descargados / actualizados: ${res.descargados}")
                        Text("• Omitidos (ya existían o eliminados localmente): ${res.omitidos}")
                        Text("• Conflictos (cambios locales protegidos): ${res.conflictos}")
                        Text("• Errores: ${res.errores}")
                        if ((res.errores > 0 || res.conflictos > 0) && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { listaComprasViewModel?.limpiarEstadoDescargaListaCompras() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is DescargaListaComprasUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { listaComprasViewModel?.limpiarEstadoDescargaListaCompras() },
                title = { Text("No se pudo recuperar la lista de compras") },
                text = { Text(estadoDescargaLC.mensaje) },
                confirmButton = {
                    TextButton(onClick = { listaComprasViewModel?.limpiarEstadoDescargaListaCompras() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    // Confirmación antes de subir el planificador semanal a Cloud Firestore
    if (mostrarConfirmacionSubidaPlanificador) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionSubidaPlanificador = false },
            title = { Text("Subir mi planificador a la nube") },
            text = {
                Text(
                    "Tienes $totalPlanesCuenta comida(s) planificada(s)" +
                        (if (totalEliminacionesPlanCuenta > 0) " y $totalEliminacionesPlanCuenta eliminación(es) pendiente(s)" else "") +
                        " en tu cuenta actual.\n\n" +
                        "• Se guardarán en Cloud Firestore junto con sus ingredientes y cantidades.\n" +
                        "• Se sincronizarán las ediciones y eliminaciones pendientes verificando conflictos.\n" +
                        "• No se descontará stock ni se generarán movimientos.\n\n" +
                        "¿Deseas confirmar la subida?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionSubidaPlanificador = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            planificadorViewModel?.subirMiPlanificadorALaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionSubidaPlanificador = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación antes de recuperar el planificador semanal desde Cloud Firestore
    if (mostrarConfirmacionDescargaPlanificador) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionDescargaPlanificador = false },
            title = { Text("Recuperar mi planificador de la nube") },
            text = {
                Text(
                    "Se consultarán las comidas planificadas en Cloud Firestore para tu cuenta actual.\n\n" +
                        "• No se sobrescribirán planes locales con cambios pendientes de subir.\n" +
                        "• Los planes eliminados localmente no volverán a insertarse.\n" +
                        "• No se duplicarán comidas en la misma semana, día y tipo.\n" +
                        "• No se descontará stock ni se generarán movimientos.\n\n" +
                        "¿Deseas confirmar la recuperación?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionDescargaPlanificador = false
                        if (!authViewModel.haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                            mensajeSinSesion = "No hay una sesión activa en Firebase. Inicia sesión primero."
                        } else {
                            planificadorViewModel?.recuperarMiPlanificadorDeLaNube()
                        }
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionDescargaPlanificador = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Resultado o error de la subida del planificador semanal
    when (val estadoSubidaP = subidaPlanificadorState) {
        is SubidaPlanificadorUiState.Resultado -> {
            val res = estadoSubidaP.resumen
            AlertDialog(
                onDismissRequest = { planificadorViewModel?.limpiarEstadoSubidaPlanificador() },
                title = {
                    Text(
                        if (res.errores > 0 || res.conflictos > 0) "Subida de planificador con observaciones"
                        else "Resultado de subida del planificador"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Operaciones evaluadas: ${res.total}")
                        Text("• Subidos / actualizados: ${res.subidos}")
                        Text("• Eliminaciones sincronizadas: ${res.eliminados}")
                        Text("• Omitidos (ya existían sin cambios): ${res.omitidos}")
                        Text("• Conflictos detectados: ${res.conflictos}")
                        Text("• Errores: ${res.errores}")
                        if ((res.errores > 0 || res.conflictos > 0) && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { planificadorViewModel?.limpiarEstadoSubidaPlanificador() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is SubidaPlanificadorUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { planificadorViewModel?.limpiarEstadoSubidaPlanificador() },
                title = { Text("No se pudo subir el planificador") },
                text = { Text(estadoSubidaP.mensaje) },
                confirmButton = {
                    TextButton(onClick = { planificadorViewModel?.limpiarEstadoSubidaPlanificador() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

    // Resultado o error de la recuperación del planificador semanal
    when (val estadoDescargaP = descargaPlanificadorState) {
        is DescargaPlanificadorUiState.Resultado -> {
            val res = estadoDescargaP.resumen
            AlertDialog(
                onDismissRequest = { planificadorViewModel?.limpiarEstadoDescargaPlanificador() },
                title = {
                    Text(
                        if (res.errores > 0 || res.conflictos > 0) "Recuperación de planificador con observaciones"
                        else "Resultado de recuperación del planificador"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Planes encontrados en tu nube: ${res.totalRemotos}")
                        Text("• Descargados / actualizados: ${res.descargados}")
                        Text("• Omitidos (ya existían o eliminados localmente): ${res.omitidos}")
                        Text("• Conflictos (cambios locales protegidos): ${res.conflictos}")
                        Text("• Errores: ${res.errores}")
                        if ((res.errores > 0 || res.conflictos > 0) && !res.detalleError.isNullOrBlank()) {
                            Text(
                                text = "Detalle: ${res.detalleError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { planificadorViewModel?.limpiarEstadoDescargaPlanificador() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is DescargaPlanificadorUiState.ErrorBloqueante -> {
            AlertDialog(
                onDismissRequest = { planificadorViewModel?.limpiarEstadoDescargaPlanificador() },
                title = { Text("No se pudo recuperar el planificador") },
                text = { Text(estadoDescargaP.mensaje) },
                confirmButton = {
                    TextButton(onClick = { planificadorViewModel?.limpiarEstadoDescargaPlanificador() }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> Unit
    }

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
                    descargaHistorialState is DescargaHistorialUiState.Descargando ||
                    subidaPlanificadorState is SubidaPlanificadorUiState.Subiendo ||
                    descargaPlanificadorState is DescargaPlanificadorUiState.Descargando ||
                    subidaListaComprasState is SubidaListaComprasUiState.Subiendo ||
                    descargaListaComprasState is DescargaListaComprasUiState.Descargando

            if (subidaListaComprasState is SubidaListaComprasUiState.Subiendo) {
                val progresoLC = if (subidaListaComprasState.total > 0) {
                    subidaListaComprasState.procesados.toFloat() / subidaListaComprasState.total.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progresoLC },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Subiendo lista de compras (${subidaListaComprasState.procesados}/${subidaListaComprasState.total})...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (descargaListaComprasState is DescargaListaComprasUiState.Descargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recuperando lista de compras desde la nube...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (subidaPlanificadorState is SubidaPlanificadorUiState.Subiendo) {
                val progresoP = if (subidaPlanificadorState.total > 0) {
                    subidaPlanificadorState.procesados.toFloat() / subidaPlanificadorState.total.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progresoP },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Subiendo planificador (${subidaPlanificadorState.procesados}/${subidaPlanificadorState.total})...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (descargaPlanificadorState is DescargaPlanificadorUiState.Descargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recuperando planificador desde la nube...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

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

            // Botón manual de subida del planificador semanal a Cloud Firestore
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para subir tu planificador."
                    } else {
                        mostrarConfirmacionSubidaPlanificador = true
                    }
                },
                enabled = !operacionNubeEnCurso && planificadorViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir mi planificador a la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de recuperación del planificador semanal desde Cloud Firestore
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para recuperar tu planificador."
                    } else {
                        mostrarConfirmacionDescargaPlanificador = true
                    }
                },
                enabled = !operacionNubeEnCurso && planificadorViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recuperar mi planificador de la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de subida de la lista de compras a Cloud Firestore
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para subir tu lista de compras."
                    } else {
                        mostrarConfirmacionSubidaListaCompras = true
                    }
                },
                enabled = !operacionNubeEnCurso && listaComprasViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir mi lista de compras a la nube")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón manual de recuperación de la lista de compras desde Cloud Firestore
            OutlinedButton(
                onClick = {
                    if (!haySesionActiva || authViewModel.usuarioActual?.uid.isNullOrBlank()) {
                        mensajeSinSesion = "No hay una sesión activa. Inicia sesión para recuperar tu lista de compras."
                    } else {
                        mostrarConfirmacionDescargaListaCompras = true
                    }
                },
                enabled = !operacionNubeEnCurso && listaComprasViewModel != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recuperar mi lista de compras de la nube")
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
