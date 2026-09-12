# Raid Logs

A development-stage RuneLite plugin for local, retrospective Theatre of Blood damage analysis. All six ToB encounters are targeted. Entry, Normal and Hard completion-message formats are recognized; in-game behavior still needs user validation.

## Recording and consent

Enable Raid Logs. The built-in **Party** plugin is a required dependency.

- Your own dealt and taken damage is recorded even without a RuneLite party.
- Players running Raid Logs in the same Party automatically exchange their own live damage observations. There is no extra sharing toggle. Party alone does not send Raid Logs data.
- A sender must also be visible in the same ToB encounter to have reports accepted. Missing reports leave partial coverage; historical logs are never fetched.
- Non-participants contribute only an aggregate of other observed outgoing damage. Their names, equipment and incoming damage are not stored. Separate anonymous-player identities are not inferred.
- Stored logs are never uploaded or shared. Live self-reports use RuneLite's existing Party service; there is no custom server or upload endpoint.

Transient anonymous matching keys contain only an NPC index/type, amount and tick. They expire after a short window and are never persisted as individual events. They support subtracting consenting self-reports from the anonymous observed total without retaining a non-participant roster.

## Analysis

In the Raid Logs sidebar, select a saved raid and encounter, then choose **Open analysis**.

- Encounter analysis unlocks on that encounter's game completion message.
- Whole-raid analysis unlocks on the raid total-completion message or entry into the treasure room.
- Death, logout, NPC despawn, phase changes and leaving an unfinished room do not unlock its analysis.
- Completed encounters remain available while later encounters are being recorded.
- Incomplete attempts are retained but stay locked individually. A subsequently completed raid can include their partial records in overall analysis.

The Totals tab separates players, identified thralls, miscellaneous unowned damage and other/unmatched observed damage. The Damage log tab shows individual local and consenting-party observations.

## Accuracy contract

Amounts come from `HitsplatApplied`, not XP estimates. Received zero hitsplats and damage-over-time hitsplats are retained. Healing, prayer drain and disease stat drain are excluded. A magic splash without a hitsplat is not synthesized.

Weapon and enemy-attack labels are **inferred**, not server-confirmed. The implementation recognizes a limited set of weapon animations and ToB projectiles/melee animations. It snapshots local equipment at animation time and correlates later damage conservatively. Overlapping candidates, simultaneous multi-hits, possible reflection and missing evidence remain unresolved. Unsupported attacks and ground hazards still contribute their observed damage with an unknown cause.

Identified thrall projectile damage is placed in Misc rather than asserting an owner. Melee-thrall and recoil/vengeance separation is not yet reliable. Ambiguous local damage remains in the player's **credited damage** with an unresolved cause, so these totals must not be interpreted as pure weapon damage. Small damage alone is never treated as evidence of a thrall.

Party events are sender-associated and deduplicated by recorder/encounter/sequence. Other-damage reconciliation matches NPC index/type and amount within a small estimated tick window, consuming one occurrence per report. This is not an authoritative cross-client event identity. Unmatched party reports may overlap the other-damage residual, so the UI does not present their sum as an exact grand total. Remote ticks remain relative to each recorder's encounter start.

Late starts, split rooms, disconnects, room transitions without completion messages and sender restarts can leave gaps. Same-room retries without a room/raid transition may be grouped into one encounter record; validate Entry Mode retries before relying on attempt-level timing.

## Local files

Files are stored in `RuneLite.RUNELITE_DIR/raid-logs`, normally `%USERPROFILE%/.runelite/raid-logs`:

- `<session>.json`: raid metadata, completion flags, detailed consenting records and anonymous totals, written at encounter/session boundaries.
- `<session>.jsonl`: append-only batches of local and consenting-party hits, flushed on a storage worker each tick.

Reload combines snapshots and journals without duplicating hits. Journal recovery never unlocks an unfinished encounter. An abrupt exit can lose the last queued writes and unsnapshotted anonymous totals. Files contain consenting participants' names.

Serialization and disk I/O run off the client thread. Shutdown queues the final snapshot and terminates the writer without waiting on the game/UI thread. Files are not automatically deleted.

## Development and manual validation

The project targets Java 11 bytecode. With a Gradle-compatible JDK:

```text
./gradlew test
./gradlew run
```

Follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide to log in to the development client. Do not share account credential files. Only the user should operate RuneScape; a build or JVM start does not verify in-game accuracy.

Test in this order:

1. **Personal:** record ToB without a Party. Compare dealt/taken amounts against hitsplats after each completed room. Include zeros, poison, weapon switches before impact, scythe/multi-hit attacks, thrall-only attacks and weapon/thrall/recoil overlap.
2. **Gates:** during combat and after a local death, analysis stays locked. Maiden completion unlocks Maiden but not Bloat or raid totals. Sote maze and Verzik phase changes must not end an encounter. Test completion, abandonment, logout/rejoin and Entry Mode retries.
3. **Party:** with two participants, compare each self-report against that player's local file. Exercise joining, leaving and reconnecting; check duplicate prevention and coverage notices.
4. **Non-participants:** add a raider without Raid Logs. Check that their contribution is only an aggregate and that their name, equipment and taken damage are absent from both files.
5. **Persistence:** reload after restarting the client. Check for duplicate hits and verify that completed encounters unlock while interrupted encounters remain locked.

See [agreed scope](docs/scope.md), [initial policy research](docs/feasibility.md), and the [unsent reviewer proposal](docs/reviewer-proposal.md). No formal RuneLite approval is claimed.
