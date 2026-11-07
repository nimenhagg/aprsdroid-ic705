package org.aprsdroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageDatabaseMigrationPlanTest {

    @Test
    fun planFrom1To6ContainsAllStepsInOrder() {
        val plan = migrationPlan(1, 6)
        assertEquals(
            listOf(
                MigrationStep.V1ToV2,
                MigrationStep.V3ToV4,
                MigrationStep.V4ToV5,
                MigrationStep.V5ToV6,
            ),
            plan,
        )
    }

    @Test
    fun planFrom2To6ContainsRenameAndSubsequentStepsInOrder() {
        val plan = migrationPlan(2, 6)
        assertEquals(
            listOf(
                MigrationStep.V2ToV3,
                MigrationStep.V3ToV4,
                MigrationStep.V4ToV5,
                MigrationStep.V5ToV6,
            ),
            plan,
        )
    }

    @Test
    fun planFrom3To6ExecutesIndexAndLaterUpgrades() {
        val plan = migrationPlan(3, 6)
        assertEquals(
            listOf(
                MigrationStep.V3ToV4,
                MigrationStep.V4ToV5,
                MigrationStep.V5ToV6,
            ),
            plan,
        )
    }

    @Test
    fun planFrom4To6ExecutesV5AndV6Only() {
        val plan = migrationPlan(4, 6)
        assertEquals(
            listOf(
                MigrationStep.V4ToV5,
                MigrationStep.V5ToV6,
            ),
            plan,
        )
    }

    @Test
    fun planFrom5To6ExecutesV6Only() {
        val plan = migrationPlan(5, 6)
        assertEquals(
            listOf(MigrationStep.V5ToV6),
            plan,
        )
    }

    @Test
    fun planSameVersionReturnsEmptyPlan() {
        assertTrue("6 -> 6 must be empty", migrationPlan(6, 6).isEmpty())
        assertTrue("5 -> 5 must be empty", migrationPlan(5, 5).isEmpty())
        assertTrue("1 -> 1 must be empty", migrationPlan(1, 1).isEmpty())
    }

    @Test
    fun planFromFutureVersionReturnsEmptyPlan() {
        // Test multiple combinations where from > to
        assertTrue("7 -> 6 must be empty", migrationPlan(7, 6).isEmpty())
        assertTrue("6 -> 1 must be empty", migrationPlan(6, 1).isEmpty())
        assertTrue("5 -> 4 must be empty", migrationPlan(5, 4).isEmpty())
        assertTrue("100 -> 6 must be empty", migrationPlan(100, 6).isEmpty())
    }

    @Test
    fun planSingleVersionTransitions() {
        assertEquals(listOf(MigrationStep.V1ToV2), migrationPlan(1, 2))
        assertEquals(listOf(MigrationStep.V2ToV3), migrationPlan(2, 3))
        assertEquals(listOf(MigrationStep.V3ToV4), migrationPlan(3, 4))
        assertEquals(listOf(MigrationStep.V4ToV5), migrationPlan(4, 5))
    }
}
