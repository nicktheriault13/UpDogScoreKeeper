package cafe.adriel.voyager.core.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.remember

/**
 * Minimal ScreenModel shim for wasmJs.
 */
interface ScreenModel {
    fun onDispose() {}
}

@Composable
inline fun <reified T : ScreenModel> rememberScreenModel(
    crossinline factory: @DisallowComposableCalls () -> T
): T {
    return remember { factory() }
}
