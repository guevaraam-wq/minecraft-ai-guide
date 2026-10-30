package minecraft_ai_guide.client.goals;

import minecraft_ai_guide.client.guide.RecipeGuidance;
import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.util.*;
import static minecraft_ai_guide.client.goals.ProgressionTasks.*;

/** Pure fixtures test evidence and persistence; they are not advancement grants or gameplay actions. */
public final class TaskProgressionTests {
    private static int checks;
    private static class Facts {
        Map<String,Integer> inventory=new HashMap<>(Map.of("minecraft:iron_pickaxe",1,"minecraft:iron_sword",1,"minecraft:shield",1));String dimension="minecraft:overworld";
        float health=20;int hunger=20,food=12,armor=15,blocks=32;
        boolean table=true,netherPortal,endPortal,ring,authoritative=true,stronghold,fortress,knownCrystals,dragonAlive,worldKilled;
        int missing=-1,crystals=-1;Set<String> advancements=new HashSet<>();
        Facts item(String id,int n){inventory.put("minecraft:"+id,n);return this;}
        Snapshot snapshot(){return new Snapshot(inventory,dimension,health,hunger,food,armor,blocks,table,netherPortal,endPortal,
                ring,missing,authoritative,advancements,stronghold,fortress,knownCrystals,crystals,dragonAlive,worldKilled,"minecraft:plains");}
    }
    public static void run()throws Exception{
        recommendationsAndGoals();milestones();portalFrames();persistence();recipes();adaptiveControls();
        System.out.println("Task/progression regression checks passed: "+checks);
    }
    private static void recommendationsAndGoals(){
        Facts needs=new Facts();needs.health=6;needs.hunger=5;needs.food=0;
        var recommended=recommendations(needs.snapshot());
        check(recommended.getFirst().id().equals("food"),"With no food, gather food before trying to eat");
        check(recommended.stream().anyMatch(t->t.id().equals("food")),"Food shortages remain visible");
        check(recommendationReason(task("food"),needs.snapshot()).contains("0"),"Recommendation explains current evidence");
        check(TASKS.stream().map(Task::category).distinct().count()==6,"All six requested categories have choices");
        check(task("dragon")!=null,"Long-term dragon goal is always selectable, including a new world");
        Facts nether=new Facts();nether.dimension="minecraft:the_nether";
        check(recommendations(nether.snapshot()).getFirst().id().equals("blaze"),"Nether location changes useful recommendations");
        check(recommendationReason(task("bed"),nether.snapshot()).contains("explode"),"Bed explanation respects dimension hazards");
        check(advice(task("stronghold"),nether.snapshot(),0).instruction().contains("Overworld"),"Eyes are not recommended for locating strongholds in the wrong dimension");
        Facts end=new Facts();end.dimension="minecraft:the_end";end.dragonAlive=true;
        check(recommendations(end.snapshot()).getFirst().id().equals("crystals"),"Active End fight recommends a relevant world goal");
        Facts equipped=new Facts();equipped.item("diamond_axe",1).item("ender_eye",12);
        check(recommendations(equipped.snapshot()).stream().noneMatch(t->t.id().equals("stone_axe") || t.id().equals("blaze") || t.id().equals("pearls")),
                "Better tools and current downstream supplies prevent unnecessary recommendations");
        check(recommendations(equipped.snapshot(),3).stream().noneMatch(t->t.id().equals("biomes")),
                "Completed exploration history is not repeatedly recommended");
        GoalSelection selection=new GoalSelection();selection.select("dragon");
        check(selection.selected().equals("dragon"),"Recommendations do not force a different selected goal");
        selection.pause(true);selection.select("food");selection.select("dragon");
        check(selection.paused(),"Switching away does not discard a goal's paused state");
        selection.pause(false);check(!selection.paused(),"A goal resumes explicitly");
        check(!selection.reminders(),"Equipment reminders are optional and off by default");
        selection.reminders(true);check(selection.reminders(),"Reminders can be enabled without affecting the selected goal");
        check(selection.selected().equals("dragon"),"Reminder settings never gate progression");
    }
    private static void milestones(){
        Facts f=new Facts();f.authoritative=false;
        check(milestone(Milestone.DEFEAT_DRAGON,f.snapshot()).state()==State.UNKNOWN,"Unavailable advancement evidence is unknown, not completion");
        f.dimension="minecraft:the_nether";
        check(milestone(Milestone.ENTER_NETHER,f.snapshot()).state()==State.DONE,"Observed current dimension verifies a visit independently of item ownership");
        f.dimension="minecraft:overworld";f.authoritative=true;
        GoalSelection history=new GoalSelection();history.observe("BLAZE_SUPPLY","Previously carried rods");
        check(milestone(Milestone.BLAZE_SUPPLY,f.snapshot()).state()==State.NEEDED,"Historical item ownership does not satisfy current stock");
        f.item("blaze_rod",6);check(milestone(Milestone.BLAZE_SUPPLY,f.snapshot()).state()==State.DONE,"Current rods verify supplies");
        f.item("blaze_rod",0);check(milestone(Milestone.BLAZE_SUPPLY,f.snapshot()).state()==State.NEEDED,"Consumed or lost supplies are rechecked");
        f.item("ender_eye",12);
        check(milestone(Milestone.BLAZE_SUPPLY,f.snapshot()).state()==State.DONE && milestone(Milestone.PEARL_SUPPLY,f.snapshot()).state()==State.DONE,
                "Current downstream Eyes bypass unnecessary raw-resource gathering");
        check(dragonAdvice(f.snapshot()).instruction().contains("Eye of Ender"),"Prepared Eyes lead toward a stronghold rather than regathering supplies");
        f.item("ender_eye",1);f.ring=true;f.missing=2;
        check(dragonAdvice(f.snapshot()).recipeAmount()==2,"Observed empty portal frames set the crafting target instead of always requesting twelve Eyes");
        check(advice(task("portal"),f.snapshot(),0).recipeAmount()==2,"Portal task also uses recipe guidance for the actual missing eyes");
        f.missing=0;
        check(milestone(Milestone.ACTIVATE_END_PORTAL,f.snapshot()).state()!=State.DONE,"Filled frames alone are not proof of portal activation");
        check(advice(task("nether_portal"),f.snapshot(),0).instruction().contains("not verified"),"A past portal visit cannot stand in for current portal access");
        f.endPortal=true;check(milestone(Milestone.ACTIVATE_END_PORTAL,f.snapshot()).state()==State.DONE,"Observed active entry portal verifies activation");
        check(dragonAdvice(f.snapshot()).instruction().contains("Enter"),"An active portal allows continuing without forcing older milestones");
        check(milestone(Milestone.ENTER_END,f.snapshot()).state()!=State.DONE,"An active portal does not imply the player has entered the End");
        check(advice(task("enter_end"),f.snapshot(),0).instruction().contains("Enter"),"End entry remains a separate chosen action");
        f.endPortal=false;f.dimension="minecraft:the_end";
        check(milestone(Milestone.ACTIVATE_END_PORTAL,f.snapshot()).state()==State.UNKNOWN,"End exit-portal context is not mistaken for verified Overworld activation");
        f.crystals=0;f.knownCrystals=false;f.dragonAlive=true;
        check(milestone(Milestone.CLEAR_CRYSTALS,f.snapshot()).state()==State.UNKNOWN,"Unloaded or unverified zero crystal count is not completion");
        f.knownCrystals=true;check(milestone(Milestone.CLEAR_CRYSTALS,f.snapshot()).state()==State.DONE,"Known empty active arena verifies the world objective");
        f.crystals=3;check(milestone(Milestone.CLEAR_CRYSTALS,f.snapshot()).state()==State.NEEDED,"New crystals revoke current readiness, not blindly retained completion");
        f.dragonAlive=false;f.worldKilled=true;
        check(milestone(Milestone.DEFEAT_DRAGON,f.snapshot()).state()!=State.DONE,"World victory is not falsely attributed to this player");
        check(dragonAdvice(f.snapshot()).instruction().contains("not verified"),"Prior world victory is explained with uncertainty");
        f.advancements.add("minecraft:end/kill_dragon");
        check(milestone(Milestone.DEFEAT_DRAGON,f.snapshot()).state()==State.DONE,"Personal dragon advancement verifies the long-term goal");
        check(dragonAdvice(f.snapshot()).instruction().contains("advancement"),"Dragon completion reports its evidence");
        f.dimension="minecraft:overworld";f.advancements.add("minecraft:story/follow_ender_eye");f.stronghold=false;
        check(milestone(Milestone.FIND_STRONGHOLD,f.snapshot()).state()==State.DONE,"Advancement can verify a historical stronghold visit");
        check(!advice(task("stronghold"),f.snapshot(),0).instruction().contains("verified stronghold"),"Historical visit does not pretend the player is currently there");
    }
    private static void portalFrames(){
        Map<BlockPos,PortalFrames.Frame> frames=new HashMap<>();
        for(int i=-1;i<=1;i++){
            frames.put(new BlockPos(i,60,-2),new PortalFrames.Frame("south",i!=0));
            frames.put(new BlockPos(i,60,2),new PortalFrames.Frame("north",i!=0));
            frames.put(new BlockPos(-2,60,i),new PortalFrames.Frame("east",true));
            frames.put(new BlockPos(2,60,i),new PortalFrames.Frame("west",true));
        }
        check(PortalFrames.find(frames).known() && PortalFrames.find(frames).missingEyes()==2,"Correct oriented ring counts only the actually empty frames");
        var removed=frames.remove(new BlockPos(2,60,0));
        check(!PortalFrames.find(frames).known(),"Partial or unloaded frame ring is unknown");
        frames.put(new BlockPos(2,60,0),new PortalFrames.Frame("east",true));
        check(!PortalFrames.find(frames).known(),"Wrong frame orientation cannot be treated as a complete ring");
    }
    private static void persistence()throws Exception{
        Path dir=Files.createTempDirectory("accessibility-goals-test-");
        try{
            Path file=dir.resolve("world-a/player-a.properties");
            GoalSelection state=GoalSelection.load(file);check(state.selected().equals("stone_axe"),"New player preserves the working stone-axe default");
            state.select("dragon");state.pause(true);state.reminders(true);state.observeBiome("minecraft:plains");
            state.observeBiome("minecraft:desert");state.observe("ENTER_NETHER","dimension observed");state.save(file);
            GoalSelection loaded=GoalSelection.load(file);
            check(loaded.selected().equals("dragon") && loaded.paused() && loaded.reminders(),"Selection, pause and preferences survive restart");
            check(loaded.biomeCount()==2 && loaded.observations().containsKey("ENTER_NETHER"),"Verified observation history persists independently of active goal");
            loaded.select("stone_axe");loaded.save(file);loaded=GoalSelection.load(file);loaded.select("dragon");
            check(loaded.paused(),"Switching goals does not lose per-goal pause state on disk");
            check(!loaded.observeBiome("minecraft:plains") && loaded.biomeCount()==2,"Repeated biome observations do not inflate exploration progress");
            check(GoalSelection.load(dir.resolve("world-b/player-a.properties")).biomeCount()==0,"Worlds cannot inherit another world's progress");
            check(GoalSelection.load(dir.resolve("world-a/player-b.properties")).selected().equals("stone_axe"),"Players cannot inherit another player's selected goal");
            Files.writeString(file,"format=unknown\nselected=dragon\n");String before=Files.readString(file);boolean refused=false;
            try{GoalSelection.load(file);}catch(java.io.IOException expected){refused=true;}
            check(refused && Files.readString(file).equals(before),"Unsupported save schema is not overwritten");
            Files.writeString(file,"format=accessibility-goals-v1\nselected=\\uZZZZ\n");
            String malformed=Files.readString(file);boolean rejected=false;
            try{GoalSelection.load(file);}catch(java.io.IOException expected){rejected=true;}
            check(rejected && Files.readString(file).equals(malformed),"Malformed property encoding cannot crash the controller or overwrite saved data");
            boolean invalid=false;try{state.select("unknown-goal");}catch(IllegalArgumentException expected){invalid=true;}
            check(invalid,"Unknown goal IDs cannot corrupt selection");
        }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    private static void recipes(){
        var plank=new RecipeGuidance.Recipe("minecraft:oak_planks",4,List.of(new RecipeGuidance.Need("minecraft:oak_log",1)),false);
        var knowledge=new RecipeGuidance.Knowledge(Map.of("minecraft:oak_planks",List.of(plank)),
                Map.of("minecraft:oak_log",new RecipeGuidance.Resource(Set.of("minecraft:oak_log"),Set.of())));
        var guide=new RecipeGuidance(knowledge,"minecraft:oak_planks",8);
        var context=new RecipeGuidance.Context(Map.of("minecraft:oak_log",2),Map.of(),RecipeGuidance.Table.NONE,true,false,true,Set.of(),"","");
        var step=guide.next(context);
        check(step.action()==RecipeGuidance.Action.CRAFT && step.amount()==8,"Selected crafting goal uses recipe batch quantities rather than a fixed sequence");
        var ready=new RecipeGuidance.Context(Map.of("minecraft:oak_planks",8),Map.of(),RecipeGuidance.Table.NONE,false,false,true,Set.of(),"","");
        check(guide.next(ready).action()==RecipeGuidance.Action.COMPLETE,"Non-stone-axe crafting goals complete against current inventory");
        var eye=new RecipeGuidance.Recipe("minecraft:ender_eye",1,List.of(new RecipeGuidance.Need("minecraft:ender_pearl",1)),false);
        var missing=new RecipeGuidance(new RecipeGuidance.Knowledge(Map.of("minecraft:ender_eye",List.of(eye)),Map.of()),"minecraft:ender_eye",12);
        check(missing.next(ready).item().equals("minecraft:ender_pearl"),"Unsupported acquisition exposes the recipe's missing ingredient for a world goal");
    }
    private static void adaptiveControls()throws Exception{
        GoalSelection goal=new GoalSelection();goal.start("dragon");
        Facts f=new Facts();f.inventory.clear();f.food=0;f.armor=0;f.blocks=0;f.table=false;
        var step=AdaptiveRecommendations.next(goal,f.snapshot());
        check(step.taskId().equals("food"),"A new dragon player starts with food rather than pearls");
        check(step.why().contains("food"),"Preparation has a short reason");
        check(goal.selected().equals("dragon"),"Immediate food task never replaces the long-term goal");
        check(recommendations(f.snapshot()).stream().noneMatch(t->Set.of("pearls","blaze","enter_end").contains(t.id())),"Unprepared suggestions omit dangerous travel and supply tasks");
        f.food=8;f.hunger=10;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("eat"),"Carried food and hunger trigger eating");
        f.hunger=20;f.health=8;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).advice().instruction().contains("Rest"),"Full hunger and low health trigger recovery rather than impossible eating");
        f.health=20;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("pickaxe"),"Missing tools are prepared before travel");
        f.item("wooden_pickaxe",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("pickaxe"),"A wooden pickaxe does not satisfy ore-trip readiness");
        f.item("iron_pickaxe",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("weapon"),"A better pickaxe skips unnecessary stone pickaxe crafting");
        f.item("iron_sword",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("armor"),"Missing equipped armor is detected");
        f.item("iron_chestplate",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).advice().instruction().contains("Put on"),"Carried armor is equipped instead of recrafted forever");
        f.armor=8;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).advice().recipeItem().equals("minecraft:iron_leggings"),"One equipped chestplate does not cause a chestplate crafting loop");
        f.item("iron_leggings",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).advice().instruction().contains("Put on"),"The next carried armor piece is equipped");
        f.armor=15;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("shield"),"Shield readiness is checked");
        f.item("shield",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("blocks"),"Supplies are prepared before the Nether");
        f.blocks=32;f.netherPortal=true;
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("nether"),"Prepared players can use a verified Nether route");
        f.dimension="minecraft:the_nether";
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("blaze"),"Dimension changes recalculate the immediate step");
        goal.preferStep("bed");
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("bed") && goal.selected().equals("dragon"),"A different chosen approach preserves the long-term goal");
        f.item("red_bed",1);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().equals("blaze"),"Completed optional preparation falls through without an endless loop");
        goal.pause(true);
        check(AdaptiveRecommendations.next(goal,f.snapshot()).advice().recipeItem().isEmpty(),"Paused goals emit no active recipe instructions");
        check(AdaptiveRecommendations.next(goal,f.snapshot()).title().equals("Goal paused"),"Paused state is explicit");
        goal.start("dragon");
        check(!goal.paused() && goal.preferredStep().isEmpty(),"Start explicitly resumes a previously paused goal and clears old step choices");
        goal.observe("ENTER_NETHER","Verified visit");goal.observeBiome("minecraft:plains");goal.reminders(true);
        check(!goal.confirmCancel(true) && goal.active(),"Cancellation is impossible without a request");
        goal.requestCancel();check(goal.active(),"Opening the cancel confirmation does not cancel");
        check(!goal.confirmCancel(false) && goal.active(),"Keep my goal preserves the goal");
        goal.requestCancel();check(goal.confirmCancel(true) && !goal.active(),"Explicit confirmation clears the active goal");
        check(goal.observations().size()==1 && goal.biomeCount()==1 && goal.reminders(),"Cancellation preserves verified history and preferences");
        check(AdaptiveRecommendations.next(goal,f.snapshot()).taskId().isEmpty(),"Canceled goals have no immediate task");
        Path dir=Files.createTempDirectory("adaptive-goals-");Path file=dir.resolve("goals.properties");
        try{
            goal.save(file);var saved=GoalSelection.load(file);
            check(!saved.active() && saved.observations().size()==1 && saved.reminders(),"Cancellation survives restart without deleting evidence");
            saved.start("dragon");saved.preferStep("blocks");saved.pause(true);saved.save(file);saved=GoalSelection.load(file);
            check(saved.active() && saved.paused() && saved.preferredStep().equals("blocks"),"Main goal, chosen immediate step and pause survive restart");
            Files.writeString(file,"format=accessibility-goals-v1\nselected=dragon\npaused.dragon=true\nreminders=true\nobserved.ENTER_END=Verified visit\n");
            saved=GoalSelection.load(file);
            check(saved.active() && saved.paused() && saved.reminders() && saved.observations().containsKey("ENTER_END"),"Original v1 saves migrate through defaults without losing pause or history");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        f=new Facts();f.inventory.clear();f.food=0;f.armor=0;
        check(AdaptiveRecommendations.display(Milestone.ENTER_NETHER,f.snapshot()).state()==AdaptiveRecommendations.DisplayState.NOT_READY,"Milestones label unmet readiness in plain terms");
        f.authoritative=false;
        check(AdaptiveRecommendations.display(Milestone.DEFEAT_DRAGON,f.snapshot()).state()==AdaptiveRecommendations.DisplayState.UNKNOWN,"Uncertain evidence is never displayed as completed");
        f.dimension="minecraft:the_nether";
        check(AdaptiveRecommendations.display(Milestone.ENTER_NETHER,f.snapshot()).state()==AdaptiveRecommendations.DisplayState.COMPLETED,"Observed dimension displays a completed visit");
        f=new Facts();f.netherPortal=true;
        check(AdaptiveRecommendations.display(Milestone.ENTER_NETHER,f.snapshot()).state()==AdaptiveRecommendations.DisplayState.NEXT,"A verified route and readiness expose an available milestone");
        var nav=new TaskMenuModel();
        check(nav.page()==TaskMenuModel.Page.HOME,"Menu opens on a predictable home screen");
        nav.open(TaskMenuModel.Page.CATEGORIES);nav.open(TaskMenuModel.Page.BROWSE);nav.open(TaskMenuModel.Page.DETAIL);nav.back();
        check(nav.page()==TaskMenuModel.Page.BROWSE,"Back returns from goal details to browsing");
        nav.home();nav.open(TaskMenuModel.Page.CONTROLS);nav.open(TaskMenuModel.Page.CANCEL);nav.back();
        check(nav.page()==TaskMenuModel.Page.CONTROLS,"Back from confirmation returns to controls");
        nav.home();nav.open(TaskMenuModel.Page.MILESTONES);nav.back();
        check(nav.page()==TaskMenuModel.Page.HOME,"Dedicated milestones are one screen away from home");
        check(TaskMenuModel.choicesPerPage(240)==1 && TaskMenuModel.choicesPerPage(400)==2,"Browsing limits cognitive load at small and large GUI sizes");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
