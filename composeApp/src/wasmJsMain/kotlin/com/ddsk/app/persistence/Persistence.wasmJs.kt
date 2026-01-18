package com.ddsk.app.persistence

import androidx.compose.runtime.Composable

private class WasmDataStore : DataStore {
    override suspend fun save(fileName: String, content: String) {}
    override suspend fun load(fileName: String): String? = null
    override suspend fun delete(fileName: String) {}
}

@Composable
actual fun rememberDataStore(): DataStore {
    return WasmDataStore()
}
