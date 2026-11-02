package com.example.ecodespensa.ui.pantry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.util.calcularEstado
import com.example.ecodespensa.util.diasParaVencer
import java.time.LocalDate

/**
 * Pantalla Mi Despensa.
 * Muestra la lista de alimentos persistidos con Room, ordenados por fecha de vencimiento.
 * Permite consumir, desperdiciar, ajustar stock o editar datos generales de cada alimento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiDespensaScreen(
    viewModel: AlimentoViewModel,
    onAgregarAlimento: () -> Unit,
    onVolver: () -> Unit
) {
    val alimentos by viewModel.alimentos.collectAsState()

    // ── Estado diálogos de movimiento ──────────────────────────────────────────
    var dialogAlimento by remember { mutableStateOf<Alimento?>(null) }
    var dialogTipo by remember { mutableStateOf("") }
    var cantidadTexto by remember { mutableStateOf("") }
    var errorStock by remember { mutableStateOf(false) }

    // ── Estado diálogo de edición ──────────────────────────────────────────────
    var editandoAlimento by remember { mutableStateOf<Alimento?>(null) }
    var editNombre by remember { mutableStateOf("") }
    var editCategoria by remember { mutableStateOf("") }
    var editCantidad by remember { mutableStateOf("") }
    var editUnidad by remember { mutableStateOf("") }
    var editFechaCompra by remember { mutableStateOf("") }
    var editFechaVencimiento by remember { mutableStateOf("") }
    var editErrorNombre by remember { mutableStateOf(false) }
    var editErrorCantidad by remember { mutableStateOf(false) }

    fun cerrarDialog() { dialogAlimento = null; cantidadTexto = ""; errorStock = false }

    fun cerrarEdicion() {
        editandoAlimento = null
        editNombre = ""; editCategoria = ""; editCantidad = ""
        editUnidad = ""; editFechaCompra = ""; editFechaVencimiento = ""
        editErrorNombre = false; editErrorCantidad = false
    }

    fun abrirEdicion(alimento: Alimento) {
        editandoAlimento = alimento
        editNombre = alimento.nombre
        editCategoria = alimento.categoria
        editCantidad = alimento.cantidadActual
        editUnidad = alimento.unidad
        editFechaCompra = alimento.fechaCompra
        editFechaVencimiento = alimento.fechaVencimiento
        editErrorNombre = false; editErrorCantidad = false
    }

    // ── Diálogo movimiento (consumir / desperdiciar / ajustar) ─────────────────
    if (dialogAlimento != null) {
        val titulo = when (dialogTipo) {
            "consumir"     -> "Consumir"
            "desperdiciar" -> "Desperdiciar"
            else           -> "Ajustar stock"
        }
        AlertDialog(
            onDismissRequest = { cerrarDialog() },
            title = { Text(text = titulo, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Alimento: ${dialogAlimento!!.nombre}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Stock actual: ${dialogAlimento!!.cantidadActual} ${dialogAlimento!!.unidad}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dialogTipo == "ajustar") {
                        Text(
                            text = "Ingresa la cantidad adicional a agregar al stock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cantidadTexto,
                        onValueChange = { cantidadTexto = it; errorStock = false },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = errorStock,
                        supportingText = if (errorStock) {
                            {
                                Text(
                                    if (dialogTipo == "ajustar")
                                        "La cantidad debe ser mayor a 0"
                                    else
                                        "Cantidad inválida o mayor al stock",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cantidad = cantidadTexto.replace(',', '.').toDoubleOrNull()
                    // cantidadActual es String y puede contener la unidad (ej: "4 L") o usar
                    // coma como separador decimal (locale es). Se extrae solo la parte numérica.
                    val stockRaw = dialogAlimento!!.cantidadActual
                        .replace(',', '.')          // normalizar separador decimal
                        .trim()
                        .split(Regex("\\s+"))       // separar número de unidad
                        .firstOrNull() ?: ""
                    val stock = stockRaw.toDoubleOrNull() ?: 0.0
                    when (dialogTipo) {
                        "consumir" -> {
                            if (cantidad == null || cantidad <= 0 || cantidad > stock) {
                                errorStock = true; return@TextButton
                            }
                            viewModel.consumir(dialogAlimento!!, cantidad)
                        }
                        "desperdiciar" -> {
                            if (cantidad == null || cantidad <= 0 || cantidad > stock) {
                                errorStock = true; return@TextButton
                            }
                            viewModel.desperdiciar(dialogAlimento!!, cantidad)
                        }
                        "ajustar" -> {
                            if (cantidad == null || cantidad <= 0) {
                                errorStock = true; return@TextButton
                            }
                            viewModel.ajustarStock(dialogAlimento!!, cantidad)
                        }
                    }
                    cerrarDialog()
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { cerrarDialog() }) { Text("Cancelar") }
            }
        )
    }

    // ── Diálogo de edición ─────────────────────────────────────────────────────
    if (editandoAlimento != null) {
        AlertDialog(
            onDismissRequest = { cerrarEdicion() },
            title = { Text("Editar alimento", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editNombre,
                        onValueChange = { editNombre = it; editErrorNombre = false },
                        label = { Text("Nombre*") },
                        singleLine = true,
                        isError = editErrorNombre,
                        supportingText = if (editErrorNombre) {
                            { Text("El nombre es obligatorio", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCategoria,
                        onValueChange = { editCategoria = it },
                        label = { Text("Categoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCantidad,
                        onValueChange = { editCantidad = it; editErrorCantidad = false },
                        label = { Text("Cantidad actual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = editErrorCantidad,
                        supportingText = if (editErrorCantidad) {
                            { Text("La cantidad no puede ser negativa", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUnidad,
                        onValueChange = { editUnidad = it },
                        label = { Text("Unidad (ej. g, ml, piezas)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFechaCompra,
                        onValueChange = { editFechaCompra = it },
                        label = { Text("Fecha de compra (dd/mm/aaaa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFechaVencimiento,
                        onValueChange = { editFechaVencimiento = it },
                        label = { Text("Fecha de vencimiento (dd/mm/aaaa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Validar nombre
                    if (editNombre.isBlank()) { editErrorNombre = true; return@TextButton }
                    // Validar cantidad >= 0
                    val nuevaCantidad = editCantidad
                        .replace(',', '.')
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.toDoubleOrNull() ?: 0.0
                    if (nuevaCantidad < 0) { editErrorCantidad = true; return@TextButton }

                    val cantidadAnterior = editandoAlimento!!.cantidadActual
                        .replace(',', '.')
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.toDoubleOrNull() ?: 0.0

                    val alimentoActualizado = editandoAlimento!!.copy(
                        nombre = editNombre.trim(),
                        categoria = editCategoria.trim(),
                        cantidadActual = editCantidad.trim(),
                        unidad = editUnidad.trim(),
                        fechaCompra = editFechaCompra.trim(),
                        fechaVencimiento = editFechaVencimiento.trim()
                    )
                    viewModel.editarAlimento(
                        alimento = alimentoActualizado,
                        cantidadAnterior = cantidadAnterior,
                        onError = { editErrorCantidad = true }
                    )
                    cerrarEdicion()
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { cerrarEdicion() }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Despensa") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAgregarAlimento,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar alimento"
                    )
                },
                text = { Text("Agregar alimento") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Alimentos disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (alimentos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tu despensa está vacía.\nAgrega tu primer alimento.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(alimentos, key = { it.id }) { alimento ->
                        AlimentoCard(
                            alimento = alimento,
                            onEliminar = { viewModel.eliminar(alimento.id) },
                            onEditar = { abrirEdicion(alimento) },
                            onConsumir = {
                                dialogAlimento = alimento
                                dialogTipo = "consumir"
                                cantidadTexto = ""
                                errorStock = false
                            },
                            onDesperdiciar = {
                                dialogAlimento = alimento
                                dialogTipo = "desperdiciar"
                                cantidadTexto = ""
                                errorStock = false
                            },
                            onAjustar = {
                                dialogAlimento = alimento
                                dialogTipo = "ajustar"
                                cantidadTexto = ""
                                errorStock = false
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AlimentoCard(
    alimento: Alimento,
    onEliminar: () -> Unit,
    onEditar: () -> Unit,
    onConsumir: () -> Unit,
    onDesperdiciar: () -> Unit,
    onAjustar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alimento.nombre,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (alimento.categoria.isNotBlank()) {
                        Text(
                            text = alimento.categoria,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val cantidad = buildString {
                        if (alimento.cantidadActual.isNotBlank()) append(alimento.cantidadActual)
                        if (alimento.unidad.isNotBlank()) append(" ${alimento.unidad}")
                    }
                    if (cantidad.isNotBlank()) {
                        Text(
                            text = cantidad,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (alimento.fechaVencimiento.isNotBlank()) {
                        val hoy = LocalDate.now()
                        val estado = calcularEstado(alimento, hoy)
                        val dias = diasParaVencer(alimento, hoy)
                        val diasTexto = when {
                            dias == null -> alimento.fechaVencimiento
                            dias < 0 -> "Venció hace ${-dias} día(s)"
                            dias == 0L -> "Vence hoy"
                            dias == 1L -> "Vence mañana"
                            else -> "Vence en $dias días"
                        }
                        val (bgColor, txtColor, label) = when {
                            estado == EstadoVencimiento.VENCIDO -> Triple(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                "VENCIDO"
                            )
                            dias == 0L -> Triple(
                                Color(0xFFFF6B35),
                                Color(0xFFFFFFFF),
                                "VENCE HOY"
                            )
                            estado == EstadoVencimiento.PROXIMO -> Triple(
                                Color(0xFFFFF3CD),
                                Color(0xFF856404),
                                "PRÓXIMO A VENCER"
                            )
                            else -> Triple(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                "NORMAL"
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = bgColor
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = txtColor
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = diasTexto,
                                style = MaterialTheme.typography.bodySmall,
                                color = txtColor
                            )
                        }
                    }
                }
                // Ícono Editar
                IconButton(onClick = onEditar) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar ${alimento.nombre}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Ícono Eliminar
                IconButton(onClick = onEliminar) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar ${alimento.nombre}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Acciones de movimiento
            val hoyCard = LocalDate.now()
            val estadoCard = calcularEstado(alimento, hoyCard)
            val diasCard = diasParaVencer(alimento, hoyCard)
            // VENCIDO: dias < 0 (no incluye vence hoy, que tiene dias == 0)
            val esVencido = estadoCard == EstadoVencimiento.VENCIDO && (diasCard == null || diasCard < 0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!esVencido) {
                    TextButton(onClick = onConsumir) {
                        Text("Consumir", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDesperdiciar) {
                    Text("Desperdiciar", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onAjustar) {
                    Text("Ajustar", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
