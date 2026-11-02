package com.example.ecodespensa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ecodespensa.ui.pantry.AlimentoViewModel
import com.example.ecodespensa.worker.VencimientoWorker

/**
 * Pantalla de inicio (dashboard principal).
 * Muestra tarjetas de acceso rápido a las secciones principales:
 * Mi Despensa, Planificador, Lista de Compras y Mi Perfil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(
    viewModel: AlimentoViewModel,
    onMiDespensa: () -> Unit,
    onPlanificador: () -> Unit,
    onListaCompras: () -> Unit,
    onMiPerfil: () -> Unit,
    onHistorial: () -> Unit,
    onConsumiPrimero: () -> Unit
) {
    val totalDisponibles by viewModel.totalDisponibles.collectAsState()
    val proximosAVencer by viewModel.proximosAVencer.collectAsState()
    val vencidos by viewModel.vencidos.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "EcoDespensa",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "¡Hola! ¿Qué deseas hacer hoy?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMiPerfil) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Mi perfil"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Panel de resumen de vencimientos ---
            Text(
                text = "Estado de tu despensa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TarjetaStat(
                    modifier = Modifier.weight(1f),
                    valor = totalDisponibles.toString(),
                    etiqueta = "Disponibles",
                    color = MaterialTheme.colorScheme.primaryContainer,
                    colorTexto = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TarjetaStat(
                    modifier = Modifier.weight(1f),
                    valor = proximosAVencer.toString(),
                    etiqueta = "Próx. a vencer",
                    color = Color(0xFFFFF3CD),
                    colorTexto = Color(0xFF856404)
                )
                TarjetaStat(
                    modifier = Modifier.weight(1f),
                    valor = vencidos.toString(),
                    etiqueta = "Vencidos",
                    color = MaterialTheme.colorScheme.errorContainer,
                    colorTexto = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // --- Botón de prueba (solo desarrollo) ---
            OutlinedButton(
                onClick = {
                    val request = OneTimeWorkRequestBuilder<VencimientoWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔔 Probar notificaciones ahora")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Acceso rápido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    TarjetaAcceso(
                        titulo = "Mi Despensa",
                        descripcion = "Gestiona tus alimentos",
                        icono = Icons.Default.Kitchen,
                        onClick = onMiDespensa
                    )
                }
                item {
                    TarjetaAcceso(
                        titulo = "Planificador",
                        descripcion = "Organiza tu semana",
                        icono = Icons.Default.CalendarMonth,
                        onClick = onPlanificador
                    )
                }
                item {
                    TarjetaAcceso(
                        titulo = "Lista de Compras",
                        descripcion = "Tu lista inteligente",
                        icono = Icons.Default.ShoppingCart,
                        onClick = onListaCompras
                    )
                }
                item {
                    TarjetaAcceso(
                        titulo = "Mi Perfil",
                        descripcion = "Tus preferencias",
                        icono = Icons.Default.Person,
                        onClick = onMiPerfil
                    )
                }
                item {
                    TarjetaAcceso(
                        titulo = "Historial",
                        descripcion = "Movimientos de stock",
                        icono = Icons.Default.History,
                        onClick = onHistorial
                    )
                }
                item {
                    TarjetaAcceso(
                        titulo = "Consumir primero",
                        descripcion = "Próximos a vencer",
                        icono = Icons.Default.Timer,
                        onClick = onConsumiPrimero
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaAcceso(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icono,
                contentDescription = titulo,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TarjetaStat(
    modifier: Modifier = Modifier,
    valor: String,
    etiqueta: String,
    color: androidx.compose.ui.graphics.Color,
    colorTexto: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorTexto
            )
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = colorTexto
            )
        }
    }
}
