package com.raidlogs;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.PartyPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(name = "Raid Logs", description = "Local retrospective Theatre of Blood damage logs",
	 tags = {"tob", "raids", "damage", "party"}, enabledByDefault = false)
@PluginDependency(PartyPlugin.class)
public class RaidLogsPlugin extends Plugin
{
	// These are the semantic values of TOB_CLIENT_PARTYSTATUS and WAVEPROGRESS_TYPE.
	private static final int RAID_STARTED = 2;
	private static final int MAX_BATCH = 64;
	private static final Set<Integer> MELEE_ANIMATIONS = Set.of(
		AnimationID.HUMAN_SWORD_STAB, AnimationID.HUMAN_SWORD_SLASH, AnimationID.HUMAN_AXE_CHOP,
		AnimationID.HUMAN_DHSWORD_STAB, AnimationID.HUMAN_DHSWORD_CHOP, AnimationID.HUMAN_DHSWORD_SLASH,
		AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK, AnimationID.SLAYER_WHIP_SP_ATTACK,
		AnimationID.GHRAZI_RAPIER_ATTACK, AnimationID.SCYTHE_OF_VITUR_ATTACK,
		AnimationID.DRAGON_WARHAMMER_SA_PLAYER);
	private static final Set<Integer> PROJECTILE_ANIMATIONS = Set.of(
		AnimationID.HUMAN_BOW, AnimationID.HUMAN_CROSSBOW, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK,
		AnimationID.TOXIC_BLOWPIPE_SPECIAL_UPDATED, AnimationID.HUMAN_CASTSTRIKE,
		AnimationID.HUMAN_CASTSTRIKE_STAFF, AnimationID.HUMAN_CASTWAVE, AnimationID.HUMAN_CASTWAVE_STAFF,
		AnimationID.HUMAN_CAST_SURGE, AnimationID.HUMAN_CASTSTRIKE_WALKMERGE,
		AnimationID.HUMAN_CAST_SURGE_WALKMERGE);
	private static final Set<Integer> THRALLS = Set.of(
		NpcID.ARCEUUS_THRALL_GHOST_LESSER, NpcID.ARCEUUS_THRALL_GHOST_SUPERIOR, NpcID.ARCEUUS_THRALL_GHOST_GREATER,
		NpcID.ARCEUUS_THRALL_SKELETON_LESSER, NpcID.ARCEUUS_THRALL_SKELETON_SUPERIOR, NpcID.ARCEUUS_THRALL_SKELETON_GREATER,
		NpcID.ARCEUUS_THRALL_ZOMBIE_LESSER, NpcID.ARCEUUS_THRALL_ZOMBIE_SUPERIOR, NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER);

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private Gson gson;
	@Inject private PartyService partyService;
	@Inject private WSClient wsClient;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;

	@Value
	private static class PendingHit
	{
		Actor actor;
		int tick;
		int cycle;
		int amount;
		int type;
		boolean mine;
		boolean others;
		int npcId;
		int npcIndex;
		int spawn;
		String npcName;
	}

	private final List<PendingHit> pending = new ArrayList<>();
	private final List<DamageHit> outgoing = new ArrayList<>();
	private final List<EncounterLog.Entry> journal = new ArrayList<>();
	private final Map<NPC, Integer> spawns = new IdentityHashMap<>();
	private final Map<Projectile, Integer> projectiles = new IdentityHashMap<>();
	private final Map<Long, String> peerEncounters = new HashMap<>();
	private final Map<Long, Integer> lastPeerTick = new HashMap<>();
	private final AttackEvidence evidence = new AttackEvidence();
	private volatile boolean running;
	private volatile int generation;
	private LocalLogStore store;
	private RaidLogsPanel panel;
	private NavigationButton navigation;
	private RaidLog raid;
	private EncounterLog encounter;
	private TobRoom previousRoom;
	private int previousRoomState = -1;
	private String encounterToken;
	private int nextSpawn;
	private long sequence;
	private int lastTakenCycle = -1000;
	private int lastAnimationCycle = -1000;
	private int lastWeapon = -1;
	private int endTick;
	private TobRoom completedRoom;
	private boolean completedRaid;
	private boolean armed = true;

	@Override
	protected void startUp()
	{
		int startedGeneration = ++generation;
		clientThread.invoke(() ->
		{
			if (generation == startedGeneration)
			{
				initialize(startedGeneration);
			}
		});
	}

	private void initialize(int startedGeneration)
	{
		running = true;
		store = new LocalLogStore(RuneLite.RUNELITE_DIR.toPath().resolve("raid-logs"), gson,
			message -> ui(startedGeneration, () -> panel.error(message)));
		panel = new RaidLogsPanel(this::loadArchives);
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new Color(188, 66, 78));
		graphics.fillRect(2, 9, 3, 5);
		graphics.fillRect(7, 5, 3, 9);
		graphics.fillRect(12, 1, 3, 13);
		graphics.dispose();
		navigation = NavigationButton.builder().tooltip("Raid Logs").icon(icon).priority(6).panel(panel).build();
		clientToolbar.addNavigation(navigation);
		wsClient.registerMessage(RaidLogsUpdate.class);
		loadArchives();
	}

	private void loadArchives()
	{
		int startedGeneration = generation;
		if (running)
		{
			store.load(snapshots -> ui(startedGeneration, () -> panel.accept(snapshots)));
		}
	}

	@Override
	protected void shutDown()
	{
		running = false;
		generation++;
		clientThread.invoke(() ->
		{
			if (store == null)
			{
				return;
			}
			wsClient.unregisterMessage(RaidLogsUpdate.class);
			clientToolbar.removeNavigation(navigation);
			RaidLogsPanel closingPanel = panel;
			SwingUtilities.invokeLater(closingPanel::close);
			flushHits();
			flushJournal();
			interruptEncounter("Plugin disabled before completion");
			if (raid != null && !raid.completed)
			{
				raid.status = "Interrupted: plugin disabled";
			}
			store.close(raid == null ? null : raid.snapshot());
			store = null;
			reset();
		});
	}

	private void reset()
	{
		raid = null;
		encounter = null;
		previousRoom = null;
		previousRoomState = -1;
		completedRoom = null;
		completedRaid = false;
		armed = true;
		clearEvidence();
	}

	private void clearEvidence()
	{
		pending.clear();
		outgoing.clear();
		journal.clear();
		spawns.clear();
		projectiles.clear();
		peerEncounters.clear();
		lastPeerTick.clear();
		evidence.clear();
		lastTakenCycle = -1000;
		lastAnimationCycle = -1000;
		lastWeapon = -1;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		flushHits();
		sendSelfReports();
		flushJournal();
		if (completedRoom != null && encounter != null && completedRoom == encounter.room)
		{
			encounter.finish(true, "Confirmed encounter completion");
			endTick = client.getTickCount();
			armed = false;
			publish();
		}
		completedRoom = null;
		if (completedRaid && raid != null)
		{
			finishRaid();
		}
		completedRaid = false;
		synchronizeLocation();
		if (encounter != null)
		{
			encounter.expire(client.getTickCount());
		}
		evidence.expire(client.getGameCycle());
		projectiles.values().removeIf(cycle -> cycle < client.getGameCycle() - 60);
	}

	private int region()
	{
		Player player = client.getLocalPlayer();
		return player == null ? -1 : WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
	}

	private void synchronizeLocation()
	{
		synchronizeLocation(false);
	}

	private void synchronizeLocation(boolean observedCombat)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		int region = region();
		TobRoom room = TobRoom.fromRegion(region);
		int state = client.getVarbitValue(VarbitID.TOB_CLIENT_WAVEPROGRESS_TYPE);
		boolean inRaid = client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) == RAID_STARTED;
		if (encounter != null && (room != encounter.room || !inRaid || !raid.player.equals(clean(player.getName()))))
		{
			// Preserve old-room observations before changing their encounter/session context.
			flushHits();
			sendSelfReports();
			flushJournal();
		}
		if (raid != null && !raid.ended && !raid.player.equals(clean(player.getName())))
		{
			interruptEncounter("Account changed");
			raid.ended = true;
			raid.status = "Interrupted: account changed";
			publish();
		}
		if (region == TobRoom.TREASURE_REGION && raid != null && !raid.ended)
		{
			finishRaid();
		}
		if (!inRaid && raid != null && !raid.ended)
		{
			interruptEncounter("Raid left without confirmed completion");
			raid.ended = true;
			raid.status = "Incomplete: raid left";
			publish();
		}
		if (room != previousRoom)
		{
			if (encounter != null && !encounter.ended)
			{
				interruptEncounter("Room left without confirmed completion");
				publish();
			}
			armed = true;
		}
		if (state == 0)
		{
			armed = true;
		}
		boolean active = (state >= 1 && state <= 3) || observedCombat;
		boolean finishedHere = encounter != null && encounter.completed && encounter.room == room && !raid.ended;
		if (inRaid && room != null && active && armed && !finishedHere)
		{
			if (encounter == null || encounter.ended || encounter.room != room || raid.ended)
			{
				if (raid == null || raid.ended)
				{
					raid = new RaidLog(clean(player.getName()));
				}
				boolean partial = previousRoom != room || previousRoomState != 0;
				clearEvidence();
				encounter = raid.begin(room, client.getTickCount(), partial);
				encounterToken = encounter.token;
				sequence = 0;
				// One initial inventory of already-spawned NPCs, then spawn/despawn events only.
				for (NPC npc : player.getWorldView().npcs())
				{
					spawns.put(npc, ++nextSpawn);
				}
				publish();
			}
		}
		previousRoom = room;
		previousRoomState = state;
		String status = encounter != null && !encounter.ended ? "Recording · " + encounter.room.displayName
			: raid != null ? raid.status : "Waiting for Theatre of Blood";
		ui(generation, () -> panel.status(status));
	}

	private boolean capturing()
	{
		return running && encounter != null && !encounter.ended && raid != null && !raid.ended
			&& client.getGameState() == GameState.LOGGED_IN && TobRoom.fromRegion(region()) == encounter.room;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Actor actor = event.getActor();
		// Incoming damage on every non-local player is ignored. Consenting players send their own.
		if (!(actor instanceof NPC) && actor != client.getLocalPlayer())
		{
			return;
		}
		Hitsplat hit = event.getHitsplat();
		if (!isDamage(hit) || hit.getAmount() < 0)
		{
			return;
		}
		if (!capturing())
		{
			// An observed NPC hit is also an activity signal, covering waves before a boss HP bar appears.
			synchronizeLocation(actor instanceof NPC);
		}
		if (!capturing())
		{
			return;
		}
		NPC npc = actor instanceof NPC ? (NPC) actor : null;
		if (npc != null && THRALLS.contains(npc.getId()))
		{
			return;
		}
		pending.add(new PendingHit(actor, client.getTickCount(), client.getGameCycle(), hit.getAmount(),
			hit.getHitsplatType(), hit.isMine(), hit.isOthers(), npc == null ? -1 : npc.getId(),
			npc == null ? -1 : npc.getIndex(), npc == null ? -1 : spawns.computeIfAbsent(npc, ignored -> ++nextSpawn),
			npc == null ? "Unknown" : clean(npc.getName())));
		if (npc == null && hit.getAmount() > 0)
		{
			lastTakenCycle = client.getGameCycle();
		}
	}

	static boolean isDamage(Hitsplat hit)
	{
		return hit.isMine() || hit.isOthers() || statusKind(hit.getHitsplatType()) != DamageHit.Kind.UNRESOLVED;
	}

	private static DamageHit.Kind statusKind(int type)
	{
		switch (type)
		{
			case HitsplatID.POISON: return DamageHit.Kind.POISON;
			case HitsplatID.VENOM: return DamageHit.Kind.VENOM;
			case HitsplatID.BLEED: return DamageHit.Kind.BLEED;
			case HitsplatID.BURN: return DamageHit.Kind.BURN;
			default: return DamageHit.Kind.UNRESOLVED;
		}
	}

	private void flushHits()
	{
		if (encounter == null || pending.isEmpty())
		{
			return;
		}
		Map<Actor, Integer> count = new IdentityHashMap<>();
		for (PendingHit hit : pending)
		{
			if (hit.mine || !(hit.actor instanceof NPC))
			{
				count.merge(hit.actor, 1, Integer::sum);
			}
		}
		for (PendingHit hit : pending)
		{
			if (hit.actor instanceof NPC && !hit.mine)
			{
				if (hit.others)
				{
					encounter.other(hit.npcId, hit.npcIndex, hit.amount, hit.tick);
				}
				else
				{
					encounter.miscellaneous(hit.amount);
				}
				continue;
			}
			boolean dealt = hit.actor instanceof NPC;
			DamageHit.Kind kind = statusKind(hit.type);
			AttackEvidence.Candidate candidate = kind == DamageHit.Kind.UNRESOLVED
				? evidence.match(hit.actor, hit.cycle, count.getOrDefault(hit.actor, 0),
					dealt && Math.abs(hit.cycle - lastTakenCycle) <= 30) : null;
			int npcId = dealt ? hit.npcId : candidate == null ? -1 : candidate.getNpcId();
			int npcIndex = dealt ? hit.npcIndex : candidate == null ? -1 : candidate.getNpcIndex();
			int weapon = candidate != null && dealt && candidate.getKind() == DamageHit.Kind.WEAPON
				? candidate.getWeaponId() : -1;
			String npcName = dealt ? hit.npcName : npcId < 0 ? "Unknown" : clean(client.getNpcDefinition(npcId).getName());
			String weaponName = weapon < 0 ? "Unknown" : clean(itemManager.getItemComposition(weapon).getMembersName());
			String attack = kind != DamageHit.Kind.UNRESOLVED ? kind.name()
				: candidate == null ? "Unknown (may include recoil / indirect damage)" : candidate.getAttack();
			String confidence = candidate == null ? "Observed amount; unresolved source/weapon"
				: "Inferred from a single nearby attack observation";
			DamageHit recorded = new DamageHit(sequence++, Math.max(0, hit.tick - encounter.startTick),
				dealt ? DamageHit.Direction.DEALT : DamageHit.Direction.TAKEN, hit.amount, hit.type,
				npcId, npcIndex, dealt ? hit.spawn : candidate == null ? -1 : candidate.getNpcSpawn(),
				candidate == null ? kind : candidate.getKind(), weapon,
				candidate == null ? -1 : candidate.getProjectileId(), npcName, weaponName, attack, confidence);
			encounter.personal(raid.session, raid.player, recorded);
			journal.add(new EncounterLog.Entry(raid.session, raid.player, false, recorded));
			outgoing.add(recorded);
		}
		pending.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (capturing())
		{
			spawns.put(event.getNpc(), ++nextSpawn);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		spawns.remove(event.getNpc());
		// Despawning, dying animations and phase changes are not completion signals.
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!capturing())
		{
			return;
		}
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer() && actor.getInteracting() instanceof NPC && actor.getAnimation() != -1)
		{
			lastAnimationCycle = client.getGameCycle();
			boolean recognized = MELEE_ANIMATIONS.contains(actor.getAnimation()) || PROJECTILE_ANIMATIONS.contains(actor.getAnimation());
			lastWeapon = recognized && client.getLocalPlayer().getPlayerComposition() != null
				? client.getLocalPlayer().getPlayerComposition().getEquipmentId(KitType.WEAPON) : -1;
			if (MELEE_ANIMATIONS.contains(actor.getAnimation()))
			{
				evidence.add(new AttackEvidence.Candidate(actor.getInteracting(), client.getGameCycle(),
					DamageHit.Kind.WEAPON, lastWeapon, -1, -1, -1, -1, "Melee weapon attack"));
			}
			else if (!recognized)
			{
				// An unsupported local action may be an attack. It must not be mistaken for a nearby thrall's hit.
				evidence.add(new AttackEvidence.Candidate(actor.getInteracting(), client.getGameCycle(),
					DamageHit.Kind.UNRESOLVED, -1, -1, -1, -1, -1, "Unresolved local action"));
			}
		}
		else if (actor instanceof NPC && actor.getInteracting() == client.getLocalPlayer())
		{
			String attack = null;
			switch (actor.getAnimation())
			{
				case AnimationID.TOB_SOTETSEG_ATTACK_MELEE: attack = "Sotetseg melee"; break;
				case AnimationID.VERZIK_PHASE2_ATTACK_MELEE: attack = "Verzik bounce / melee"; break;
				case AnimationID.VERZIK_PHASE3_ATTACK_MELEE: attack = "Verzik melee"; break;
				default: break;
			}
			if (attack != null)
			{
				NPC npc = (NPC) actor;
				evidence.add(new AttackEvidence.Candidate(client.getLocalPlayer(), client.getGameCycle(),
					DamageHit.Kind.UNRESOLVED, -1, -1, npc.getId(), npc.getIndex(),
					spawns.computeIfAbsent(npc, ignored -> ++nextSpawn), attack));
			}
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (!capturing())
		{
			return;
		}
		Projectile projectile = event.getProjectile();
		if (projectiles.containsKey(projectile))
		{
			return;
		}
		Actor source = projectile.getSourceActor();
		Actor target = projectile.getTargetActor();
		boolean personal = source == client.getLocalPlayer() && target instanceof NPC;
		boolean incoming = source instanceof NPC && target == client.getLocalPlayer();
		boolean thrall = source instanceof NPC && THRALLS.contains(((NPC) source).getId()) && target instanceof NPC
			&& client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) > 0;
		if (!personal && !incoming && !thrall)
		{
			return;
		}
		projectiles.put(projectile, projectile.getEndCycle());
		NPC npc = source instanceof NPC ? (NPC) source : null;
		int weapon = personal && client.getGameCycle() - lastAnimationCycle <= 60 ? lastWeapon : -1;
		String attack = personal ? "Ranged / magic weapon attack" : thrall ? "Thrall projectile" : projectileAttack(projectile.getId());
		evidence.add(new AttackEvidence.Candidate(target, projectile.getEndCycle(),
			thrall ? DamageHit.Kind.THRALL : personal ? DamageHit.Kind.WEAPON : DamageHit.Kind.UNRESOLVED,
			weapon, projectile.getId(), npc == null ? -1 : npc.getId(), npc == null ? -1 : npc.getIndex(),
			npc == null ? -1 : spawns.computeIfAbsent(npc, ignored -> ++nextSpawn), attack));
	}

	private static String projectileAttack(int id)
	{
		switch (id)
		{
			case SpotanimID.MAIDEN_SHADOW_PROJ: return "Maiden shadow projectile";
			case SpotanimID.MAIDEN_BLOOD_PROJ: return "Maiden blood projectile";
			case SpotanimID.TOB_NYLOCAS_RANGEDPROJECTILE_SIZE1:
			case SpotanimID.TOB_NYLOCAS_RANGEDPROJECTILE_SIZEMID:
			case SpotanimID.TOB_NYLOCAS_RANGEDPROJECTILE_SIZE2: return "Nylocas ranged projectile";
			case SpotanimID.TOB_SOTETSEG_MAGING: return "Sotetseg magic projectile";
			case SpotanimID.TOB_SOTETSEG_RANGING: return "Sotetseg ranged projectile";
			case SpotanimID.TOB_SOTETSEG_SHAREDATTACK: return "Sotetseg shared attack";
			case SpotanimID.TOB_XARPUS_ACIDSPIT: return "Xarpus acid spit";
			case SpotanimID.VERZIK_PHASE1_PROJECTILE: return "Verzik phase 1 projectile";
			case SpotanimID.VERZIK_PHASE2_RANGED: return "Verzik phase 2 ranged projectile";
			case SpotanimID.VERZIK_PHASE2_LIGHTNING: return "Verzik lightning";
			case SpotanimID.VERZIK_PHASE2_BLOODPROJ: return "Verzik blood projectile";
			case SpotanimID.VERZIK_PHASE3_RANGEPROJ: return "Verzik phase 3 ranged projectile";
			case SpotanimID.VERZIK_PHASE3_MAGEPROJ: return "Verzik phase 3 magic projectile";
			default: return "Unknown NPC projectile";
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!running || raid == null || raid.ended || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		TobRoom room = TobRoom.completedBy(message);
		if (room != null && encounter != null && encounter.room == room && !encounter.ended)
		{
			completedRoom = room;
		}
		if (message.startsWith("Theatre of Blood total completion time:"))
		{
			completedRaid = true;
		}
	}

	private void finishRaid()
	{
		endTick = client.getTickCount();
		if (encounter != null && !encounter.ended)
		{
			encounter.finish(encounter.room == TobRoom.VERZIK, "Confirmed raid completion");
		}
		raid.completed = true;
		raid.ended = true;
		raid.status = "Raid completed";
		publish();
	}

	private void interruptEncounter(String reason)
	{
		if (encounter != null && !encounter.ended)
		{
			encounter.finish(false, reason);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (running && (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING))
		{
			flushHits();
			flushJournal();
			interruptEncounter("Connection interrupted");
			if (raid != null)
			{
				raid.status = raid.completed ? "Raid completed" : "Recording interrupted";
				publish();
			}
			clearEvidence();
			previousRoomState = -1;
			armed = true;
		}
	}

	private void sendSelfReports()
	{
		if (raid == null || encounter == null || !partyService.isInParty() || partyService.getLocalMember() == null)
		{
			outgoing.clear();
			return;
		}
		if (outgoing.isEmpty() && (!capturing() || client.getTickCount() % 5 != 0))
		{
			return;
		}
		RaidLogsUpdate update = new RaidLogsUpdate();
		update.setSession(raid.session);
		update.setEncounter(encounterToken);
		update.setPlayer(raid.player);
		update.setWorld(client.getWorld());
		update.setRoom(encounter.room.name());
		update.setTick(Math.max(0, client.getTickCount() - encounter.startTick));
		update.setHits(new ArrayList<>(outgoing.subList(0, Math.min(MAX_BATCH, outgoing.size()))));
		if (outgoing.size() > MAX_BATCH)
		{
			encounter.partial = true;
		}
		try
		{
			partyService.send(update);
		}
		catch (RuntimeException exception)
		{
			log.debug("Party self-report could not be sent", exception);
			encounter.partial = true;
			ui(generation, () -> panel.error("Party connection unavailable; local recording continues."));
		}
		outgoing.clear();
	}

	@Subscribe
	public void onRaidLogsUpdate(RaidLogsUpdate update)
	{
		int receivedGeneration = generation;
		clientThread.invoke(() ->
		{
			if (running && generation == receivedGeneration)
			{
				receive(update);
			}
		});
	}

	private void receive(RaidLogsUpdate update)
	{
		if (client.getGameState() != GameState.LOGGED_IN || raid == null || encounter == null || !partyService.isInParty()
			|| (encounter.ended && client.getTickCount() - endTick > 12)
			|| update.getProtocol() != 1 || update.getWorld() != client.getWorld()
			|| !encounter.room.name().equals(update.getRoom()) || !uuid(update.getSession()) || !uuid(update.getEncounter())
			|| update.getHits() == null || update.getHits().size() > MAX_BATCH
			|| update.getTick() < 0 || update.getTick() > 100_000)
		{
			return;
		}
		PartyMember member = partyService.getMemberById(update.getMemberId());
		PartyMember local = partyService.getLocalMember();
		if (member == null || local == null || member.getMemberId() == local.getMemberId()
			|| !clean(member.getDisplayName()).equals(clean(update.getPlayer())))
		{
			return;
		}
		// Read a name only to validate the opted-in sender against this instance. Never retain a non-participant roster.
		Player sender = null;
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (clean(player.getName()).equals(clean(update.getPlayer())))
			{
				sender = player;
				break;
			}
		}
		if (sender == null || TobRoom.fromRegion(WorldPoint.fromLocalInstance(client, sender.getLocalLocation()).getRegionID()) != encounter.room)
		{
			return;
		}
		if (lastPeerTick.getOrDefault(update.getMemberId(), -1) == client.getTickCount())
		{
			return;
		}
		lastPeerTick.put(update.getMemberId(), client.getTickCount());
		String peer = update.getSession() + "/" + update.getEncounter();
		String previous = peerEncounters.putIfAbsent(update.getMemberId(), peer);
		if (previous != null && !previous.equals(peer))
		{
			encounter.partial = true;
			return; // A restarted sender cannot silently attach a different attempt to this encounter.
		}
		String recorder = update.getMemberId() + "/" + peer;
		boolean changed = false;
		for (DamageHit hit : update.getHits())
		{
			if (hit == null || !hit.valid() || hit.getTick() > update.getTick() || update.getTick() - hit.getTick() > 12)
			{
				continue;
			}
			int estimatedTick = client.getTickCount() - update.getTick() + hit.getTick();
			if (encounter.remote(recorder, clean(update.getPlayer()), hit, estimatedTick))
			{
				journal.add(new EncounterLog.Entry(recorder, clean(update.getPlayer()), true, hit));
				changed = true;
			}
		}
		if (changed && encounter.ended)
		{
			flushJournal();
			publish();
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		int receivedGeneration = generation;
		clientThread.invoke(() ->
		{
			if (running && generation == receivedGeneration)
			{
				peerEncounters.clear();
				lastPeerTick.clear();
				outgoing.clear();
				if (encounter != null && !encounter.ended)
				{
					encounter.partial = true;
				}
			}
		});
	}

	private void flushJournal()
	{
		if (raid != null && encounter != null && !journal.isEmpty())
		{
			store.append(raid.session, new LocalLogStore.JournalBatch(encounterToken, new ArrayList<>(journal)));
			journal.clear();
		}
	}

	private void publish()
	{
		if (raid != null)
		{
			RaidLog.Snapshot snapshot = raid.snapshot();
			store.save(snapshot);
			ui(generation, () -> panel.current(snapshot));
		}
	}

	private void ui(int expectedGeneration, Runnable action)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (running && generation == expectedGeneration)
			{
				action.run();
			}
		});
	}

	private static String clean(String value)
	{
		return value == null ? "Unknown" : Text.removeTags(value).replace('\u00a0', ' ');
	}

	private static boolean uuid(String value)
	{
		return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}
}
