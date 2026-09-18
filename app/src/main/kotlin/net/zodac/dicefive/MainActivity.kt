package net.zodac.dicefive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import net.zodac.dicefive.navigation.DiceFiveNavHost
import net.zodac.dicefive.ui.theme.DiceFiveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiceFiveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiceFiveNavHost()
                }
            }
        }
    }
}
