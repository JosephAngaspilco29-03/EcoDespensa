package com.example.ecodespensa.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ecodespensa.ui.auth.AuthViewModel
import com.example.ecodespensa.ui.auth.IniciarSesionScreen
import com.example.ecodespensa.ui.auth.RegistrarseScreen
import com.example.ecodespensa.ui.expiry.ConsumiPrimeroScreen
import com.example.ecodespensa.ui.history.HistorialScreen
import com.example.ecodespensa.ui.history.MovimientoViewModel
import com.example.ecodespensa.ui.home.InicioScreen
import com.example.ecodespensa.ui.pantry.AgregarAlimentoScreen
import com.example.ecodespensa.ui.pantry.AlimentoViewModel
import com.example.ecodespensa.ui.pantry.CapturaAlimentoCameraScreen
import com.example.ecodespensa.ui.pantry.MiDespensaScreen
import com.example.ecodespensa.ui.planner.PlanificadorSemanalScreen
import com.example.ecodespensa.ui.planner.PlanificadorViewModel
import com.example.ecodespensa.ui.profile.MiPerfilScreen
import com.example.ecodespensa.ui.setup.ConfiguracionInicialScreen
import com.example.ecodespensa.ui.shopping.ListaComprasScreen
import com.example.ecodespensa.ui.shopping.ListaComprasViewModel
import com.example.ecodespensa.ui.welcome.BienvenidaScreen

/**
 * Grafo de navegación principal de la aplicación.
 *
 * - Sesión persistente: si Firebase ya tiene un usuario autenticado al iniciar,
 *   el destino inicial salta directamente a [Destino.Inicio].
 * - Logout: [MiPerfilScreen] llama a [AuthViewModel.cerrarSesion] y navega de
 *   vuelta a [Destino.Bienvenida], limpiando todo el back-stack.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    alimentoViewModel: AlimentoViewModel,
    movimientoViewModel: MovimientoViewModel,
    planificadorViewModel: PlanificadorViewModel,
    listaComprasViewModel: ListaComprasViewModel
) {
    // AuthViewModel compartido en todo el grafo; Factory instancia AuthRepository internamente
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory())

    // Destino inicial según sesión persistida en Firebase
    val destinoInicial = if (authViewModel.haySesionActiva) {
        Destino.Inicio.ruta
    } else {
        Destino.Bienvenida.ruta
    }

    NavHost(
        navController = navController,
        startDestination = destinoInicial
    ) {
        composable(Destino.Bienvenida.ruta) {
            BienvenidaScreen(
                onIniciarSesion = { navController.navigate(Destino.IniciarSesion.ruta) },
                onRegistrarse = { navController.navigate(Destino.Registrarse.ruta) }
            )
        }

        composable(Destino.IniciarSesion.ruta) {
            IniciarSesionScreen(
                authViewModel = authViewModel,
                onSesionIniciada = {
                    navController.navigate(Destino.Inicio.ruta) {
                        popUpTo(Destino.Bienvenida.ruta) { inclusive = true }
                    }
                },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.Registrarse.ruta) {
            RegistrarseScreen(
                authViewModel = authViewModel,
                onRegistroExitoso = {
                    navController.navigate(Destino.ConfiguracionInicial.ruta) {
                        popUpTo(Destino.Bienvenida.ruta) { inclusive = true }
                    }
                },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.ConfiguracionInicial.ruta) {
            ConfiguracionInicialScreen(
                onConfigurado = {
                    navController.navigate(Destino.Inicio.ruta) {
                        popUpTo(Destino.ConfiguracionInicial.ruta) { inclusive = true }
                    }
                }
            )
        }

        composable(Destino.Inicio.ruta) {
            InicioScreen(
                viewModel = alimentoViewModel,
                onMiDespensa = { navController.navigate(Destino.MiDespensa.ruta) },
                onPlanificador = { navController.navigate(Destino.PlanificadorSemanal.ruta) },
                onListaCompras = { navController.navigate(Destino.ListaCompras.ruta) },
                onMiPerfil = { navController.navigate(Destino.MiPerfil.ruta) },
                onHistorial = { navController.navigate(Destino.Historial.ruta) },
                onConsumiPrimero = { navController.navigate(Destino.ConsumiPrimero.ruta) }
            )
        }

        composable(Destino.ConsumiPrimero.ruta) {
            ConsumiPrimeroScreen(
                viewModel = alimentoViewModel,
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.MiDespensa.ruta) {
            MiDespensaScreen(
                viewModel = alimentoViewModel,
                onAgregarAlimento = { navController.navigate(Destino.AgregarAlimento.ruta) },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.AgregarAlimento.ruta) {
            AgregarAlimentoScreen(
                viewModel = alimentoViewModel,
                onGuardar = { navController.popBackStack() },
                onVolver = { navController.popBackStack() },
                onAbrirCamara = {
                    navController.navigate(Destino.CapturaCamaraAlimento.ruta)
                }
            )
        }

        composable(Destino.CapturaCamaraAlimento.ruta) {
            val context = androidx.compose.ui.platform.LocalContext.current
            CapturaAlimentoCameraScreen(
                onConfirmarCaptura = { entrada ->
                    alimentoViewModel.confirmarCapturaCamaraParaReconocimiento(context, entrada)
                    navController.popBackStack()
                },
                onCancelarYVolver = {
                    navController.popBackStack()
                }
            )
        }

        composable(Destino.PlanificadorSemanal.ruta) {
            PlanificadorSemanalScreen(
                viewModel = planificadorViewModel,
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.ListaCompras.ruta) {
            ListaComprasScreen(
                viewModel = listaComprasViewModel,
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Destino.MiPerfil.ruta) {
            MiPerfilScreen(
                authViewModel = authViewModel,
                alimentoViewModel = alimentoViewModel,
                movimientoViewModel = movimientoViewModel,
                planificadorViewModel = planificadorViewModel,
                listaComprasViewModel = listaComprasViewModel,
                onVolver = { navController.popBackStack() },
                onCerrarSesion = {
                    // Cerrar sesión ya fue llamado en MiPerfilScreen;
                    // aquí limpiamos el back-stack y volvemos a Bienvenida.
                    navController.navigate(Destino.Bienvenida.ruta) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Destino.Historial.ruta) {
            HistorialScreen(
                viewModel = movimientoViewModel,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
