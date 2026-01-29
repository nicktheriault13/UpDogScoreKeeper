package com.ddsk.app.network

/**
 * Platform-specific network connectivity checker
 */
interface NetworkConnectivity {
    /**
     * Check if the device has an active internet connection
     */
    suspend fun isConnected(): Boolean
}

/**
 * Get the platform-specific NetworkConnectivity instance
 */
expect fun getNetworkConnectivity(): NetworkConnectivity
