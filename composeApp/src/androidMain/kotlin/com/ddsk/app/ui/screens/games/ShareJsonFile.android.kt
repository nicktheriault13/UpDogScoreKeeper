package com.ddsk.app.ui.screens.games

import android.content.Intent
import androidx.core.content.FileProvider
import com.ddsk.app.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun saveJsonFileWithPicker(filename: String, content: String) {
    // Android: Use ACTION_SEND with a temp file in cache. This always prompts the user via chooser.
    // Note: True location picking (CreateDocument) requires an ActivityResult launcher in UI; this is
    // a practical cross-screen approach that still asks the user where to send/save.
    withContext(Dispatchers.IO) {
        val ctx = appContext
        val cacheDir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(cacheDir, filename)
        outFile.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", outFile)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Save JSON")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ctx.startActivity(chooser)
    }
}

@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
actual suspend fun shareJsonFile(filename: String, content: String) {
    saveJsonFileWithPicker(filename, content)
}
