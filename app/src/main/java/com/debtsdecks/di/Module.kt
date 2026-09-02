package com.debtsdecks.di

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.data.AssetSource
import com.debtsdecks.core.data.DataLoader
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.model.District
import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.core.intro.IntroSequence
import com.debtsdecks.gdx.GameScreen
import com.debtsdecks.gdx.IntroScreen
import com.debtsdecks.gdx.audio.SoundManager
import com.debtsdecks.gdx.data.AndroidAssetSource
import com.debtsdecks.gdx.i18n.BundleLocalizer
import com.debtsdecks.gdx.input.CombatInputHandler
import com.debtsdecks.gdx.render.CombatRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import kotlin.random.Random

val coreModule = module {
    // Core classes declare only the pure-Kotlin AssetSource; the Android adapter is registered
    // here (where androidContext() is valid) and keeps core/ free of android.* imports per
    // CONVENTIONS.md Architecture Rule #1 (same split as Localizer/BundleLocalizer below).
    single<AssetSource> { AndroidAssetSource(androidContext()) }
    single<CardRegistry> { DataLoader.createCardRegistry(get()) }
    single<Random> { Random(System.currentTimeMillis()) }
    single<List<EnemyDefinition>> { DataLoader.loadEnemies(get()) }
    single<com.debtsdecks.core.model.RunSequence> { DataLoader.loadRunSequence(get()) }
    single<List<District>> { DataLoader.loadDistricts(get()) }
    single { CombatEngine(get(), get(), get()) }
    single { RunManager(get(), get(), DataLoader.loadEnemies(get()), get(), get(), DataLoader.loadDistricts(get())) }
}

val gdxModule = module {
    single<Camera> { OrthographicCamera() }
    // ExtendViewport, not FitViewport: 720 world units tall with the width following the
    // device aspect, so a 20:9 phone uses the whole panel instead of being pillarboxed into
    // 1280x720 with black bars. Layout code reads the live worldWidth, never the 1280 minimum.
    single<Viewport> { ExtendViewport(1280f, 720f, get()) }
    // Lazy (default Koin `single` behavior, not `createdAtStart`): Gdx.files is only valid after
    // AndroidApplication.initialize() runs in MainActivity, which happens after
    // DebtsAndDecksApp.onCreate()'s startKoin{} call. Lazy resolution defers the actual
    // Gdx.files.internal(...) call until GameApp.create() first triggers container.get().
    single { I18NBundle.createBundle(Gdx.files.internal("i18n/strings")) }
    // Core classes declare only the pure-Kotlin Localizer; the GDX adapter is registered here in
    // gdxModule (where I18NBundle + Gdx.files are valid) and resolved across modules, keeping
    // core/ free of com.badlogic.gdx.* imports per CONVENTIONS.md Architecture Rule #1.
    single<Localizer> { BundleLocalizer(get()) }
    single { CombatRenderer(get()) }
    single { SoundManager() }
    factory { CombatInputHandler(get(), get(), get(), get(), get()) }
    factory { GameScreen(get(), get(), get(), get(), get()) }
    // Factories, not singles: the opening is played once per launch and then disposed.
    factory { IntroSequence() }
    factory { IntroScreen(get(), get(), get()) }
}