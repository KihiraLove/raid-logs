package com.raidlogs;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalysisAccessTest
{
	@Test
	public void encounterCompletionDoesNotUnlockRaidOrNextEncounter()
	{
		RaidLog raid = new RaidLog("Self");
		EncounterLog maiden = raid.begin(TobRoom.MAIDEN, 100, false);
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 1));
		maiden.finish(true, "Completion message");
		raid.begin(TobRoom.BLOAT, 200, false);
		assertTrue(AnalysisAccess.allowed(raid.snapshot(), 1));
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 0));
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 2));
	}

	@Test
	public void interruptionNeverUnlocksAnalysis()
	{
		RaidLog raid = new RaidLog("Self");
		EncounterLog room = raid.begin(TobRoom.SOTETSEG, 100, false);
		room.finish(false, "Logout, death, leaving or room unload");
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 1));
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 0));
		assertTrue(room.snapshot().isPartial());
	}

	@Test
	public void raidCompletionUnlocksRaid()
	{
		RaidLog raid = new RaidLog("Self");
		raid.begin(TobRoom.VERZIK, 100, false).finish(true, "Completion");
		raid.completed = true;
		assertTrue(AnalysisAccess.allowed(raid.snapshot(), 0));
		assertFalse(AnalysisAccess.allowed(raid.snapshot(), 2));
	}

	@Test
	public void onlyExactGameCompletionMessagesMatch()
	{
		assertEquals(TobRoom.NYLOCAS, TobRoom.completedBy("Wave 'The Nylocas' (Normal Mode) complete!Duration: 3:20"));
		assertEquals(TobRoom.VERZIK, TobRoom.completedBy("Wave 'The Final Challenge' (Hard Mode) complete! Duration: 5:00"));
		assertEquals(TobRoom.MAIDEN, TobRoom.completedBy("Wave 'The Maiden of Sugadinti' (Entry Mode) complete!Duration: 1:00"));
		assertNull(TobRoom.completedBy("Player: Wave 'The Nylocas' (Normal Mode) complete!Duration: 3:20"));
		assertNull(TobRoom.completedBy("The Nylocas boss spawned"));
		assertNull(TobRoom.completedBy("Oh dear, you are dead!"));
	}

	@Test
	public void sotetsegMazeRemainsSameEncounter()
	{
		assertEquals(TobRoom.fromRegion(13123), TobRoom.fromRegion(13379));
		assertNull(TobRoom.fromRegion(14642));
	}
}
