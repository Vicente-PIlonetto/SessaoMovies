package dev.viko.sessao

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.viko.sessao.data.settings.Settings
import dev.viko.sessao.data.settings.ThemeMode
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.nav.AppRoot
import dev.viko.sessao.ui.nav.LocalContainer
import dev.viko.sessao.ui.theme.SessaoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Título a abrir (vindo de uma notificação), como "tv:1234". */
    private val openTitle = mutableStateOf<String?>(null)
    private val container get() = (application as App).container
    private var latest: Settings? = null

    /** O ícone só troca ao sair do app: trocar com o app aberto pode fechá-lo em alguns lançadores. */
    override fun onStop() {
        super.onStop()
        latest?.let { s -> runCatching { dev.viko.sessao.ui.AppIcon.apply(this, s.accent) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Segura o splash até ler as configurações (evita piscar claro e depois escuro).
        val firstSettings = mutableStateOf<Settings?>(null)
        splash.setKeepOnScreenCondition { firstSettings.value == null }
        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                if (firstSettings.value == null) firstSettings.value = s
                latest = s
            }
        }
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val first = firstSettings.value ?: return@setContent
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = first)
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.OLED -> true
            }
            LaunchedEffect(dark, settings.oled, settings.theme) {
                val bg = if (dark) (if (settings.oled || settings.theme == ThemeMode.OLED) 0xFF000000.toInt() else 0xFF161616.toInt()) else 0xFFF6F4EF.toInt()
                val style = if (dark) SystemBarStyle.dark(bg) else SystemBarStyle.light(bg, bg)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SessaoTheme(settings.theme, settings.accent, settings.materialYou, settings.oled) {
                CompositionLocalProvider(LocalSettings provides settings, LocalContainer provides container) {
                    AppRoot(openTitle = openTitle.value, onOpened = { openTitle.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TITLE)?.let { openTitle.value = it }
    }

    companion object {
        const val EXTRA_TITLE = "title_key"
    }
}
