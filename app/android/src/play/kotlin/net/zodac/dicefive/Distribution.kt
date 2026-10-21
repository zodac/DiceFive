package net.zodac.dicefive

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.PlayGamesServices

/** The play flavour's own part of the app, for Google Play: Google Play Games. */
internal object Distribution {

    /** Only sets Play Games up: it starts once the player has signed in from Settings. */
    fun initialize(application: Application) = PlayGamesServices.initialize(application)

    fun gameServices(activity: ComponentActivity): GameServices = PlayGamesServices.attach(activity)
}
