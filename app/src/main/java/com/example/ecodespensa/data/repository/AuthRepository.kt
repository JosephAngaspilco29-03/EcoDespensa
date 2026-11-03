package com.example.ecodespensa.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Repositorio que encapsula todas las operaciones de Firebase Authentication.
 * Expone funciones suspendidas para registro, inicio de sesión y cierre de sesión,
 * así como un flujo reactivo del UID autenticado.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Usuario actualmente autenticado, o null si no hay sesión activa. */
    val usuarioActual: FirebaseUser?
        get() = auth.currentUser

    /** true si hay una sesión activa (sesión persistente automática de Firebase). */
    val haySesionActiva: Boolean
        get() = auth.currentUser != null

    /**
     * Flujo reactivo que emite el UID del usuario autenticado al iniciar sesión
     * y `null` al cerrar sesión, liberando el [FirebaseAuth.AuthStateListener] al cerrarse.
     */
    val uidActualFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()

    /**
     * Registra un nuevo usuario con correo y contraseña.
     * Opcionalmente actualiza el displayName con el nombre proporcionado.
     * @return [FirebaseUser] si el registro fue exitoso.
     * @throws Exception si Firebase rechaza la operación.
     */
    suspend fun registrar(nombre: String, correo: String, contrasena: String): FirebaseUser {
        val resultado = auth.createUserWithEmailAndPassword(correo.trim(), contrasena).await()
        val usuario = resultado.user
            ?: throw Exception("No se pudo crear el usuario")

        // Actualizar displayName con el nombre real
        val perfil = userProfileChangeRequest {
            displayName = nombre.trim()
        }
        usuario.updateProfile(perfil).await()
        return usuario
    }

    /**
     * Inicia sesión con correo y contraseña existentes.
     * @return [FirebaseUser] si el inicio fue exitoso.
     * @throws Exception si las credenciales son incorrectas.
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): FirebaseUser {
        val resultado = auth.signInWithEmailAndPassword(correo.trim(), contrasena).await()
        return resultado.user
            ?: throw Exception("No se pudo iniciar sesión")
    }

    /**
     * Cierra la sesión del usuario actual.
     * Firebase limpia el token local automáticamente.
     */
    fun cerrarSesion() {
        auth.signOut()
    }
}
