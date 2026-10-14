package net.zodac.dicefive

import android.app.Application
import net.zodac.dicefive.online.PlayGamesServices

class DiceFiveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Only sets Play Games up: it starts once the player has signed in from Settings.
        PlayGamesServices.initialize(this)
    }
}
