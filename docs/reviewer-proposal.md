# Draft scope question for RuneLite reviewers

Draft only; not sent. Updated for the user's agreed scope.

Raid Logs is a Theatre of Blood recorder for retrospective damage analysis. Encounter details become available after that encounter is confirmed complete for the group. Raid analysis becomes available after raid completion. It offers no live attack timeline, boss attack counter, focus/projectile indicator, prediction, prayer/position advice, or encounter simulation. Local death or logout does not unlock an ongoing encounter.

The requested data is personal dealt and taken damage, weapon and incoming-attack attribution where inferable, and thrall/recoil categories where supportable. Unknown attribution remains explicit. PartyPlugin is a dependency. Running Raid Logs in the same Party opts participants into exchanging their own live observations. Stored logs remain under `.runelite/raid-logs` and are never uploaded or shared.

Non-participants contribute only an aggregate of observed outgoing damage. Their names, equipment, incoming damage and individual damage events are not stored. Short-lived NPC/amount/tick fingerprints support reconciliation against consenting self-reports, then expire without persistence.

Could you confirm whether this scope, including encounter-completion unlocking and named incoming attacks, fits the existing restrictions? How do the high-end PvM plugin and player-data restrictions apply to this specific retrospective design?

Could you also advise on permitted message volume and payload limits for per-tick self-report batches through PartyService? The implementation does not fetch historical data or automatically create/join parties.

We have examined DPS Counter, Damage History and Blert as precedents without treating their acceptance as automatic approval. No additional content exclusions have been specified by the user.
