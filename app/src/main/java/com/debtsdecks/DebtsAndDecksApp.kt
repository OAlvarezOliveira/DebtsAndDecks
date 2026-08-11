package com.debtsdecks

import android.app.Application
import com.debtsdecks.di.coreModule
import com.debtsdecks.di.gdxModule
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.module.Module

class DebtsAndDecksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.NONE)
            androidContext(this@DebtsAndDecksApp)
            modules(listOf(coreModule, gdxModule))
        }
    }

    companion object {
        val container = org.koin.core.context.GlobalContext.getOrCreate()
    }
}