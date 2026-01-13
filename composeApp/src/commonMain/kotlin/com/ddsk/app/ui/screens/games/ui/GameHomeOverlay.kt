package com.ddsk.app.ui.screens.games.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator

/**
 * Consistent home button placement for game screens.
 *
 * Use inside a Box/BoxWithConstraints so the button overlays at the top-left.
 */
@Composable
fun BoxScope.GameHomeOverlay(
    navigator: Navigator,
) {
    GameHomeButton(
        onClick = {
            // Pop back to the root (home) screen.
            while (navigator.canPop) {
                navigator.pop()
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .size(48.dp)
    )
}
