package org.aprsdroid.app.ic705.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory

/**
 * Finds the currently attached Wi-Fi network even when Android does not select
 * it as the default network because the IC-705 access point has no Internet.
 */
object Ic705WifiNetworkSelector {
    fun find(context: Context): Network? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null

        // 1. Prefer active network if it has Wi-Fi transport
        val active = connectivity.activeNetwork
        if (active != null) {
            val caps = connectivity.getNetworkCapabilities(active)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return active
            }
        }

        // 2. Safe scan for attached Wi-Fi networks (e.g. IC-705 AP without internet)
        return runCatching {
            @Suppress("DEPRECATION")
            connectivity.allNetworks.firstOrNull { network ->
                connectivity.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }.getOrNull()
    }
}

/**
 * Creates a UDP socket routed only through the selected Wi-Fi [Network]. Process-wide routing is
 * deliberately avoided so APRS-IS and map traffic can continue using cellular.
 */
class Ic705AndroidNetworkSocketFactory(
    private val network: Network,
    private val wifiIpv4Address: InetAddress? = null,
) : Ic705DatagramSocketFactory {
    override fun create(localAddress: InetSocketAddress): DatagramSocket {
        val socket = DatagramSocket(null)
        try {
            network.bindSocket(socket)
            socket.broadcast = true
            val bindAddress = if (
                localAddress.address?.isAnyLocalAddress != false &&
                wifiIpv4Address is Inet4Address
            ) {
                InetSocketAddress(wifiIpv4Address, localAddress.port)
            } else {
                localAddress
            }
            socket.bind(bindAddress)
            return socket
        } catch (error: Exception) {
            socket.close()
            if (error is SocketException) throw error
            throw SocketException("Could not bind IC-705 UDP socket to Wi-Fi").apply {
                initCause(error)
            }
        }
    }

}

/** API-isolated entry point whose signature contains only project-owned types. */
object Ic705AndroidSocketFactoryProvider {
    fun forCurrentWifi(context: Context): Ic705DatagramSocketFactory? {
        val applicationContext = context.applicationContext
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        val network = Ic705WifiNetworkSelector.find(applicationContext) ?: return null
        val wifiIpv4Address = connectivity.getLinkProperties(network)
            ?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isAnyLocalAddress && !it.isLoopbackAddress }
        return Ic705AndroidNetworkSocketFactory(network, wifiIpv4Address)
    }
}
