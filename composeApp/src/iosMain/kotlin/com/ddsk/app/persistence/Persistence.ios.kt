package com.ddsk.app.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.IO

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
actual fun rememberDataStore(): DataStore {
    return remember {
        object : DataStore {
            private fun getFileUrl(fileName: String): NSURL? {
                val fileManager = NSFileManager.defaultManager
                val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                val documentDirectory = urls.firstOrNull() as? NSURL ?: return null
                return documentDirectory.URLByAppendingPathComponent(fileName)
            }

            override suspend fun save(fileName: String, content: String) = withContext(Dispatchers.IO) {
                val url = getFileUrl(fileName) ?: return@withContext
                val data = NSString.create(string = content).dataUsingEncoding(NSUTF8StringEncoding)
                data?.writeToURL(url, true)
                Unit
            }

            override suspend fun load(fileName: String): String? = withContext(Dispatchers.IO) {
                val url = getFileUrl(fileName) ?: return@withContext null
                // Check if file exists
                if (NSFileManager.defaultManager.fileExists(url.path!!)) {
                     return@withContext NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) as String?
                }
                return@withContext null
            }

            override suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
                val url = getFileUrl(fileName) ?: return@withContext
                if (NSFileManager.defaultManager.fileExists(url.path!!)) {
                    NSFileManager.defaultManager.removeItemAtURL(url, null)
                }
                Unit
            }
        }
    }
}

