package com.example.ecodespensa.ui.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pantalla Lista Inteligente de Compras.
 * Muestra sugerencias de compra basadas en los alimentos de la despensa.
 * Por ahora muestra datos de ejemplo con checkboxes funcionales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaComprasScreen(
    onVolver: () -> Unit
) {
    // Datos de ejemplo para visualización
    data class ItemCompra(val nombre: String, val razon: String)

    val itemsEjemplo = listOf(
        ItemCompra("Leche", "Se agota en 3 días"),
        ItemCompra("Pan integral", "No tienes en despensa"),
        ItemCompra("Manzanas", "Stock bajo"),
        ItemCompra("Yogur", "No tienes en despensa"),
        ItemCompra("Pollo", "Planificado para el miércoles")
    )

    val estadoItems = remember { mutableStateListOf(*Array(itemsEjemplo.size) { false }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de compras") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sugerencias inteligentes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Basadas en tu despensa y planificador",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(itemsEjemplo.size) { index ->
                    val item = itemsEjemplo[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (estadoItems[index])
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = estadoItems[index],
                                onCheckedChange = { estadoItems[index] = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = item.nombre,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = item.razon,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
