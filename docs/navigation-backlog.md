# Navigation issues deferred during the task-menu milestone

The following live-test issues remain unresolved. Headless route/marker checks do
not prove that in-game rendering or route retention works reliably.

1. **Visibility:** the blue route trail and white next-step marker are not visible
   reliably. Reproduce with current render/particle settings and inspect frame
   ordering, camera transforms, occlusion, contrast and marker placement.
2. **Stone route loss:** the guide sometimes loses its route to stone. Capture
   target identity, player feet/pose/elevation, retained route stage, world changes,
   interaction reach, and search-budget results during a live reproduction.
3. **Terrain access and alternatives:** suggest safe excavation when justified and
   investigate alternative traversal, including existing waterfalls. Waterfall
   traversal is not implemented. Existing conservative fluid/hazard refusal remains;
   no route should be permitted merely to hide a pathfinding failure.
4. **Useful resource groups:** when several stone blocks are accessible, identify
   a useful cluster/quantity for the selected task instead of an arbitrary distant
   block. Account for required drops, tools, footing and reachable approaches.

The task-menu implementation reuses the current navigation integration. It makes
no claim that these issues are fixed and does not weaken safety checks. Tackle
these as a separate live-tested navigation milestone after menu/progression review.
