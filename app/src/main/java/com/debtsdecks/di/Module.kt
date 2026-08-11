package com.debtsdecks.di

import android.content.Context
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.data.DataLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import kotlin.random.Random

val coreModule = module {
    single<CardRegistry> { DataLoader.createCardRegistry(androidContext()) }
    single<Random> { Random(System.currentTimeMillis()) }
    factory { (enemies: List<com.debtsdecks.core.enemies.EnemyDefinition>, starterDeck: List<String>) ->
        CombatEngine(get(), get()).also { it.startCombat(enemies, starterDeck) }
    }
}

val gdxModule = module {
    single { CombatRenderer(get()) }
    factory { CombatInputHandler(get(), get()) }
    factory { GameScreen(get(), get(), get()) }
}