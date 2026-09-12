package com.raidlogs;

import java.util.Collections;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartyProtocolTest
{
	@Test
	public void senderIdentityIsTransportAssignedAndHitDataRoundTrips()
	{
		RaidLogsUpdate update = new RaidLogsUpdate();
		update.setMemberId(123);
		update.setHits(Collections.singletonList(HitFixtures.dealt(2, 3, 17)));
		String json = RuneLiteAPI.GSON.toJson(update);
		assertFalse(json.contains("memberId"));
		RaidLogsUpdate restored = RuneLiteAPI.GSON.fromJson(json, RaidLogsUpdate.class);
		assertTrue(restored.getHits().get(0).valid());
		assertEquals(17, restored.getHits().get(0).getAmount());
		assertEquals(0, restored.getMemberId());
		RaidLogsUpdate spoofed = RuneLiteAPI.GSON.fromJson("{\"memberId\":123}", RaidLogsUpdate.class);
		assertEquals(0, spoofed.getMemberId());
	}
}
