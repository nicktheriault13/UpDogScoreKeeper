package cafe.adriel.voyager.navigator.tab

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter

/**
 * Minimal Tab navigation shim for wasmJs.
 */
interface Tab {
    val options: TabOptions
        @Composable get

    @Composable
    fun Content()
}

data class TabOptions(
    val index: UShort,
    val title: String,
    val icon: Painter? = null
)

/**
 * Minimal TabNavigator for wasmJs.
 */
class TabNavigator(initialTab: Tab) {
    var current by mutableStateOf(initialTab)
        private set

    fun navigate(tab: Tab) {
        current = tab
    }
}

/**
 * Internal composition local for tab navigator storage.
 */
private val TabNavigatorLocal = compositionLocalOf<TabNavigator?> { null }

/**
 * Public accessor object that provides the Voyager-compatible API.
 */
object LocalTabNavigator {
    val current: TabNavigator?
        @Composable
        get() = TabNavigatorLocal.current

    val currentOrThrow: TabNavigator
        @Composable
        get() = current ?: error("No TabNavigator in composition")

    infix fun provides(value: TabNavigator) = TabNavigatorLocal provides value
}

/**
 * Top-level property for compatibility with imports like:
 * import cafe.adriel.voyager.navigator.tab.currentOrThrow
 * This delegates to LocalTabNavigator.currentOrThrow
 */
val currentOrThrow: TabNavigator
    @Composable
    get() = LocalTabNavigator.currentOrThrow

@Composable
fun TabNavigator(
    initialTab: Tab,
    content: @Composable (TabNavigator) -> Unit
) {
    val navigator = remember { TabNavigator(initialTab) }

    CompositionLocalProvider(LocalTabNavigator provides navigator) {
        content(navigator)
    }
}
