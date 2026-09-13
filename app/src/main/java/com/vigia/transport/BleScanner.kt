package com.vigia.transport

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.vigia.model.AlumnoVigilado
import com.vigia.model.PacketCodec
import com.vigia.model.RiskLevel
import com.vigia.model.StudentStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BleScanner {

    private const val CADUCIDAD_MS = 15_000L

    @SuppressLint("MissingPermission")
    fun alumnos(sala: Int) = callbackFlow<List<AlumnoVigilado>> {

        val vistos = linkedMapOf<Int, StudentStatus>()
        val ausentes = mutableMapOf<Int, Long>()          // id -> cuando se perdió
        val incidencias = mutableMapOf<Int, Int>()
        val riesgoPrevio = mutableMapOf<Int, RiskLevel>()
        val salioPrevio = mutableMapOf<Int, Boolean>()

        fun emitir() {
            trySend(
                vistos.values.map { st ->
                    AlumnoVigilado(
                        estado = st,
                        incidencias = incidencias[st.id] ?: 0,
                        ausenteDesde = ausentes[st.id],
                        enPadron = PadronStore.aceptado(st.id)
                    )
                }.sortedWith(
                    compareBy<AlumnoVigilado> { it.ausenteDesde != null }   // presentes primero
                        .thenByDescending { !it.enPadron }                  // no registrados arriba
                        .thenByDescending { it.estado.risk.ordinal }
                        .thenByDescending { it.incidencias }
                        .thenBy { it.estado.id }
                )
            )
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val crudo = result.scanRecord
                    ?.getManufacturerSpecificData(PacketCodec.MANUFACTURER_ID) ?: return
                val status = runCatching { PacketCodec.decodeAlumno(crudo) }.getOrNull() ?: return
                if (status.sala != sala) return          // es de otro salón

                // Primera vez que lo vemos
                if (!vistos.containsKey(status.id)) {
                    Bitacora.registrar(status.etiqueta, Evento.UNIDO)
                    if (!PadronStore.aceptado(status.id)) {
                        Bitacora.registrar(status.etiqueta, Evento.NO_REGISTRADO)
                    }
                }

                // Estaba ausente y volvió
                if (ausentes.remove(status.id) != null) {
                    Bitacora.registrar(status.etiqueta, Evento.REGRESO)
                }

                // Incidencia por entrada en ALERTA
                val antes = riesgoPrevio[status.id]
                if (status.risk == RiskLevel.ALERTA && antes != RiskLevel.ALERTA) {
                    incidencias[status.id] = (incidencias[status.id] ?: 0) + 1
                    Bitacora.registrar(status.etiqueta, Evento.ALERTA)
                }
                riesgoPrevio[status.id] = status.risk

                // Transición al salir de la app
                if (status.salioDeLaApp && salioPrevio[status.id] != true) {
                    Bitacora.registrar(status.etiqueta, Evento.SALIO_APP)
                }
                salioPrevio[status.id] = status.salioDeLaApp

                vistos[status.id] = status
                emitir()
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e("VIGIA_BLE", "Escaneo de alumnos falló: $errorCode")
            }
        }

        emitir()

        val limpieza = launch {
            while (isActive) {
                delay(2_000)
                val corte = System.currentTimeMillis() - CADUCIDAD_MS
                var cambio = false
                for (st in vistos.values) {
                    if (st.lastSeen < corte && ausentes[st.id] == null) {
                        ausentes[st.id] = st.lastSeen
                        Bitacora.registrar(st.etiqueta, Evento.SIN_SENAL)
                        cambio = true
                    }
                }
                if (cambio) emitir()
            }
        }

        awaitClose { limpieza.cancel() }
    }
}