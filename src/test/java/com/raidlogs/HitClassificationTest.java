package com.raidlogs;

import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import org.junit.Test;
import static org.junit.Assert.*;

public class HitClassificationTest
{
	private Hitsplat hit(int type)
	{
		return new Hitsplat()
		{
			public int getHitsplatType() { return type; }
			public int getAmount() { return 0; }
			public int getDisappearsOnGameCycle() { return 0; }
		};
	}

	@Test
	public void capturesZeroAndStatusDamage()
	{
		assertTrue(RaidLogsPlugin.isDamage(hit(HitsplatID.BLOCK_ME)));
		assertTrue(RaidLogsPlugin.isDamage(hit(HitsplatID.BLOCK_OTHER)));
		assertTrue(RaidLogsPlugin.isDamage(hit(HitsplatID.POISON)));
		assertTrue(RaidLogsPlugin.isDamage(hit(HitsplatID.VENOM)));
		assertTrue(RaidLogsPlugin.isDamage(hit(HitsplatID.BLEED)));
	}

	@Test
	public void healingAndNonHealthResourcesAreNotDamage()
	{
		assertFalse(RaidLogsPlugin.isDamage(hit(HitsplatID.HEAL)));
		assertFalse(RaidLogsPlugin.isDamage(hit(HitsplatID.PRAYER_DRAIN)));
		assertFalse(RaidLogsPlugin.isDamage(hit(HitsplatID.DISEASE)));
		assertFalse(RaidLogsPlugin.isDamage(hit(HitsplatID.SANITY_DRAIN)));
	}
}
