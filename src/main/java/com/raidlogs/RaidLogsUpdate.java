package com.raidlogs;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Only the sender's live self-observations. No stored logs or third-party observations. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RaidLogsUpdate extends PartyMemberMessage
{
	private int protocol = 1;
	private String session;
	private String encounter;
	private String player;
	private String room;
	private int world;
	private int tick;
	private List<DamageHit> hits;
}
