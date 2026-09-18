package net.zodac.yahtzee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import net.zodac.yahtzee.ui.game.GameScreen
import net.zodac.yahtzee.ui.game.GameViewModel
import net.zodac.yahtzee.ui.theme.YahtzeeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YahtzeeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameScreen(viewModel = viewModel)
                }
            }
        }
    }
}
