package com.raidlogs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum TobRoom
{
	MAIDEN("The Maiden of Sugadinti", 12613, 12869),
	BLOAT("The Pestilent Bloat", 13125),
	NYLOCAS("The Nylocas", 13122),
	SOTETSEG("Sotetseg", 13123, 13379),
	XARPUS("Xarpus", 12612),
	VERZIK("The Final Challenge", 12611);

	// Map region identifiers have no gameval equivalent. These are template regions,
	// not the dynamically allocated instance coordinates.
	static final int TREASURE_REGION = 12867;
	private static final Pattern COMPLETION = Pattern.compile(
		"^Wave '([^']+)' \\((?:Entry|Story|Normal|Hard) Mode\\) complete!\\s*Duration:.*$");
	final String displayName;
	private final int[] regions;

	TobRoom(String displayName, int... regions)
	{
		this.displayName = displayName;
		this.regions = regions;
	}

	static TobRoom fromRegion(int region)
	{
		for (TobRoom room : values())
		{
			for (int candidate : room.regions)
			{
				if (region == candidate)
				{
					return room;
				}
			}
		}
		return null;
	}

	static TobRoom completedBy(String message)
	{
		Matcher matcher = COMPLETION.matcher(message);
		if (matcher.matches())
		{
			for (TobRoom room : values())
			{
				if (room.displayName.equals(matcher.group(1)))
				{
					return room;
				}
			}
		}
		return null;
	}
}
