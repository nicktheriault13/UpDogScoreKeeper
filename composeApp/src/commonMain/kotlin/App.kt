import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.ddsk.app.di.appModule
import com.ddsk.app.ui.screens.auth.LoginScreen
import org.koin.compose.KoinApplication

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme {
            Navigator(LoginScreen())
        }
    }
}
