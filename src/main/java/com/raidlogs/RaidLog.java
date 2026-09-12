package com.raidlogs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Value;

class RaidLog
{
	@Value
	static class Snapshot
	{
		int schema;
		String session;
		String started;
		String player;
		boolean completed;
		String status;
		List<EncounterLog.Snapshot> encounters;
	}

	final String session = UUID.randomUUID().toString();
	final String started = Instant.now().toString();
	final String player;
	final List<EncounterLog> encounters = new ArrayList<>();
	boolean completed;
	boolean ended;
	String status = "Recording";

	RaidLog(String player)
	{
		this.player = player;
	}

	EncounterLog begin(TobRoom room, int tick, boolean partial)
	{
		EncounterLog encounter = new EncounterLog(encounters.size() + 1, room, tick, partial);
		encounters.add(encounter);
		return encounter;
	}

	Snapshot snapshot()
	{
		return new Snapshot(1, session, started, player, completed, status,
			encounters.stream().map(EncounterLog::snapshot).collect(Collectors.toList()));
	}
}
