package org.aprsdroid.app.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Records Wi-Fi network lifecycle changes so IC-705 failures can be separated from Android Wi-Fi loss. */
object NetworkEventLogger {
    @Volatile
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        synchronized(this) {
            if (registered) return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            runCatching {
                connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        AppLog.i("NET", "wifi_available", mapOf("network" to network.toString()))
                        updateSnapshot(connectivity, network)
                    }

                    override fun onLost(network: Network) {
                        AppLog.w("NET", "wifi_lost", mapOf("network" to network.toString()))
                        val selected = AppLog.snapshotState()["ic705.network"]
                        if (selected == network.toString()) {
                            AppLog.setState("ic705.network_status", "LOST")
                        }
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        AppLog.i(
                            "NET",
                            "wifi_capabilities_changed",
                            mapOf(
                                "network" to network.toString(),
                                "validated" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                                "internet" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                                "not_metered" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                            ),
                        )
                        updateSnapshot(connectivity, network)
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        AppLog.i(
                            "NET",
                            "wifi_link_properties_changed",
                            mapOf(
                                "network" to network.toString(),
                                "interface" to (linkProperties.interfaceName ?: "null"),
                                "addresses" to linkProperties.linkAddresses.joinToString(",") { it.toString() },
                                "routes" to linkProperties.routes.joinToString(";") { it.toString() },
                            ),
                        )
                        updateSnapshot(connectivity, network)
                    }
                })
                registered = true
            }.onFailure {
                AppLog.w("NET", "wifi_callback_registration_failed", error = it)
            }
        }
    }

    fun snapshot(context: Context): List<String> {
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        return runCatching {
            @Suppress("DEPRECATION")
            connectivity.allNetworks.map { network -> describe(connectivity, network) }
        }.getOrElse {
            listOf("network snapshot failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun updateSnapshot(connectivity: ConnectivityManager, network: Network) {
        val selected = AppLog.snapshotState()["ic705.network"]
        if (selected != network.toString()) return
        val caps = connectivity.getNetworkCapabilities(network)
        val links = connectivity.getLinkProperties(network)
        AppLog.setState("ic705.network_status", "AVAILABLE")
        AppLog.setState("ic705.network_validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        AppLog.setState("ic705.network_interface", links?.interfaceName)
        AppLog.setState("ic705.network_addresses", links?.linkAddresses?.joinToString(",") { it.toString() })
    }

    private fun describe(connectivity: ConnectivityManager, network: Network): String {
        val caps = connectivity.getNetworkCapabilities(network)
        val links = connectivity.getLinkProperties(network)
        return buildString {
            append("network=").append(network)
            append(" wifi=").append(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            append(" validated=").append(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            append(" internet=").append(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            append(" interface=").append(links?.interfaceName)
            append(" addresses=").append(links?.linkAddresses?.joinToString(","))
            append(" routes=").append(links?.routes?.joinToString(";"))
        }
    }
}
