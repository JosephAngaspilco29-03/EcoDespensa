package com.example.ecodespensa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.ecodespensa.data.model.Alimento
import com.example.ecodespensa.data.model.Movimiento
import com.example.ecodespensa.data.model.TipoMovimiento

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
 * Singleton para evitar múltiples instancias abiertas simultáneamente.
 */
@Database(entities = [Alimento::class, Movimiento::class], version = 2, exportSchema = false)
@TypeConverters(TipoMovimientoConverter::class)
abstract class EcoDespensaDatabase : RoomDatabase() {

    abstract fun alimentoDao(): AlimentoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun despensaTransactionDao(): DespensaTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: EcoDespensaDatabase? = null

        fun getInstance(context: Context): EcoDespensaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EcoDespensaDatabase::class.java,
                    "ecodespensa_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
