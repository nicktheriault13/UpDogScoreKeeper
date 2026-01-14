package com.ddsk.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator

/**
 * Wrap a screen's content with a consistently-positioned Home button pinned to the top-left.
 *
 * This avoids per-screen placement tweaks and ensures content has enough top/left padding
 * so it doesn't sit underneath the button.
 */
@Composable
fun HomeButtonFrame(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    // Reserve space for the button so content can't overlap the top-left corner.
    contentPadding: PaddingValues = PaddingValues(start = 72.dp, top = 72.dp, end = 16.dp, bottom = 16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main screen content.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            content()
        }

        // Overlayed Home button.
        GameHomeButton(
            navigator = navigator,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
    }
}

