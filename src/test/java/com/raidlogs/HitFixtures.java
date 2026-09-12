package com.raidlogs;

import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.NpcID;

final class HitFixtures
{
	private HitFixtures() { }

	static DamageHit dealt(long sequence, int tick, int amount)
	{
		return new DamageHit(sequence, tick, DamageHit.Direction.DEALT, amount, HitsplatID.DAMAGE_ME,
			NpcID.TOB_BLOAT, 42, 1, DamageHit.Kind.UNRESOLVED, -1, -1,
			"Pestilent Bloat", "Unknown", "Unknown", "Observed amount; unresolved source/weapon");
	}
}
