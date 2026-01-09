package com.ddsk.app.ui.screens.games

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerModeImport
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual suspend fun pickImportFile(): ImportResult = suspendCancellableCoroutine { continuation ->
    val controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: run {
        continuation.resume(ImportResult.None)
        return@suspendCancellableCoroutine
    }
    val picker = UIDocumentPickerViewController(documentTypes = listOf("public.comma-separated-values-text", "org.openxmlformats.spreadsheetml.sheet"), inMode = UIDocumentPickerModeImport)
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                continuation.resume(ImportResult.None)
            } else {
                val data = NSData.dataWithContentsOfURL(url)
                if (data == null) {
                    continuation.resume(ImportResult.None)
                } else {
                    val ext = url.pathExtension?.lowercaseString ?: ""
                    val bytes = data.toByteArray()
                    val result = if (ext == "xlsx" || ext == "xls" || ext == "xlsm") {
                        ImportResult.Xlsx(bytes)
                    } else {
                        ImportResult.Csv(bytes.decodeToString())
                    }
                    continuation.resume(result)
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            continuation.resume(ImportResult.None)
        }
    }
    picker.delegate = delegate
    controller.presentViewController(picker, true, completion = null)
    continuation.invokeOnCancellation { picker.dismissViewControllerAnimated(true, completion = null) }
}
