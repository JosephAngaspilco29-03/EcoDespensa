package com.example.ecodespensa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.EliminacionPendiente
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento
import java.util.UUID

/**
 * Convierte [TipoMovimiento] a/desde String para persistencia en Room.
 */
class TipoMovimientoConverter {
    @TypeConverter
    fun fromTipo(tipo: TipoMovimiento): String = tipo.name

    @TypeConverter
    fun toTipo(nombre: String): TipoMovimiento = TipoMovimiento.valueOf(nombre)
}

/**
 * Base de datos Room de EcoDespensa.
 * Versión 2: agrega la tabla movimientos para historial de stock.
 * Versión 3: agrega la columna usuarioUid a alimentos y movimientos.
 * Versión 4: agrega la columna remoteId (UUID único persistente) a alimentos.
 * Versión 5: agrega las columnas pendienteSincronizacion, actualizadoEn y baseRemotaActualizadoEn a alimentos.
 * Versión 6: agrega la tabla eliminaciones_pendientes (tombstones) para sincronizar eliminaciones con Firestore.
 * Versión 7: agrega la columna remoteId (UUID único persistente) a movimientos.
 * Singleton para evitar múltiples instancias abiertas simultáneamente.
 */
@Database(
    entities = [Alimento::class, Movimiento::class, EliminacionPendiente::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(TipoMovimientoConverter::class)
abstract class EcoDespensaDatabase : RoomDatabase() {

    abstract fun alimentoDao(): AlimentoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun despensaTransactionDao(): DespensaTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: EcoDespensaDatabase? = null

        /**
         * Migración de versión 1 a 2: crea la tabla `movimientos` si no existe.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movimientos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `alimentoId` INTEGER NOT NULL,
                        `alimentoNombre` TEXT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `cantidad` REAL NOT NULL,
                        `fecha` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migración de versión 2 a 3: agrega `usuarioUid` con valor por defecto ''
         * a las tablas `alimentos` y `movimientos` sin perder los registros existentes.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `alimentos` ADD COLUMN `usuarioUid` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `movimientos` ADD COLUMN `usuarioUid` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Migración de versión 3 a 4: agrega `remoteId` a la tabla `alimentos` y genera
         * por única vez un UUID único para cada alimento existente (como LECHE, YOGURT y ARROZ)
         * sin perder ni sobrescribir sus datos.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `alimentos` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''"
                )
                val cursor = db.query("SELECT `id` FROM `alimentos` WHERE `remoteId` = ''")
                cursor.use { c ->
                    val idIndex = c.getColumnIndexOrThrow("id")
                    while (c.moveToNext()) {
                        val id = c.getInt(idIndex)
                        val uuidUnico = UUID.randomUUID().toString()
                        db.execSQL(
                            "UPDATE `alimentos` SET `remoteId` = ? WHERE `id` = ?",
                            arrayOf<Any>(uuidUnico, id)
                        )
                    }
                }
            }
        }

        /**
         * Migración de versión 4 a 5: agrega las columnas persistentes para el estado de
         * sincronización y control de conflictos (`pendienteSincronizacion`, `actualizadoEn`
         * y `baseRemotaActualizadoEn`) conservando intactos todos los alimentos existentes.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `alimentos` ADD COLUMN `pendienteSincronizacion` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `alimentos` ADD COLUMN `actualizadoEn` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `alimentos` ADD COLUMN `baseRemotaActualizadoEn` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migración de versión 5 a 6: crea la tabla `eliminaciones_pendientes` (tombstones)
         * para registrar de forma persistente las eliminaciones pendientes de sincronizar con
         * Cloud Firestore, conservando todos los alimentos y movimientos existentes.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `eliminaciones_pendientes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `remoteId` TEXT NOT NULL,
                        `usuarioUid` TEXT NOT NULL,
                        `nombreAlimento` TEXT NOT NULL,
                        `fechaEliminacion` INTEGER NOT NULL,
                        `baseRemotaActualizadoEn` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migración de versión 6 a 7: agrega `remoteId` a la tabla `movimientos` y genera por
         * única vez un UUID único para cada movimiento existente, conservando todo el historial.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `movimientos` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''"
                )
                val ids = mutableListOf<Int>()
                db.query("SELECT `id` FROM `movimientos` WHERE `remoteId` = ''").use { c ->
                    val idIndex = c.getColumnIndexOrThrow("id")
                    while (c.moveToNext()) ids.add(c.getInt(idIndex))
                }
                ids.forEach { id ->
                    db.execSQL(
                        "UPDATE `movimientos` SET `remoteId` = ? WHERE `id` = ?",
                        arrayOf<Any>(UUID.randomUUID().toString(), id)
                    )
                }
            }
        }

        fun getInstance(context: Context): EcoDespensaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EcoDespensaDatabase::class.java,
                    "ecodespensa_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
