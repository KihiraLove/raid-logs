package com.raidlogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/** Client-thread owned. Anonymous observations are transient matching keys, never log rows. */
class EncounterLog
{
	@Value
	static class Entry
	{
		String recorder;
		String player;
		boolean selfReported;
		DamageHit hit;
	}

	@Value
	static class Snapshot
	{
		String token;
		int attempt;
		TobRoom room;
		boolean completed;
		boolean partial;
		String endReason;
		long otherObserved;
		long otherMatched;
		long miscellaneous;
		int unmatchedSelfReports;
		List<Entry> entries;
	}

	@Value
	private static class Observation
	{
		int npcId;
		int npcIndex;
		int amount;
		int tick;
	}

	final int attempt;
	final String token = java.util.UUID.randomUUID().toString();
	final TobRoom room;
	final int startTick;
	boolean completed;
	boolean ended;
	boolean partial;
	String endReason = "Recording";
	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, Set<Long>> received = new HashMap<>();
	private final List<Observation> anonymous = new ArrayList<>();
	private final List<Observation> pendingReports = new ArrayList<>();
	private long otherObserved;
	private long otherMatched;
	private long miscellaneous;
	private int unmatchedSelfReports;

	EncounterLog(int attempt, TobRoom room, int startTick, boolean partial)
	{
		this.attempt = attempt;
		this.room = room;
		this.startTick = startTick;
		this.partial = partial;
	}

	void personal(String recorder, String player, DamageHit hit)
	{
		entries.add(new Entry(recorder, player, false, hit));
	}

	boolean remote(String recorder, String player, DamageHit hit, int estimatedLocalTick)
	{
		if (!hit.valid() || !received.computeIfAbsent(recorder, ignored -> new HashSet<>()).add(hit.getSequence()))
		{
			return false;
		}
		entries.add(new Entry(recorder, player, true, hit));
		if (hit.getDirection() == DamageHit.Direction.DEALT)
		{
			Observation report = new Observation(hit.getNpcId(), hit.getNpcIndex(), hit.getAmount(), estimatedLocalTick);
			if (removeMatch(anonymous, report))
			{
				otherMatched += hit.getAmount();
			}
			else
			{
				pendingReports.add(report);
				unmatchedSelfReports++;
			}
		}
		return true;
	}

	void other(int npcId, int npcIndex, int amount, int tick)
	{
		otherObserved += amount;
		Observation observation = new Observation(npcId, npcIndex, amount, tick);
		if (removeMatch(pendingReports, observation))
		{
			otherMatched += amount;
			unmatchedSelfReports--;
		}
		else
		{
			anonymous.add(observation);
		}
	}

	private static boolean removeMatch(List<Observation> candidates, Observation observation)
	{
		// Consume one occurrence, preserving simultaneous equal hits as separate damage.
		for (Iterator<Observation> iterator = candidates.iterator(); iterator.hasNext();)
		{
			Observation candidate = iterator.next();
			if (candidate.npcId == observation.npcId && candidate.npcIndex == observation.npcIndex
				&& candidate.amount == observation.amount && Math.abs(candidate.tick - observation.tick) <= 2)
			{
				iterator.remove();
				return true;
			}
		}
		return false;
	}

	void expire(int tick)
	{
		anonymous.removeIf(hit -> tick - hit.tick > 12);
		pendingReports.removeIf(hit -> tick - hit.tick > 12);
	}

	void miscellaneous(int amount)
	{
		miscellaneous += amount;
	}

	void finish(boolean confirmed, String reason)
	{
		ended = true;
		completed = confirmed;
		endReason = reason;
		if (!confirmed)
		{
			partial = true;
		}
	}

	Snapshot snapshot()
	{
		return new Snapshot(token, attempt, room, completed, partial, endReason, otherObserved, otherMatched,
			miscellaneous, unmatchedSelfReports, new ArrayList<>(entries));
	}

	Map<String, Long> totals(DamageHit.Direction direction)
	{
		Map<String, Long> totals = new LinkedHashMap<>();
		for (Entry entry : entries)
		{
			if (entry.hit.getDirection() == direction)
			{
				String name = entry.hit.getKind() == DamageHit.Kind.THRALL ? "Misc: thralls" : entry.player;
				totals.merge(name, (long) entry.hit.getAmount(), Long::sum);
			}
		}
		return totals;
	}
}
