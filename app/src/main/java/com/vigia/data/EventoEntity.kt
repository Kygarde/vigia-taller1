package com.vigia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bitacora_eventos")
data class EventoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sala: Int,
    val codigoAlumno: String,
    val tipoEvento: String,
    val descripcion: String,
    val timestamp: Long = System.currentTimeMillis()
)