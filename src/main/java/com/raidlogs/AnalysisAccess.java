package com.raidlogs;

final class AnalysisAccess
{
	private AnalysisAccess() { }

	static boolean allowed(RaidLog.Snapshot raid, int selectedIndex)
	{
		if (raid == null || selectedIndex < 0 || selectedIndex > raid.getEncounters().size())
		{
			return false;
		}
		return selectedIndex == 0 ? raid.isCompleted() : raid.getEncounters().get(selectedIndex - 1).isCompleted();
	}
}
