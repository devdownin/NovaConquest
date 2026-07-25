package com.novaempire.core.engine

import com.novaempire.core.engine.save.SaveMigration
import com.novaempire.core.engine.save.SaveMigrations
import com.novaempire.core.engine.save.SaveVersionException
import com.novaempire.core.engine.save.SavedGameSnapshotCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the migration pipeline with a synthetic chain: [SaveMigrations.ALL] is still empty
 * (CURRENT_VERSION is 1), so injecting fake steps is the only way to prove the mechanism works
 * *before* a real breaking schema change depends on it.
 */
class SaveMigrationsTest {

    private fun obj(vararg pairs: Pair<String, Int>) =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    /** Marks the JSON so we can assert the step ran, and in which order. */
    private fun step(from: Int) = SaveMigration(from) { root ->
        val trail = root["trail"]?.jsonPrimitive?.content ?: ""
        JsonObject(root + ("trail" to JsonPrimitive(trail + "->${from + 1}")))
    }

    @Test
    fun appliesStepsInOrderAndStampsFinalVersion() {
        val steps = listOf(step(1), step(2), step(3))
        val result = SaveMigrations.migrate(obj("version" to 1), from = 1, to = 4, steps = steps)

        assertEquals("->2->3->4", result["trail"]!!.jsonPrimitive.content)
        assertEquals("migrated save must report the schema it now holds", 4, result["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun singleStepUpgrade() {
        val result = SaveMigrations.migrate(obj("version" to 1), from = 1, to = 2, steps = listOf(step(1)))
        assertEquals("->2", result["trail"]!!.jsonPrimitive.content)
        assertEquals(2, result["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun alreadyCurrentIsLeftUntouched() {
        val input = obj("version" to 2, "credits" to 5)
        assertSame("no copy, no version restamp when nothing to do",
            input, SaveMigrations.migrate(input, from = 2, to = 2, steps = listOf(step(1))))
    }

    @Test
    fun missingStepRaisesVersionExceptionRatherThanCorrupting() {
        // A gap in the chain means the save is intact but unreadable by this build — SaveManager
        // treats SaveVersionException as "skip, do not quarantine".
        val steps = listOf(step(1)) // nothing for v2 -> v3
        val error = runCatching {
            SaveMigrations.migrate(obj("version" to 1), from = 1, to = 3, steps = steps)
        }.exceptionOrNull()
        assertTrue("expected SaveVersionException, got $error", error is SaveVersionException)
    }

    @Test
    fun productionChainIsCompleteUpToCurrentVersion() {
        // Guards the real registry: every version from the oldest supported up to CURRENT_VERSION
        // must be reachable. This test starts passing trivially and becomes meaningful the moment
        // CURRENT_VERSION is bumped without a matching migration being added.
        for (v in SaveMigrations.OLDEST_SUPPORTED_VERSION until SavedGameSnapshotCodec.CURRENT_VERSION) {
            assertTrue(
                "No migration registered for v$v -> v${v + 1}",
                SaveMigrations.ALL.any { it.fromVersion == v }
            )
        }
    }
}
