package com.example.ecodespensa.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ecodespensa.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de UI para las pantallas de autenticación.
 */
sealed class AuthUiState {
    /** Estado inicial o limpio (sin operación en curso). */
    data object Idle : AuthUiState()

    /** Operación en curso (spinner). */
    data object Cargando : AuthUiState()

    /** Operación exitosa, contiene el usuario autenticado. */
    data class Exito(val usuario: FirebaseUser) : AuthUiState()

    /** Operación fallida, contiene el mensaje de error. */
    data class Error(val mensaje: String) : AuthUiState()
}

/**
 * ViewModel compartido para registro e inicio de sesión con Firebase Authentication.
 * También expone el usuario actual para sesión persistente.
 */
class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Usuario actualmente autenticado (null si no hay sesión). */
    val usuarioActual: FirebaseUser?
        get() = repo.usuarioActual

    /** true si Firebase ya tiene una sesión persistida en disco. */
    val haySesionActiva: Boolean
        get() = repo.haySesionActiva

    /** Registra un nuevo usuario con nombre, correo y contraseña. */
    fun registrar(nombre: String, correo: String, contrasena: String) {
        if (nombre.isBlank() || correo.isBlank() || contrasena.isBlank()) {
            _uiState.value = AuthUiState.Error("Todos los campos son obligatorios")
            return
        }
        if (contrasena.length < 6) {
            _uiState.value = AuthUiState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Cargando
            try {
                val usuario = repo.registrar(nombre, correo, contrasena)
                _uiState.value = AuthUiState.Exito(usuario)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mensajeAmigable(e))
            }
        }
    }

    /** Inicia sesión con correo y contraseña existentes. */
    fun iniciarSesion(correo: String, contrasena: String) {
        if (correo.isBlank() || contrasena.isBlank()) {
            _uiState.value = AuthUiState.Error("Ingresa correo y contraseña")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Cargando
            try {
                val usuario = repo.iniciarSesion(correo, contrasena)
                _uiState.value = AuthUiState.Exito(usuario)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mensajeAmigable(e))
            }
        }
    }

    /** Cierra la sesión y limpia el estado. */
    fun cerrarSesion() {
        repo.cerrarSesion()
        _uiState.value = AuthUiState.Idle
    }

    /** Limpia el estado de error para reutilizar la pantalla. */
    fun limpiarEstado() {
        _uiState.value = AuthUiState.Idle
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Traduce excepciones de Firebase a mensajes legibles en español. */
    private fun mensajeAmigable(e: Exception): String {
        val msg = e.message ?: return "Error desconocido"
        return when {
            msg.contains("email address is already in use", ignoreCase = true) ->
                "Este correo ya está registrado"
            msg.contains("badly formatted", ignoreCase = true) ->
                "El formato del correo no es válido"
            msg.contains("no user record", ignoreCase = true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Correo o contraseña incorrectos"
            msg.contains("password is invalid", ignoreCase = true) ->
                "Contraseña incorrecta"
            msg.contains("network error", ignoreCase = true) ->
                "Sin conexión a Internet"
            msg.contains("too many requests", ignoreCase = true) ->
                "Demasiados intentos. Intenta más tarde"
            else -> "Error: $msg"
        }
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(AuthRepository()) as T
        }
    }
}
