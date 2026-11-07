package com.example.ecodespensa.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.DiaSemana
import com.example.ecodespensa.data.model.EstadoVencimiento
import com.example.ecodespensa.data.model.IngredientePlanificado
import com.example.ecodespensa.data.model.PlanComidaConIngredientes
import com.example.ecodespensa.data.model.TipoComida
import com.example.ecodespensa.util.calcularEstado
import com.example.ecodespensa.util.diasParaVencer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private data class CasillaSeleccionada(
    val dia: DiaSemana,
    val tipo: TipoComida,
    val existente: PlanComidaConIngredientes?
)

private data class BorradorIngrediente(
    val remoteId: String,
    val alimentoRemoteId: String,
    val alimentoNombre: String,
    val cantidadTexto: String,
    val unidad: String
)

/**
 * Pantalla funcional del Planificador Semanal de Comidas (Etapas 5A + 5B).
 *
 * - Permite navegar entre semanas sin mezclar comidas (`fechaInicioSemana`).
 * - Muestra de Lunes a Domingo con casillas para DESAYUNO, ALMUERZO y CENA.
 * - Permite crear, editar y eliminar comidas con nombre del plato y múltiples ingredientes
 *   opcionales de Mi Despensa (con cantidad y unidad previstas).
 * - Prioriza en primer lugar los alimentos próximos a vencer con stock disponible y distingue
 *   los vencidos o agotados para no sugerirlos como disponibles.
 * - Planificar NO descuenta stock ni genera movimientos de consumo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanificadorSemanalScreen(
    viewModel: PlanificadorViewModel,
    onVolver: () -> Unit
) {
    val lunesSeleccionado by viewModel.lunesSemanaSeleccionada.collectAsState()
    val planesSemana by viewModel.planesSemana.collectAsState()
    val alimentosDisponibles by viewModel.alimentosDisponiblesPriorizados.collectAsState()
    val alimentosNoDisponibles by viewModel.alimentosVencidosOSinStock.collectAsState()

    val domingoSeleccionado = remember(lunesSeleccionado) { lunesSeleccionado.plusDays(6) }
    val formatoCorto = remember { DateTimeFormatter.ofPattern("dd/MM") }
    val formatoCompleto = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val esSemanaActual = remember(lunesSeleccionado) {
        lunesSeleccionado == LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    // Reiniciar diálogos si cambia la semana seleccionada
    var casillaEnEdicion by remember(lunesSeleccionado) { mutableStateOf<CasillaSeleccionada?>(null) }
    var planAEliminar by remember(lunesSeleccionado) { mutableStateOf<PlanComidaConIngredientes?>(null) }

    val mapaPlanes = remember(planesSemana) {
        planesSemana.associateBy { Pair(it.plan.diaSemana, it.plan.tipoComida) }
    }

    val totalComidasSemana = planesSemana.size

    // Diálogo de confirmación para eliminar una comida planificada
    planAEliminar?.let { item ->
        val diaEtiqueta = DiaSemana.fromNumero(item.plan.diaSemana).etiqueta
        val tipoEtiqueta = TipoComida.fromNombre(item.plan.tipoComida).etiqueta
        AlertDialog(
            onDismissRequest = { planAEliminar = null },
            title = { Text("Eliminar comida planificada") },
            text = {
                Text(
                    "¿Deseas quitar \"${item.plan.nombrePlato}\" del $tipoEtiqueta del $diaEtiqueta?\n\n" +
                        "Esto no afectará el stock de tu despensa."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idRemoto = item.plan.remoteId
                        planAEliminar = null
                        casillaEnEdicion = null
                        viewModel.eliminarComida(idRemoto)
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { planAEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para crear o editar una comida planificada y sus ingredientes
    casillaEnEdicion?.let { seleccion ->
        DialogoPlanificarComida(
            dia = seleccion.dia,
            fechaDia = lunesSeleccionado.plusDays((seleccion.dia.numero - 1).toLong()).format(formatoCompleto),
            tipo = seleccion.tipo,
            existente = seleccion.existente,
            alimentosDisponibles = alimentosDisponibles,
            alimentosNoDisponibles = alimentosNoDisponibles,
            onDismiss = { casillaEnEdicion = null },
            onSolicitarEliminar = { item ->
                planAEliminar = item
            },
            onGuardar = { nombrePlato, ingredientes ->
                viewModel.guardarComida(
                    diaSemana = seleccion.dia,
                    tipoComida = seleccion.tipo,
                    nombrePlato = nombrePlato,
                    ingredientes = ingredientes,
                    planExistente = seleccion.existente?.plan,
                    onGuardado = { casillaEnEdicion = null }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Planificador semanal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Desayuno, almuerzo y cena sin descontar stock",
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
                    if (!esSemanaActual) {
                        IconButton(
                            onClick = { viewModel.irASemanaActual() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "Ir a la semana actual"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Selector de semana (navegación entre semanas por fechaInicioSemana)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { viewModel.semanaAnterior() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Semana anterior"
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Semana ${lunesSeleccionado.format(formatoCorto)} al ${domingoSeleccionado.format(formatoCompleto)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (esSemanaActual) {
                                        "Semana actual • $totalComidasSemana de 21 comidas planificadas"
                                    } else {
                                        "$totalComidasSemana de 21 comidas planificadas"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { viewModel.semanaSiguiente() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Semana siguiente"
                                )
                            }
                        }

                        if (!esSemanaActual) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.irASemanaActual() },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Volver a la semana actual")
                            }
                        }
                    }
                }
            }

            // Aviso de alimentos próximos a vencer sugeridos para esta semana
            item {
                val hoy = LocalDate.now()
                val proximosCount = alimentosDisponibles.count {
                    calcularEstado(it, hoy) == EstadoVencimiento.PROXIMO
                }
                if (proximosCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3CD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Tienes $proximosCount alimento(s) próximo(s) a vencer en Mi Despensa. Aparecerán primero al elegir ingredientes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF856404),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Tarjetas de Lunes a Domingo con Desayuno, Almuerzo y Cena
            items(DiaSemana.entries, key = { it.numero }) { dia ->
                val fechaDelDia = lunesSeleccionado.plusDays((dia.numero - 1).toLong())
                val esHoy = fechaDelDia == LocalDate.now()

                DiaPlanificadorCard(
                    dia = dia,
                    fechaFormateada = fechaDelDia.format(formatoCorto),
                    esHoy = esHoy,
                    obtenerPlan = { tipo -> mapaPlanes[Pair(dia.numero, tipo.name)] },
                    onSeleccionarCasilla = { tipo, existente ->
                        casillaEnEdicion = CasillaSeleccionada(dia, tipo, existente)
                    },
                    onEliminarPlan = { item ->
                        planAEliminar = item
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DiaPlanificadorCard(
    dia: DiaSemana,
    fechaFormateada: String,
    esHoy: Boolean,
    obtenerPlan: (TipoComida) -> PlanComidaConIngredientes?,
    onSeleccionarCasilla: (TipoComida, PlanComidaConIngredientes?) -> Unit,
    onEliminarPlan: (PlanComidaConIngredientes) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (esHoy) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dia.etiqueta} ($fechaFormateada)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (esHoy) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "HOY",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            TipoComida.entries.forEach { tipo ->
                val planConIngredientes = obtenerPlan(tipo)
                CasillaComidaItem(
                    tipo = tipo,
                    planConIngredientes = planConIngredientes,
                    onClick = { onSeleccionarCasilla(tipo, planConIngredientes) },
                    onEliminar = planConIngredientes?.let { item -> { onEliminarPlan(item) } }
                )
            }
        }
    }
}

@Composable
private fun CasillaComidaItem(
    tipo: TipoComida,
    planConIngredientes: PlanComidaConIngredientes?,
    onClick: () -> Unit,
    onEliminar: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tipo.etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (planConIngredientes == null) {
                    Text(
                        text = "Sin planificar — toca para agregar plato e ingredientes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = planConIngredientes.plan.nombrePlato,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (planConIngredientes.ingredientes.isNotEmpty()) {
                        val resumenIngredientes = planConIngredientes.ingredientes.joinToString(", ") { ing ->
                            val cantStr = formatearNumero(ing.cantidadPlanificada)
                            if (ing.unidad.isNotBlank()) {
                                "${ing.alimentoNombre} ($cantStr ${ing.unidad})"
                            } else {
                                "${ing.alimentoNombre} ($cantStr)"
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ingredientes: $resumenIngredientes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Sin ingredientes de despensa vinculados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (planConIngredientes == null) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Planificar ${tipo.etiqueta}",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar ${tipo.etiqueta}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (onEliminar != null) {
                    IconButton(onClick = onEliminar) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar ${tipo.etiqueta}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoPlanificarComida(
    dia: DiaSemana,
    fechaDia: String,
    tipo: TipoComida,
    existente: PlanComidaConIngredientes?,
    alimentosDisponibles: List<Alimento>,
    alimentosNoDisponibles: List<Alimento>,
    onDismiss: () -> Unit,
    onSolicitarEliminar: (PlanComidaConIngredientes) -> Unit,
    onGuardar: (String, List<IngredientePlanificado>) -> Unit
) {
    var nombrePlato by remember(existente) {
        mutableStateOf(existente?.plan?.nombrePlato.orEmpty())
    }
    var errorValidacion by remember { mutableStateOf<String?>(null) }

    val borradores = remember(existente) {
        mutableStateListOf<BorradorIngrediente>().apply {
            existente?.ingredientes?.forEach { ing ->
                add(
                    BorradorIngrediente(
                        remoteId = ing.remoteId,
                        alimentoRemoteId = ing.alimentoRemoteId,
                        alimentoNombre = ing.alimentoNombre,
                        cantidadTexto = formatearNumero(ing.cantidadPlanificada),
                        unidad = ing.unidad
                    )
                )
            }
        }
    }

    var mostrarVencidosNoDisponibles by remember { mutableStateOf(false) }
    val hoy = remember { LocalDate.now() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (existente == null) {
                        "Planificar ${tipo.etiqueta}"
                    } else {
                        "Editar ${tipo.etiqueta}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${dia.etiqueta} • $fechaDia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nombrePlato,
                    onValueChange = {
                        nombrePlato = it
                        errorValidacion = null
                    },
                    label = { Text("Nombre del plato *") },
                    placeholder = { Text("Ej. Avena con leche, Arroz con pollo...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Ingredientes previstos (${borradores.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (borradores.isEmpty()) {
                    Text(
                        text = "Opcional: selecciona alimentos disponibles de Mi Despensa abajo para incluirlos en esta comida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    borradores.forEachIndexed { idx, item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.alimentoNombre,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { borradores.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Quitar ingrediente",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = item.cantidadTexto,
                                        onValueChange = { nuevo ->
                                            borradores[idx] = item.copy(cantidadTexto = nuevo)
                                            errorValidacion = null
                                        },
                                        label = { Text("Cantidad *") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = item.unidad,
                                        onValueChange = { nuevaUnidad ->
                                            borradores[idx] = item.copy(unidad = nuevaUnidad)
                                        },
                                        label = { Text("Unidad") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Agregar de Mi Despensa (próximos a vencer primero)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (alimentosDisponibles.isEmpty()) {
                    Text(
                        text = "No tienes alimentos con stock disponible y vigentes en Mi Despensa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    alimentosDisponibles.forEach { alimento ->
                        val yaSeleccionado = borradores.any {
                            it.alimentoRemoteId.isNotBlank() && it.alimentoRemoteId == alimento.remoteId
                        }
                        val estado = calcularEstado(alimento, hoy)
                        val dias = diasParaVencer(alimento, hoy)
                        val esProximo = estado == EstadoVencimiento.PROXIMO

                        val etiquetaVencimiento = when {
                            dias == null -> "Sin fecha de vencimiento"
                            dias == 0L -> "Vence hoy"
                            dias == 1L -> "Vence mañana"
                            else -> "Vence en $dias días"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (esProximo) Color(0xFFFFF3CD) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = alimento.nombre,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (esProximo) Color(0xFF856404) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (esProximo) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF856404)
                                            ) {
                                                Text(
                                                    text = "USAR PRIMERO",
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFFFF3CD),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    val stockTexto = buildString {
                                        append("Disponible: ${alimento.cantidadActual}")
                                        if (alimento.unidad.isNotBlank()) append(" ${alimento.unidad}")
                                        append(" • $etiquetaVencimiento")
                                    }
                                    Text(
                                        text = stockTexto,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (esProximo) Color(0xFF856404) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    enabled = !yaSeleccionado,
                                    onClick = {
                                        borradores.add(
                                            BorradorIngrediente(
                                                remoteId = java.util.UUID.randomUUID().toString(),
                                                alimentoRemoteId = alimento.remoteId,
                                                alimentoNombre = alimento.nombre,
                                                cantidadTexto = "1",
                                                unidad = alimento.unidad
                                            )
                                        )
                                        if (nombrePlato.isBlank()) {
                                            nombrePlato = alimento.nombre
                                        }
                                        errorValidacion = null
                                    }
                                ) {
                                    Text(if (yaSeleccionado) "Agregado" else "Elegir")
                                }
                            }
                        }
                    }
                }

                // Sección diferenciada para alimentos vencidos o sin stock (no sugeridos como disponibles)
                if (alimentosNoDisponibles.isNotEmpty()) {
                    TextButton(
                        onClick = { mostrarVencidosNoDisponibles = !mostrarVencidosNoDisponibles },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (mostrarVencidosNoDisponibles) {
                                "Ocultar no disponibles (${alimentosNoDisponibles.size} vencidos o sin stock)"
                            } else {
                                "Ver no disponibles (${alimentosNoDisponibles.size} vencidos o sin stock)"
                            }
                        )
                    }

                    if (mostrarVencidosNoDisponibles) {
                        alimentosNoDisponibles.forEach { alimento ->
                            val esVencido = calcularEstado(alimento, hoy) == EstadoVencimiento.VENCIDO
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alimento.nombre,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = if (esVencido) {
                                                "Vencido (${alimento.fechaVencimiento}) — no sugerido"
                                            } else {
                                                "Sin stock disponible — no sugerido"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = if (esVencido) "VENCIDO" else "SIN STOCK",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onError,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorValidacion != null) {
                    Text(
                        text = errorValidacion!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (existente != null) {
                    OutlinedButton(
                        onClick = { onSolicitarEliminar(existente) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eliminar esta comida planificada")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val platoLimpio = nombrePlato.trim()
                    if (platoLimpio.isEmpty()) {
                        errorValidacion = "Ingresa el nombre del plato."
                        return@TextButton
                    }

                    val ingredientesValidados = mutableListOf<IngredientePlanificado>()
                    for (b in borradores) {
                        val cant = b.cantidadTexto.replace(',', '.').trim().toDoubleOrNull()
                        if (cant == null || !cant.isFinite() || cant <= 0.0) {
                            errorValidacion = "Ingresa una cantidad válida mayor a 0 para '${b.alimentoNombre}'."
                            return@TextButton
                        }
                        ingredientesValidados.add(
                            IngredientePlanificado(
                                remoteId = b.remoteId,
                                alimentoRemoteId = b.alimentoRemoteId,
                                alimentoNombre = b.alimentoNombre,
                                cantidadPlanificada = cant,
                                unidad = b.unidad.trim()
                            )
                        )
                    }

                    onGuardar(platoLimpio, ingredientesValidados)
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

private fun formatearNumero(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
