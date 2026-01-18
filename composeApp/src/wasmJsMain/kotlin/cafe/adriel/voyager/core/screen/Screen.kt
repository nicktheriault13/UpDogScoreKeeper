package cafe.adriel.voyager.core.screen

import androidx.compose.runtime.Composable

/**
 * Lightweight Voyager Screen API shim for wasmJs.
 * Allows reusing existing game screens without the full Voyager dependency.
 */
interface Screen {
    @Composable
    fun Content()
}
