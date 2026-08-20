package com.debtsdecks.di

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.data.DataLoader
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.gdx.GameScreen
import com.debtsdecks.gdx.audio.SoundManager
import com.debtsdecks.gdx.input.CombatInputHandler
import com.debtsdecks.gdx.render.CombatRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import kotlin.random.Random

val coreModule = module {
    single<CardRegistry> { DataLoader.createCardRegistry(androidContext()) }
    single<Random> { Random(System.currentTimeMillis()) }
    single<List<EnemyDefinition>> { DataLoader.loadEnemies(androidContext()) }
    single { CombatEngine(get(), get()) }
    single { RunManager(get(), get(), get(), get()) }
}

val gdxModule = module {
    single<Camera> { OrthographicCamera() }
    single<Viewport> { FitViewport(1280f, 720f, get()) }
    single { CombatRenderer() }
    single { SoundManager() }
    factory { CombatInputHandler(get(), get(), get(), get(), get()) }
    factory { GameScreen(get(), get(), get(), get(), get()) }
}