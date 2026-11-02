package com.example.ecodespensa.ui.navigation

/**
 * Define todas las rutas de navegación de la aplicación.
 * Centraliza los destinos para evitar strings dispersos en el código.
 */
sealed class Destino(val ruta: String) {
    data object Bienvenida : Destino("bienvenida")
    data object IniciarSesion : Destino("iniciar_sesion")
    data object Registrarse : Destino("registrarse")
    data object ConfiguracionInicial : Destino("configuracion_inicial")
    data object Inicio : Destino("inicio")
    data object MiDespensa : Destino("mi_despensa")
    data object AgregarAlimento : Destino("agregar_alimento")
    data object PlanificadorSemanal : Destino("planificador_semanal")
    data object ListaCompras : Destino("lista_compras")
    data object MiPerfil : Destino("mi_perfil")
    data object Historial : Destino("historial")
    data object ConsumiPrimero : Destino("consumi_primero")
}
