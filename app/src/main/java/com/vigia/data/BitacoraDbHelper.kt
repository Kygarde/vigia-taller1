package com.vigia.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base de Datos SQLite nativa de Android para persistir la bitácora del examen.
 * No depende de generadores de código de terceros, garantizando 100% estabilidad.
 */
class BitacoraDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SALA INTEGER NOT NULL,
                $COL_CODIGO TEXT NOT NULL,
                $COL_EVENTO TEXT NOT NULL,
                $COL_HORA INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    suspend fun insertarEvento(sala: Int, codigo: String, evento: String, hora: Long) = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(COL_SALA, sala)
                put(COL_CODIGO, codigo)
                put(COL_EVENTO, evento)
                put(COL_HORA, hora)
            }
            writableDatabase.insert(TABLE_NAME, null, values)
        }
    }

    suspend fun limpiarSala(sala: Int) = withContext(Dispatchers.IO) {
        runCatching {
            writableDatabase.delete(TABLE_NAME, "$COL_SALA = ?", arrayOf(sala.toString()))
        }
    }

    companion object {
        private const val DATABASE_NAME = "vigia_bitacora.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "bitacora_eventos"
        const val COL_ID = "id"
        const val COL_SALA = "sala"
        const val COL_CODIGO = "codigo"
        const val COL_EVENTO = "evento"
        const val COL_HORA = "hora"

        @Volatile
        private var instance: BitacoraDbHelper? = null

        fun getInstance(context: Context): BitacoraDbHelper {
            return instance ?: synchronized(this) {
                instance ?: BitacoraDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}