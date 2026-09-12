package com.raidlogs;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/** All serialization and disk I/O run on the store's worker, never the client thread. */
@Slf4j
class LocalLogStore
{
	@Value
	static class JournalBatch
	{
		String encounter;
		List<EncounterLog.Entry> hits;
	}

	private final Path directory;
	private final Gson gson;
	private final Consumer<String> errors;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "raid-logs-storage");
		thread.setDaemon(true);
		return thread;
	});

	LocalLogStore(Path directory, Gson gson, Consumer<String> errors)
	{
		this.directory = directory;
		this.gson = gson;
		this.errors = errors;
	}

	void append(String session, JournalBatch batch)
	{
		if (batch.hits.isEmpty())
		{
			return;
		}
		submit(() ->
		{
			Files.createDirectories(directory);
			try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve(session + ".jsonl"),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))
			{
				writer.write(gson.toJson(batch));
				writer.newLine();
			}
		});
	}

	void save(RaidLog.Snapshot snapshot)
	{
		submit(() -> writeSnapshot(snapshot));
	}

	private void writeSnapshot(RaidLog.Snapshot snapshot) throws IOException
	{
		Files.createDirectories(directory);
		Path temporary = directory.resolve(snapshot.getSession() + ".json.tmp");
		Path destination = directory.resolve(snapshot.getSession() + ".json");
		try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
		{
			gson.toJson(snapshot, writer);
		}
		try
		{
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException exception)
		{
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	void load(Consumer<List<RaidLog.Snapshot>> consumer)
	{
		submit(() ->
		{
			List<RaidLog.Snapshot> snapshots = new ArrayList<>();
			if (Files.isDirectory(directory))
			{
				List<Path> files;
				try (Stream<Path> paths = Files.list(directory))
				{
					files = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
						.collect(Collectors.toList());
				}
				for (Path path : files)
				{
					try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
					{
						RaidLog.Snapshot snapshot = gson.fromJson(reader, RaidLog.Snapshot.class);
						if (snapshot != null && snapshot.getSchema() == 1 && snapshot.getEncounters() != null)
						{
							recoverJournal(snapshot, path.resolveSibling(path.getFileName().toString().replace(".json", ".jsonl")));
							snapshots.add(snapshot);
						}
					}
					catch (RuntimeException | IOException exception)
					{
						log.debug("Could not read raid archive {}", path.getFileName(), exception);
						errors.accept("An archive could not be read; the file has been retained.");
					}
				}
			}
			snapshots.sort(Comparator.comparing(RaidLog.Snapshot::getStarted).reversed());
			consumer.accept(snapshots);
		});
	}

	private void recoverJournal(RaidLog.Snapshot snapshot, Path path) throws IOException
	{
		if (!Files.isRegularFile(path))
		{
			return;
		}
		java.util.Map<String, EncounterLog.Snapshot> rooms = new java.util.HashMap<>();
		java.util.Map<String, Set<String>> seen = new java.util.HashMap<>();
		for (EncounterLog.Snapshot room : snapshot.getEncounters())
		{
			rooms.put(room.getToken(), room);
			Set<String> keys = new HashSet<>();
			for (EncounterLog.Entry entry : room.getEntries())
			{
				keys.add(entry.getRecorder() + "/" + entry.getHit().getSequence());
			}
			seen.put(room.getToken(), keys);
		}
		try (java.io.BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				try
				{
					JournalBatch batch = gson.fromJson(line, JournalBatch.class);
					EncounterLog.Snapshot room = batch == null ? null : rooms.get(batch.getEncounter());
					if (room == null || batch.getHits() == null)
					{
						continue;
					}
					for (EncounterLog.Entry entry : batch.getHits())
					{
						if (entry != null && entry.getHit() != null && entry.getHit().valid()
							&& seen.get(room.getToken()).add(entry.getRecorder() + "/" + entry.getHit().getSequence()))
						{
							room.getEntries().add(entry);
						}
					}
				}
				catch (RuntimeException exception)
				{
					// A crash may leave a torn last line. Earlier complete batches remain recoverable.
					log.debug("Skipping invalid raid journal batch", exception);
				}
			}
		}
	}

	void close(RaidLog.Snapshot finalSnapshot)
	{
		// Drain previously queued writes before the final snapshot without blocking shutdown.
		// The executor terminates itself after this last accepted operation.
		submit(() ->
		{
			try
			{
				if (finalSnapshot != null)
				{
					writeSnapshot(finalSnapshot);
				}
			}
			finally
			{
				worker.shutdownNow();
			}
		});
	}

	private void submit(DiskOperation operation)
	{
		try
		{
			worker.execute(() ->
			{
				try
				{
					operation.run();
				}
				catch (IOException | RuntimeException exception)
				{
					log.debug("Raid log storage failed", exception);
					errors.accept("Local save failed. Check RuneLite's client log and disk space.");
				}
			});
		}
		catch (RejectedExecutionException exception)
		{
			log.debug("Storage is already closed", exception);
		}
	}

	@FunctionalInterface
	private interface DiskOperation
	{
		void run() throws IOException;
	}
}
