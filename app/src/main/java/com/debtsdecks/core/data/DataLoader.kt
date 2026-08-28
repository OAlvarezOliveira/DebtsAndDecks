package com.debtsdecks.core.data

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.District
import com.debtsdecks.core.model.RunSequence
import com.debtsdecks.core.enemies.EnemyDefinition
import kotlinx.serialization.json.Json

object DataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCards(source: AssetSource): List<CardDefinition> =
        json.decodeFromString<List<CardDefinition>>(source.readCards())

    fun loadEnemies(source: AssetSource): List<EnemyDefinition> =
        json.decodeFromString<List<EnemyDefinition>>(source.readEnemies())

    /**
     * Decodes the run sequence and checks every slot against the district catalog. A slot naming a
     * district that does not exist is a data error, not a runtime surprise: without this check the
     * bad id travels silently — nothing in combat or the node economy reads [
     * com.debtsdecks.core.model.EncounterSlot.districtId], so it would only ever surface as a
     * missing name on screen, or not at all.
     */
    fun loadRunSequence(source: AssetSource): RunSequence {
        val sequence = json.decodeFromString<RunSequence>(source.readRunSequence())
        val known = loadDistricts(source).map { it.id }.toSet()
        sequence.slots.forEachIndexed { index, slot ->
            require(slot.districtId in known) {
                "Slot $index (enemy ${slot.enemyId}) names an unknown district: " +
                    "${slot.districtId}. Known districts: ${known.sorted().joinToString()}"
            }
        }
        return sequence
    }

    fun loadDistricts(source: AssetSource): List<District> =
        json.decodeFromString<List<District>>(source.readDistricts())

    fun createCardRegistry(source: AssetSource): CardRegistry =
        CardRegistry.create(loadCards(source))
}
