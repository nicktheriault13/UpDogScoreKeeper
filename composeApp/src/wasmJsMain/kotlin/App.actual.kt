import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.ddsk.app.ui.screens.auth.LoginScreen

@Composable
actual fun App() {
    // Web/Wasm: Full UI with custom Voyager shim (Voyager 1.0.0 doesn't support wasm).
    // Uses DemoAuthService (no Firebase on web).
    MaterialTheme {
        Navigator(LoginScreen())
    }
}
