package com.example.ecodespensa.ui.pantry

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecodespensa.data.model.Alimento

/**
 * Pantalla Agregar Alimento.
 * Permite registrar un nuevo alimento y persistirlo mediante [AlimentoViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarAlimentoScreen(
    viewModel: AlimentoViewModel,
    onGuardar: () -> Unit,
    onVolver: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var cantidadActual by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }
    var fechaCompra by remember { mutableStateOf("") }
    var fechaVencimiento by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar alimento") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Datos del alimento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del alimento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = categoria,
                onValueChange = { categoria = it },
                label = { Text("Categoría (ej. Lácteo, Fruta, Vegetal)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = cantidadActual,
                onValueChange = { cantidadActual = it },
                label = { Text("Cantidad actual (ej. 500)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = unidad,
                onValueChange = { unidad = it },
                label = { Text("Unidad (ej. g, ml, piezas)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fechaCompra,
                onValueChange = { fechaCompra = it },
                label = { Text("Fecha de compra (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fechaVencimiento,
                onValueChange = { fechaVencimiento = it },
                label = { Text("Fecha de vencimiento (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

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
