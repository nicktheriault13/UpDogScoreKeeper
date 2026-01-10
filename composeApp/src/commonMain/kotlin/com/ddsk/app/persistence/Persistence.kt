package com.ddsk.app.persistence

import androidx.compose.runtime.Composable

interface DataStore {
    suspend fun save(fileName: String, content: String)
    suspend fun load(fileName: String): String?
    suspend fun delete(fileName: String)
}

@Composable
expect fun rememberDataStore(): DataStore

