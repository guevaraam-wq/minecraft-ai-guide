# One-log training and frozen-policy experiments

The first live test confirmed that the agent can move, approach a tree, mine, and
pick up a log. That demonstrates execution capability, not measured learning
improvement. The evaluation/CSV additions have headless regression coverage and
still need live testing.

## Explicit controls and safety

- **F7** starts one TRAINING episode: configured epsilon-greedy selection and Q updates.
- **F9** starts one EVALUATION episode: epsilon=0, no Q insertion/update, and no
  creation, replacement, or timestamp rewrite of the Q-table. Greedy ties can
  still be random, and the EXPLORE executor itself still chooses random turns.
- **F8** releases movement/mining controls and stops, before file persistence.
  Stop wins over start in the same tick. All controls are remappable in Controls.
- F9 has no default conflict in this project's existing Minecraft bindings. At
  activation the mod also checks registered key mappings for conflicts and
  refuses evaluation until the evaluation control is remapped.
- R/J observation and guidance remain player controlled. Selecting either stops
  the experiment. Starting an experiment while recording is refused.
- Every episode requires another explicit F7 or F9 activation. No automatic
  startup, restart, background training, teleportation, inventory deletion, or
  world reset occurs.
- Both modes require an active private local survival world. LAN/shared and
  remote servers are refused. Authorization binds to the exact integrated
  server and client level. Menus, focus loss, pauses, damage, death, level/world
  changes, and disconnects stop both modes. Limits remain 120 seconds and 100
  decisions, plus the executor's existing action/confirmation timeouts.

## Inventory baselines and success

Each activation records the current tagged-log inventory count. The goal is a
**net gain of at least one log above that baseline**. Existing inventory can be
kept: an episode starting with eight logs requires nine; the next starting with
nine requires ten. Losing and replacing old logs does not satisfy the goal.
The learning-state terminal bit is relative to this baseline. Existing saved Q
states remain compatible: zero-log episodes had a baseline of zero.

Episode success and action reward attribution are distinct:

- `success=true` means an actual snapshot in that episode showed log inventory
  above baseline. This also covers a pickup while exploring, or observed on stop.
- `credited_goal_reward=true` means a completed successful COLLECT_RESOURCE
  attempt confirmed both targeted acquisition and terminal log gain, earning +10.
- A pickup during EXPLORE can therefore be task success with reward -0.05 and no
  collection credit. Observing a gain outside any completed attempt records the
  task outcome without inventing a transition or reward.

The prior live-test TSV showed exactly this distinction: mining was marked failed,
then the log appeared during exploration. Historical records and the existing
Q-table are preserved; their incomplete counters are not reconstructed or migrated.
Client-side timing and unrelated pickups remain attribution limitations.

## State, actions, and learning

GameState counts logs using Minecraft's item tag. The compact policy state
encodes baseline-relative goal completion, reachable target availability,
distance bucket (near <=3, middle <=8, far >8), and mining reach/visibility.
The bounded target search reuses local pathfinding and tests up to 20 nearby
candidate logs within the existing 16-block horizontal/4-block vertical range.

| State | Applicable policy choices |
| --- | --- |
| No reachable target | Explore |
| Reachable target, not mineable yet | Explore or navigate |
| Target mineable | Explore, navigate closer, or collect |
| Baseline-relative goal complete | None |

Masks are physical preconditions, not a fixed task sequence. Experimental
navigation never automatically starts mining or fallback exploration. One
completed executor attempt corresponds to one assessed policy decision.
R/J demonstrations never feed this learner.

TRAINING performs `Q += alpha * (r + gamma * max_applicable_next_Q - Q)`;
terminal states have zero bootstrap. EVALUATION calculates the same reward but
never changes Q. Cancelled attempts are not learned. When post-action perception
is unavailable after losing world/focus access, the completed outcome can be
measured but is not used for a bootstrap update.

Rewards remain +10 for confirmed collection completing the episode, -0.05 for
successful movement/exploration (or collection replacing lost baseline logs),
and -1 for failure, timeout, or collection without targeted acquisition.
Cancellation has zero reward and no update. These values are not evidence of
learning progress. Macro-actions are single discount steps regardless of duration.

## Files and metrics

Configuration remains `<game directory>/config/minecraft-ai-guide/qlearning.properties`:

```properties
epsilon=0.25
learningRate=0.2
discountFactor=0.95
```

Settings reload on each activation. Evaluation ignores configured epsilon and
records effective epsilon=0. Alpha/gamma are recorded but do not update a frozen
policy. Invalid config or corrupt existing experiment data refuses activation.

Under `<world>/minecraft-ai-guide/`:

- `one-log-q.properties`: existing versioned learned table, saved only in TRAINING.
- `episodes.csv`: versioned header and one row per UUID, checkpointed from activation
  through decisions/attempts and final stop using atomic replacement where supported.
- `transitions.csv`: the same episode UUID, mode, decision number, states, action,
  epsilon-exploration flag, outcome, reward, Q before/after, and Q-updated flag.
- `experiment-summary.md`: regenerated descriptive TRAINING/EVALUATION comparison,
  plus separate evaluation cohorts for each canonical Q-content SHA-256 fingerprint.
- Existing `one-log-*.tsv` files are left untouched as historical data.

Episode CSV columns include result (`RUNNING`, `SUCCESS`, `FAILURE`, `ABORTED`),
success flag, selected decisions, completed/failed/cancelled actions, total reward,
monotonic elapsed milliseconds, epsilon-exploration decisions, EXPLORE action
count, actual Q-update count, starting/ending/new logs, effective epsilon,
alpha/gamma, starting/ending policy hashes, stop reason, and credited-goal flag.
Times use epoch milliseconds for episode start; elapsed time is monotonic.

A decision is counted when selected, even if interrupted before completion.
Failed actions are FAILURE/TIMEOUT or collection without targeted gain;
cancelled actions are separate. `exploration_decisions` counts the epsilon branch,
not random greedy ties or every EXPLORE action. Evaluation has zero epsilon
exploration and zero Q updates but may still select EXPLORE greedily.

Summaries include counts, success rates, mean decisions/failed actions/reward/time,
and exploration/update totals. Aborts remain visible and count as unsuccessful
in finished-episode success rates. RUNNING checkpoints are excluded and shown as
incomplete; a hard crash does not manufacture a final outcome. Persistence errors
stop the experiment and appear in the HUD. Records cannot be guaranteed if the
filesystem fails. CSV quoting preserves commas, quotes, and multiline reasons.

## A fair baseline-versus-trained experiment

1. Press F8 and back up the current Q-table before any experiment. Do not discard
   the policy from the successful live test.
2. Collect frozen **baseline evaluation** episodes. Before any training, an absent
   Q-table gives an untrained greedy policy with random ties. If a table already
   exists, move it to a clearly named backup while stopped, run F9 baseline trials,
   then restore it while stopped. Evaluation does not create a replacement table.
   Alternatively use a separate prepared local test world. No mod-driven world
   reset is provided or needed for repeated inventory-baseline episodes.
3. Press F7 explicitly for each training episode. Keep the logs; every activation
   takes a fresh inventory baseline. Back up the resulting trained checkpoint.
4. Press F9 explicitly for each trained-policy evaluation. Its Q-content hash must
   remain unchanged. Evaluation cohorts in the summary identify which checkpoint
   produced which results.
5. Compare **EVALUATION versus EVALUATION**, grouped by checkpoint, using matched
   starting distance/terrain/tool conditions and enough trials. The first transition
   records the starting distance bucket. Keep manual input out of trials except
   emergency stop. Report success rate, elapsed time, decisions, failures, and
   abort reasons, not just reward. Include incomplete runs and attribution gaps.

Training-versus-evaluation averages alone do not prove improvement: exploration
rates differ and training changes Q. Trees are depleted and positions/tools vary
across this evolving world; inventory baselines remove inventory clearing, not
those confounds. Use manually selected comparable starting conditions or separately
prepared worlds and report those differences. The summary never asserts causal
improvement or statistical significance. No automatic experimental resets occur.

## Verification and limits

Run `./gradlew compileClientJava clientTest check`. Regression tests cover frozen
selection/update/persistence behavior, per-episode baselines, counters, success
versus reward credit, CSV round trips, checkpoint summaries, and the existing
planning/attribution/Q-learning foundation. Synthetic fixtures are not gameplay
measurements. Live checks should verify F9, F8, safety stops, repeated episodes,
and the CSV rows against observed behavior.

The compact state omits terrain shape, orientation, danger, held tools, and resource
history. Existing pathfinding/exploration remain rudimentary. Mining range exceeds
pickup range, confirmation windows can miss late drops, and same-target pickups
can still have ambiguous causes. Both modes control the current player, not an
isolated simulation. This milestone measures those behaviors without claiming
that the policy has improved.
