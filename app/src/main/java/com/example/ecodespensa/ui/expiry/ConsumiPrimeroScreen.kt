package com.example.ecodespensa.ui.expiry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.ui.pantry.AlimentoViewModel
import com.example.ecodespensa.util.diasParaVencer
import java.time.LocalDate

/**
 * Pantalla "Consumir primero".
 * Muestra alimentos con stock > 0 cuya fecha de vencimiento sea POSTERIOR a hoy.
 * Excluye vencidos y los que vencen hoy. Ordena por fecha más cercana primero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumiPrimeroScreen(
    viewModel: AlimentoViewModel,
    onVolver: () -> Unit
) {
    val alimentos by viewModel.alimentosConsumiPrimero.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Consumir primero",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Solo alimentos con vencimiento futuro",
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
                }
            )
        }
    ) { innerPadding ->
        if (alimentos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sin alimentos próximos a vencer.\nLos vencidos y los de hoy no se muestran aquí.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(alimentos, key = { it.id }) { alimento ->
                    ConsumiPrimeroCard(alimento = alimento)
                }
            }
        }
    }
}

@Composable
private fun ConsumiPrimeroCard(alimento: Alimento) {
    val hoy = LocalDate.now()
    val dias = diasParaVencer(alimento, hoy)

    // Todos los items en esta pantalla tienen dias > 0 (vence mañana o más)
    // Badge por días concretos para mayor claridad
    val etiqueta = when {
        dias == null                  -> "SIN FECHA"
        dias == 1L                   -> "VENCE MAÑANA"
        dias == 2L                   -> "VENCE EN 2 DÍAS"
        dias == 3L                   -> "VENCE EN 3 DÍAS"
        dias <= 7L                   -> "VENCE EN $dias DÍAS"
        else                         -> "VENCE EN $dias DÍAS"
    }

    val (containerColor, labelColor) = when {
        dias != null && dias <= 3L -> Pair(
            Color(0xFFFFF3CD),
            Color(0xFF856404)
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val diasTexto = when {
        dias == null -> ""
        dias < 0 -> "Venció hace ${-dias} día(s)"
        dias == 0L -> "Vence hoy"
        dias == 1L -> "Vence mañana"
        else -> "Vence en $dias días"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alimento.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (alimento.categoria.isNotBlank()) {
                    Text(
                        text = alimento.categoria,
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor
                    )
                }
                val cantidad = buildString {
                    if (alimento.cantidadActual.isNotBlank()) append(alimento.cantidadActual)
                    if (alimento.unidad.isNotBlank()) append(" ${alimento.unidad}")
                }
                if (cantidad.isNotBlank()) {
                    Text(
                        text = "Stock: $cantidad",
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor
                    )
                }
                if (alimento.fechaVencimiento.isNotBlank()) {
                    Text(
                        text = if (diasTexto.isNotBlank()) "${alimento.fechaVencimiento} — $diasTexto"
                               else alimento.fechaVencimiento,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Badge de estado
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = labelColor,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = etiqueta,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = containerColor
                )
            }
        }
    }
}
