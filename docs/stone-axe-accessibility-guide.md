# Adaptive stone-axe accessibility guide

The normal HUD now presents one instruction from the recipe-based guide. It is
available immediately in a world; J returns to it after observation or an
experimental mode. No recorded demonstration is required. The player performs
all movement, mining, crafting, placement, and interaction.

MinecraftRecipePlanner snapshots the loaded Minecraft crafting recipes, ingredient
alternatives, and batch output counts. RecipeGuidance determines the next unmet
dependency against current inventory and crafting inputs. Game mechanics supplement
recipes with appropriate mining tools and resource-source blocks. There is no
manually ordered stone-axe playthrough in the active HUD.

The older task graph and recorded sequences remain in the project, but do not
supply parallel instructions or material checklists on this HUD. The Q-learning
implementation, research controls, saved checkpoints, and experiment data remain
preserved and separate. R/J observation controls still work; F7/F8/F9 remain
explicit experimental research controls rather than accessibility features.

## What the player sees

The heading is "Goal: Make a stone axe", followed by one wrapped instruction.
Depending on inventory, open screens, and nearby blocks, it might say:

- "Go to the highlighted tree."
- "Break 1 log from the highlighted tree."
- "Walk over the dropped log to pick it up."
- "Open your inventory to make oak planks."
- "Make 4 oak planks."
- "Move your crafting table to your hotbar."
- "Hold your crafting table."
- "Place your crafting table on the ground."
- "Open the highlighted crafting table."
- "Hold your pickaxe to mine stone."
- "Mine 1 cobblestone with your pickaxe."
- "Take the stone axe from the result box."
- "Your stone axe is ready."

These are examples, not an enforced sequence. Owned materials, suitable tools,
different valid wood/stone ingredients, and nearby tables change or skip actions.
Existing ingredients in the crafting grid still count. Useful results are taken
from the output slot before moving on. The solver reserves shared ingredients
within a dependency calculation rather than counting the same planks twice.

A target outline, particle trail, and non-imperative distance/direction detail
support the current instruction. None of these features sends player input.
Instruction updates and environmental checks run on the client tick, at most
four times per second; resource scans are normally limited to once per second.
Drawing the HUD does not rebuild the plan or search the world each frame.

If a screen must be closed first, only that action is shown. Missing visible or
reachable resources, blocked crafting tables, insufficient placement space, and
unsupported recipes produce explanations instead of impossible crafting prompts.

## Scope and limitations

Full recipe knowledge currently uses the integrated local server. Remote-server
guidance explains that full recipes are unavailable; it does not invent a recipe.
Recipes are cached for the world and refreshed by J or re-entering the world;
press J after reloading data packs. Guidance is English text for this milestone.
Recipe names are derived from identifiers. Voice, personalization, configurable
reading levels, and external AI services are not included.

Target search is bounded and uses the conservative accessibility navigation model
with elevation, hazard and limited player-excavation checks. See
[resource-navigation.md](resource-navigation.md) for its safety model, markers, and limits.
It is not a guarantee about future world changes. A local scan may miss a resource outside its
range, and placement checks are conservative rather than proof the server will
accept placement. Dropped-item pickup and screen/result synchronization still
need live testing. Unsupported acquisition mechanics beyond the stone-axe scope
produce explanations.

## Verification

Run `./gradlew compileClientJava clientTest check`. Focused headless recipe fixtures
cover owned materials and higher-tier tools, tool prerequisites, partial and mixed
ingredients, batch quantities, shared-material reservation, alternate wood types,
crafting input/output slots, table acquisition/placement/interaction, blocked
states, changing inventory, completion, and cycle/missing-knowledge handling.
They exercise the production solver but do not validate rendering or real-world
screen/network timing.

Live checks still needed:

1. Start with no materials, then with logs/planks already owned: check that the
   single instruction advances after taking each crafting result.
2. Start with stone/sticks and with an iron pickaxe: confirm unnecessary wood and
   tool steps disappear, and pickaxe selection/hotbar instructions are accurate.
3. Reuse a placed table, carry a table outside the hotbar, place it, and open it.
   Confirm the guide does not ask for another table after placement.
4. Test mixed accepted ingredients, a crafting grid containing ingredients, a
   useful output waiting to be taken, and inventory loss during the goal.
5. Test missing resources, buried/blocked targets, blocked tables, and no open
   placement space. Check the explanation, outline, particle trail, and direction.
6. Craft and take the stone axe: confirm the completion message replaces all
   resource prompts and clears the old target.

No Minecraft world is launched or modified by the headless checks.
