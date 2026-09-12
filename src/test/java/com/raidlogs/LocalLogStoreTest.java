package com.raidlogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LocalLogStoreTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void journalRecoversUnsnapshottedHitsWithoutDuplicatesOrUnlocking() throws Exception
	{
		Path directory = folder.newFolder(".runelite").toPath().resolve("raid-logs");
		CompletableFuture<List<RaidLog.Snapshot>> loaded = new CompletableFuture<>();
		LocalLogStore store = new LocalLogStore(directory, RuneLiteAPI.GSON,
			message -> loaded.completeExceptionally(new AssertionError(message)));
		RaidLog raid = new RaidLog("Self");
		EncounterLog room = raid.begin(TobRoom.BLOAT, 100, false);
		DamageHit first = HitFixtures.dealt(0, 0, 17);
		room.personal(raid.session, "Self", first);
		store.save(raid.snapshot());
		EncounterLog.Entry existing = new EncounterLog.Entry(raid.session, "Self", false, first);
		store.append(raid.session, new LocalLogStore.JournalBatch(room.token, Collections.singletonList(existing)));
		EncounterLog.Entry later = new EncounterLog.Entry(raid.session, "Self", false, HitFixtures.dealt(1, 1, 21));
		store.append(raid.session, new LocalLogStore.JournalBatch(room.token, Collections.singletonList(later)));
		store.load(loaded::complete);
		try
		{
			RaidLog.Snapshot recovered = loaded.get(10, TimeUnit.SECONDS).get(0);
			assertEquals(2, recovered.getEncounters().get(0).getEntries().size());
			assertFalse(AnalysisAccess.allowed(recovered, 1));
			assertFalse(Files.exists(directory.resolve(raid.session + ".json.tmp")));
		}
		finally
		{
			store.close(null);
		}
	}
}
