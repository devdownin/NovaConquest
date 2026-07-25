package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.engine.save.SaveVersionException
import com.novaempire.core.engine.save.SavedGameSnapshotCodec
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There is no schema-migration layer (see CLAUDE.md): every new `@Serializable` field must carry a
 * default, or existing saves stop loading. These tests simulate an older save by stripping the
 * recently added keys from an encoded snapshot, so the compatibility promise is actually enforced
 * rather than assumed.
 */
class SavedGameSnapshotCodecTest {

    private val planet = HexCoord(0, 0, 0)

    private fun richState() = GameState(
        turn = 7,
        activeFaction = Faction.TRADERS,
        playerStates = mapOf(
            Faction.TRADERS to PlayerState(
                Faction.TRADERS,
                credits = 120,
                researchInProgress = ResearchProgress("tech_hull_plating", turnsRemaining = 2, costPaid = 8)
            )
        ),
        map = GameMap(
            tiles = mapOf(planet to HexTile(planet, TerrainType.PLANET, systemLevel = 3, owner = Faction.TRADERS)),
            radius = 3,
            seed = 123456L
        )
    )

    @Test
    fun roundTripPreservesNewFields() {
        val decoded = SavedGameSnapshotCodec.decode(SavedGameSnapshotCodec.encode(richState()))
        assertEquals(123456L, decoded.map.seed)
        assertEquals(8, decoded.playerStates[Faction.TRADERS]?.researchInProgress?.costPaid)
    }

    @Test
    fun legacySaveWithoutSeedStillLoads() {
        // A save written before GameMap.seed existed simply has no "seed" key.
        val legacy = SavedGameSnapshotCodec.encode(richState()).replace(Regex(",\"seed\":-?\\d+"), "")
        assertTrue("test fixture must actually drop the key", !legacy.contains("\"seed\""))

        val decoded = SavedGameSnapshotCodec.decode(legacy)
        assertEquals("missing seed must fall back to the default", 0L, decoded.map.seed)
        assertEquals("the rest of the save must survive", 7, decoded.turn)
    }

    @Test
    fun legacySaveWithoutCostPaidStillLoads() {
        // A research queued before ResearchProgress.costPaid existed has no "costPaid" key.
        val legacy = SavedGameSnapshotCodec.encode(richState()).replace(Regex(",\"costPaid\":-?\\d+"), "")
        assertTrue("test fixture must actually drop the key", !legacy.contains("costPaid"))

        val research = SavedGameSnapshotCodec.decode(legacy).playerStates[Faction.TRADERS]?.researchInProgress
        assertEquals("tech_hull_plating", research?.techId)
        assertEquals("missing costPaid must fall back to the default", 0, research?.costPaid)
    }

    @Test
    fun unknownKeysFromANewerBuildAreIgnored() {
        val withExtra = SavedGameSnapshotCodec.encode(richState())
            .replaceFirst("{", "{\"someFutureField\":42,")
        assertEquals(7, SavedGameSnapshotCodec.decode(withExtra).turn)
    }

    @Test
    fun transientVisionIsNotPersistedAndDefaultsEmpty() {
        // visibleHexes is @Transient — it must come back empty and be recomputed by updateVision.
        val state = richState().let {
            it.copy(playerStates = it.playerStates.mapValues { (_, p) -> p.copy(visibleHexes = setOf(planet)) })
        }
        val decoded = SavedGameSnapshotCodec.decode(SavedGameSnapshotCodec.encode(state))
        assertTrue(decoded.playerStates[Faction.TRADERS]!!.visibleHexes.isEmpty())
    }

    @Test
    fun futureVersionSaveIsRejected() {
        val future = SavedGameSnapshotCodec.encode(richState())
            .replace("\"version\":${SavedGameSnapshotCodec.CURRENT_VERSION}", "\"version\":99")
        val error = runCatching { SavedGameSnapshotCodec.decode(future) }.exceptionOrNull()
        assertTrue("a newer save must raise SaveVersionException", error is SaveVersionException)
    }

    @Test
    fun saveWithoutVersionKeyIsTreatedAsOldestSchema() {
        // A snapshot written before versioning existed simply has no "version" key; it must load
        // as the oldest supported schema rather than being rejected or assumed current.
        // `version` is GameState's first field, so dropping the first match is precise.
        val unversioned = SavedGameSnapshotCodec.encode(richState()).replaceFirst(Regex("\"version\":\\d+,?"), "")
        assertTrue("test fixture must actually drop the key", !unversioned.contains("\"version\""))
        assertEquals(7, SavedGameSnapshotCodec.decode(unversioned).turn)
    }

    @Test
    fun lastCombatEventIsNotPersisted() {
        // @Transient one-shot effect: it must never come back from disk.
        val decoded = SavedGameSnapshotCodec.decode(SavedGameSnapshotCodec.encode(richState()))
        assertNull(decoded.lastCombatEvent)
    }
}
