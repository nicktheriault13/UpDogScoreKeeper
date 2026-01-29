package com.ddsk.app.network

/**
 * API configuration settings
 *
 * CURRENTLY DISABLED - Re-enable when you have selected a database service
 */
object ApiConfig {
    /**
     * The base URL of your database API
     * Update this when you select your database service
     */
    const val BASE_URL = "https://your-api-endpoint.com"

    /**
     * Your database API authentication key
     * Update this with your actual key when ready
     */
    const val SUPABASE_ANON_KEY = ""

    /**
     * The endpoint path for FourWayPlay game data
     * This will be appended to BASE_URL
     */
    const val FOURWAY_ENDPOINT = "/api/games/fourway"

    /**
     * Connection timeout in milliseconds
     */
    const val TIMEOUT_MS = 15000

    /**
     * Enable/disable API posting
     * Set to true when you're ready to enable database integration
     */
    const val ENABLED = false  // DISABLED - Change to true when ready
}
