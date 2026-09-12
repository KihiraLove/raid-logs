package com.raidlogs;

import lombok.Value;

/** An immutable observation. Unknown attribution never changes the observed amount. */
@Value
class DamageHit
{
	enum Direction { DEALT, TAKEN }
	enum Kind { UNRESOLVED, WEAPON, THRALL, POISON, VENOM, BLEED, BURN }

	long sequence;
	int tick;
	Direction direction;
	int amount;
	int hitsplatType;
	// The destination NPC for dealt damage; inferred source NPC for taken damage.
	int npcId;
	int npcIndex;
	int npcSpawn;
	Kind kind;
	int weaponId;
	int projectileId;
	String npcName;
	String weaponName;
	String attack;
	String evidence;

	boolean valid()
	{
		return sequence >= 0 && tick >= 0 && tick < 100_000 && amount >= 0 && amount <= 100_000
			&& direction != null && kind != null && npcId >= -1 && npcIndex >= -1 && npcIndex < 65536
			&& weaponId >= -1 && projectileId >= -1 && shortText(npcName) && shortText(weaponName)
			&& shortText(attack) && shortText(evidence);
	}

	private static boolean shortText(String value)
	{
		return value != null && value.length() <= 160;
	}
}
