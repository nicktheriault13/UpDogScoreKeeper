package com.ddsk.app.ui.screens.games

import android.content.Intent
import androidx.core.content.FileProvider
import com.ddsk.app.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun shareJsonFile(filename: String, content: String) {
    val context = runCatching { appContext }.getOrNull() ?: return
    val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
    val file = File(dir, filename)
    withContext(Dispatchers.IO) {
        file.writeText(content)
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share log"))
}
