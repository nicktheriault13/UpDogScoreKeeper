import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
actual fun App() {
    // Minimal web UI for now (no Voyager/Koin on JS target yet).
    Div {
        Text("UpDogScoreKeeper (Web)")
    }
}
