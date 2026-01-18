import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.ddsk.app.ui.screens.auth.LoginScreen

@Composable
actual fun App() {
    // Web/JS: Full UI without Koin DI (screens create their own models).
    // Uses DemoAuthService (no Firebase on web).
    MaterialTheme {
        Navigator(LoginScreen())
    }
}
