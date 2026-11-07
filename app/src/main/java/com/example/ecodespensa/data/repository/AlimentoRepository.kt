package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.ResultadoOperacionStock
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.AlimentoFirestore
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.Movimiento
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de alimentos.
 * Define el contrato de acceso a datos para los alimentos de la despensa.
 *
 * Preparado para ser implementado con:
 * - AlimentoRepositoryImpl (Room para persistencia local)
 * - Sincronización con Cloud Firestore en etapas posteriores
 */
interface AlimentoRepository {
    // ── Operaciones globales (compatibilidad temporal con Etapa 2) ────────────
    fun obtenerAlimentos(): Flow<List<Alimento>>
    /** Agrega un alimento y registra automáticamente un movimiento COMPRA (operación atómica). */
    suspend fun agregarAlimento(alimento: Alimento)
    /** Registra alimento + movimiento COMPRA en una única transacción Room y devuelve el [Movimiento] creado. */
    suspend fun agregarAlimentoConCompra(alimento: Alimento): Movimiento
    /** Actualiza el alimento y registra un movimiento AJUSTE en una única transacción Room. */
    suspend fun ajustarStock(alimento: Alimento, cantidadAjuste: Double)
    suspend fun actualizarAlimento(alimento: Alimento)
    /**
     * Edita los datos generales del alimento y registra AJUSTE solo si
     * cambió [cantidadAnterior]. NO registra movimiento COMPRA.
     */
    suspend fun editarAlimento(alimento: Alimento, cantidadAnterior: Double)
    suspend fun eliminarAlimento(id: Int)

    // ── Operaciones filtradas y verificadas por usuarioUid ────────────────────
    /** Obtiene la lista reactiva de alimentos pertenecientes a [usuarioUid]. */
    fun obtenerAlimentosPorUsuario(usuarioUid: String): Flow<List<Alimento>>

    /** Obtiene un alimento por su [id] verificando que pertenezca a [usuarioUid]. */
    suspend fun obtenerAlimentoPorIdYUsuario(id: Int, usuarioUid: String): Alimento?

    /** Inserta el alimento asociándolo a [usuarioUid]. */
    suspend fun agregarAlimento(alimento: Alimento, usuarioUid: String)

    /** Registra alimento + movimiento COMPRA asociados a [usuarioUid] en una transacción y devuelve el [Movimiento] COMPRA. */
    suspend fun agregarAlimentoConCompra(alimento: Alimento, usuarioUid: String): Movimiento

    /**
     * Ajusta el stock en una única transacción Room verificando que el alimento pertenezca a [usuarioUid],
     * que [cantidadAjuste] sea mayor a 0 y que el stock resultante no sea negativo,
     * marcando `pendienteSincronizacion = true` y [marcaActualizacion], y registrando el movimiento AJUSTE.
     *
     * @return El [ResultadoOperacionStock] con el alimento actualizado y su movimiento AJUSTE, o `null` si falla.
     */
    suspend fun ajustarStock(
        alimento: Alimento,
        cantidadAjuste: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock?

    /** Actualiza el alimento verificando que pertenezca a [usuarioUid]. */
    suspend fun actualizarAlimentoPorUsuario(alimento: Alimento, usuarioUid: String)

    /**
     * Edita el alimento verificando que pertenezca a [usuarioUid], conserva su `remoteId`,
     * lo marca como pendiente de sincronización en Room y registra AJUSTE si cambió la cantidad.
     * @return El [ResultadoOperacionStock] con el alimento editado y el movimiento AJUSTE opcional, o `null` si falla.
     */
    suspend fun editarAlimento(
        alimento: Alimento,
        cantidadAnterior: Double,
        usuarioUid: String
    ): ResultadoOperacionStock?

    /**
     * Registra un consumo en una única transacción Room:
     * descuenta [cantidad] del stock verificando que el alimento pertenezca a [usuarioUid] y
     * que no supere el stock disponible, marca `pendienteSincronizacion = true` y [marcaActualizacion],
     * e inserta el movimiento CONSUMO en el historial local.
     *
     * @return El [ResultadoOperacionStock] con el alimento actualizado y su movimiento CONSUMO, o `null` si falla.
     */
    suspend fun consumirAlimento(
        alimentoId: Int,
        cantidad: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock?

    /**
     * Registra un desperdicio en una única transacción Room:
     * descuenta [cantidad] del stock verificando que el alimento pertenezca a [usuarioUid] y
     * que sea mayor a 0 y no supere el stock disponible, marca `pendienteSincronizacion = true`
     * y [marcaActualizacion], e inserta el movimiento DESPERDICIO en el historial local.
     *
     * @return El [ResultadoOperacionStock] con el alimento actualizado y su movimiento DESPERDICIO, o `null` si falla.
     */
    suspend fun desperdiciarAlimento(
        alimentoId: Int,
        cantidad: Double,
        usuarioUid: String,
        marcaActualizacion: Long = System.currentTimeMillis()
    ): ResultadoOperacionStock?

    /**
     * Marca en Room que el alimento con [remoteId] perteneciente a [usuarioUid]
     * ya fue sincronizado exitosamente con Cloud Firestore.
     */
    suspend fun marcarAlimentoSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    )

    /**
     * Registra la marca de tiempo remota base si el registro local aún tenía `baseRemotaActualizadoEn == 0`.
     */
    suspend fun registrarBaseRemotaSiCero(
        remoteId: String,
        usuarioUid: String,
        baseRemotaActualizadoEn: Long
    )

    /** Elimina un alimento por [id] verificando que pertenezca a [usuarioUid]. */
    suspend fun eliminarAlimentoPorUsuario(id: Int, usuarioUid: String)

    /**
     * Registra de forma persistente una eliminación pendiente (tombstone) en Room y retira
     * el alimento visible de la despensa dentro de una única transacción, sin borrar su historial.
     *
     * @return El registro [EliminacionPendiente] creado, o `null` si el alimento no existe o no pertenece a [usuarioUid].
     */
    suspend fun registrarEliminacionPendienteYBorrarLocal(
        alimentoId: Int,
        usuarioUid: String,
        fechaEliminacion: Long = System.currentTimeMillis()
    ): EliminacionPendiente?

    /** Obtiene las eliminaciones pendientes de sincronizar pertenecientes a [usuarioUid]. */
    fun obtenerEliminacionesPendientesPorUsuario(usuarioUid: String): Flow<List<EliminacionPendiente>>

    /** Limpia el registro de eliminación pendiente una vez confirmada su baja en Cloud Firestore. */
    suspend fun limpiarEliminacionPendiente(remoteId: String, usuarioUid: String)

    /** Emite `true` si existen alimentos o movimientos antiguos sin propietario (`usuarioUid = ''`). */
    fun existenDatosSinPropietario(): Flow<Boolean>

    /**
     * Vincula en una única transacción Room los alimentos y movimientos antiguos
     * sin propietario (`usuarioUid = ''`) al [usuarioUid] autenticado.
     */
    suspend fun vincularDatosAntiguos(usuarioUid: String)

    /**
     * Inserta en Room un alimento recuperado desde Cloud Firestore únicamente si su `remoteId`
     * no existe aún para [usuarioUid].
     *
     * - Asigna un nuevo `id` local autogenerado (`id = 0`).
     * - Conserva `remoteId` y `usuarioUid`.
     * - NO sobrescribe alimentos existentes ni genera movimientos de COMPRA.
     *
     * @return `true` si fue insertado como nuevo registro, `false` si ya existía localmente y se omitió.
     */
    suspend fun insertarAlimentoRecuperadoDeNube(
        alimentoRemoto: AlimentoFirestore,
        usuarioUid: String
    ): Boolean
}
