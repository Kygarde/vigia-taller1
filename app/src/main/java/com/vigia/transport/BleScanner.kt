package com.vigia.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult
import android.content.Context
import com.vigia.model.RiskLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Escucha los anuncios BLE de la aplicacion.
 *
 * Escucha anuncios (advertising), no conexiones. Android soporta alrededor de siete
 * conexiones GATT simultaneas; el advertising es sin conexion y escala a todo el salon.
 */
class BleScanner(context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    /** false si el equipo no tiene Bluetooth o esta apagado. */
    val bluetoothListo: Boolean get() = adapter?.isEnabled == true

    /**
     * Codigos de las aulas que se estan anunciando ahora mismo.
     *
     * Sin servidor, un aula solo "existe" mientras el equipo del docente la anuncia.
     * El alumno solo puede unirse a un aula que aparezca en este conjunto.
     */
    @SuppressLint("MissingPermission")
    fun aulasAbiertas() = callbackFlow<Set<Int>> {

        val vistas = linkedMapOf<Int, Long>()          // sala -> ultimo anuncio
        fun emitir() = trySend(vistas.keys.toSet())

        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val crudo = result.scanRecord
                    ?.getManufacturerSpecificData(PacketCodec.MANUFACTURER_ID) ?: return
                if (!PacketCodec.esAulaAbierta(crudo)) return
                val sala = PacketCodec.salaDe(crudo) ?: return
                vistas[sala] = System.currentTimeMillis()
                emitir()
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e("VIGIA_BLE", "Escaneo de aulas fallo: $errorCode")
            }
        }

        arrancar(callback)
        emitir()

        val limpieza = launch {
            while (isActive) {
                delay(2_000)
                val corte = System.currentTimeMillis() - CADUCIDAD_MS
                if (vistas.values.any { it < corte }) {
                    vistas.entries.removeAll { it.value < corte }
                    emitir()
                }
            }
        }

        awaitClose { limpieza.cancel(); detener(callback) }
    }

    /** Anuncios de estado de los equipos que declaran el aula indicada. */
    @SuppressLint("MissingPermission")
    fun alumnos(sala: Int) = callbackFlow<List<AlumnoVigilado>> {

        val vistos = linkedMapOf<Int, StudentStatus>()
        val ausentes = mutableMapOf<Int, Long>()          // id -> cuando se perdio
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
                if (status.sala != sala) return          // es de otro salon

                // Primera vez que lo vemos
                if (!vistos.containsKey(status.id)) {
                    Bitacora.registrar(status.etiqueta, Evento.UNIDO)
                    if (!PadronStore.aceptado(status.id)) {
                        Bitacora.registrar(status.etiqueta, Evento.NO_REGISTRADO)
                    }
                }

                // Estaba ausente y volvio
                if (ausentes.remove(status.id) != null) {
                    Bitacora.registrar(status.etiqueta, Evento.REGRESO)
                }

                // Una incidencia es una ENTRADA en ALERTA, no cada anuncio en ALERTA:
                // si no, agitar el equipo sumaria decenas de puntos por segundo.
                val antes = riesgoPrevio[status.id]
                if (status.risk == RiskLevel.ALERTA && antes != RiskLevel.ALERTA) {
                    incidencias[status.id] = (incidencias[status.id] ?: 0) + 1
                    Bitacora.registrar(status.etiqueta, Evento.ALERTA)
                }
                riesgoPrevio[status.id] = status.risk

                // Salir de la app tambien se cuenta por transicion
                if (status.salioDeLaApp && salioPrevio[status.id] != true) {
                    Bitacora.registrar(status.etiqueta, Evento.SALIO_APP)
                }
                salioPrevio[status.id] = status.salioDeLaApp

                vistos[status.id] = status
                emitir()
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e("VIGIA_BLE", "Escaneo de alumnos fallo: $errorCode")
            }
        }

        arrancar(callback)
        emitir()

        // Ya NO se borra a nadie: se marca como ausente y se queda a la vista.
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

        awaitClose { limpieza.cancel(); detener(callback) }
    }


    // ---------------------------------------------------------------- interno

    @SuppressLint("MissingPermission")
    private fun arrancar(callback: ScanCallback) {
        val filtro = ScanFilter.Builder()
            .setManufacturerData(PacketCodec.MANUFACTURER_ID, byteArrayOf(), byteArrayOf())
            .build()
        val ajustes = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner?.startScan(listOf(filtro), ajustes, callback) }
    }

    @SuppressLint("MissingPermission")
    private fun detener(callback: ScanCallback) {
        runCatching { scanner?.stopScan(callback) }
    }

    companion object {
        /** Sin señal por este tiempo, el aula o el alumno desaparecen. */
        const val CADUCIDAD_MS = 15_000L
    }
}
