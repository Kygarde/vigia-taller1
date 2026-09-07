package com.vigia.sensing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class ConnectivityState(val wifi: Boolean, val mobile: Boolean)

/**
 * Transporte disponible: wifi y datos moviles. Requiere ACCESS_NETWORK_STATE.
 *
 * Revisa TODAS las redes del dispositivo, no solo la activa. Android usa una sola
 * red a la vez y prefiere el wifi, asi que mirando solo la activa los datos moviles
 * siempre saldrian como false mientras hubiera wifi conectado.
 */
class ConnectivityProvider(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val state = callbackFlow {
        fun read(): ConnectivityState {
            var wifi = false
            var mobile = false

            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mobile = true
            }
            return ConnectivityState(wifi, mobile)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(read()) }
            override fun onLost(network: Network) { trySend(read()) }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { trySend(read()) }
        }

        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        trySend(read())

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
