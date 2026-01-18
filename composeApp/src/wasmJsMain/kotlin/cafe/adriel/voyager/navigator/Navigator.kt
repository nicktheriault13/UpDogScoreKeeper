package cafe.adriel.voyager.navigator

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.screen.Screen

/**
 * Minimal Navigator shim for wasmJs that provides a simple navigation stack.
 */
class Navigator(initialScreen: Screen) {
    private val stack = mutableStateListOf(initialScreen)

    val lastItem: Screen
        get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size > 1) {
            stack.removeLast()
            return true
        }
        return false
    }

    fun replace(screen: Screen) {
        if (stack.isNotEmpty()) {
            stack[stack.lastIndex] = screen
        }
    }

    fun replaceAll(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}

/**
 * Internal composition local for navigator storage.
 */
private val NavigatorLocal = compositionLocalOf<Navigator?> { null }

/**
 * Public accessor object that provides the Voyager-compatible API.
 */
object LocalNavigator {
    val current: Navigator?
        @Composable
        get() = NavigatorLocal.current

    val currentOrThrow: Navigator
        @Composable
        get() = current ?: error("No Navigator in composition")

    infix fun provides(value: Navigator) = NavigatorLocal provides value
}

/**
 * Top-level property for compatibility with imports like:
 * import cafe.adriel.voyager.navigator.currentOrThrow
 * This delegates to LocalNavigator.currentOrThrow
 */
val currentOrThrow: Navigator
    @Composable
    get() = LocalNavigator.currentOrThrow

@Composable
fun Navigator(
    screen: Screen,
    content: @Composable (Navigator) -> Unit = { navigator ->
        navigator.lastItem.Content()
    }
) {
    val navigator = remember { Navigator(screen) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        content(navigator)
    }
}
