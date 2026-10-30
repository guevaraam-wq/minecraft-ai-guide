# Accessible environmental survival guidance

The emergency shelter milestone now extends the limited construction described
below. See [emergency-shelter-builder.md](emergency-shelter-builder.md) for compact
freestanding/hillside designs, materials, markers, nighttime waiting and exit guidance.

This milestone adds passive environmental evidence to the existing survival monitor.
The player performs every action. It does not move, mine, place blocks, craft, equip,
attack, sleep, change time, or reset a world.

## What the player sees

The HUD keeps the main goal, labels the temporary task, gives one immediate
instruction and explains why it appeared. Examples include:

- “Zombie nearby. Move away toward cover; avoid fighting.”
- “Night is approaching. Prepare cover before it gets dark.”
- “Place one dirt in the lower opening ahead of you (X ..., Y ..., Z ...).”
- “Look for the Cow to your left, about 6 blocks away.” The reason explains that
  cows can provide beef and raw beef should be cooked.

K remains the configured Tasks binding. Start/Pause/Resume/Cancel, confirmation,
optional reminders, food monitoring and progression saves retain their behavior.
There is no independent always-on safety-alert setting in the current project;
all new alerts stop with paused/canceled/inactive accessibility guidance. They do
not restart the goal or interfere with research controls.

Minecraft's existing SYSTEM/ALL narrator preference controls optional spoken
survival instructions; OFF and CHAT are respected by Minecraft's narrator API.
Unchanged instructions are not spoken every tick. Changed ordinary speech is
limited to once per eight seconds; changed urgent speech has a two-second minimum.
The task-menu narration also includes a current temporary instruction. Actual
voice/focus interaction still requires live testing.

## Time and warning stability

The observer reads Minecraft 26.2's Overworld clock (`getOverworldClockTime`).
Only an Overworld with skylight, no fixed time and no ceiling is treated as the
normal 24,000-tick cycle. Nether/End/fixed-time/custom-dimension contexts do not
inherit a normal night assumption. Highly customized Overworld timeline lengths
are not supported yet.

- Dusk preparation begins at tick 11,000.
- Night begins at tick 13,000.
- After an observed night, the dawn buffer lasts until tick 1,000 of the next day.
  A fresh world at time zero is correctly treated as daytime.
- Small backward clock changes near sunset retain the dusk state until below
  10,500. Time commands/sleep can therefore retain a conservative dawn warning
  briefly; this is not a perfect astronomical or weather model.
- Peaceful difficulty does not demand shelter or a weapon solely because it is night.

Protected players are not repeatedly sent to find shelter solely because it is
night. A stable quiet period of eight seconds releases shelter/danger advice.
Ordinary new interruptions retain the survival monitor's settling delay. A nearby
immediate threat bypasses crafting/mining/menu activity delays. One highest-priority
instruction is shown; alerts do not accumulate into a list.

Dusk suggestions are dismissible using More controls / suggestions. Becoming night,
a materially stronger threat or loss of protection can revoke a dismissal. Immediate
threats cannot be dismissed, while explicit Pause and Cancel always remain available.

## Entity recognition and food identification

Loaded living mobs are observed within approximately 20 blocks. Classification uses
Minecraft's `Enemy`, `MobCategory.MONSTER`, `NeutralMob`, animal classes and observed
targeting. It is not a small species-name whitelist for danger.

Neutral behavior takes precedence over the broad monster category. Actual targeting
overrides a normally passive/neutral classification. Bright daytime ordinary spiders
are conditional; cave spiders stay hostile. Piglins are treated as conditional rather
than assuming that every encounter is safe or always aggressive. The guide does not
model every provocation rule, gold-armor interaction, reputation, species variant or
modded behavior. Unknown entities are not declared safe food sources.

Distance, visibility, targeting, agitation and whether an entity is inside a checked
room affect threat priority. Close visible hostiles (within eight blocks) trigger
retreat even if client targeting information is absent. A targeting mob within sixteen
visible blocks or a hostile inside the room is urgent. A visible more distant hostile
can prompt cover first. Close unprovoked neutral animals get “leave it alone” advice.
Occluded outside hostiles do not repeatedly disturb a structurally protected player.
A sticky selection keeps similarly ranked mobs from alternating their names on every
sample; a materially stronger threat can still take over.

Cows (including cow subclasses), pigs, chickens and sheep are named food options.
Only visible, generally passive, non-targeting, non-agitated candidates within sixteen
blocks are suggested. A nearer option can be preferred. A detected potential threat
suppresses animal collection suggestions. The HUD names the animal, gives a facing-
relative direction and distance, identifies the meat and advises cooking it. These
are optional food-source descriptions, not automatic attacks or a complete hunting,
farming or cooking workflow. Raw chicken remains excluded from usable food reserves.

When animal-food collection or dangerous environmental preparation is relevant and
the player only has a pickaxe, the guide explains that it is a mining tool. A usable
sword or axe counts as a weapon. A missing weapon requests a stone sword through the
existing Minecraft recipe solver, including actual materials/table prerequisites.
A worn existing sword requests a spare rather than completing on the old one.
While the player is in checked cover at night, temporary recipes do not send them
out to collect ingredients or reach an outside table. Missing ingredients are
explained; crafting with materials already carried remains available. Low food
reserves alone do not send a protected player back into the night. Critical hunger
and damage still take priority. This does not lock the player's controls or stop
them choosing a different goal/approach.
Immediate escape/cover advice takes priority over crafting. The guide never orders
an unarmed player to fight a nearby hostile mob.

## What shelter support genuinely checks

The shelter verifier is deliberately limited to small, flat, two-block-high rooms.
It checks up to 25 connected standing cells within a local four-block radius:

- Known solid, non-falling floor and roof, with clear feet/head space.
- Boundaries at both body heights; windows and openings count as gaps.
- No observed fluid/hazard or falling terrain in the inspected volume/boundaries.
- Block light of at least eight throughout the standing cells, rather than assuming
  that a dark enclosed cave cannot spawn monsters.
- Closed iron doors, or closed wooden doors on eligible difficulty. Wooden doors
  do not certify protection on Hard difficulty or with a nearby loaded Vindicator.
  Door checks are conservative and are not a complete raid/breach simulator.
- Nearby observed mobs. A hostile inside the room overrides structural protection.

This is **checked structural cover**, not a guarantee of survival. Teleporting mobs,
explosions outside the checked volume, raids not locally observed, fire spread,
modded hazards and every possible mob breach are not fully modeled. “Underground,”
“roofed,” and “surrounded by blocks” alone never verify protection. Taller/larger
rooms, many partial collision shapes, trapdoor constructions and complex entrances
can remain unverified even when a human would reasonably use them.

## Limited construction and finding nearby cover

The supported construction case is sealing **one simple opening of at most two
blocks** in an otherwise checkable room:

1. Check actual inventory for ordinary usable solid materials. Falling blocks and
   arbitrary/modded building items are not treated as interchangeable safe material.
2. If needed, ask the player to move the material to the hotbar, then hold it.
3. Ask for one exact supported block placement outside the player's feet/head
   space, within reach and sight. The lower opening is filled before an upper one.
4. Re-read world blocks. The next placement appears only after the earlier gap is
   actually filled. No progress is inferred merely from inventory loss.
5. Verify enclosure and lighting again. A closed but dark room requests a torch
   through recipe knowledge if none is carried, or asks the player to place light.
   It remains unverified until actual block-light readings satisfy the check.

The guide does not suggest excavation, replace support blocks, fill the player's
body space, build beside an unchecked drop, or build during a nearby threat. Existing
full blocks must support the proposed placement. It does not place anything itself.
Sealing an entrance is temporary refuge; automatic doorway reopening/exit coaching
is not implemented. The player controls when and how to reopen it.

Nearby cover search checks at most six candidates within five horizontal blocks at
the player's feet elevation. It reuses the existing **walking-only** navigation planner,
without excavation. It does not assume that a closed door can be walked through.
The covered destination still needs its entrance/light/protection checks after entry.
Cached walking prefixes and placements are rechecked against current terrain.

If no checked route or simple sealing plan exists, the HUD explicitly says:
“Look for an enclosed building; I cannot safely guide a new shelter at this spot.”
General freestanding construction and long-range shelter location remain unsupported.
The newer [emergency shelter builder](emergency-shelter-builder.md) supports two compact
designs, conditional bed advice and a checked short exit, with explicit limits. No arbitrary
structure is labeled safe to hide these limitations.

Existing route/destination markers are reused for a checked walking prefix or the
next placement. Air placement uses the existing destination marker, not the mining
outline. Those visuals and coordinate instructions still need live accessibility
review. The previously documented route-visibility problems are not fixed by this
work; see [navigation-backlog.md](navigation-backlog.md).

## Persistence, preserved code and verification

The existing world/player goal format remains `accessibility-goals-v1`. The optional
survival need can now be `SHELTER`; the saved main goal, unfinished step, food latch,
pause flags and preferences remain intact. Old saves still load. World geometry and
threats are re-observed rather than saved as permanent proof that a shelter is safe.

GameState, AgentGoalManager, recipe data extraction, navigation implementation,
Q-learning, checkpoints and research/world/options files are preserved. New code
contains no executor actions. No Minecraft session is launched by regression tests.

Run `./gradlew compileClientJava clientTest check`.

Live verification needed:

1. Dusk/night/dawn, sleep and dimension changes; no night-only advice in Peaceful.
2. A zombie/skeleton/creeper versus a cow, unprovoked wolf/Enderman/piglin and daytime
   spider; visible versus occluded threats; escape before weapon crafting.
3. Names, left/right directions, food/cooking descriptions and a player carrying
   only a wooden pickaxe. Existing sword/axe and food supplies should skip needless work.
4. An open entrance, a sealed lit room, a dark cave, an unsafe floor/roof, closed/open
   doors and wooden doors on Hard. A hostile inside must invalidate protection.
5. Seal one simple opening, one block at a time; remove a placed block and confirm
   guidance re-evaluates. Check reach, hotbar/held material, lighting, marker position
   and limited nearby walking directions.
6. Pause/resume, dismissal, confirmed cancellation and save/rejoin. Confirm the exact
   unfinished step resumes only after safety has settled and no goal is replaced.
7. Narrator OFF/CHAT/SYSTEM/ALL, voice throttling, GUI scale and task-menu narration.

Client mob targets/anger may not be synchronized; observations are local and bounded.
Unseen projectiles, long-range attackers, PvP, all weather risks and mob-specific
combat tactics are not predicted. A lack of warning is not proof that an area is safe.
