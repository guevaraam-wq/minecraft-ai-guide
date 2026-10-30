# Player-controlled emergency shelter builder

This feature generates instructions, never player actions. It does not place, mine,
jump, walk, equip, sleep, teleport, or modify the world. The main goal and unfinished
step stay saved while shelter is a temporary task.

## Controls and what appears

Open Tasks using the configured key (**K** in the live-test setup). When shelter is
relevant, the home screen offers **Emergency shelter options**:

- Choose safest available design.
- Try a compact box.
- Try a hillside recess.
- Keep my current plan.

A preference is not permission to use an unsafe/unfunded design. If the requested
strategy does not fit, the explanation identifies that a checked alternative is
being used. A completed refuge is retained rather than destroyed when preferences
change. Pause, Cancel, Back and Close retain their existing behavior. The K binding
and options file are not reset. Strategy preference is an optional property in the
existing per-world/player goal save; old files default to AUTO.

The HUD shows the unchanged main goal, a temporary shelter task, **Step n of total**,
one instruction and its reason. Moving material to the hotbar or holding the needed
item keeps the same construction step. Counts advance from real blocks or the player's
actual position, not inventory loss. A safe alternate material or enclosure is accepted.

## Supported designs

Coordinates below are relative to the checked standing cell `(0, 0, 0)`. The existing
solid floor is at Y=-1. Foot space is Y=0, head space Y=1, roof Y=2.

### Compact freestanding box: up to 10 blocks

The interior is **one block wide and two blocks high**. The standing cell remains
empty. Four cardinal walls each have two blocks (eight total), followed by one upper
support and a roof block:

| Part | Relative cells |
| --- | --- |
| North wall | `(0,0,-1)`, `(0,1,-1)` |
| East wall | `(1,0,0)`, `(1,1,0)` |
| South wall / planned exit | `(0,0,1)`, `(0,1,1)` |
| West wall | `(-1,0,0)`, `(-1,1,0)` |
| Roof support | `(0,2,-1)` |
| Roof | `(0,2,0)` |

**The roof support requires a manual jump-placement.** Without it the roof would
have no supporting face to click. The plan checks clear jump headroom and retains
the same target during the jump. The final roof waits until the player is grounded
so it does not intersect the player's head. Choose the hillside option if jumping
and placing is unsuitable; no alternative is promised where terrain does not fit.

Existing solid walls/roof and safely completed work reduce the material requirement.
The design does not build a foundation over water, a cliff, or unknown ground.
A conservative 5×5 area of stable floor and a loaded, hazard-free nearby volume
must be available. This can reject places a human builder could use.

### Hillside recess: usually 2 carried blocks, up to 5 steps

A checked vertical bank one horizontal block away must have a solid back, both
sides, roof and floor for a one-cell, two-high recess:

1. Mine its head-space block.
2. Mine its feet-space block.
3. Move into the marked recess.
4. Seal the lower entrance behind the player.
5. Seal the upper entrance.

Already-open cells are reused. Only ordinary dirt/grass/stone-type terrain is
eligible; no containers, falling blocks or arbitrary structures are excavated.
Stone requires an available correct, usable tool. Critically low health rejects
slow rock excavation; a short dirt recess can remain an option. There is no mining
straight down, removal of the player's floor, ceiling excavation, or long tunnel.
Adjacent fluids, unknown cells, falling terrain and unstable footing reject the plan.

Both carried entrance blocks must exist before starting. Potential drops from mining
are not credited as future inventory. The entrance cannot be sealed until the player
has actually entered, and actual collision checks must permit the placement.

## Materials and selection

Usable stock includes ordinary vanilla dirt, cobblestone/cobbled deepslate, stone,
andesite, granite, diorite, tuff, bricks, planks, logs, wood, stems and hyphae. Sand,
gravel, explosives, fluids and arbitrary modded building materials are excluded.
Counts are quantities across inventory stacks, not occupied slots. Existing held or
hotbar materials are preferred to unnecessary inventory changes. Block conversion
(such as budgeting future planks from logs) is not implemented: the budget uses blocks
already carried in their current form.

AUTO compares fully funded nearby candidates using carried material cost, excavation
and jump-placement effort. A short dirt recess can cost less than a new box. Explicit
strategy choices rank a feasible preferred design first. The builder stays at the
player's current position or one adjacent hillside cell; it does not send the player
on a long nighttime route.

When neither design is feasible, the instruction distinguishes hazardous footing,
a material deficit and excavation prerequisites. It reports actual required/carried
quantities. Low-health players are directed to carried-food recovery or remaining on
checked level ground, not a materials-gathering trip. A shortage cannot magically
produce shelter. Healthy-player fallback still does not certify a night gathering
route. Pause or choose another approach when the local checks cannot support a plan.

## Exact targets and actual-world verification

Placement uses a stable outlined **empty block cell**, plus a **white square on the
supporting face to click**. Mining uses an orange block outline; standing/entry uses
a low ground marker. Shape and text distinguish actions as well as color. These are
geometry outlines, not particles. Existing navigation/render infrastructure is reused;
the navigation algorithm is unchanged.

Read-only Minecraft `BlockPlaceContext`, world-border/interaction, block survival,
reach, line-of-sight and entity-collision checks validate a placement. They never call
placement/executor APIs. A blocked target pauses the instruction. Server permissions
or latency may still reject an attempt; only observed world changes advance it.

Each snapshot rechecks the plan and actual materials. Different safe solid materials
satisfy a wall. Removing a wall invalidates protection. An alternate safe enclosure can
finish the task early. New fluid, falling blocks, unknown terrain or blocked standing
space stops/replans rather than insisting on a now-unsafe block. The original plan
stays stable while its checks remain valid.

## Protection, waiting, sleep and exit

Completion requires real enclosure/floor/roof evidence and no observed immediate
hostile threat. It is not inferred from using blocks or owning materials.

A one-cell, two-high **occupied** dark refuge is treated as temporary structural
cover: there is no spare interior standing/spawning cell, and it is next to the
player. This does not label a dark cave or larger unlit room safe. It is not a
permanent lit base, and it should be rechecked if the player leaves or terrain changes.
The existing lighting checks remain for larger rooms. Local threat checks still
invalidate apparent enclosure.

While night remains, the task says to **wait inside until daylight**. Completing the
box does not resume outdoor resource gathering. Eating and recovery can temporarily
supersede waiting while keeping the player in cover. Nearby immediate threats always
supersede construction and waiting. A known close hostile behind an obstruction
also stops exposed construction advice; a protected player is not repeatedly
warned solely about an occluded outside mob. A funded short refuge may replace generic
“rest in shelter” text for an exposed low-health player; it never overrides active
lava/fire/low-air/recent-damage or immediate-mob warnings.

The compact designs have no bed space. Possessing a bed is not proof it can be used.
In a larger checked, lit room the guide can suggest an actually placed, complete,
unoccupied, reachable bed. It checks both bed halves, nearby monsters, Overworld
context and Minecraft 26.2's current `BED_RULE` (including explosion and sleep rules).
It never suggests a Nether/End bed or automatically sleeps.

After daylight, a completed builder refuge gets **three exit steps**: mine the upper
planned entrance block, mine the lower one, then walk into the checked doorway. Each
removal and the short walk route are verified. No floor/roof or arbitrary wall is
mined for this exit. A newly unsafe/blocked exit stops the instruction. Slow hand
breaking may be possible for the exit without recovering all drops; drops are never
assumed. Existing larger houses are not automatically remodeled to make an exit.

The previous unfinished goal resumes after exit and the normal safety settling period.
Deliberately leaving a completed shelter at night permits a two-minute continuation
grace; a new threat overrides it. This is player choice, not proof that outside is
safe. Changing the goal, Pause, or Cancel never triggers automated departure.

## Persistence and limitations

Only strategy preference uses a new optional save property (`shelterStrategy`), in
`accessibility-goals-v1`. Main goal, pause flags, observations, food latches and
preferences are retained. Plans are session-local; actual partial structures are
rechecked after rejoin. A single-cell refuge can recover a checked short exit from
current geometry rather than assuming construction history.

Limits include: vanilla ordinary solids; small, flat footprints; no bridges, multiroom
houses, long tunnels, staircases, ladders, door crafting/placement or arbitrary repairs.
The box includes a jump-placement that needs live usability testing. Collision/mob
conditions can pause a plan. Client mob knowledge is bounded and incomplete; explosions,
PvP, distant attackers, teleportation, fire spread, mods and every possible breach are
not fully predicted. Checked cover is not a guarantee of survival.

No Q-learning code, checkpoint, experiment data or player action executor is changed.
No world reset or Minecraft launch is part of verification.

## Verification

Run `./gradlew compileClientJava clientTest check`.

Regression fixtures cover both designs, exact coordinates/supports, quantities, world
verification, safe alternate construction, replanning, hazard/unknown/drop rejection,
threat priority, occupied-refuge completion, low health, night waiting/eating, daylight
exit, pause/cancel, strategy/menu navigation and old/new save compatibility.

Live-test these before relying on the builder:

1. Flat ground with ten dirt/plank/log blocks: hotbar/hold prompts, lower/upper walls,
   manual jump support, roof, empty-cell outline and white supporting-face square.
2. Dirt bank with two carried blocks; stone bank with a usable pickaxe; no-pickaxe
   and critically-low-health rock refusals. Verify entry precedes sealing.
3. Wrong-but-safe material/placement, missing blocks, world changes, an entity in a
   target cell, fluids/falling terrain/drops and a nearby hostile.
4. Completion holds at night; eating/recovery remains practical. Test daylight exit,
   deliberate departure, previous-goal restoration and rejoin inside the refuge.
5. K strategy choices, narration, large GUI scale, Pause/Resume/Cancel, and a bed in
   an already checked larger room. Confirm no autonomous player action occurs.
