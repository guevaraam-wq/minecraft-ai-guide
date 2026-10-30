package minecraft_ai_guide.client.guide;

import minecraft_ai_guide.agent.GameState;
import minecraft_ai_guide.agent.MinecraftRecipePlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Observes client state and supplies one instruction; never sends gameplay input. */
public final class StoneAxeGuide {
    public record View(String instruction, String targetDetail) {}
    private record Target(BlockPos position, NavigationPlanner.Route route, boolean inReach) {
        boolean reachable() { return route.status() != NavigationPlanner.Status.INACCESSIBLE; }
    }
    private String goalItem="minecraft:stone_axe";
    private int goalAmount=1;
    private RecipeGuidance.Step currentStep;
    public RecipeGuidance.Step currentStep(){return currentStep;}
    public void setGoal(String item,int amount){
        if(goalItem.equals(item) && goalAmount==amount)return;
        goalItem=item;goalAmount=amount;currentStep=null;lastUpdate=0;
        planner=knowledge==null?null:new RecipeGuidance(knowledge,item,amount);
        resourceTarget=null;resourceSources=Set.of();clearVisuals();
    }
    private View view = new View("Loading the stone axe guide...", "");
    private Object world;
    private RecipeGuidance.Knowledge knowledge;
    private RecipeGuidance planner;
    private long lastUpdate;
    private long lastResourceSearch;
    private Set<String> resourceSources = Set.of();
    private Target resourceTarget;
    private BlockPos routeOrigin;
    private String routeTool = "";

    public View view() { return view; }
    public void reset() {
        world = null; knowledge = null; planner = null; lastUpdate = 0; currentStep=null;
        resourceTarget = null; resourceSources = Set.of(); lastResourceSearch = 0;
        clearVisuals();
    }
    private static void clearVisuals() { PathGuide.clearPath(); TargetHighlighter.clearTarget(); }

    public void tick(Minecraft minecraft, boolean enabled) {
        if (!enabled || minecraft.player == null || minecraft.level == null) {
            clearVisuals();
            if (minecraft.level == null) reset();
            return;
        }
        if (world != minecraft.level) { reset(); world = minecraft.level; }
        long now = System.nanoTime();
        if (now - lastUpdate < 250_000_000L) return;
        lastUpdate = now;
        var inventory = GameState.observe(minecraft).getInventory();
        if (inventory.getOrDefault(goalItem,0) >= goalAmount) {
            currentStep=null;
            view = new View(RecipeGuidance.completedText(goalItem,goalAmount), ""); clearVisuals(); return;
        }
        if (knowledge == null) {
            knowledge = MinecraftRecipePlanner.guideKnowledge(minecraft);
            if (knowledge == null) {
                view = new View("Open a local world. Full recipe guidance is not available on this server yet.", "");
                clearVisuals(); return;
            }
            planner = new RecipeGuidance(knowledge,goalItem,goalAmount);
        }
        var player = minecraft.player;
        Map<String, Integer> grid = new HashMap<>();
        boolean tableOpen = player.containerMenu instanceof CraftingMenu;
        boolean inventoryOpen = minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        boolean otherScreen = minecraft.gui.screen() != null && !inventoryOpen
                && !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen);
        boolean craftingMenu = tableOpen || player.containerMenu instanceof InventoryMenu;
        if (craftingMenu) {
            for (int i = 1; i <= (tableOpen ? 9 : 4); i++) {
                var stack = player.containerMenu.getSlot(i).getItem();
                if (!stack.isEmpty()) grid.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        String result = craftingMenu && !player.containerMenu.getSlot(0).getItem().isEmpty()
                ? BuiltInRegistries.ITEM.getKey(player.containerMenu.getSlot(0).getItem().getItem()).toString() : "";
        Set<String> hotbar = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) hotbar.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        String held = BuiltInRegistries.ITEM.getKey(player.getInventory().getSelectedItem().getItem()).toString();
        var terrain = Pathfinder.guideWorld(minecraft);
        Target table = find(minecraft, Set.of("minecraft:crafting_table"), 6, 4, false, terrain);
        RecipeGuidance.Table tableState = tableOpen ? RecipeGuidance.Table.OPEN : table == null ? RecipeGuidance.Table.NONE
                : !table.reachable() ? RecipeGuidance.Table.BLOCKED : table.inReach() ? RecipeGuidance.Table.IN_REACH : RecipeGuidance.Table.NEARBY;
        var step = planner.next(new RecipeGuidance.Context(inventory, grid, tableState, inventoryOpen,
                otherScreen, canPlace(minecraft), hotbar, held, result));
        currentStep=step;
        String text = step.text();
        Target target = null;
        if (step.action() == RecipeGuidance.Action.OPEN_TABLE || step.action() == RecipeGuidance.Action.GO_TO_TABLE) target = table;
        if (step.action() == RecipeGuidance.Action.COLLECT) {
            // A broken block may have dropped an item that has not reached inventory yet.
            // Point to the actual drop rather than asking the player to break another block.
            var drops = minecraft.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(6), entity -> entity.isAlive()
                            && step.alternatives().contains(BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString()));
            drops.sort(Comparator.comparingDouble(player::distanceToSqr));
            for (var drop : drops) {
                BlockPos position = drop.blockPosition();
                var route = NavigationPlanner.plan(terrain, Pathfinder.cell(player.blockPosition()), Pathfinder.cell(position),
                        NavigationPlanner.Goal.WALK, false);
                if (route.status() == NavigationPlanner.Status.INACCESSIBLE) continue;
                String item = BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString();
                boolean logDrop = knowledge.resources().get(item).tools().isEmpty();
                navigate(minecraft, terrain, new Target(position, route, true), logDrop
                        ? "Walk over the dropped log to pick it up." : "Walk over the dropped stone to pick it up.", false);
                return;
            }
            Set<String> sources = new TreeSet<>();
            for (String item : step.alternatives()) {
                var spec = knowledge.resources().get(item);
                if (spec != null) sources.addAll(spec.sources());
            }
            // Keep a checked destination and trim/revalidate its remaining stages before
            // spending any new search budget. Movement is progress, not target invalidation.
            boolean eligible = resourceTarget != null && sources.contains(blockId(minecraft, resourceTarget.position()));
            if (!player.onGround()) {
                TargetHighlighter.clearTarget();
                TargetHighlighter.setNavigation(eligible ? resourceTarget.position() : null, null);
                view = new View("Wait until you are standing on solid ground.", "Route paused while you move between levels");
                return;
            }
            NavigationPlanner.Route continued = eligible && resourceTarget.reachable()
                    ? NavigationPlanner.resume(terrain, resourceTarget.route(), Pathfinder.cell(player.blockPosition()), NavigationPlanner.Goal.HARVEST)
                    : null;
            if (continued != null && continued.status() != NavigationPlanner.Status.INACCESSIBLE) {
                resourceTarget = new Target(resourceTarget.position(), continued,
                        inReach(minecraft, resourceTarget.position(), player.getEyePosition()));
            } else if (!resourceSources.equals(sources) || !eligible || !held.equals(routeTool)
                    || !player.blockPosition().equals(routeOrigin) || now - lastResourceSearch >= 1_000_000_000L) {
                // Give the pinned resource a fresh budget before any broad candidate scan.
                terrain = Pathfinder.guideWorld(minecraft);
                Target pinned = null;
                if (eligible) {
                    var route = NavigationPlanner.plan(terrain, Pathfinder.cell(player.blockPosition()),
                            Pathfinder.cell(resourceTarget.position()), NavigationPlanner.Goal.HARVEST, true);
                    pinned = new Target(resourceTarget.position(), route, inReach(minecraft, resourceTarget.position(), player.getEyePosition()));
                }
                if (pinned != null && pinned.reachable()) resourceTarget = pinned;
                else {
                    Target alternative = find(minecraft, sources, 20, 12, true, terrain);
                    resourceTarget = alternative != null && alternative.reachable() ? alternative
                            : pinned != null ? pinned : alternative;
                }
                resourceSources = Set.copyOf(sources);
                routeOrigin = player.blockPosition().immutable(); routeTool = held;
                lastResourceSearch = now;
            } else if (continued != null) {
                // Never show an old unsafe instruction while waiting for the next bounded search.
                resourceTarget = new Target(resourceTarget.position(), continued, false);
            }
            target = resourceTarget != null && resourceTarget.reachable() ? resourceTarget : null;
            boolean log = knowledge.resources().get(step.item()).tools().isEmpty();
            if (target == null) {
                text = log ? "Look for another tree. No checked route was found nearby."
                        : "Look for another stone location. No checked route was found with this tool and terrain.";
                if (resourceTarget != null) text = "Look for another resource location. " + resourceTarget.route().reason();
            } else {
                String targetBlock = blockId(minecraft, target.position());
                String drop = knowledge.resources().entrySet().stream().filter(e -> step.alternatives().contains(e.getKey())
                        && e.getValue().sources().contains(targetBlock))
                        .map(Map.Entry::getKey).sorted().findFirst().orElse(step.item());
                text = log ? "Break " + step.amount() + (step.amount() == 1 ? " log" : " logs") + " from the highlighted tree."
                        : "Mine " + step.amount() + " " + RecipeGuidance.name(drop) + " with your pickaxe.";
            }
        }
        // Keep the checked plan dormant while the player closes a screen or equips a tool.
        // It is revalidated before reuse; non-navigation instructions do not destroy progress.
        if (target != null && target.reachable()) {
            navigate(minecraft, terrain, target, text, true);
        } else {
            clearVisuals();
            String detail = "";
            if (step.action() == RecipeGuidance.Action.COLLECT) {
                detail = "Route: currently inaccessible";
                if (resourceTarget != null) {
                    TargetHighlighter.setNavigation(resourceTarget.position(), null);
                    detail += " | " + detail(minecraft, resourceTarget.position());
                }
            }
            view = new View(text, detail);
        }
    }

    private static String blockId(Minecraft mc, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
    }
    private static boolean inReach(Minecraft mc, BlockPos pos, Vec3 eye) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceTo(center) > mc.player.blockInteractionRange() - .5) return false;
        var hit = mc.level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
    private static Target find(Minecraft mc, Set<String> sources, int radius, int height, boolean excavation,
            NavigationPlanner.World world) {
        var found = TargetFinder.findGuideTarget(mc, sources, radius, height, excavation, world);
        return found == null ? null : new Target(found.position(), found.route(),
                found.route().status() == NavigationPlanner.Status.REACHABLE_NOW
                        && inReach(mc, found.position(), mc.player.getEyePosition()));
    }

    private void navigate(Minecraft mc, NavigationPlanner.World terrain, Target target, String arrivedText, boolean blockTarget) {
        var instruction = NavigationPlanner.next(terrain, target.route(), Pathfinder.cell(mc.player.blockPosition()));
        TargetHighlighter.clearTarget();
        BlockPos next = instruction.marker() == null ? null : Pathfinder.block(instruction.marker());
        String text = instruction.text();
        if (instruction.action() == NavigationPlanner.Action.BLOCKED) {
            clearVisuals(); view = new View(text, "Route: currently inaccessible"); return;
        }
        if (instruction.action() == NavigationPlanner.Action.MINE) {
            if (inReach(mc, next, mc.player.getEyePosition())) TargetHighlighter.setExcavation(next);
            else { text = "Move to the white marker to see the block."; next = mc.player.blockPosition(); }
        } else if (instruction.action() == NavigationPlanner.Action.ARRIVED) {
            if (blockTarget && !inReach(mc, target.position(), mc.player.getEyePosition())) {
                text = "Move to the white marker for a clear view."; next = mc.player.blockPosition();
            } else {
                text = arrivedText; next = null;
                if (blockTarget) TargetHighlighter.setTarget(target.position());
            }
        }
        boolean mine = instruction.action() == NavigationPlanner.Action.MINE && TargetHighlighter.getTarget() != null;
        TargetHighlighter.setNavigation(target.position(), mine ? null : next);
        PathGuide.showRoute(mc, instruction.walkPath().stream().map(Pathfinder::block).toList(), target.position(), mine ? null : next);
        String state = target.route().status() == NavigationPlanner.Status.AFTER_EXCAVATION
                ? "Route: clear terrain first" : "Route: reachable now";
        view = new View(text, state + " | " + detail(mc, target.position()));
    }
    private static boolean canPlace(Minecraft mc) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos pos = mc.player.blockPosition().offset(x, 0, z);
            if (mc.level.getBlockState(pos).isAir() && mc.level.getFluidState(pos).isEmpty()
                    && mc.level.getBlockState(pos.below()).isFaceSturdy(mc.level, pos.below(), Direction.UP)
                    && !mc.player.getBoundingBox().intersects(new AABB(pos))
                    && inReach(mc, pos.below(), mc.player.getEyePosition())) return true;
        }
        return false;
    }
    private static String detail(Minecraft mc, BlockPos pos) {
        double dx = pos.getX() + .5 - mc.player.getX(), dz = pos.getZ() + .5 - mc.player.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        angle = ((angle + 540) % 360) - 180;
        String direction = Math.abs(angle) < 25 ? "ahead" : Math.abs(angle) > 155 ? "behind you" : angle > 0 ? "right" : "left";
        int vertical = pos.getY() - mc.player.blockPosition().getY();
        String elevation = vertical == 0 ? "same level" : Math.abs(vertical) + " blocks " + (vertical < 0 ? "below" : "above");
        return "Cyan destination: " + (int) Math.sqrt(mc.player.blockPosition().distSqr(pos)) + " blocks, " + direction + ", " + elevation;
    }
}
