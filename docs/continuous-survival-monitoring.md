# Continuous survival guidance

Environmental survival now adds clock context, nearby mob/food identification and
limited checked shelter work. See [environmental-survival-guidance.md](environmental-survival-guidance.md)
for supported construction cases, thresholds, limitations and live verification.

This is a passive accessibility feature. It reads the player's current state and
changes guidance; it never moves, mines, crafts, equips, eats, attacks, or alters
the world. The research agent's GameState and AgentGoalManager are unchanged.

## What the player sees

The selected **Main goal** remains visible. When needed, the HUD adds a labeled
**Temporary task**, one immediate instruction and a short reason. For example:

- Main goal: Defeat the Ender Dragon
- Temporary task: Replenish food
- Instruction: Gather usable food in a safe area for the rest of your trip.
- Why: Your food supplies are running low.

Once resolved, guidance returns to the previous unfinished task. Current completion
is rechecked, so a step completed during the interruption is skipped. The selected
main goal and player-chosen next step are not silently replaced. Ordinary survival
needs apply during optional goals too; equipment preparation is strongest for the
Nether/End/dragon goals. A worn carried tool can also prompt a replacement outside
those goals.

Open Tasks with the configured key (**K** in the live-test setup), then choose
**More controls / suggestions**. A noncritical interruption offers **Dismiss
suggestion for 2 minutes**. It returns sooner if conditions materially worsen.
Critical warnings cannot be dismissed. Pause and Cancel always take precedence:
no survival event resumes a paused/canceled goal. The existing confirm-to-cancel
flow and Back/Close controls are unchanged.

## Sampling and thresholds

Survival inventory, nutrition, hunger, health, durability, equipped armor and
nearby observed hazards are sampled about every **250 ms** during active guidance.
The heavier progression/structure snapshot retains its existing roughly one-second
refresh. Task-menu evidence remains stable while reading; explicit menu actions
refresh survival advice. The local game pauses in this screen. On remote servers,
the menu does not pause other players/the server; continuous menu-open alerts are
not implemented. Other screens defer ordinary new interruptions; urgent warnings
can still supersede them.

The constants are intentionally documented in `SurvivalMonitor`; no new keybinding
or configuration file is required.

| Need | Start | Resolve / stability |
| --- | --- | --- |
| Food reserve | At most 12 hunger points of usable food remain | At least 32 hunger points available |
| Eat | Hunger at most 14/20, with usable food | Hunger at least 18/20 |
| Recover health | Health at most 12/20 (6 hearts) | Health at least 18/20 (9 hearts) |
| Critical health | Health at most 6/20 (3 hearts) | At least 10/20, then ordinary recovery may continue |
| Severe hunger | Hunger at most 6/20 with no usable food | Safe-state recheck; ordinary eating/resupply may follow |
| Worn gear | At most 10 durability uses remain OR at most 10% remains | A usable replacement/spare; armor must be equipped and provide sufficient protection |
| Protective preparation | Equipped armor below 10 points before dangerous progression | At least 10 points, with no nearly broken equipped piece |
| Building reserve | Below 16 ordinary blocks for dangerous progression | At least 32 blocks |

Food totals sum each usable stack's **count × Minecraft FoodProperties.nutrition**.
They are not occupied inventory slots. For example, four cooked beef provide 32
hunger points; eight apples also provide 32. Low-nutrition foods need more items.
Saturation, spoilage-like mod mechanics and nutrition per second are not modeled.
Known harmful/random-effect foods (including rotten flesh, raw chicken,
pufferfish and suspicious stew) and chorus fruit are excluded conservatively.
Offhand/equipped stacks are deduplicated by stack identity.

The optional explicitly selected "Gather food" task still has its original
eight-item completion target. That optional collection goal is separate from the
continuous nutrition reserve. Active long-term progression and Suggested goals no
longer re-run the old eight-item readiness check when nutrition is adequate.

An initial readiness sample can recommend preparation immediately. Subsequent
ordinary new needs must persist for **2 seconds** before switching. Separate start
and resolution thresholds keep a one-point fluctuation from alternating tasks.
Food resupply remains pending if a health warning temporarily takes priority.

Dismissal lasts **120 seconds** in the current session. It is revoked by materially
lower reserves (half the prior reserve, crossing down to 4 nutrition points, or a
four-point hunger decline), additional health loss, substantially worse durability,
lower armor protection or fewer building supplies. An unchanged zero/missing
resource does not repeatedly revoke dismissal. Critical conditions bypass dismissal.
Dismissal timers are not saved or transferred between worlds.

## Immediate danger and context

Lava, fire, low underwater air (below 40 ticks), a fall over three blocks, a nearby
mob with an observed target of the player, recent health loss and critical health
are safety signals. They produce a practical safety/recovery instruction immediately,
not a recipe or resource hunt. Safety advice remains until danger has been quiet
for **8 seconds** and critical health has recovered sufficiently. Recent health
loss itself remains a hazard signal for eight seconds, so its advice may remain
longer than eight seconds total.

New ordinary preparation waits while mining, using an item, or using another
screen. Existing advice can remain visible. Hazards and critical health bypass
that delay. In the End, food advice acknowledges limited sources; equipment advice
asks for a sheltered check rather than sending the player into a crafting checklist
mid-fight. No position is certified safe, and no escape route is invented.

Equipment checks consider usable spares. A weak wooden/gold pickaxe is not treated
as an ore-trip-ready pickaxe. Nearly broken tools and shields use the existing
recipe solver to prepare an additional item rather than completing against the
old damaged one. The target count updates if the old item breaks. Missing armor
uses existing armor crafting/equipping guidance. Worn armor currently gets basic
repair/replacement advice; anvil, grindstone, enchantment and repair-material
planning are not implemented. Existing noncraft acquisition explanations (for
example raw iron needing smelting) remain in use.

## Persistence and preservation

The existing per-world, per-player `accessibility-goals-v1` file gains optional
`survivalResumeStep`, `survivalNeed` and `foodResupplyPending` fields. Old saves load
with safe defaults. These fields preserve the unfinished step and food hysteresis
through pause/save/rejoin; fresh observations must still validate any need.
Historical progress is never overwritten with temporary readiness. Cancellation
clears active transient needs while retaining verified progress/preferences.
World/player changes reset the session monitor. No Q-table, checkpoint, experiment
or world-reset files are read or written by the monitor.

Food decreases are observed regardless of whether items were eaten, dropped,
stored or otherwise used. A decrease together with a hunger increase is labeled
**likely consumption**, not a proven action event. It creates no experience/reward
and is not used as research evidence. This feature is not a learning algorithm.

## Verification and remaining live work

`./gradlew compileClientJava clientTest check` runs pure fixtures for consumption,
reserve loss/replenishment, nutrition/count differences, return to an unfinished
step, persistent priority/hysteresis, health, gear damage/spares/breakage, danger
versus ordinary needs, activity deferral, dismissal/worsening, pause, cancel and
old/new persistence. These fixtures do not launch Minecraft.

Live-test:

1. Start a long-term goal with adequate food; consume it while exploring. Verify
   that the temporary task appears once, replenishment clears it, and the exact
   unfinished step returns.
2. Compare four cooked beef with eight low-nutrition foods. Check menu suggestions
   and HUD agreement near thresholds and after eating.
3. Mine/use/craft while reserves are low. Confirm ordinary advice waits and urgent
   damage/low-air warnings still take priority.
4. Test worn tools, a healthy spare, offhand shields and worn/equipped armor.
   Confirm replacement quantities and prerequisite instructions in actual menus.
5. Dismiss a noncritical suggestion, worsen the need, pause/resume and save/rejoin.
   Verify cancellation never restarts guidance, including under low health.
6. Verify K, narration/focus, existing stone-axe guidance and navigation integration.

Threat detection is incomplete: client mob targeting can be unavailable, unseen
mobs/projectiles are not predicted, and a health decrease cannot identify its
cause reliably. Effects, full combat tactics, a safe-shelter finder, food-source
navigation and complete repair planning are outside this milestone. Hazard text
is advisory, not a guarantee of survival. Navigation remains unchanged; see
[navigation-backlog.md](navigation-backlog.md).
