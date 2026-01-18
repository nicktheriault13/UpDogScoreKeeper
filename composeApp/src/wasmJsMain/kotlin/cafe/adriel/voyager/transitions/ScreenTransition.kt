package cafe.adriel.voyager.transitions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * Minimal transitions shim for wasmJs with simple fade animation.
 */
@Composable
fun ScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    transition: (Screen, Screen) -> ContentTransform = { _, _ ->
        fadeIn() togetherWith fadeOut()
    },
    content: @Composable (Screen) -> Unit = { it.Content() }
) {
    val currentScreen = navigator.lastItem

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            transition(initialState, targetState)
        },
        modifier = modifier
    ) { screen ->
        content(screen)
    }
}
