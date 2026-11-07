package com.example.ecodespensa.ui.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.ItemCompra
import com.example.ecodespensa.data.model.OrigenItemCompra
import com.example.ecodespensa.data.model.SugerenciaCompraAutomatica

/**
 * Pantalla funcional de la Lista Inteligente de Compras.
 *
 * - Conecta el Planificador Semanal con el stock vigente de Mi Despensa para sugerir
 *   únicamente las cantidades faltantes agrupadas por ingrediente y unidad compatible.
 * - Diferencia claramente las sugerencias automáticas de los productos de la lista del usuario
 *   (manuales o agregados desde sugerencia).
 * - Permite agregar, editar, marcar como comprado y eliminar productos sin alterar el stock
 *   de Mi Despensa ni generar movimientos COMPRA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaComprasScreen(
    viewModel: ListaComprasViewModel,
    onVolver: () -> Unit
) {
    val itemsLista by viewModel.itemsCompra.collectAsState()
    val resumenSugerencias by viewModel.resumenSugerencias.collectAsState()
    val alimentosVigentes by viewModel.alimentosVigentesEnDespensa.collectAsState()

    var mostrarDialogoAgregar by remember { mutableStateOf(false) }
    var itemEnEdicion by remember { mutableStateOf<ItemCompra?>(null) }
    var itemAEliminar by remember { mutableStateOf<ItemCompra?>(null) }
    var confirmarLimpiarComprados by remember { mutableStateOf(false) }

    val cantidadComprados = remember(itemsLista) { itemsLista.count { it.comprado } }
    val sugerencias = resumenSugerencias.sugerencias
    val cubiertosCount = resumenSugerencias.ingredientesCubiertosPorDespensa

    // Diálogo para agregar producto manualmente
    if (mostrarDialogoAgregar) {
        DialogoItemCompra(
            existente = null,
            alimentosVigentes = alimentosVigentes,
            onDismiss = { mostrarDialogoAgregar = false },
            onGuardar = { nombre, cantidad, unidad, nota ->
                viewModel.agregarManual(
                    nombre = nombre,
                    cantidad = cantidad,
                    unidad = unidad,
                    nota = nota
                )
                mostrarDialogoAgregar = false
            }
        )
    }

    // Diálogo para editar producto existente
    itemEnEdicion?.let { item ->
        DialogoItemCompra(
            existente = item,
            alimentosVigentes = alimentosVigentes,
            onDismiss = { itemEnEdicion = null },
            onGuardar = { nombre, cantidad, unidad, nota ->
                viewModel.editarItem(
                    id = item.id,
                    nombre = nombre,
                    cantidad = cantidad,
                    unidad = unidad,
                    nota = nota
                )
                itemEnEdicion = null
            }
        )
    }

    // Confirmación para eliminar un producto individual
    itemAEliminar?.let { item ->
        AlertDialog(
            onDismissRequest = { itemAEliminar = null },
            title = { Text("Eliminar de la lista") },
            text = { Text("¿Deseas quitar \"${item.nombre}\" de tu lista de compras?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = item
                        itemAEliminar = null
                        viewModel.eliminarItem(target)
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemAEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmación para limpiar los productos ya marcados como comprados
    if (confirmarLimpiarComprados) {
        AlertDialog(
            onDismissRequest = { confirmarLimpiarComprados = false },
            title = { Text("Limpiar productos comprados") },
            text = {
                Text(
                    "Se quitarán de la lista los $cantidadComprados producto(s) marcados como comprados.\n\n" +
                        "Esto no modifica tu despensa ni tu historial."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarLimpiarComprados = false
                        viewModel.limpiarComprados()
                    }
                ) {
                    Text("Limpiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarLimpiarComprados = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Lista de compras",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sugerencias por faltantes y lista personal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (cantidadComprados > 0) {
                        IconButton(onClick = { confirmarLimpiarComprados = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar comprados"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarDialogoAgregar = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar producto") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Aviso de aprovechamiento de alimentos existentes en Mi Despensa
            if (cubiertosCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Aprovechando tu despensa: $cubiertosCount ingrediente(s) de tus comidas planificadas ya están cubiertos por tu stock actual y no requieren compra.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sección 1: Sugerencias automáticas calculadas desde el Planificador Semanal
            item {
                Column {
                    Text(
                        text = "Sugerencias automáticas del Planificador (${sugerencias.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Calculadas restando tu stock disponible en Mi Despensa a lo necesario en tus planes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sugerencias.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "No tienes ingredientes faltantes por comprar para tus comidas planificadas.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(sugerencias, key = { it.claveAgrupacion }) { sug ->
                    SugerenciaAutomaticaCard(
                        sugerencia = sug,
                        onAgregarALista = { viewModel.agregarSugerenciaALista(sug) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Text(
                        text = "Mi lista de compras (${itemsLista.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Marcar como comprado no altera automáticamente el stock de Mi Despensa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (itemsLista.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tu lista de compras está vacía.\nAgrega productos manualmente o incorpora una sugerencia de arriba.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(itemsLista, key = { it.id }) { item ->
                    ItemCompraCard(
                        item = item,
                        onCambiarComprado = { nuevoEstado ->
                            viewModel.cambiarComprado(item, nuevoEstado)
                        },
                        onEditar = { itemEnEdicion = item },
                        onEliminar = { itemAEliminar = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun SugerenciaAutomaticaCard(
    sugerencia: SugerenciaCompraAutomatica,
    onAgregarALista: () -> Unit
) {
    val cantFaltanteTexto = buildString {
        append(formatearCantidadVista(sugerencia.cantidadFaltante))
        if (sugerencia.unidad.isNotBlank()) append(" ${sugerencia.unidad}")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sugerencia.unidadesIncompatibles) {
                Color(0xFFFFF3CD)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${sugerencia.nombre} — Falta: $cantFaltanteTexto",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (sugerencia.unidadesIncompatibles) {
                            Color(0xFF856404)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Text(
                            text = if (sugerencia.unidadesIncompatibles) {
                                "SUGERENCIA • UNIDAD NO CONVERTIBLE"
                            } else {
                                "SUGERENCIA AUTOMÁTICA"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onAgregarALista,
                    enabled = !sugerencia.yaCubiertoEnLista,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (sugerencia.yaCubiertoEnLista) "Ya en tu lista" else "Agregar a lista",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Text(
                text = sugerencia.detalleExplicacion,
                style = MaterialTheme.typography.bodySmall,
                color = if (sugerencia.unidadesIncompatibles) {
                    Color(0xFF856404)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ItemCompraCard(
    item: ItemCompra,
    onCambiarComprado: (Boolean) -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit
) {
    val esDeSugerencia = item.origen == OrigenItemCompra.SUGERENCIA_PLANIFICADOR.name
    val cantidadFormateada = buildString {
        append(formatearCantidadVista(item.cantidad))
        if (item.unidad.isNotBlank()) append(" ${item.unidad}")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.comprado) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.comprado,
                onCheckedChange = onCambiarComprado
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${item.nombre} ($cantidadFormateada)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.comprado) TextDecoration.LineThrough else TextDecoration.None
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (esDeSugerencia) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    ) {
                        Text(
                            text = if (esDeSugerencia) "DESDE SUGERENCIA" else "MANUAL",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (esDeSugerencia) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onSecondary
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (item.comprado) {
                        Text(
                            text = "Comprado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (item.notaOrigen.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.notaOrigen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEditar) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar producto",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onEliminar) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar producto",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DialogoItemCompra(
    existente: ItemCompra?,
    alimentosVigentes: List<Alimento>,
    onDismiss: () -> Unit,
    onGuardar: (String, Double, String, String) -> Unit
) {
    var nombre by remember(existente) { mutableStateOf(existente?.nombre.orEmpty()) }
    var cantidadTexto by remember(existente) {
        mutableStateOf(existente?.let { formatearCantidadVista(it.cantidad) } ?: "1")
    }
    var unidad by remember(existente) { mutableStateOf(existente?.unidad.orEmpty()) }
    var nota by remember(existente) { mutableStateOf(existente?.notaOrigen.orEmpty()) }
    var errorValidacion by remember { mutableStateOf<String?>(null) }

    val coincidenciaDespensa = remember(nombre, alimentosVigentes) {
        val limpio = nombre.trim()
        if (limpio.length < 2) null
        else alimentosVigentes.firstOrNull { it.nombre.trim().equals(limpio, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existente == null) "Agregar producto a la lista" else "Editar producto de la lista")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        errorValidacion = null
                    },
                    label = { Text("Producto *") },
                    placeholder = { Text("Ej. Leche, Arroz, Tomates...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Aviso inteligente si ya existe stock disponible en Mi Despensa
                if (coincidenciaDespensa != null) {
                    val stockDisp = buildString {
                        append(coincidenciaDespensa.cantidadActual)
                        if (coincidenciaDespensa.unidad.isNotBlank()) {
                            append(" ${coincidenciaDespensa.unidad}")
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3CD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Ya tienes $stockDisp de ${coincidenciaDespensa.nombre} disponible en Mi Despensa. Prioriza aprovecharlo antes de comprar más.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF856404)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cantidadTexto,
                        onValueChange = {
                            cantidadTexto = it
                            errorValidacion = null
                        },
                        label = { Text("Cantidad *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unidad,
                        onValueChange = { unidad = it },
                        label = { Text("Unidad") },
                        placeholder = { Text("kg, L, unid.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Nota opcional") },
                    placeholder = { Text("Ej. Marca o detalle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorValidacion != null) {
                    Text(
                        text = errorValidacion!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nombreLimpio = nombre.trim()
                    if (nombreLimpio.isEmpty()) {
                        errorValidacion = "Ingresa el nombre del producto."
                        return@TextButton
                    }
                    val cant = cantidadTexto.replace(',', '.').trim().toDoubleOrNull()
                    if (cant == null || !cant.isFinite() || cant <= 0.0) {
                        errorValidacion = "Ingresa una cantidad válida mayor a 0."
                        return@TextButton
                    }
                    onGuardar(nombreLimpio, cant, unidad.trim(), nota.trim())
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatearCantidadVista(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
