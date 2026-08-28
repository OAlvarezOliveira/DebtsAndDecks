package com.debtsdecks.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * F2 districts: covers [DataLoader.loadDistricts], the production decode path, through a fake
 * [AssetSource]. Its three siblings (`loadCards`/`loadEnemies`/`loadRunSequence`) have no direct
 * test — they are exercised only indirectly — so this covers the seam being added rather than
 * retrofitting coverage the rest of the loader never had.
 */
class DataLoaderDistrictTest {

    private class FakeAssetSource(private val districts: String) : AssetSource {
        override fun readCards(): String = error("not used")
        override fun readEnemies(): String = error("not used")
        override fun readRunSequence(): String = error("not used")
        override fun readDistricts(): String = districts
    }

    @Test
    fun `decodes a district catalog off the asset source`() {
        val source = FakeAssetSource(
            """[{"id":"casino","name":"district.casino.name","description":"district.casino.description"}]"""
        )

        val districts = DataLoader.loadDistricts(source)

        assertEquals(1, districts.size)
        assertEquals("casino", districts[0].id)
        assertEquals("district.casino.name", districts[0].name)
        assertEquals("district.casino.description", districts[0].description)
    }

    @Test
    fun `decodes the real shipped catalog end to end`() {
        val source = FakeAssetSource(File("src/main/assets/districts/all.json").readText())

        val districts = DataLoader.loadDistricts(source)

        assertEquals(listOf("slaughterhouse", "casino", "boardroom"), districts.map { it.id })
    }
}
