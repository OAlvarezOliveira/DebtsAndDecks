package com.debtsdecks.core.simulation

import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.core.model.CardDefinition
import kotlinx.serialization.json.Json
import java.io.File

/** Headless no-op [Localizer]: returns the bundle key (and rendered args) without any LibGDX dependency. */
object NoOpLocalizer : Localizer {
    override fun get(key: String): String = key
    override fun format(key: String, vararg args: Any?): String =
        if (args.isEmpty()) key else "$key(${args.joinToString()})"
}

/**
 * Plain-`File` JSON loader mirroring [com.debtsdecks.core.data.DataLoader]'s decode but bypassing
 * the Android-`Context` requirement (same pattern as TestI18n.kt's `File(...)`). Reads repo assets
 * relative to the Gradle module working directory.
 */
object TestAssetLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCards(): List<CardDefinition> =
        json.decodeFromString(File("src/main/assets/cards/all.json").readText())

    fun loadEnemies(): List<EnemyDefinition> =
        json.decodeFromString(File("src/main/assets/enemies/all.json").readText())

    fun loadSequence(): com.debtsdecks.core.model.RunSequence =
        json.decodeFromString(File("src/main/assets/run/sequence.json").readText())
}