package com.debtsdecks.core.model

import com.debtsdecks.core.simulation.TestAssetLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F2 districts (R2.1-R2.9): the 8-slot run is re-cut into three named districts, 3+3+2, each
 * closing on exactly one boss seat. This is metadata only — no combat, economy or reward value
 * changes, which is why every assertion here is about structure and naming, never about numbers
 * the balance gate owns. The zero-delta claim itself is proven by the simulation harness, not here.
 */
class DistrictTest {

    private val districts = TestAssetLoader.loadDistricts()
    private val slots = TestAssetLoader.loadSequence().slots

    @Test
    fun `catalog holds exactly the three designed districts in run order`() {
        assertEquals(listOf("slaughterhouse", "casino", "boardroom"), districts.map { it.id })
    }

    @Test
    fun `district names and descriptions are bundle keys, never literal prose`() {
        for (district in districts) {
            assertEquals("district.${district.id}.name", district.name)
            assertEquals("district.${district.id}.description", district.description)
        }
    }

    @Test
    fun `every slot names a district that exists in the catalog`() {
        val ids = districts.map { it.id }.toSet()
        for ((index, slot) in slots.withIndex()) {
            assertTrue(slot.districtId in ids, "slot $index district ${slot.districtId} must exist in districts/all.json")
        }
    }

    @Test
    fun `districts cut the run into contiguous blocks of three, three and two`() {
        assertEquals(
            listOf(
                "slaughterhouse", "slaughterhouse", "slaughterhouse",
                "casino", "casino", "casino",
                "boardroom", "boardroom"
            ),
            slots.map { it.districtId }
        )
    }

    @Test
    fun `each district closes on exactly one boss seat`() {
        val bossesPerDistrict = slots.filter { it.role == SlotRole.BOSS }.groupingBy { it.districtId }.eachCount()
        assertEquals(mapOf("slaughterhouse" to 1, "casino" to 1, "boardroom" to 1), bossesPerDistrict)
    }

    @Test
    fun `the boss seats are slots three, six and eight`() {
        val bossIndices = slots.withIndex().filter { it.value.role == SlotRole.BOSS }.map { it.index }
        assertEquals(listOf(2, 5, 7), bossIndices)
    }

    @Test
    fun `a boss seat is always the last slot of its district`() {
        for ((districtId, block) in slots.groupBy { it.districtId }) {
            assertEquals(SlotRole.BOSS, block.last().role, "district $districtId must end on a boss seat")
            assertTrue(block.dropLast(1).all { it.role == SlotRole.STREET }, "district $districtId has a boss before its last slot")
        }
    }

    @Test
    fun `street slots stay street so the reskin adds no hidden encounter`() {
        assertEquals(5, slots.count { it.role == SlotRole.STREET })
    }
}
