package com.ddsk.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import com.ddsk.app.ui.screens.MainScreen

/**
 * Small, consistent "Home" button for game screens.
 *
 * Uses [Navigator.replaceAll] so we don't stack multiple pages when a user bounces
 * between games and home.
 */
@Composable
fun GameHomeButton(
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    // A small circular background ensures the icon remains visible on every game page.
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        elevation = 6.dp
    ) {
        IconButton(onClick = { navigator.replaceAll(MainScreen()) }) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = "Home",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
