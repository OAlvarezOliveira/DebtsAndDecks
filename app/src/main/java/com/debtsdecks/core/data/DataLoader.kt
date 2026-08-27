package com.debtsdecks.core.data

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.RunSequence
import com.debtsdecks.core.enemies.EnemyDefinition
import kotlinx.serialization.json.Json

object DataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCards(source: AssetSource): List<CardDefinition> =
        json.decodeFromString<List<CardDefinition>>(source.readCards())

    fun loadEnemies(source: AssetSource): List<EnemyDefinition> =
        json.decodeFromString<List<EnemyDefinition>>(source.readEnemies())

    fun loadRunSequence(source: AssetSource): RunSequence =
        json.decodeFromString<RunSequence>(source.readRunSequence())

    fun createCardRegistry(source: AssetSource): CardRegistry =
        CardRegistry.create(loadCards(source))
}
