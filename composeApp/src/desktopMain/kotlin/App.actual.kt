import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.Navigator
import com.ddsk.app.di.appModule
import com.ddsk.app.di.platformModule
import com.ddsk.app.ui.screens.auth.LoginScreen
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

@Composable
actual fun App() {
    LaunchedEffect(Unit) {
        runCatching { stopKoin() }
        startKoin { modules(appModule, platformModule) }
    }

    MaterialTheme {
        Navigator(LoginScreen())
    }
}
