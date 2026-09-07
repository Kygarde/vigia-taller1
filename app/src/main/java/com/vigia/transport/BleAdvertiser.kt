package com.vigia.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resultado real del anuncio. startAdvertising es asincrono: no basta con que no lance. */
sealed interface EstadoAnuncio {
    data object Detenido : EstadoAnuncio
    data object Esperando : EstadoAnuncio
    data object Anunciando : EstadoAnuncio
    data object SinPermiso : EstadoAnuncio
    data object BluetoothApagado : EstadoAnuncio
    data object NoSoportado : EstadoAnuncio
    data class Fallo(val codigo: Int) : EstadoAnuncio

    val mensaje: String
        get() = when (this) {
            Detenido -> "sin anunciar"
            Esperando -> "iniciando…"
            Anunciando -> "anunciando"
            SinPermiso -> "falta el permiso de Bluetooth"
            BluetoothApagado -> "el Bluetooth está apagado"
            NoSoportado -> "este equipo no puede emitir por Bluetooth"
            is Fallo -> when (codigo) {
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "el paquete es demasiado grande"
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "el equipo ya tiene demasiados anuncios activos"
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "el anuncio ya estaba activo"
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "este equipo no puede emitir por Bluetooth"
                else -> "error interno del Bluetooth ($codigo)"
            }
        }
}

class BleAdvertiser(context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser
    private var callback: AdvertiseCallback? = null

    private val _estado = MutableStateFlow<EstadoAnuncio>(EstadoAnuncio.Detenido)
    val estado = _estado.asStateFlow()

    /** false si el equipo no tiene hardware para emitir anuncios BLE. */
    val soportado: Boolean get() = adapter?.bluetoothLeAdvertiser != null

    @SuppressLint("MissingPermission")
    fun publish(payload: ByteArray) {
        stop()

        if (adapter?.isEnabled != true) {
            _estado.value = EstadoAnuncio.BluetoothApagado
            return
        }
        val emisor = advertiser
        if (emisor == null) {
            _estado.value = EstadoAnuncio.NoSoportado
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)          // el nombre del equipo no cabe y no hace falta
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(PacketCodec.MANUFACTURER_ID, payload)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                _estado.value = EstadoAnuncio.Anunciando
            }
            override fun onStartFailure(errorCode: Int) {
                _estado.value = EstadoAnuncio.Fallo(errorCode)
                android.util.Log.e("VIGIA_BLE", "Advertising fallo: $errorCode")
            }
        }
        callback = cb
        _estado.value = EstadoAnuncio.Esperando

        try {
            emisor.startAdvertising(settings, data, cb)
        } catch (e: SecurityException) {
            _estado.value = EstadoAnuncio.SinPermiso
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { cb -> runCatching { advertiser?.stopAdvertising(cb) } }
        callback = null
        _estado.value = EstadoAnuncio.Detenido
    }
}
