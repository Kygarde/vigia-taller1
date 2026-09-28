package com.vigia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BitacoraDao {
    @Insert
    suspend fun insertar(evento: EventoEntity)

    @Query("SELECT * FROM bitacora_eventos WHERE sala = :sala ORDER BY timestamp DESC")
    fun obtenerPorSala(sala: Int): Flow<List<EventoEntity>>

    @Query("SELECT * FROM bitacora_eventos WHERE sala = :sala ORDER BY timestamp ASC")
    suspend fun obtenerListaParaExportar(sala: Int): List<EventoEntity>

    @Query("DELETE FROM bitacora_eventos WHERE sala = :sala")
    suspend fun limpiarSala(sala: Int)
}