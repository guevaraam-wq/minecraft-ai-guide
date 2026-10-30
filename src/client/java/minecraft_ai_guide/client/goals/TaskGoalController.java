package minecraft_ai_guide.client.goals;

import minecraft_ai_guide.client.guide.RecipeGuidance;
import minecraft_ai_guide.client.guide.StoneAxeGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Path;

/** Selected-task state and a single passive HUD instruction. */
public final class TaskGoalController {
    private final StoneAxeGuide recipes;
    private final ProgressionObserver observer=new ProgressionObserver();
    private final FoodCookingObserver cooking=new FoodCookingObserver();
    private FoodPreparation.Step cookingStep;
    private final SurvivalMonitor survival=new SurvivalMonitor();
    private final GuidanceNarration narration=new GuidanceNarration();
    private final SurvivalObserver survivalObserver=new SurvivalObserver();
    private long lastSurvival;
    private String survivalDimension="";
    private SurvivalMonitor.Observation latestSurvival;
    private GoalSelection selection=new GoalSelection();
    private ProgressionTasks.Snapshot snapshot=empty();
    private Object scope;
    private java.util.UUID player;
    private Path file;
    private boolean writable=true;
    private String notice="";
    private long lastObservation;
    private String instruction="Loading task guidance...",detail="",reminder="";

    public TaskGoalController(StoneAxeGuide recipes){this.recipes=recipes;}
    public GoalSelection selection(){return selection;}
    public ProgressionTasks.Snapshot snapshot(){return snapshot;}
    public String notice(){return notice;}
    public void menuNotice(String message){notice=message;}
    public String title(){if(!selection.active())return "No active goal";return "Main goal: "+ProgressionTasks.task(selection.selected()).title()+(selection.paused()?" (paused)":"");}
    public String instruction(){return instruction;}
    public String detail(){return detail;}
    public String reminder(){return reminder;}
    private AdaptiveRecommendations.Step baseStep(){return AdaptiveRecommendations.next(selection,snapshot,true);}
    public java.util.List<ProgressionTasks.Task> suggestions(){return AdaptiveRecommendations.suggestions(selection,snapshot,latestSurvival,survival.current());}
    public boolean shelterOptionsAvailable(){return selection.active() && latestSurvival!=null && (latestSurvival.environment().nightRisk()
            || latestSurvival.environment().shelter()!=null);}
    public void shelterStrategy(EmergencyShelterPlanner.Strategy strategy){
        selection.shelterStrategy(strategy);survivalObserver.shelterStrategy(strategy);survival.suspend();recipes.reset();lastSurvival=0;save();
    }
    public boolean temporary(){return selection.active() && !selection.paused() && survival.current()!=null;}
    public boolean canDismissSurvival(){return temporary() && !survival.current().critical();}
    public boolean dismissSurvival(){
        boolean dismissed=survival.dismiss(System.nanoTime()/1_000_000L);
        if(dismissed){selection.survivalNeed("");selection.foodResupplyPending(survival.foodLatched());recipes.reset();save();}return dismissed;
    }
    public java.util.List<EnvironmentalSurvival.FoodSource> nearbyFoodSources(){return survivalObserver.nearbyFood();}
    public void readCurrentInstruction(Minecraft mc){mc.getNarrator().saySystemNow(instruction);}
    public AdaptiveRecommendations.Step nextStep(){
        if(selection.active() && !selection.paused() && cookingStep!=null)return new AdaptiveRecommendations.Step("","Prepare food",cookingStep.why(),
                new ProgressionTasks.Advice(cookingStep.instruction(),cookingStep.recipeItem(),cookingStep.recipeAmount(),""));
        return temporary()?survival.current().step():baseStep();
    }
    public void choose(String id){selection.start(id);survival.reset();cookingStep=null;recipes.reset();lastObservation=0;lastSurvival=0;primeSurvival();save();}
    public void preferStep(String id){selection.preferStep(id);selection.survivalResumeStep("");selection.survivalNeed("");selection.foodResupplyPending(false);survival.reset();cookingStep=null;recipes.reset();primeSurvival();save();}
    public void cancel(boolean confirm){if(selection.confirmCancel(confirm)){survival.reset();cookingStep=null;recipes.reset();ShelterBuildMarker.clear();instruction="Open Tasks to choose a goal.";detail="";save();}}
    private void primeSurvival(){
        if(latestSurvival!=null){
            var base=baseStep();var interruption=survival.update(selection,snapshot,latestSurvival,System.nanoTime()/1_000_000L);
            if(interruption!=null){selection.survivalResumeStep(base.taskId());selection.survivalNeed(interruption.kind().name());}
            selection.foodResupplyPending(survival.foodLatched());
        }
    }
    public String why(){return nextStep().why();}
    public void pause(){selection.pause(!selection.paused());cookingStep=null;recipes.reset();ShelterBuildMarker.clear();instruction=selection.paused()?"Your goal is paused. Open Tasks to resume it.":"Checking your next step...";detail="";lastObservation=0;lastSurvival=0;save();}
    public void toggleReminders(){selection.reminders(!selection.reminders());save();}
    private void save(){
        if(file==null || !writable)return;
        try{selection.save(file);}catch(IOException e){notice="Goal save failed: "+e.getMessage();}
    }
    public void tick(Minecraft mc,boolean enabled){
        reminder="";ShelterBuildMarker.clear();
        if(mc.player==null || mc.level==null){recipes.tick(mc,false);scope=null;observer.reset();survival.reset();cooking.reset();cookingStep=null;narration.reset();survivalObserver.reset();latestSurvival=null;return;}
        Object context=mc.getSingleplayerServer()!=null?mc.getSingleplayerServer():mc.getConnection();
        if(scope!=context || !mc.player.getUUID().equals(player)){
            scope=context;player=mc.player.getUUID();observer.reset();survival.reset();cooking.reset();cookingStep=null;narration.reset();survivalObserver.reset();recipes.reset();lastObservation=0;lastSurvival=0;latestSurvival=null;survivalDimension="";
            selection=new GoalSelection();notice="";writable=true;
            file=mc.getSingleplayerServer()==null?null:mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .resolve("minecraft-ai-guide/accessibility-goals/"+player+".properties");
            if(file!=null)try{selection=GoalSelection.load(file);}catch(IOException e){writable=false;notice=e.getMessage();}
            else notice="Remote-world selection is session-only; advancement evidence is limited.";
        }
        // Keep the menu's evidence snapshot stable while the player reads it.
        if(!(mc.gui.screen() instanceof TaskMenuScreen) && System.nanoTime()-lastObservation>=1_000_000_000L){
            snapshot=observer.observe(mc);lastObservation=System.nanoTime();boolean changed=false;
            if(snapshot.dimension().equals("minecraft:overworld"))changed|=selection.observeBiome(snapshot.biome());
            for(var milestone:ProgressionTasks.Milestone.values()){
                var evidence=ProgressionTasks.milestone(milestone,snapshot);
                if(evidence.state()==ProgressionTasks.State.DONE)changed|=selection.observe(milestone.name(),
                        java.time.Instant.now()+" "+evidence.reason());
            }
            if(changed)save();
        }
        if(!enabled || !selection.active() || selection.paused()){
            survival.suspend();cookingStep=null;narration.reset();
            recipes.tick(mc,false);instruction=selection.paused()?"Your goal is paused. Open Tasks to resume it.":"Open Tasks to choose a goal.";detail="";return;
        }
        if(!selection.preferredStep().isEmpty() && AdaptiveRecommendations.complete(selection.preferredStep(),snapshot,selection.biomeCount())){
            selection.clearStep();save();
        }
        long survivalNow=System.nanoTime()/1_000_000L;
        if(!(mc.gui.screen() instanceof TaskMenuScreen) && survivalNow-lastSurvival>=250){
            lastSurvival=survivalNow;
            sampleSurvival(mc,survivalNow);
        }
        var advice=nextStep().advice();
        if(!advice.recipeItem().isEmpty()){
            recipes.setGoal(advice.recipeItem(),advice.recipeAmount());recipes.tick(mc,true);
            instruction=recipes.view().instruction();detail=recipes.view().targetDetail();
            var step=recipes.currentStep();
            if(step!=null && step.action()==RecipeGuidance.Action.BLOCKED){
                instruction=acquisition(step.item(),snapshot,step.text());
            }
            String cover=temporary() && latestSurvival!=null?EnvironmentalSurvival.coveredRecipeInstruction(latestSurvival.environment(),step):"";
            if(cookingStep!=null && cooking.facts()!=null && !cooking.facts().gatheringSafe() && step!=null
                    && (step.action()==RecipeGuidance.Action.COLLECT || step.action()==RecipeGuidance.Action.BLOCKED || step.action()==RecipeGuidance.Action.GO_TO_TABLE))
                cover="Keep cover; this preparation still needs "+RecipeGuidance.name(step.item())+". Gather it when conditions are safe.";
            if(!cover.isEmpty()){instruction=cover;detail="";recipes.tick(mc,false);}
        }else{
            recipes.tick(mc,false);instruction=advice.instruction();detail="";
        }
        if(cookingStep==null && temporary() && survival.current().kind()==SurvivalMonitor.Kind.SHELTER && latestSurvival!=null){
            var shelter=latestSurvival.environment().shelter();
            if(shelter!=null && nextStep().advice().recipeItem().isEmpty()){
                if(shelter.build()!=null){
                    var build=shelter.build();
                    if(build.total()>0)detail=(build.step()>0?"Step "+build.step()+" of "+build.total():"Plan paused: "+build.total()+" steps")
                            +" | "+(build.exit()?"Exit":build.strategy()==EmergencyShelterPlanner.Strategy.HILLSIDE?"Hillside shelter":"Compact box");
                    ShelterBuildMarker.show(build);
                }else if(shelter.placement()!=null)minecraft_ai_guide.client.guide.TargetHighlighter.setNavigation(
                        minecraft_ai_guide.client.guide.Pathfinder.block(shelter.placement()),null);
                else if(shelter.destination()!=null)minecraft_ai_guide.client.guide.PathGuide.showRoute(mc,
                        shelter.path().stream().map(minecraft_ai_guide.client.guide.Pathfinder::block).toList(),
                        minecraft_ai_guide.client.guide.Pathfinder.block(shelter.destination()),
                        shelter.path().size()>1?minecraft_ai_guide.client.guide.Pathfinder.block(shelter.path().get(1)):null);
            }
        }
        if(cookingStep!=null)cooking.visuals(mc,cookingStep);
        if(temporary() && (mc.gui.screen()==null || cookingStep!=null && mc.player.containerMenu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu)){
            String spoken="Temporary task: "+nextStep().title()+". "+instruction;
            if(narration.shouldSpeak(spoken,survival.current().critical(),survivalNow))mc.getNarrator().saySystemNow(spoken);
        }else if(!temporary())narration.reset();
        if(selection.reminders() && !temporary())for(var equipment:observer.equipment()){
            double distance=mc.player.blockPosition().distSqr(equipment.position());
            if(equipment.dimension().equals(snapshot.dimension()) && distance>64 && distance<1024
                    && snapshot.inventory().getOrDefault(equipment.item(),0)==0 && mc.level.hasChunkAt(equipment.position())
                    && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(equipment.position()).getBlock()).toString().equals(equipment.item())){
                reminder="Optional: "+RecipeGuidance.name(equipment.item())+" nearby. Check your equipment before leaving.";break;
            }
        }
    }
    /** Explicit menu actions refresh advice without advancing or controlling the player. */
    public void refreshMenuAdvice(Minecraft mc){
        if(mc.player==null || mc.level==null)return;
        survivalObserver.shelterStrategy(selection.shelterStrategy());
        latestSurvival=survivalObserver.observe(mc,System.nanoTime()/1_000_000L);
        if(selection.active() && !selection.paused())sampleSurvival(mc,System.nanoTime()/1_000_000L);
    }
    private void sampleSurvival(Minecraft mc,long survivalNow){
            survivalObserver.shelterStrategy(selection.shelterStrategy());
            survivalObserver.cookingRecipes(cooking.recipes());
            var observed=survivalObserver.observe(mc,survivalNow);latestSurvival=observed;
            if(!observed.dimension().equals(survivalDimension)){
                survival.reset();cooking.reset();cookingStep=null;narration.reset();survivalObserver.reset();survivalDimension=observed.dimension();
            }
            cooking.observe(mc,observed);
            var base=baseStep();
            var interruption=survival.update(selection,snapshot,observed,survivalNow);
            if(selection.foodResupplyPending()!=survival.foodLatched()){
                selection.foodResupplyPending(survival.foodLatched());save();
            }
            if(interruption!=null && !selection.survivalNeed().equals(interruption.kind().name())){
                selection.survivalNeed(interruption.kind().name());save();
            }else if(interruption==null && !selection.survivalNeed().isEmpty()
                    && survival.resolved(SurvivalMonitor.Kind.valueOf(selection.survivalNeed()),observed,snapshot,selection)){
                selection.survivalNeed("");save();
            }
            if(interruption!=null && selection.survivalResumeStep().isEmpty() && !base.taskId().isEmpty()){
                selection.survivalResumeStep(base.taskId());save();
            }
            if(interruption==null && !selection.survivalResumeStep().isEmpty()
                    && AdaptiveRecommendations.complete(selection.survivalResumeStep(),snapshot,selection.biomeCount())){
                selection.survivalResumeStep("");save();
            }
            updateCooking(mc);
    }
    private void updateCooking(Minecraft mc){
        cookingStep=null;
        if(!selection.active() || selection.paused() || latestSurvival==null || cooking.facts()==null)return;
        var o=latestSurvival;var need=survival.current();
        boolean physical=o.hazard()!=SurvivalMonitor.Hazard.NONE || o.environment().threat()!=null;
        if(physical)return;
        boolean critical=need!=null && need.kind()==SurvivalMonitor.Kind.SAFETY;
        if(critical){
            if(o.hunger()<18 && (!cooking.facts().readyItem().isEmpty() || !cooking.facts().emergencyRaw().isEmpty()))
                cookingStep=cooking.plan(mc,o.hunger(),true,SurvivalMonitor.RESTOCK_FOOD);
            return;
        }
        boolean hold=need!=null && need.kind()==SurvivalMonitor.Kind.SHELTER && o.environment().protectedHere()
                && o.environment().shelter()!=null && o.environment().shelter().build()!=null
                && o.environment().shelter().build().action()==EmergencyShelterPlanner.Action.COMPLETE;
        boolean foodNeed=need!=null && (need.kind()==SurvivalMonitor.Kind.FOOD || need.kind()==SurvivalMonitor.Kind.EAT
                || need.kind()==SurvivalMonitor.Kind.RECOVERY && o.hunger()<18);
        boolean inCookingScreen=mc.player.containerMenu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu;
        if(foodNeed || hold && (o.hunger()<18 || selection.foodResupplyPending() || cooking.facts().readyNutrition()<=SurvivalMonitor.LOW_FOOD)
                || selection.selected().equals("food") && (need==null || need.kind()!=SurvivalMonitor.Kind.SHELTER) || inCookingScreen && selection.foodResupplyPending())
            cookingStep=cooking.plan(mc,o.hunger(),false,SurvivalMonitor.RESTOCK_FOOD);
    }
    private static String acquisition(String item,ProgressionTasks.Snapshot s,String fallback){
        return switch(item){
            case "minecraft:ender_pearl" -> "Gather ender pearls for the recipe. Cleric trading is one possible approach.";
            case "minecraft:blaze_rod" -> !s.nether()?ProgressionTasks.advice(ProgressionTasks.task("nether"),s,0).instruction()
                    :!s.inFortress()?"Look for a Nether fortress. Its location is not confirmed.":"Gather blaze rods from blazes. Detailed combat guidance is not available yet.";
            case "minecraft:iron_ingot" -> "Gather iron ingots for the recipe. Raw iron can be smelted in a furnace.";
            case "minecraft:string" -> "Gather white wool for the white bed. Sheep are one possible source; string is another recipe option.";
            case "minecraft:white_wool" -> "Gather white wool for the white bed recipe.";
            case "minecraft:coal", "minecraft:charcoal" -> "Gather fuel for this recipe. Coal or charcoal may be useful.";
            default -> fallback;
        };
    }
    private static ProgressionTasks.Snapshot empty(){return new ProgressionTasks.Snapshot(java.util.Map.of(),"minecraft:overworld",
            20,20,0,0,0,false,false,false,false,-1,false,java.util.Set.of(),false,false,false,-1,false,false,"");}
}
