package com.ddsk.app.network

import java.net.HttpURLConnection
import java.net.URL

/**
 * Desktop (JVM) implementation of NetworkConnectivity
 */
class DesktopNetworkConnectivity : NetworkConnectivity {
    override suspend fun isConnected(): Boolean {
        return try {
            // Try to connect to a reliable endpoint
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
}

actual fun getNetworkConnectivity(): NetworkConnectivity {
    return DesktopNetworkConnectivity()
}
