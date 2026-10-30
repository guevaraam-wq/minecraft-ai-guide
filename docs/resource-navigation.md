# Resource navigation and limited player excavation

Current live-test failures are tracked in [navigation-backlog.md](navigation-backlog.md).
The task-menu milestone does not resolve them; the implementation notes below are
not a claim of reliable live rendering or route retention.

Navigation remains read-only: the player walks, jumps, mines, and picks up items.
No progression menu, automatic block breaking, block placement, or player input
was added. Recipe guidance and the existing research controls remain separate.

## What the player sees

- Dense **blue breadcrumbs** show the currently walkable part of the route.
- A **small stable white square/cross** marks the next footstep. It is geometric
  line rendering just above the next step's floor, with no white particles.
  The instruction says whether to walk, step up one block, or step down one block.
- A **cyan outlined resource cube and capped vertical pillar** mark the final
  destination. They are geometric waypoints, not particles. The pillar remains
  anchored to the resource's actual XYZ and extends above the viewing level for
  lower destinations. Its rendering does not use depth testing, so terrain cannot
  hide the waypoint. This indicates location only, never permission to walk through
  terrain. The HUD also gives destination bearing and blocks above/below.
- An **orange full-block outline** identifies the one terrain block to mine next.
  Yellow marks the final resource/interaction block once its approach is reached.
- Text labels describe the action and route status; color is not the only cue.

The HUD distinguishes `reachable now`, `clear terrain first`, and `currently
inaccessible`. There is only one action instruction. For example:

1. "Follow the blue path to the white marker."
2. "Mine the orange outlined block to open the path."
3. "Step down one block toward the white marker."
4. The normal recipe resource instruction resumes when the approach is reached.

These examples describe possible transitions, not a fixed scripted sequence.
Only the walking prefix before the first obstruction is drawn. The guide never
shows a walkable path through terrain that has not been removed. Blue breadcrumbs
can briefly fade after a change, but white/cyan markers have no drifting particle
history; clearing them takes effect on the next rendered frame.

## Route checks

The accessibility guide uses a new voxel-based route model through Pathfinder;
the existing research agent's `findPath` calls retain their previous behavior.
TargetFinder now samples resource candidates at different heights and searches
up to 12 blocks vertically, rather than considering only the closest horizontal
layer. A real three-dimensional interaction approach is required.

Walking checks full collision support, two-block body clearance, jumping headroom,
one-block ascents/descents, and unguarded side drops. It rejects fluids, damaging
blocks, falling-block footing, unknown cells, and unsupported partial collision
shapes. Water is deliberately not treated as a swimming route in this milestone.

A walkable route is preferred. If none is established, the planner may simulate
removing **at most six terrain blocks**. It only considers a small whitelist of
ordinary solid terrain (dirt/grass and common stone types), with an appropriate
held tool where needed. It does not mine containers, structures, gravity blocks,
or arbitrary mod blocks. The destination resource is not excavated merely to
create a passage.

Each proposed cut must be reachable and visible from a checked staging position
in the simulated sequence. The guide clears head space before lower blocks,
checks known stable landing support, and protects the current/earlier route
footing. It refuses excavation in the player's downward column, unknown
neighbors, fluid/hazard exposure, and falling material directly above a cut.
The final resource is also approached from a position that does not require
mining the block supporting the player. This allows a short checked hillside
cut or side staircase while refusing straight-down shafts.

The checks are conservative observations of loaded blocks, not a guarantee about
future game changes or every mod's physics. Unknown mod blocks are excluded.
Protected claims, entity movement, server mining permissions, and future hazards
cannot be inferred perfectly. The player remains in control, and unsuitable
routes are rejected rather than guessed.

## Recalculation and limits

The active guide now pins a valid resource destination and replays/revalidates the
remaining stages at each update (four times per second). Moving onto a planned
step trims completed walking stages rather than discarding the route or triggering
resource reselection. On reaching the staging step, the next action becomes the
first visible, safe clearance block. Cleared blocks are verified as passable and
removed from the remaining cut list; replacing a solid block is not completion.
No new search budget is needed to continue a still-valid plan.

Revalidation repeats the original headroom, support, hazard, reach, safe-removal,
protected-footing, and final interaction checks. A changed tool or genuinely unsafe
terrain can reject continuation. Completed prefixes are no longer treated as the
player's starting position, and unrelated changes far behind do not invalidate a
safe remaining route. The plan stays dormant while another recipe instruction asks
the player to close a screen or equip a tool. While airborne between levels, the
guide pauses actions and retains the destination instead of declaring it unreachable.

If continuation cannot be checked, the guide first searches for a new approach to
the same resource with a fresh budget, separate from nearby-table searches. It
only then considers another destination. There is no one-second replacement of
valid plans. A genuinely unresolved destination keeps its cyan waypoint and an
explanation, while unsafe walking/action markers are cleared.

Unsuccessful target searches retry approximately once per second. Searches are
bounded: 20-block horizontal resource scans, height-diverse candidate sampling,
24-block horizontal/16-block vertical route bounds, and node/time budgets. Runtime
search budgets reserve separate effort for walking and excavation so a failed
walking search does not consume all excavation attempts. The guide may conservatively
miss a longer valid route; it does not report that a resource is globally impossible.

If checks fail, the guide clears action markers, retains a known destination
waypoint, and asks the player to look for another resource location with a
route/tool/terrain explanation. No route is
executed automatically. Partial blocks, ice, special movement mechanics, larger
excavations, bridges, swimming, and world-wide exploration remain outside scope.

## Verification and live checks

`./gradlew compileClientJava clientTest check` includes synthetic-world regressions
for uphill/downhill approaches, blocked paths, hillside excavation, one-block
clearance ordering, removal verification, current-footing protection, lava,
gravity blocks, unknown terrain, deep drops, excessive tunneling, search budgets,
and invalidation after player/terrain/destination changes. The tests exercise the
same planner and instruction selector used by the guide. They do not launch
Minecraft or alter saved worlds/checkpoints.

## Findings from the first navigation live test

The white dust rings accumulated animated particles, causing the observed smoke.
The cyan particle pillar was short and depth-occluded by terrain for underground
resources. Both have been replaced with the geometry described above.

The route was tied to its original starting cell, so moving one block forced a
fresh search. The active flow also periodically reselected candidate resources,
and resource searches shared a bounded world view with table searches. Tests now
reproduce the key failure: budget-exhausted fresh search at an excavation staging
point, followed by successful continuation of the already valid original plan.
A complete synthetic hillside descent verifies walking/mining/walking transitions
without any new search. These establish code-level causes and fixes; exact live
terrain, renderer behavior, and frame timing still require verification.

Live checks still needed:

- Repeat the mountain/stone scenario: verify the three-dimensional approach and
  any orange clearance blocks, including the order of cuts and retained footing.
- Verify the white square/cross is stable, not smoke; cyan remains visible with
  particles reduced, behind terrain, and below the player; and each marker remains
  on its intended block after descending or mining. Check shader/resource packs,
  lighting, through-terrain waypoint rendering, and orange/yellow action outlines.
- Move off-route or place/remove a route block; verify the next instruction and
  markers refresh rather than continuing along a stale path.
- Test lava/water boundaries, gravel/sand overhead, cliff edges, slabs/stairs,
  insufficient tools, and resources below the standing block. Refusals should
  produce an explanation and an alternative-location prompt.
- Test long/complex local terrain for frame time and false refusals. The node/time
  budgets intentionally favor conservative refusal over an unbounded client stall.
