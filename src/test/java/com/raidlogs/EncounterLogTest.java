package com.raidlogs;

import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterLogTest
{
	private EncounterLog room()
	{
		return new EncounterLog(1, TobRoom.BLOAT, 100, false);
	}

	@Test
	public void anonymousDamageRetainsOnlyTotals()
	{
		EncounterLog log = room();
		log.other(NpcID.TOB_BLOAT, 42, 17, 100);
		log.other(NpcID.TOB_BLOAT, 42, 23, 101);
		EncounterLog.Snapshot saved = log.snapshot();
		assertEquals(40, saved.getOtherObserved());
		assertTrue(saved.getEntries().isEmpty());
		String json = RuneLiteAPI.GSON.toJson(saved);
		assertFalse(json.contains("npcIndex"));
		assertFalse(json.contains("npcId"));
		assertFalse(json.contains("anonymous"));
		assertFalse(json.contains("pendingReports"));
	}

	@Test
	public void selfReportConsumesObservedDamageOnlyOnce()
	{
		EncounterLog log = room();
		log.other(NpcID.TOB_BLOAT, 42, 25, 102);
		assertTrue(log.remote("recorder", "Consenting player", HitFixtures.dealt(1, 2, 25), 102));
		assertFalse(log.remote("recorder", "Consenting player", HitFixtures.dealt(1, 2, 25), 102));
		assertEquals(25, log.snapshot().getOtherMatched());
		assertEquals(1, log.snapshot().getEntries().size());
	}

	@Test
	public void equalSimultaneousHitsAreSeparateOccurrences()
	{
		EncounterLog log = room();
		log.other(NpcID.TOB_BLOAT, 42, 25, 102);
		log.other(NpcID.TOB_BLOAT, 42, 25, 102);
		log.remote("a", "A", HitFixtures.dealt(1, 2, 25), 102);
		assertEquals(25, log.snapshot().getOtherMatched());
		log.remote("b", "B", HitFixtures.dealt(1, 2, 25), 102);
		assertEquals(50, log.snapshot().getOtherMatched());
		assertEquals(0, log.snapshot().getOtherObserved() - log.snapshot().getOtherMatched());
	}

	@Test
	public void messageCanArriveBeforeLocalHitsplat()
	{
		EncounterLog log = room();
		log.remote("a", "A", HitFixtures.dealt(1, 2, 25), 102);
		assertEquals(1, log.snapshot().getUnmatchedSelfReports());
		log.other(NpcID.TOB_BLOAT, 42, 25, 102);
		assertEquals(0, log.snapshot().getUnmatchedSelfReports());
		assertEquals(25, log.snapshot().getOtherMatched());
	}

	@Test
	public void remoteCoverageDoesNotSubtractUnobservedDamage()
	{
		EncounterLog log = room();
		log.other(NpcID.TOB_BLOAT, 42, 10, 100);
		log.remote("a", "A", HitFixtures.dealt(1, 2, 25), 102);
		assertEquals(0, log.snapshot().getOtherMatched());
		assertEquals(10, log.snapshot().getOtherObserved());
		assertEquals(1, log.snapshot().getUnmatchedSelfReports());
	}

	@Test
	public void repeatedDamageOutsideMatchingWindowStaysUnmatched()
	{
		EncounterLog log = room();
		log.other(NpcID.TOB_BLOAT, 42, 25, 100);
		log.remote("a", "A", HitFixtures.dealt(1, 10, 25), 110);
		assertEquals(0, log.snapshot().getOtherMatched());
	}

	@Test
	public void zeroAndPositivePersonalHitsKeepTheirExactAmounts()
	{
		EncounterLog log = room();
		log.personal("self", "Self", HitFixtures.dealt(0, 0, 0));
		EncounterLog.Snapshot first = log.snapshot();
		log.personal("self", "Self", HitFixtures.dealt(1, 1, 17));
		assertEquals(1, first.getEntries().size());
		assertEquals(2, log.snapshot().getEntries().size());
		assertEquals(Long.valueOf(17), log.totals(DamageHit.Direction.DEALT).get("Self"));
	}

	@Test
	public void invalidRemoteAmountIsRejected()
	{
		EncounterLog log = room();
		assertFalse(log.remote("a", "A", HitFixtures.dealt(1, 0, -1), 100));
		assertTrue(log.snapshot().getEntries().isEmpty());
	}
}
