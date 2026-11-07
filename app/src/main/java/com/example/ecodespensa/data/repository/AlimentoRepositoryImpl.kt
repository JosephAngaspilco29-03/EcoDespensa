package com.example.ecodespensa.data.repository

import com.example.ecodespensa.data.local.AlimentoDao
import com.example.ecodespensa.data.local.DespensaTransactionDao
import com.example.ecodespensa.data.local.ResultadoOperacionStock
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.AlimentoFirestore
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.Movimiento
import kotlinx.coroutines.flow.Flow

/**
 * Implementación de [AlimentoRepository] usando Room (persistencia local).
 * Delega operaciones simples al [AlimentoDao] y las transaccionales
 * (insertar+COMPRA, ajustar+AJUSTE) al [DespensaTransactionDao].
 */
class AlimentoRepositoryImpl(
    private val dao: AlimentoDao,
    private val transactionDao: DespensaTransactionDao
) : AlimentoRepository {

    // ── Operaciones globales (compatibilidad temporal con Etapa 2) ────────────

    override fun obtenerAlimentos(): Flow<List<Alimento>> = dao.obtenerTodos()

    override suspend fun agregarAlimento(alimento: Alimento) {
        dao.insertar(alimento)
    }

    /**
     * Inserta el alimento y registra automáticamente un movimiento COMPRA
     * en una única transacción Room. Si algo falla, ninguno se persiste.
     */
    override suspend fun agregarAlimentoConCompra(alimento: Alimento): Movimiento {
        return transactionDao.insertarAlimentoConCompra(alimento)
    }

    /**
     * Actualiza el stock del alimento y registra un movimiento AJUSTE
     * en una única transacción Room.
     */
    override suspend fun ajustarStock(alimento: Alimento, cantidadAjuste: Double) {
        transactionDao.ajustarStockConMovimiento(alimento, cantidadAjuste)
    }

    override suspend fun actualizarAlimento(alimento: Alimento) {
        dao.actualizar(alimento)
    }

    /**
     * Edita los datos generales del alimento y registra AJUSTE solo si
     * cambió la cantidad. Delega al [DespensaTransactionDao] para atomicidad.
     */
    override suspend fun editarAlimento(alimento: Alimento, cantidadAnterior: Double) {
        transactionDao.editarAlimentoConAjuste(alimento, cantidadAnterior)
    }

    override suspend fun eliminarAlimento(id: Int) {
        dao.eliminarPorId(id)
    }

    // ── Operaciones filtradas y verificadas por usuarioUid ────────────────────

    override fun obtenerAlimentosPorUsuario(usuarioUid: String): Flow<List<Alimento>> =
        dao.obtenerPorUsuario(usuarioUid)

    override suspend fun obtenerAlimentoPorIdYUsuario(id: Int, usuarioUid: String): Alimento? =
        dao.obtenerPorIdYUsuario(id = id, usuarioUid = usuarioUid)

    override suspend fun agregarAlimento(alimento: Alimento, usuarioUid: String) {
        dao.insertar(alimento.copy(usuarioUid = usuarioUid))
    }

    override suspend fun agregarAlimentoConCompra(alimento: Alimento, usuarioUid: String): Movimiento {
        return transactionDao.insertarAlimentoConCompra(alimento.copy(usuarioUid = usuarioUid.trim()))
    }

    override suspend fun ajustarStock(
        alimento: Alimento,
        cantidadAjuste: Double,
        usuarioUid: String,
        marcaActualizacion: Long
    ): ResultadoOperacionStock? {
        return transactionDao.ajustarStockConMovimiento(
            alimento = alimento.copy(usuarioUid = usuarioUid),
            cantidadAjuste = cantidadAjuste,
            usuarioUid = usuarioUid,
            marcaActualizacion = marcaActualizacion
        )
    }

    override suspend fun actualizarAlimentoPorUsuario(alimento: Alimento, usuarioUid: String) {
        val marca = alimento.actualizadoEn.takeIf { it > 0L } ?: System.currentTimeMillis()
        dao.actualizarPorUsuario(
            id = alimento.id,
            usuarioUid = usuarioUid,
            nombre = alimento.nombre,
            categoria = alimento.categoria,
            cantidadActual = alimento.cantidadActual,
            unidad = alimento.unidad,
            fechaCompra = alimento.fechaCompra,
            fechaVencimiento = alimento.fechaVencimiento,
            actualizadoEn = marca
        )
    }

    override suspend fun editarAlimento(
        alimento: Alimento,
        cantidadAnterior: Double,
        usuarioUid: String
    ): ResultadoOperacionStock? {
        return transactionDao.editarAlimentoConAjuste(
            alimentoActualizado = alimento.copy(usuarioUid = usuarioUid),
            cantidadAnterior = cantidadAnterior,
            usuarioUid = usuarioUid
        )
    }

    override suspend fun consumirAlimento(
        alimentoId: Int,
        cantidad: Double,
        usuarioUid: String,
        marcaActualizacion: Long
    ): ResultadoOperacionStock? {
        return transactionDao.consumirAlimentoConMovimiento(
            alimentoId = alimentoId,
            cantidadConsumida = cantidad,
            usuarioUid = usuarioUid,
            marcaActualizacion = marcaActualizacion
        )
    }

    override suspend fun desperdiciarAlimento(
        alimentoId: Int,
        cantidad: Double,
        usuarioUid: String,
        marcaActualizacion: Long
    ): ResultadoOperacionStock? {
        return transactionDao.desperdiciarAlimentoConMovimiento(
            alimentoId = alimentoId,
            cantidadDesperdiciada = cantidad,
            usuarioUid = usuarioUid,
            marcaActualizacion = marcaActualizacion
        )
    }

    override suspend fun marcarAlimentoSincronizado(
        remoteId: String,
        usuarioUid: String,
        actualizadoEnConfirmado: Long
    ) {
        val idRemoto = remoteId.trim()
        val uidLimpio = usuarioUid.trim()
        if (idRemoto.isEmpty() || uidLimpio.isEmpty()) return
        dao.marcarSincronizadoPorRemoteId(
            remoteId = idRemoto,
            usuarioUid = uidLimpio,
            actualizadoEn = actualizadoEnConfirmado,
            baseRemotaActualizadoEn = actualizadoEnConfirmado
        )
    }

    override suspend fun registrarBaseRemotaSiCero(
        remoteId: String,
        usuarioUid: String,
        baseRemotaActualizadoEn: Long
    ) {
        val idRemoto = remoteId.trim()
        val uidLimpio = usuarioUid.trim()
        if (idRemoto.isEmpty() || uidLimpio.isEmpty() || baseRemotaActualizadoEn <= 0L) return
        dao.registrarBaseRemotaSiCero(
            remoteId = idRemoto,
            usuarioUid = uidLimpio,
            baseRemotaActualizadoEn = baseRemotaActualizadoEn
        )
    }

    override suspend fun eliminarAlimentoPorUsuario(id: Int, usuarioUid: String) {
        dao.eliminarPorIdYUsuario(id = id, usuarioUid = usuarioUid)
    }

    override suspend fun registrarEliminacionPendienteYBorrarLocal(
        alimentoId: Int,
        usuarioUid: String,
        fechaEliminacion: Long
    ): EliminacionPendiente? {
        return transactionDao.registrarEliminacionPendienteYBorrarLocal(
            alimentoId = alimentoId,
            usuarioUid = usuarioUid,
            fechaEliminacion = fechaEliminacion
        )
    }

    override fun obtenerEliminacionesPendientesPorUsuario(
        usuarioUid: String
    ): Flow<List<EliminacionPendiente>> =
        dao.obtenerEliminacionesPendientesPorUsuario(usuarioUid)

    override suspend fun limpiarEliminacionPendiente(remoteId: String, usuarioUid: String) {
        val idRemoto = remoteId.trim()
        val uidLimpio = usuarioUid.trim()
        if (idRemoto.isEmpty() || uidLimpio.isEmpty()) return
        dao.eliminarTombstonePorRemoteIdYUsuario(remoteId = idRemoto, usuarioUid = uidLimpio)
    }

    override fun existenDatosSinPropietario(): Flow<Boolean> =
        dao.existenDatosSinPropietario()

    override suspend fun vincularDatosAntiguos(usuarioUid: String) {
        val uidLimpio = usuarioUid.trim()
        if (uidLimpio.isNotEmpty()) {
            transactionDao.vincularDatosAntiguosAUsuario(uidLimpio)
        }
    }

    override suspend fun insertarAlimentoRecuperadoDeNube(
        alimentoRemoto: AlimentoFirestore,
        usuarioUid: String
    ): Boolean {
        val uidLimpio = usuarioUid.trim()
        val remoteIdLimpio = alimentoRemoto.remoteId.trim()
        require(uidLimpio.isNotEmpty()) { "El usuarioUid no puede estar vacío." }
        require(remoteIdLimpio.isNotEmpty()) { "El remoteId no puede estar vacío." }

        // 1. Si el alimento tiene una eliminación local pendiente (tombstone), NO volver a insertarlo
        val eliminacionPendiente = dao.obtenerEliminacionPendientePorRemoteIdYUsuario(
            remoteId = remoteIdLimpio,
            usuarioUid = uidLimpio
        )
        if (eliminacionPendiente != null) {
            return false
        }

        // 2. Si el remoteId ya existe en Room para este usuario, omitir sin sobrescribir
        val existente = dao.obtenerPorRemoteIdYUsuario(remoteIdLimpio, uidLimpio)
        if (existente != null) {
            return false
        }

        val marcaRemota = if (alimentoRemoto.actualizadoEn > 0L) {
            alimentoRemoto.actualizadoEn
        } else {
            System.currentTimeMillis()
        }

        // Insertar con id = 0 para que Room autogenere un nuevo ID local,
        // conservando remoteId y usuarioUid, marcando sincronizado y SIN generar movimiento de COMPRA.
        val nuevoLocal = Alimento(
            id = 0,
            remoteId = remoteIdLimpio,
            nombre = alimentoRemoto.nombre.trim(),
            categoria = alimentoRemoto.categoria.trim(),
            cantidadActual = alimentoRemoto.cantidadActual.trim(),
            unidad = alimentoRemoto.unidad.trim(),
            fechaCompra = alimentoRemoto.fechaCompra.trim(),
            fechaVencimiento = alimentoRemoto.fechaVencimiento.trim(),
            usuarioUid = uidLimpio,
            pendienteSincronizacion = false,
            actualizadoEn = marcaRemota,
            baseRemotaActualizadoEn = marcaRemota
        )
        val rowId = dao.insertarSiNoExiste(nuevoLocal)
        return rowId != -1L
    }
}
