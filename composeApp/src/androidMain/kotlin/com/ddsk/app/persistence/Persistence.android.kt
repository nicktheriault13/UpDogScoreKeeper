package com.ddsk.app.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberDataStore(): DataStore {
    val context = LocalContext.current
    return remember(context) {
        object : DataStore {
            private fun getFile(fileName: String): File = File(context.filesDir, fileName)

            override suspend fun save(fileName: String, content: String) = withContext(Dispatchers.IO) {
                try {
                    getFile(fileName).writeText(content)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override suspend fun load(fileName: String): String? = withContext(Dispatchers.IO) {
                try {
                    val file = getFile(fileName)
                    if (file.exists()) file.readText() else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            override suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
                try {
                    val file = getFile(fileName)
                    if (file.exists()) file.delete()
                    Unit
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

