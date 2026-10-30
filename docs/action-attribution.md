# Planning and action-attribution foundation

Run `./gradlew compileClientJava clientTest check` to compile and run the headless
regression checks. `check` includes `clientTest`; the checks do not launch a game.

Recipe planning traverses unmet dependencies for both crafted items and world
resources. An already-owned resource or completed goal skips its prerequisites.
The existing cobblestone acquisition rule uses Minecraft's stone-tool drop rules
to recognize suitable tools, including tools acquired after building the plan.
This is a prerequisite rule, not a fixed stone-axe task sequence.

`ActionAttemptTracker` records one executor attempt at a time. Each completed
experience contains its action, target item when available, immutable starting
and ending inventory/position/world snapshots, timestamps, terminal outcome, and
reward. Starting a second attempt without closing the first is rejected.
Completed records are drained once into the agent's experience memory.

Executor boundaries:

- Navigation ends at the path endpoint. Mining starts a new attempt there.
- A rejected navigation target closes a failed attempt before a retry starts.
- No reachable target closes a failed navigation attempt before exploration.
- Exploration succeeds only if the player moved at least half a block horizontally.
- Crafting and collection require the target item count to increase. Input
  acceptance and block disappearance alone do not establish success.
- Placement requires a crafting table to appear at the attempted placement
  position, which was empty when selected.
- Attempts have a 30-second limit. Inventory/placement confirmation has a
  1.5-second window; crafting also retains its existing 3-second result timeout.
- Stopping, disconnecting, or changing levels cancels an unfinished attempt.
  Cancellation is recorded with zero reward and excluded from recommendations.
- Failed/timed-out attempts cannot receive positive rewards from unrelated gains.
- WAIT produces no experience; unsupported action requests terminate as failures.

R/J observation and demonstration guidance remain available. Inventory deltas
alone cannot establish actual human action attempts, so inferred demonstration
steps are no longer added to experience memory. Future autonomous learning can
consume executor experiences without a demonstration. R/J remain player-controlled.
The subsequent [one-log Q-learning milestone](one-log-q-learning.md) adds a separate,
explicitly activated experimental F7/F8 training mode.

Remaining limits: this is client-side outcome verification, not authoritative
causal attribution. An unrelated pickup of the same target item could still look
like success, and delayed confirmations beyond the window can look like failure.
Cancellation after world loss uses the last valid snapshot. The legacy rewards remain
heuristics, experience memory is not persisted, and the existing executor resource coverage
and crafting-table interaction limitations remain. Live-world testing is still
needed for movement, mining drops, menu synchronization, and placement.
