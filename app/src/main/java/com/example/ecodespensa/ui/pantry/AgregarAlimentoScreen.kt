package com.example.ecodespensa.ui.pantry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.ml.ResultadoReconocimientoAlimento

/**
 * Pantalla Agregar Alimento.
 * Permite registrar un nuevo alimento y persistirlo mediante [AlimentoViewModel].
 *
 * Incluye el botón "Reconocer alimento con cámara" (Etapa 7A) que abre la vista previa
 * de CameraX conservando todos los campos ya escritos en el formulario ante navegación,
 * recomposición o recreación de pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarAlimentoScreen(
    viewModel: AlimentoViewModel,
    onGuardar: () -> Unit,
    onVolver: () -> Unit,
    onAbrirCamara: () -> Unit = {}
) {
    val borrador by viewModel.formularioAgregarState.collectAsState()

    var nombre by rememberSaveable { mutableStateOf(borrador.nombre) }
    var categoria by rememberSaveable { mutableStateOf(borrador.categoria) }
    var cantidadActual by rememberSaveable { mutableStateOf(borrador.cantidadActual) }
    var unidad by rememberSaveable { mutableStateOf(borrador.unidad) }
    var fechaCompra by rememberSaveable { mutableStateOf(borrador.fechaCompra) }
    var fechaVencimiento by rememberSaveable { mutableStateOf(borrador.fechaVencimiento) }

    // Sincronizar los campos locales cuando el ViewModel autocompleta o confirma un reemplazo
    LaunchedEffect(borrador.versionSincronizacionCampos) {
        nombre = borrador.nombre
        categoria = borrador.categoria
        cantidadActual = borrador.cantidadActual
        unidad = borrador.unidad
        fechaCompra = borrador.fechaCompra
        fechaVencimiento = borrador.fechaVencimiento
    }

    val abrirCamaraGuardandoBorrador = {
        viewModel.actualizarBorradorAgregarAlimento(
            nombre = nombre,
            categoria = categoria,
            cantidadActual = cantidadActual,
            unidad = unidad,
            fechaCompra = fechaCompra,
            fechaVencimiento = fechaVencimiento
        )
        onAbrirCamara()
    }

    // Confirmación antes de sobrescribir si el usuario ya había escrito nombre o categoría
    borrador.sugerenciaPendienteConfirmacion?.let { sugerencia ->
        val categoriaDetectada = sugerencia.categoriaSugerida?.takeIf { it.isNotBlank() } ?: "Fruta"
        AlertDialog(
            onDismissRequest = { viewModel.descartarReemplazoPorReconocimiento() },
            title = { Text("Se detectó: ${sugerencia.etiqueta}") },
            text = {
                Text(
                    "Ya habías escrito datos en Nombre o Categoría antes de usar la cámara.\n\n" +
                        "¿Deseas reemplazarlos automáticamente por:\n" +
                        "• Nombre: ${sugerencia.etiqueta}\n" +
                        "• Categoría: $categoriaDetectada?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmarReemplazoPorReconocimiento() }
                ) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.descartarReemplazoPorReconocimiento() }
                ) {
                    Text("Conservar mis datos")
                }
            }
        )
    }

    val salirLimpiandoBorrador = {
        viewModel.limpiarBorradorAgregarAlimento()
        onVolver()
    }

    BackHandler {
        salirLimpiandoBorrador()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar alimento") },
                navigationIcon = {
                    IconButton(onClick = salirLimpiandoBorrador) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Datos del alimento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Botón "Reconocer alimento con cámara" (Etapa 7A)
            FilledTonalButton(
                onClick = abrirCamaraGuardandoBorrador,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reconocer alimento con cámara")
            }

            // Tarjeta con la fotografía capturada y el resultado simplificado del reconocimiento
            borrador.imagenCapturada?.let { captura ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = captura.bitmap.asImageBitmap(),
                                contentDescription = "Fotografía del alimento capturada",
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (borrador.analizandoImagen) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Analizando fotografía...",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    when (val resultado = borrador.resultadoReconocimiento) {
                                        is ResultadoReconocimientoAlimento.Exito -> {
                                            Text(
                                                text = "Se detectó: ${resultado.prediccionPrincipal.etiqueta}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Puedes editar el nombre y la categoría si lo necesitas.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is ResultadoReconocimientoAlimento.BajaConfianza -> {
                                            Text(
                                                text = "No se pudo identificar el alimento. Ingresa sus datos manualmente",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        is ResultadoReconocimientoAlimento.SinModeloConfigurado -> {
                                            Text(
                                                text = resultado.mensaje,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is ResultadoReconocimientoAlimento.Error -> {
                                            Text(
                                                text = resultado.mensaje,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        null -> {
                                            Text(
                                                text = "Fotografía capturada",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = abrirCamaraGuardandoBorrador,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Repetir foto")
                            }

                            OutlinedButton(
                                onClick = { viewModel.descartarFotoCapturadaFormulario() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eliminar foto")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it
                    viewModel.actualizarBorradorAgregarAlimento(nombre = it)
                },
                label = { Text("Nombre del alimento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = categoria,
                onValueChange = {
                    categoria = it
                    viewModel.actualizarBorradorAgregarAlimento(categoria = it)
                },
                label = { Text("Categoría (ej. Lácteo, Fruta, Vegetal)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = cantidadActual,
                onValueChange = {
                    cantidadActual = it
                    viewModel.actualizarBorradorAgregarAlimento(cantidadActual = it)
                },
                label = { Text("Cantidad actual (ej. 500)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = unidad,
                onValueChange = {
                    unidad = it
                    viewModel.actualizarBorradorAgregarAlimento(unidad = it)
                },
                label = { Text("Unidad (ej. g, ml, piezas)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fechaCompra,
                onValueChange = {
                    fechaCompra = it
                    viewModel.actualizarBorradorAgregarAlimento(fechaCompra = it)
                },
                label = { Text("Fecha de compra (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fechaVencimiento,
                onValueChange = {
                    fechaVencimiento = it
                    viewModel.actualizarBorradorAgregarAlimento(fechaVencimiento = it)
                },
                label = { Text("Fecha de vencimiento (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (nombre.isNotBlank()) {
                        viewModel.agregar(
                            Alimento(
                                nombre = nombre.trim(),
                                categoria = categoria.trim(),
                                cantidadActual = cantidadActual.trim(),
                                unidad = unidad.trim(),
                                fechaCompra = fechaCompra.trim(),
                                fechaVencimiento = fechaVencimiento.trim()
                            )
                        )
                        viewModel.limpiarBorradorAgregarAlimento()
                        onGuardar()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar alimento")
            }
        }
    }
}
