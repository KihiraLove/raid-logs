# Raid Logs feasibility

Initial research reviewed 2026-09-11. Its design recommendations are historical; the user's subsequent [agreed scope](scope.md) supersedes them. A first implementation now exists; in-game behavior has not been verified.

The idea is feasible as a retrospective raid recorder with explicitly incomplete attribution. A guaranteed record of every player's every damage instance, weapon, thrall, and incoming enemy attack is not available from the inspected RuneLite APIs. Party improves coverage and attribution by combining each participant's observations; it does not create missing combat metadata.

## Rules and review boundary

Jagex prohibits boss assistance through attack prediction, attack counters, projectile target indicators, NPC focus indicators, and additional mechanic cues. A live incoming-attack timeline could provide such assistance even if it only lists recent events. My recommendation is to record silently and unlock detailed analysis after the raid has ended for the group. A local death alone must not unlock an ongoing encounter. This is a conservative design proposal, not an explicit exemption in the rules. [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

RuneLite also lists new high-end PvM boss plugins, exposing player information over HTTP, and crowdsourcing information about other players among rejected features. These make encounter-specific decoding and any hosted team-log service subjects for reviewer discussion. Consent and a network warning do not by themselves guarantee acceptance. [RuneLite restrictions](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)

There is relevant precedent: the Plugin Hub contains a pinned Blert entry with a raid-data upload warning. Blert describes recording encounters for later tick-by-tick review. This supports the general retrospective-analysis concept, but does not approve our proposed implementation or establish a blanket exception to the restrictions. We should ask reviewers how those restrictions apply to this exact scope, rather than assume all raid logging or all uploading is forbidden. [Hub entry](https://raw.githubusercontent.com/runelite/plugin-hub/master/plugins/blert), [Blert project](https://github.com/blert-io/plugin)

## What the client can actually observe

`HitsplatApplied` contains the recipient actor and hitsplat. `Hitsplat` supplies amount, type, expiry, and mine/others classification. Neither contains an attacker ID, weapon ID, spell ID, thrall owner, or named enemy attack. The event also fires for processed hitsplats that are not rendered because visible slots are full; this does not extend visibility beyond information received by the client. [HitsplatApplied](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/events/HitsplatApplied.java), [Hitsplat](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/Hitsplat.java)

| Requested data | One recording client | With participating recorders |
| --- | --- | --- |
| Damage amount and recipient | Record received hitsplats for loaded actors, including received zero hitsplats. Distinguish damage from healing and other hitsplat types. | More viewpoints improve coverage; duplicates must be reconciled. |
| Local player's outgoing damage | Mine classification supports local attribution for applicable hitsplat types. Damage mechanism remains a separate question. | Each sender reports its own observations. |
| Each teammate's outgoing damage | Other classification does not identify which teammate. Simultaneous attacks cannot always be separated. | Self-reports provide attribution unavailable to the observing client. Missing participants remain incomplete. |
| Weapon for each hit | Correlate an attack-time equipment snapshot with the later hit. Weapon equipped when damage lands may already have changed. | Local snapshots improve reliability, but delayed, multi-hit, area and indirect damage still need matching. |
| Thrall damage and owner | Requires correlation of summon, NPC and attack observations. The inspected NPC API has no generic owner accessor. Small damage is not sufficient evidence of a thrall. | Each owner can contribute summon context; separating coincident weapon, thrall and reflected damage still needs validation. |
| Incoming damage to each player | Recipient and amount are observable when the player is loaded. The hitsplat does not name its source. | Each player provides its own incoming observations, including when outside another recorder's view. |
| Enemy and named attack causing damage | Sometimes inferable from projectiles, animations and encounter knowledge; ambiguous for overlapping attackers, ground effects, recoil and damage over time. | Additional viewpoints help but do not make every cause certain. |

The local `Projectile` API exposes nullable source and target actors plus projectile IDs and timing. These can be evidence for retrospective matching, but do not directly link a projectile to a particular hitsplat. `Actor.getInteracting()` is likewise not a damage-source field. Do not present either as a live focus/impact indicator.

Local references: `runelite-api/.../Projectile.java:64`, `Actor.java:83`, `NPC.java:32` in the linked RuneLite checkout. RuneLite's own special-attack tracker contains matching logic for weapon hits versus thralls/vengeance, and recent development documents remaining splash ambiguity. This is a concrete reason not to promise perfect attribution. [Special Attack Counter change](https://github.com/runelite/runelite/pull/20377)

## Damage History findings

Inspected linked checkout: `d31bbaf`, dated 2026-02-05. RuneLite checkout: `ac79ed8bd`, dated 2026-09-03. Current upstream hitsplat definitions were also checked online.

Damage History consumes `predicted-hit` plugin messages from Customizable XP Drops, deserializes an XP-derived estimate with weapon/NPC metadata, and distributes it through `PartyService`. It is not a complete server combat log. Its README explicitly documents missing zero hitsplats and splashes; the inspected model has no thrall-specific or incoming enemy-attack record. It excludes Hunllef and NPC IDs above a fixed cutoff. Those exclusions are implementation facts, not proof of why reviewers required them. [Upstream README](https://github.com/QuestingPet/DamageHistory)

Local source entry points:

- `F:/Github/DamageHistory/src/main/java/com/damagehistory/DamageHistoryPlugin.java:127`: XP message intake and sharing.
- `F:/Github/DamageHistory/src/main/java/com/damagehistory/PredictedHit.java`: estimate and metadata model.
- `F:/Github/DamageHistory/src/main/java/com/damagehistory/DamageHistoryPartyMessage.java`: transport message.
- `F:/Github/fork/runelite/runelite-client/src/main/java/net/runelite/client/plugins/dpscounter/DpsCounterPlugin.java:180`: direct hitsplat capture and local-versus-party attribution pattern.

Use Damage History as a reference, not the core data model. Record observed hitsplats as evidence; any XP estimate should remain separate rather than silently replacing an observed amount or being counted twice.

## Is the built-in Party plugin necessary?

No hard dependency on `PartyPlugin` is technically necessary. `PartyService` and `WSClient` live outside that UI plugin. The built-in DPS Counter injects them and exchanges custom messages without a `PluginDependency(PartyPlugin.class)` annotation. `PartyService.changeParty()` provides session joining, so Raid Logs could offer a small explicit create/join/leave interface. This would still use RuneLite's Party infrastructure. It must respect the shared session used by other plugins.

For named team records, each participant needs Raid Logs or a compatible sender. Enabling the standard Party plugin alone does not transmit the detailed events we need; the existing DPS message only carries a hit amount and boss flag.

Without any Party infrastructure, the options are personal/partial local recording, manually combining exported participant logs after the raid, or building a separate opt-in transport. A separate relay adds hosting, privacy, protocol and review work without solving attribution ambiguity. Recommendation: an independent recorder with optional PartyService integration, not a fork of PartyPlugin. Follow its registration/unregistration lifecycle and use sender-associated custom messages, as DPS Counter does.

## Proposed implementation after scope review

1. Start with local recording and retrospective tables for a small explicitly supported raid scope. Unknown sources, weapons and attacks remain unknown. Do not silently enable future content through a broad NPC-ID range.
2. Keep raw observations separate from inferred attribution. Record confidence per field: an observed amount can have an inferred weapon and an unknown attack name.
3. Give events recorder/session IDs and sequence numbers. Establish raid-relative timing; do not assume raw client tick counters align across clients. Track NPC spawn lifetimes and instance context, since type IDs and reused indices are insufficient identity.
4. Add optional participant self-report exchange. Deduplicate multiple viewpoints without merging legitimate same-tick hits; report disconnects and missing coverage explicitly. Bound message size/rate and confirm permitted Party traffic with maintainers.
5. Keep persistence under `RuneLite.RUNELITE_DIR/raid-logs`, move disk work off the client thread, and provide user-initiated export if needed. No public upload service in the initial proposal.

Unresolved points for RuneLite reviewers are in [reviewer-proposal.md](reviewer-proposal.md). No reviewer has been contacted and no approval is claimed.

## Future manual validation

There is no changed runtime behavior to test yet. Once implemented, run `./gradlew run` from this project's root and follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide to log in. Only the user should operate the game and verify results.

Test single attacks, weapon swaps before projectile impact, multi-hit specials, area damage, zero hits and magic splashes, thrall-only and simultaneous thrall/weapon hits, reflected and damage-over-time effects, overlapping enemy attacks, NPC transformations/respawns, split rooms, death/disconnect/rejoin, duplicate reports, and the analysis lock until group completion. Startup or unit-test success does not validate in-game attribution.
