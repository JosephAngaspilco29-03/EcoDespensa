package com.example.ecodespensa.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pantalla de configuración inicial del usuario.
 * Se muestra tras el registro. Permite al usuario personalizar
 * preferencias básicas antes de acceder al Inicio.
 * Por ahora no persiste datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionInicialScreen(
    onConfigurado: () -> Unit
) {
    var cantidadPersonas by remember { mutableStateOf("1") }
    var preferenciasVegetariano by remember { mutableStateOf(false) }
    var preferenciasVegano by remember { mutableStateOf(false) }
    var preferenciasGlutenFree by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Configura tu perfil",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Personaliza EcoDespensa según tus necesidades.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = cantidadPersonas,
                    onValueChange = { cantidadPersonas = it },
                    label = { Text("¿Cuántas personas en tu hogar?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Preferencias alimenticias",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = preferenciasVegetariano,
                        onCheckedChange = { preferenciasVegetariano = it }
                    )
                    Text("Vegetariano")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = preferenciasVegano,
                        onCheckedChange = { preferenciasVegano = it }
                    )
                    Text("Vegano")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = preferenciasGlutenFree,
                        onCheckedChange = { preferenciasGlutenFree = it }
                    )
                    Text("Sin gluten")
                }
            }

            Button(
                onClick = onConfigurado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Comenzar")
            }
        }
    }
}
