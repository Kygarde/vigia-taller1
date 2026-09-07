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

        // Historial de la sesion. No se limpia cuando un alumno desaparece: si sale
        // del alcance y vuelve, sus incidencias siguen contando.
        val incidencias = mutableMapOf<Int, Int>()
        val riesgoPrevio = mutableMapOf<Int, RiskLevel>()

        fun emitir() {
            trySend(
                vistos.values
                    .map { AlumnoVigilado(it, incidencias[it.id] ?: 0) }
                    .sortedWith(
                        compareByDescending<AlumnoVigilado> { it.estado.risk.ordinal }
                            .thenByDescending { it.incidencias }
                            .thenBy { it.estado.id }
                    )
            )
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val crudo = result.scanRecord
                    ?.getManufacturerSpecificData(PacketCodec.MANUFACTURER_ID) ?: return
                // Un paquete corrupto no debe tumbar la pantalla del docente.
                val status = runCatching { PacketCodec.decodeAlumno(crudo) }.getOrNull() ?: return
                if (status.sala != sala) return          // es de otro salon

                // Una incidencia es una ENTRADA en ALERTA, no cada anuncio en ALERTA:
                // si no, un alumno agitando el equipo sumaria decenas de puntos por segundo.
                val antes = riesgoPrevio[status.id]
                if (status.risk == RiskLevel.ALERTA && antes != RiskLevel.ALERTA) {
                    incidencias[status.id] = (incidencias[status.id] ?: 0) + 1
                }
                riesgoPrevio[status.id] = status.risk

                vistos[status.id] = status
                emitir()
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e("VIGIA_BLE", "Escaneo de alumnos fallo: $errorCode")
            }
        }

        arrancar(callback)
        emitir()

        // Da de baja a los equipos que dejaron de emitir.
        val limpieza = launch {
            while (isActive) {
                delay(2_000)
                val corte = System.currentTimeMillis() - CADUCIDAD_MS
                if (vistos.values.any { it.lastSeen < corte }) {
                    vistos.entries.removeAll { it.value.lastSeen < corte }
                    emitir()
                }
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
