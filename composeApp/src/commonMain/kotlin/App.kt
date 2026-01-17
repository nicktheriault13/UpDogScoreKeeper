import androidx.compose.runtime.Composable

// App entry point is platform-specific:
// - Android/Desktop can use Koin + Voyager navigation
// - Web (JS) uses a lightweight UI (no Koin/Voyager for now)
@Composable
expect fun App()
