package com.ddsk.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * API client for posting game data to a remote database
 */
class ApiClient(private val baseUrl: String = ApiConfig.BASE_URL) {

    /**
     * Post JSON data to the API
     * @param endpoint The API endpoint path (e.g., "/api/games/fourway")
     * @param jsonContent The JSON content to post
     * @return true if successful, false otherwise
     */
    suspend fun postJson(endpoint: String, jsonContent: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!ApiConfig.ENABLED) {
            return@withContext Result.failure(Exception("API posting is disabled in config"))
        }

        try {
            postJsonInternal(baseUrl + endpoint, jsonContent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Platform-specific implementation of HTTP POST
 */
internal expect suspend fun postJsonInternal(url: String, jsonContent: String): Unit
