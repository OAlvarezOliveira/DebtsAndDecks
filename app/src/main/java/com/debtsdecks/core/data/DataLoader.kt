package com.debtsdecks.core.data

import android.content.Context
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.enemies.EnemyDefinition
import kotlinx.serialization.json.Json

object DataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCards(context: Context): List<CardDefinition> {
        val input = context.assets.open("cards/all.json").reader()
        return try {
            json.decodeFromString<List<CardDefinition>>(input.readText())
        } finally {
            input.close()
        }
    }

    fun loadEnemies(context: Context): List<EnemyDefinition> {
        val input = context.assets.open("enemies/all.json").reader()
        return try {
            json.decodeFromString<List<EnemyDefinition>>(input.readText())
        } finally {
            input.close()
        }
    }

    fun loadRunSequence(context: Context): com.debtsdecks.core.model.RunSequence {
        val input = context.assets.open("run/sequence.json").reader()
        return try {
            json.decodeFromString<com.debtsdecks.core.model.RunSequence>(input.readText())
        } finally {
            input.close()
        }
    }

    fun createCardRegistry(context: Context): CardRegistry {
        return CardRegistry.create(loadCards(context))
    }
}