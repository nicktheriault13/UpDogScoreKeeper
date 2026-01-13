package com.ddsk.app.ui.screens.games.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Small top-left Home button used on every game page.
 */
@Composable
fun GameHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colors.primary,
    contentColor: Color = MaterialTheme.colors.onPrimary,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier,
        elevation = ButtonDefaults.elevation(defaultElevation = 6.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text("Home")
    }
}
