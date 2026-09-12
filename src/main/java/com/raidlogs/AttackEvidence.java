package com.raidlogs;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/** Short-lived correlation evidence. No enemy mechanic is displayed during combat. */
class AttackEvidence
{
	@Value
	static class Candidate
	{
		Object target;
		int cycle;
		DamageHit.Kind kind;
		int weaponId;
		int projectileId;
		int npcId;
		int npcIndex;
		int npcSpawn;
		String attack;
	}

	private final List<Candidate> candidates = new ArrayList<>();

	void add(Candidate candidate)
	{
		candidates.add(candidate);
	}

	Candidate match(Object target, int cycle, int simultaneousHits, boolean possibleReflection)
	{
		List<Candidate> nearby = new ArrayList<>();
		for (Candidate candidate : candidates)
		{
			if (candidate.target == target && Math.abs(candidate.cycle - cycle) <= 30)
			{
				nearby.add(candidate);
			}
		}
		// Ambiguous evidence has already participated in a hit and cannot be recycled
		// into a seemingly unique match for another hit on the following tick.
		candidates.removeAll(nearby);
		return simultaneousHits == 1 && !possibleReflection && nearby.size() == 1 ? nearby.get(0) : null;
	}

	void expire(int cycle)
	{
		candidates.removeIf(candidate -> candidate.cycle < cycle - 60);
	}

	void clear()
	{
		candidates.clear();
	}
}
