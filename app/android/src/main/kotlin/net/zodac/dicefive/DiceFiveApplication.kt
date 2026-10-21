package net.zodac.dicefive

import android.app.Application

class DiceFiveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Distribution.initialize(this)
    }
}
