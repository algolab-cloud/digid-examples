package tz.co.digid.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tz.co.digid.example.ui.CameraScreen
import tz.co.digid.example.ui.IntroScreen
import tz.co.digid.example.ui.ProblemScreen
import tz.co.digid.example.ui.ProvideLang
import tz.co.digid.example.ui.ResultScreen
import tz.co.digid.example.ui.StartScreen
import tz.co.digid.example.ui.theme.DigidExampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigidExampleTheme { ExampleNavHost() }
        }
    }
}

/**
 * The app's own start screen, then the screens of the digid drop-in flow (minimal layout):
 * intro → camera → result, and a message screen for blocking problems. One ViewModel drives
 * them; a navigation library would do the same job.
 */
@Composable
private fun ExampleNavHost(viewModel: KycViewModel = viewModel(factory = KycViewModel.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val engine by viewModel.engineState.collectAsStateWithLifecycle()

    ProvideLang(ui.settings.lang) {
        when (val screen = ui.screen) {
            Screen.Start -> StartScreen(
                settings = ui.settings,
                engine = engine,
                onWithSelfie = viewModel::setWithSelfie,
                onScanBack = viewModel::setScanBack,
                onLang = viewModel::setLang,
                onStart = viewModel::openIntro,
            )

            Screen.Intro -> {
                BackHandler(onBack = viewModel::backToStart)
                IntroScreen(settings = ui.settings, onBack = viewModel::backToStart, onOpenCamera = viewModel::start)
            }

            is Screen.Camera -> {
                BackHandler(onBack = viewModel::cancel)
                CameraScreen(
                    capture = screen.capture,
                    lang = ui.settings.lang,
                    analyzerFor = viewModel::analyzer,
                    frameExecutor = viewModel.frameExecutor,
                    onImageCapture = { viewModel.imageCapture = it },
                    onShutter = viewModel::takePhoto,
                    onBack = viewModel::cancel,
                    onFinishNow = if (BuildConfig.DEBUG) viewModel::finishNow else null,
                )
            }

            is Screen.Result -> {
                BackHandler(onBack = viewModel::backToStart)
                ResultScreen(
                    result = screen.result,
                    onDone = viewModel::backToStart,
                    onTryAgain = viewModel::start,
                )
            }

            is Screen.Message -> {
                BackHandler(onBack = viewModel::dismissProblem)
                ProblemScreen(screen, onBack = viewModel::dismissProblem, onRetry = viewModel::retryOnline)
            }
        }
    }
}
