package net.zodac.dicefive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.data.settings.Theme
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.ui.theme.DiceFiveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val theme by settingsRepository.theme.collectAsState(initial = Theme.SYSTEM)
            val darkTheme = when (theme) {
                Theme.LIGHT -> false
                Theme.DARK -> true
                Theme.SYSTEM -> isSystemInDarkTheme()
            }

            DiceFiveTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiceFiveNavHost()
                }
            }
        }
    }
}
