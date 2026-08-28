package com.debtsdecks.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * F2 districts: covers [DataLoader.loadDistricts], the production decode path, through a fake
 * [AssetSource]. Its three siblings (`loadCards`/`loadEnemies`/`loadRunSequence`) have no direct
 * test — they are exercised only indirectly — so this covers the seam being added rather than
 * retrofitting coverage the rest of the loader never had.
 */
class DataLoaderDistrictTest {

    private class FakeAssetSource(
        private val districts: String,
        private val runSequence: String = ""
    ) : AssetSource {
        override fun readCards(): String = error("not used")
        override fun readEnemies(): String = error("not used")
        override fun readRunSequence(): String = runSequence
        override fun readDistricts(): String = districts
    }

    private fun slot(enemyId: String, districtId: String) =
        """{"enemyId":"$enemyId","districtId":"$districtId","rewards":{"gold":1,"cardChoices":1}}"""

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

    @Test
    fun `a slot naming a district outside the catalog is rejected, and the message names it`() {
        val source = FakeAssetSource(
            districts = """[{"id":"casino","name":"district.casino.name","description":"district.casino.description"}]""",
            runSequence = """{"slots":[${slot("collector", "casino")},${slot("collector", "sewers")}]}"""
        )

        val failure = assertThrows<IllegalArgumentException> { DataLoader.loadRunSequence(source) }

        assertTrue(
            failure.message!!.contains("sewers"),
            "the message must name the offending id, was: ${failure.message}"
        )
    }

    @Test
    fun `the shipped run sequence passes the cross-catalog check`() {
        val source = FakeAssetSource(
            districts = File("src/main/assets/districts/all.json").readText(),
            runSequence = File("src/main/assets/run/sequence.json").readText()
        )

        assertEquals(8, DataLoader.loadRunSequence(source).slots.size)
    }
}
