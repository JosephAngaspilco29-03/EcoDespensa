package com.example.ecodespensa.data.model

/**
 * Modelo de datos para representar el perfil del usuario.
 * Preparado para ser extendido con Firebase Authentication y Firestore.
 *
 * @param uid Identificador único del usuario
 * @param nombre Nombre completo
 * @param correo Correo electrónico
 * @param cantidadPersonas Número de personas en el hogar
 * @param esVegetariano Preferencia vegetariana
 * @param esVegano Preferencia vegana
 * @param sinGluten Preferencia sin gluten
 */
data class Usuario(
    val uid: String = "",
    val nombre: String = "",
    val correo: String = "",
    val cantidadPersonas: Int = 1,
    val esVegetariano: Boolean = false,
    val esVegano: Boolean = false,
    val sinGluten: Boolean = false
)
