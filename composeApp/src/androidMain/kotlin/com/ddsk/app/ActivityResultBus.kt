package com.ddsk.app

import android.content.Intent

object ActivityResultBus {
    private val callbacks = mutableMapOf<Int, (resultCode: Int, data: Intent?) -> Unit>()
    private val lock = Any()

    fun register(requestCode: Int, callback: (resultCode: Int, data: Intent?) -> Unit) {
        synchronized(lock) { callbacks[requestCode] = callback }
    }

    fun unregister(requestCode: Int) {
        synchronized(lock) { callbacks.remove(requestCode) }
    }

    fun dispatch(requestCode: Int, resultCode: Int, data: Intent?) {
        val callback = synchronized(lock) { callbacks.remove(requestCode) } ?: return
        callback(resultCode, data)
    }
}

