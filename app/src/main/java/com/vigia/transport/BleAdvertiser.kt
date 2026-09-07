package com.vigia.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context

class BleAdvertiser(context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var callback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun publish(payload: ByteArray) {
        stop()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(PacketCodec.MANUFACTURER_ID, payload)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                android.util.Log.e("VIGIA_BLE", "Advertising falló: $errorCode")
            }
        }
        advertiser?.startAdvertising(settings, data, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { advertiser?.stopAdvertising(it) }
        callback = null
    }
}