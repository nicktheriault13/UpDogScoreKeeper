package com.ddsk.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Android implementation of NetworkConnectivity
 */
class AndroidNetworkConnectivity(private val context: Context) : NetworkConnectivity {
    override suspend fun isConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

actual fun getNetworkConnectivity(): NetworkConnectivity {
    // This will be injected via Koin or passed from Android context
    throw IllegalStateException("NetworkConnectivity must be provided via dependency injection on Android")
}
