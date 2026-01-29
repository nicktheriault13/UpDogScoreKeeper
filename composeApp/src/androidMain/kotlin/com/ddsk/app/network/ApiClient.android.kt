package com.ddsk.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android implementation of HTTP POST
 */
internal actual suspend fun postJsonInternal(url: String, jsonContent: String): Unit = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        Log.d("ApiClient", "Attempting to POST data to: $url")
        Log.d("ApiClient", "JSON payload size: ${jsonContent.length} bytes")

        val urlObj = URL(url)
        connection = urlObj.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")

        // Add Supabase authentication headers if key is provided
        if (ApiConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            connection.setRequestProperty("apikey", ApiConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${ApiConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Prefer", "return=representation")
            Log.d("ApiClient", "Supabase auth headers added")
        }

        connection.doOutput = true
        connection.connectTimeout = ApiConfig.TIMEOUT_MS
        connection.readTimeout = ApiConfig.TIMEOUT_MS

        // Write JSON content
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(jsonContent)
            writer.flush()
        }
        Log.d("ApiClient", "JSON data written to request body")

        val responseCode = connection.responseCode
        Log.d("ApiClient", "Response code: $responseCode")

        if (responseCode in 200..299) {
            // Read success response
            val response = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
            Log.d("ApiClient", "Successfully posted data to $url")
            if (response.isNotBlank()) {
                Log.d("ApiClient", "Response body: ${response.take(200)}...") // Log first 200 chars
            }
        } else {
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            } catch (e: Exception) {
                "Could not read error stream"
            }
            Log.e("ApiClient", "HTTP error code: $responseCode")
            Log.e("ApiClient", "Error response: $errorBody")
            throw Exception("HTTP error code: $responseCode - $errorBody")
        }
    } catch (e: Exception) {
        Log.e("ApiClient", "Failed to post JSON to $url", e)
        Log.e("ApiClient", "Error details: ${e.message}")
        throw e
    } finally {
        connection?.disconnect()
        Log.d("ApiClient", "Connection closed")
    }
}
