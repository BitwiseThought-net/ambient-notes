package com.ambientnotes.app

import android.app.Application
import com.ambientnotes.app.di.AppContainer

class AmbientNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
