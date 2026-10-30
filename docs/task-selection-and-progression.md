# Accessible goals and progression

## Opening the menu

Use your configured **Tasks key** (currently **K** in the live-test setup).
The registration identifier and default G binding are unchanged; no options file
is rewritten. Minecraft Controls remains the place to change the key. The HUD
shows the actual configured binding. R/J and F7/F8/F9 research controls retain
existing behavior. Opening Tasks stops an active autonomous experiment.

The home screen shows your **main goal**, the **next suggested task**, and a short
**why**. It offers Pause/Resume, Change Goal, Ender Dragon milestones and More controls / suggestions. With no active goal it offers Start Goal. Close returns to gameplay.
The local world pauses while the menu is open.

Browse All Goals is separate: choose a category, then view one goal per page at
small GUI sizes or two at larger sizes. Read its explanation before pressing
**Start Goal**. That explicit action changes the main goal and starts/resumes it.
The Suggested goals group shows up to three adaptive alternatives.

Use mouse or Tab/arrows and Enter. Native buttons retain narration and keyboard
input. Primary labels are enlarged, with a dark high-contrast background, border,
and an additional inner outline and arrow when focused/hovered. Status always
uses words, not color alone. Back and Close remain at the bottom in consistent
positions. Full button text is also in tooltips/narration when a narrow screen
truncates a label. GUI scale, screen-reader behavior and real keyboard focus must
still be verified in Minecraft; headless tests cannot verify rendering.

## Goal controls

- **Start Goal** starts the selected goal and explicitly clears its paused state.
- **Pause Goal** stops active instructions and clears route/target visuals; it
  preserves the main goal, observations and preferences.
- **Resume Goal** recalculates the next step from fresh current evidence.
- **Change Goal** opens browsing. Reading another goal does not change yours.
- **Cancel Goal...** opens a confirmation. Keep my goal, Back, Close or Escape do
  not cancel. **Yes, cancel goal** clears the active goal and its chosen step;
  verified observations, biome history and preferences remain saved.

If the main goal is Defeat the Ender Dragon, a goal-detail screen also offers
**Use as next step; keep dragon goal**. This permits another approach without
silently replacing the main goal. A chosen step stays until completed; then the
adaptive suggestion resumes. Paused goals remain paused until explicitly resumed.

## Continuous survival monitoring

Active guidance now uses a stateful survival monitor with live nutrition and
gear durability, temporary interruptions, hysteresis and dismissible noncritical
suggestions. This supersedes the old one-off eight-item readiness check for active
long-term progression. See [continuous-survival-monitoring.md](continuous-survival-monitoring.md)
for thresholds, controls, save compatibility and live verification.

## Adaptive preparation and progression

The dragon goal assesses current food, hunger, health, tools, equipped armor,
shield and building stock before recommending dangerous travel. A new player is
not sent directly for pearls. Examples:

- No food: gather food; low hunger with food: eat; full hunger with low health:
  rest for recovery.
- No suitable pickaxe: prepare a stone pickaxe, skipping it if a sturdier one is
  already carried. A carried stone-or-better axe can satisfy weapon readiness.
- Insufficient equipped armor: make/equip armor rather than endlessly crafting an
  already-carried piece. Equipped armor value is distinct from carried items.
- Missing shield or building supplies: prepare those before a dangerous trip.

These are conservative initial readiness heuristics, not proof that a trip is
safe or a forced checklist. They do not model armor durability, difficulty,
enchantments, shelter, nearby threats, or every useful equipment alternative yet.
A player can choose another step or goal. In the End arena the guide does not send
the player into a tool/armor crafting checklist during a fight; recovery and food
needs can still be shown. Live usability testing is essential.

The selected main goal stays unchanged as preparation steps complete. Current
state is observed about once per second during gameplay and after resume/change;
the menu freezes its latest evidence while the player reads. The HUD shows one
immediate action with a short reason, keeping the main goal visible. Completing
an optional goal does not automatically select another main goal.

Crafting dependencies, ingredient choices and output quantities come from loaded
Minecraft recipe data through the existing recipe planner. Readiness rules choose
a useful output, not a hand-written sequence of crafting actions. Stone-axe
navigation/guidance is preserved. Initial crafting goals now also include a stone
pickaxe, stone sword and armor, alongside the prior table/furnace/shield/bed goals.
Detailed smelting, trading, portal building/casting and combat still require work.

## Dragon milestones and evidence

Ender Dragon milestones has a dedicated home-screen button. Each page describes
one milestone and its purpose. Start the dragon goal directly on this screen, or
read about a particular step. Status words are:

- **Completed:** current inventory, dimension, personal advancement, or appropriate
  observed world state verifies it.
- **Next — available choice:** its local readiness conditions are met. Several
  milestones can be valid choices; this is not a required linear order.
- **Not Ready:** prepare supplies or find the needed place first.
- **Unknown:** required evidence cannot currently be verified.

Milestones cover working Nether access, Nether entry, blaze supplies, pearl
supplies, Eyes, strongholds, End portal activation, End entry, crystals and dragon
victory. Historical visits do not prove current location or current inventory.
Current prepared Eyes bypass raw-resource gathering. A complete observed portal
ring requests only its missing Eyes. Portal blocks verify activation; filled
frames alone do not. An End exit portal is not an Overworld entry portal.

Integrated-server queries inspect actual advancements and current structures.
Crystal completion requires the relevant arena chunks already loaded, an active
live dragon fight and zero observed crystals. Unknown/unloaded counts never mean
success. Personal dragon completion requires the player's advancement; a previous
world victory is not attributed to this player. Queries do not grant advancements
or force-load arena chunks. Remote-server evidence remains limited and is marked
unknown where appropriate. Detailed dragon aiming, cages, attack timing, retreat,
healing strategy and respawn guidance are not complete.

## Saves and preserved features

Local saves remain:

`<world>/minecraft-ai-guide/accessibility-goals/<player-UUID>.properties`

The existing `accessibility-goals-v1` format is retained with optional `active`
and `preferredStep` fields. Original files load with their goal, pause flags,
preferences, biome observations and evidence journal intact; missing new fields
use compatible defaults. Cancellation persists without deleting evidence. Schema
or read errors preserve the file and display a warning rather than overwriting it.
The journal is historical context, never proof of current resource readiness.
Remote-world selection remains session-only.

Optional equipment reminders remain off by default. They do not block progression
or replace the immediate instruction. They refer to observed nearby equipment,
not assumed ownership.

This accessibility work performs no movement, mining, crafting or combat. It does
not modify Q-learning, checkpoints, experiment data, recipe dependencies, or the
navigation implementation. See [navigation-backlog.md](navigation-backlog.md) for
unresolved route visibility/loss, safe excavation/waterfall alternatives and useful
stone quantity/group selection.

## Verification

Run `./gradlew compileClientJava clientTest check`. Regression fixtures cover
early-game readiness, current versus historical evidence, better tools, armor
loops, main versus immediate goals, optional approaches, pause/start/resume,
cancellation confirmation, original and extended save compatibility, milestone
states, and shared menu navigation/page-size rules, alongside existing checks.

Before relying on the revised interface, live-test:

1. K remains bound, keyboard focus is obvious, Tab/arrows/Enter/Escape and narrator
   are usable, and text fits at normal and large GUI scales.
2. A new player's dragon goal suggests food/tools/preparation, then updates after
   collecting, crafting, equipping, eating or recovering.
3. Pause clears instructions/visuals; resume updates; cancel requires confirmation
   and leaves history intact across save/rejoin.
4. A chosen optional step preserves the main dragon goal and advances when done.
5. Milestone descriptions, unknown evidence, dimension transitions and equipped
   shield/armor detection match actual gameplay.
6. Existing stone-axe guidance still works. Navigation's documented issues remain
   separate; this menu work does not claim to fix them.
