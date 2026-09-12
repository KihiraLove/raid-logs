package com.raidlogs;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttackEvidenceTest
{
	private AttackEvidence.Candidate candidate(Object target, DamageHit.Kind kind)
	{
		return new AttackEvidence.Candidate(target, 100, kind, -1, -1, -1, -1, -1, "Attack");
	}

	@Test
	public void uniqueEvidenceCannotBeUsedTwice()
	{
		Object target = new Object();
		AttackEvidence evidence = new AttackEvidence();
		evidence.add(candidate(target, DamageHit.Kind.WEAPON));
		assertNotNull(evidence.match(target, 100, 1, false));
		assertNull(evidence.match(target, 100, 1, false));
	}

	@Test
	public void simultaneousThrallAndWeaponRemainUnresolved()
	{
		Object target = new Object();
		AttackEvidence evidence = new AttackEvidence();
		evidence.add(candidate(target, DamageHit.Kind.WEAPON));
		evidence.add(candidate(target, DamageHit.Kind.THRALL));
		assertNull(evidence.match(target, 100, 1, false));
		assertNull(evidence.match(target, 100, 2, false));
	}

	@Test
	public void multiHitOrReflectionDoesNotBecomeWeaponDamage()
	{
		Object target = new Object();
		AttackEvidence evidence = new AttackEvidence();
		evidence.add(candidate(target, DamageHit.Kind.WEAPON));
		assertNull(evidence.match(target, 100, 3, false));
		assertNull(evidence.match(target, 100, 1, true));
	}

	@Test
	public void ambiguousEvidenceIsNotReusedOnTheFollowingTick()
	{
		Object target = new Object();
		AttackEvidence evidence = new AttackEvidence();
		evidence.add(candidate(target, DamageHit.Kind.WEAPON));
		assertNull(evidence.match(target, 100, 2, false));
		assertNull(evidence.match(target, 130, 1, false));
	}

	@Test
	public void staleOrDifferentTargetEvidenceCannotMatch()
	{
		Object target = new Object();
		AttackEvidence evidence = new AttackEvidence();
		evidence.add(candidate(target, DamageHit.Kind.WEAPON));
		assertNull(evidence.match(new Object(), 100, 1, false));
		assertNull(evidence.match(target, 200, 1, false));
		evidence.expire(200);
		assertNull(evidence.match(target, 100, 1, false));
	}
}
