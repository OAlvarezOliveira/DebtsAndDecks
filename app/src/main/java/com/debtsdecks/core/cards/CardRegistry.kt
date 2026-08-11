package com.debtsdecks.core.cards

class CardRegistry(private val definitions: List<CardDefinition>) {
    private val byId = definitions.associateBy { it.id }

    fun get(id: String): CardDefinition? = byId[id]

    fun getOrThrow(id: String): CardDefinition = byId[id] ?: throw IllegalArgumentException("Card not found: $id")

    fun all(): List<CardDefinition> = definitions

    fun byTag(tag: String): List<CardDefinition> = definitions.filter { tag in it.tags }

    fun byRarity(rarity: Rarity): List<CardDefinition> = definitions.filter { it.rarity == rarity }

    companion object {
        fun create(definitions: List<CardDefinition>): CardRegistry = CardRegistry(definitions)
    }
}