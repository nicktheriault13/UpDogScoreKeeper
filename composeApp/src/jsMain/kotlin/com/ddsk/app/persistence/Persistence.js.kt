package com.ddsk.app.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class LocalStorageDataStore : DataStore {
    override suspend fun save(fileName: String, content: String) {
        runCatching {
            kotlinx.browser.localStorage.setItem(fileName, content)
        }
    }

    override suspend fun load(fileName: String): String? {
        return runCatching { kotlinx.browser.localStorage.getItem(fileName) }.getOrNull()
    }

    override suspend fun delete(fileName: String) {
        runCatching { kotlinx.browser.localStorage.removeItem(fileName) }
    }
}

@Composable
actual fun rememberDataStore(): DataStore = remember { LocalStorageDataStore() }
