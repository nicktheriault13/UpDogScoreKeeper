package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable
import com.ddsk.app.ui.screens.games.GreedyScreenModel
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    return object : FilePickerLauncher {
        override fun launch() {
            // Not implemented for iOS in this task
            onResult(ImportResult.Error("Not supported on iOS yet"))
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> {
    return emptyList() // Not supported
}

actual fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray {
    return ByteArray(0)
}

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    return emptyList()
}

@Composable
actual fun rememberFileExporter(): FileExporter {
    return object : FileExporter {
        override fun save(fileName: String, data: ByteArray) {
            println("Export not supported on iOS")
        }
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader {
    return object : AssetLoader {
        override fun load(path: String): ByteArray? {
            val bundle = NSBundle.mainBundle
            val paths = listOf(
                "assets/$path",
                "compose-resources/assets/$path",
                path
            )

            for (p in paths) {
                // Try to find resource with extension separate
                val lastDot = p.lastIndexOf('.')
                if (lastDot != -1) {
                    val name = p.substring(0, lastDot)
                    val ext = p.substring(lastDot + 1)
                     val filePath = bundle.pathForResource(name, ofType = ext)
                     if (filePath != null) {
                         return NSData.dataWithContentsOfFile(filePath)?.toByteArray()
                     }
                }

                // Try exact match
                val filePath = bundle.pathForResource(p, ofType = null)
                 if (filePath != null) {
                     return NSData.dataWithContentsOfFile(filePath)?.toByteArray()
                 }
            }
            return null
        }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val buffer = ByteArray(size)
    if (size > 0) {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return buffer
}

actual fun generateGreedyXlsx(participants: List<GreedyScreenModel.GreedyParticipant>, templateBytes: ByteArray): ByteArray {
    // iOS implementation pending
    return ByteArray(0)
}

actual fun generateFourWayPlayXlsx(participants: List<FourWayPlayExportParticipant>, templateBytes: ByteArray): ByteArray {
    return ByteArray(0)
}
