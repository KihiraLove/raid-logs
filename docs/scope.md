# Agreed scope — 2026-09-11

This supersedes the initial design recommendations in feasibility.md.

- Theatre of Blood only: Maiden, Bloat, Nylocas, Sotetseg, Xarpus and Verzik.
- Encounter analysis after encounter completion; combined raid analysis after raid completion.
- Personal dealt and taken damage accuracy first, followed by consenting-party records and anonymous outgoing totals.
- Named enemy attacks where defensible; unknown causes remain explicit.
- Thralls and reflected damage may use a miscellaneous category if ownership cannot be established. Ambiguous damage must not be silently assigned to a weapon or another player.
- Available raid data is stored locally. Stored logs are never uploaded or shared.
- The built-in Party plugin is a dependency. Running Raid Logs in the same Party opts participants into live self-report exchange.
- Non-participants contribute only aggregate outgoing damage. No names, equipment, incoming damage, or detailed log rows for them.
- The initial implementation uses one other-damage category, since individual anonymous attribution is not reliable.
- No additional content exclusions were specified. ToB scope is explicit, rather than enabling arbitrary future NPCs.

The user considers retrospective data collection distinct from a boss-assistance plugin. This is the scope we are implementing; it is not an official RuneLite policy decision.

## Initial implementation limits

The recorder preserves observed hitsplats and labels inference separately. First-pass thrall classification is limited to projectile correlation. Exact melee-thrall/recoil attribution, complete weapon/ground-effect decoding, and Entry Mode retry segmentation remain unresolved. Conservative unknowns are preferable to invented attribution.

Even a single candidate can be ambiguous if the client does not expose every cause. Inferred labels must be evaluated during user testing. Anonymous residual reconciliation is best-effort and must not be added to unmatched self-reports as an exact grand total.

## Lifecycle references

Room template regions, ToB room-state values and completion-message formats were checked against [Blert's TheatreChallenge](https://github.com/blert-io/plugin/blob/main/src/main/java/io/blert/challenges/tob/TheatreChallenge.java), [Location](https://github.com/blert-io/plugin/blob/main/src/main/java/io/blert/challenges/tob/Location.java) and [RoomDataTracker](https://github.com/blert-io/plugin/blob/main/src/main/java/io/blert/challenges/tob/rooms/RoomDataTracker.java). Our implementation uses RuneLite gameval constants where available. Map-region numbers and varbit enum values have no corresponding gameval constants.
