package net.zodac.dicefive

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.dicefive.online.GameServices
import net.zodac.dicefive.online.PlayGamesServices
import net.zodac.dicefive.platform.ProgressFiles
import net.zodac.dicefive.purchases.PlayBillingStore
import net.zodac.dicefive.purchases.Store

/** The play flavour's own part of the app, for Google Play: Google Play Games, Play Billing, and no progress files. */
internal object Distribution {

    /** Only sets Play Games up: it starts once the player has signed in from Settings. */
    fun initialize(application: Application) = PlayGamesServices.initialize(application)

    fun gameServices(activity: ComponentActivity): GameServices = PlayGamesServices.attach(activity)

    fun store(activity: ComponentActivity): Store = PlayBillingStore.attach(activity)

    /** None: a file's progress could otherwise reach Play Games' achievements and leaderboards. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles? = null
}
