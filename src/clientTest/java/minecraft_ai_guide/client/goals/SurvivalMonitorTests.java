package minecraft_ai_guide.client.goals;

import java.nio.file.*;
import java.util.*;
import static minecraft_ai_guide.client.goals.SurvivalMonitor.*;
import static minecraft_ai_guide.client.goals.ProgressionTasks.*;

/** Actual observations are never synthesized in gameplay; these are headless fixtures only. */
public final class SurvivalMonitorTests {
    private static int checks;
    private static class Frame {
        float health=20;int hunger=20,food=8,nutrition=64,armor=15,blocks=32;
        boolean busy,survival=true,alive=true;String dimension="minecraft:overworld";Hazard hazard=Hazard.NONE;
        Map<Kind,List<Gear>> gear=new EnumMap<>(Kind.class);
        Map<String,Integer> inventory=new HashMap<>(Map.of("minecraft:iron_pickaxe",1,"minecraft:iron_sword",1,"minecraft:shield",1,"minecraft:ender_eye",12));
        Frame(){
            gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:iron_pickaxe",200,250,false)));
            gear.put(Kind.WEAPON,List.of(new Gear("minecraft:iron_sword",200,250,false)));
            gear.put(Kind.SHIELD,List.of(new Gear("minecraft:shield",300,336,false)));
            gear.put(Kind.ARMOR,List.of(new Gear("minecraft:iron_chestplate",200,240,true)));
        }
        Observation observation(){return new Observation(health,hunger,food,nutrition,armor,blocks,gear,hazard,busy,survival,alive,dimension);}
        Snapshot snapshot(){return new Snapshot(inventory,dimension,health,hunger,food,armor,blocks,true,false,false,false,-1,true,Set.of(),false,false,false,-1,false,false,"minecraft:plains");}
    }
    private static GoalSelection dragon(){GoalSelection goal=new GoalSelection();goal.start("dragon");return goal;}
    private static Interruption sample(SurvivalMonitor m,GoalSelection g,Frame f,long time){return m.update(g,f.snapshot(),f.observation(),time);}
    private static Interruption settled(SurvivalMonitor m,GoalSelection g,Frame f,long time){sample(m,g,f,time);return sample(m,g,f,time+SETTLE_MS);}
    public static void run()throws Exception{
        foodAndContinuation();stabilityAndDismissal();safetyAndActivity();equipment();lifecycleAndPersistence();priorityAndRejoin();
        System.out.println("Survival monitoring regression checks passed: "+checks);
    }
    private static void foodAndContinuation(){
        Frame f=new Frame();var g=dragon();var m=new SurvivalMonitor();g.preferStep("biomes");
        check(sample(m,g,f,0)==null,"Adequate supplies do not interrupt an active goal");
        String original=AdaptiveRecommendations.next(g,f.snapshot(),true).taskId();
        f.food=1;f.nutrition=8;f.hunger=18;
        check(sample(m,g,f,1_000)==null && m.foodDecrease()==7,"Current stack quantities detect loss while debouncing advice");
        check(sample(m,g,f,3_000).kind()==Kind.FOOD,"Supplies consumed during a long goal cause resupply");
        g.survivalResumeStep(original);g.survivalNeed("FOOD");
        check(g.selected().equals("dragon") && g.preferredStep().equals("biomes"),"Temporary tasks preserve main and preferred goals");
        f.food=3;f.nutrition=24;
        check(sample(m,g,f,4_000).kind()==Kind.FOOD,"Partial replenishment retains the task until the upper threshold");
        f.food=4;f.nutrition=32;
        check(sample(m,g,f,5_000)==null,"Four nutritious food items can satisfy resupply without eight items");
        check(AdaptiveRecommendations.next(g,f.snapshot(),true).taskId().equals(original),"The exact unfinished chosen step returns after replenishment");
        g.survivalNeed("");g.survivalResumeStep("");g.clearStep();
        check(AdaptiveRecommendations.next(g,f.snapshot(),true).taskId().equals("stronghold"),"Managed progression does not restart the old eight-item readiness loop");
        f.food=2;f.nutrition=16;f.hunger=12;sample(m,g,f,6_000);
        check(sample(m,g,f,8_000).kind()==Kind.EAT,"Hunger is evaluated separately from remaining nutrition");
        f.food=1;f.nutrition=8;f.hunger=18;sample(m,g,f,9_000);
        check(m.likelyAte() && m.foodDecrease()==1,"Fewer food items plus increased hunger is recorded as likely consumption");
        check(sample(m,g,f,11_000).kind()==Kind.FOOD,"Eating can reveal a remaining supply shortage");
        check(!SurvivalObserver.usableFood("minecraft:rotten_flesh") && !SurvivalObserver.usableFood("minecraft:pufferfish"),"Known harmful foods are excluded");
        check(SurvivalObserver.usableFood("minecraft:cooked_beef"),"Ordinary usable food is accepted");
    }
    private static void stabilityAndDismissal(){
        Frame f=new Frame();var g=dragon();var m=new SurvivalMonitor();sample(m,g,f,-1_000);f.nutrition=12;
        check(sample(m,g,f,0)==null,"A brief threshold crossing does not immediately interrupt");
        f.nutrition=13;sample(m,g,f,1_000);f.nutrition=12;
        check(sample(m,g,f,2_000)==null,"Returning above the threshold resets the candidate delay");
        check(sample(m,g,f,4_000).kind()==Kind.FOOD,"A sustained shortage triggers a temporary task");
        f.nutrition=13;check(sample(m,g,f,5_000).kind()==Kind.FOOD,"One-point fluctuation cannot clear a latched shortage");
        f.nutrition=31;check(sample(m,g,f,6_000).kind()==Kind.FOOD,"Upper threshold prevents rapid back-and-forth switching");
        check(m.dismiss(6_000),"Noncritical suggestions can be dismissed");
        f.nutrition=12;check(sample(m,g,f,7_000)==null,"A dismissed suggestion stays out of the way");
        f.nutrition=4;
        check(settled(m,g,f,8_000).kind()==Kind.FOOD,"Materially worsening supplies revoke dismissal");
        m.dismiss(11_000);f.nutrition=8;
        check(sample(m,g,f,12_000)==null,"Dismissal creates a new baseline rather than repeatedly firing");
        check(settled(m,g,f,132_000).kind()==Kind.FOOD,"Dismissal expires after two minutes");
        f.nutrition=32;check(sample(m,g,f,135_000)==null,"A replenished need clears");
        check(sample(m,g,f,136_000)==null,"Adequate supplies do not repeatedly generate a food task");
    }
    private static void safetyAndActivity(){
        Frame f=new Frame();var g=dragon();var m=new SurvivalMonitor();f.nutrition=8;f.busy=true;
        check(settled(m,g,f,0)==null,"Ordinary resupply waits during crafting, mining or item use");
        f.hazard=Hazard.LAVA;
        var urgent=sample(m,g,f,3_000);
        check(urgent.critical() && urgent.advice().instruction().contains("lava"),"Lava overrides activity delays immediately");
        check(!m.dismiss(3_000),"Critical warnings cannot be dismissed");
        check(urgent.advice().recipeItem().isEmpty(),"Immediate danger never recommends crafting");
        f.hazard=Hazard.DROWNING;
        check(sample(m,g,f,3_250).advice().instruction().contains("air"),"Low air produces a practical immediate instruction");
        f.hazard=Hazard.NONE;f.busy=false;
        check(sample(m,g,f,5_000).critical(),"Danger advice remains stable during a short quiet interval");
        check(sample(m,g,f,12_000)==null,"After the quiet interval ordinary needs begin their normal delay");
        check(sample(m,g,f,14_000).kind()==Kind.FOOD,"Resupply returns after immediate danger has passed");
        m.reset();f.nutrition=64;f.health=5;
        check(sample(m,g,f,0).critical(),"Critically low health interrupts immediately even without a visible attacker");
        f.health=9;
        check(sample(m,g,f,20_000).critical(),"Critical health has a separate recovery threshold");
        f.health=12;
        check(settled(m,g,f,21_000).kind()==Kind.RECOVERY,"Health recovery continues as an ordinary step after danger subsides");
        f.health=17;
        check(sample(m,g,f,24_000).kind()==Kind.RECOVERY,"Recovery does not flicker near the low-health trigger");
        f.health=18;check(sample(m,g,f,25_000)==null,"Recovery clears at the documented healthy threshold");
        m.reset();f.health=20;f.nutrition=0;f.food=0;f.hunger=4;
        check(sample(m,g,f,0).critical(),"Severe hunger without food is urgent rather than ordinary preparation");
        m.reset();f.hunger=20;f.nutrition=8;f.dimension="minecraft:the_end";
        check(settled(m,g,f,0).advice().instruction().contains("limited"),"End resupply explains limited food access rather than ordering impossible local gathering");
        m.reset();f.survival=false;
        check(settled(m,g,f,0)==null,"Creative or spectator mode does not create survival interruptions");
        f.survival=true;f.alive=false;
        check(settled(m,g,f,3_000)==null,"No survival action is shown for a dead player");
    }
    private static void equipment(){
        Frame f=new Frame();var g=dragon();var m=new SurvivalMonitor();
        f.inventory.put("minecraft:stone_pickaxe",1);
        f.gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:stone_pickaxe",5,131,false)));
        var need=settled(m,g,f,0);
        check(need.kind()==Kind.PICKAXE && need.advice().recipeAmount()==2,"A nearly broken existing tool requests a spare rather than completing on the old item");
        check(need.advice().recipeItem().equals("minecraft:stone_pickaxe"),"Replacement dependencies go through Minecraft recipe knowledge");
        f.gear.put(Kind.PICKAXE,List.of());f.inventory.remove("minecraft:stone_pickaxe");
        check(sample(m,g,f,3_000).advice().recipeAmount()==1,"After the old tool breaks the target quantity is recalculated");
        f.gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:iron_pickaxe",200,250,false)));
        check(sample(m,g,f,4_000)==null,"A healthy replacement resolves the tool interruption");
        f.gear.put(Kind.WEAPON,List.of(new Gear("minecraft:iron_sword",20,250,false)));
        check(settled(m,g,f,5_000).kind()==Kind.WEAPON,"Low remaining percentage catches a nearly broken weapon");
        f.gear.put(Kind.WEAPON,List.of(new Gear("minecraft:iron_sword",20,250,false),new Gear("minecraft:stone_sword",100,131,false)));
        check(sample(m,g,f,8_000)==null,"A usable spare prevents needless replacement advice");
        f.gear.put(Kind.ARMOR,List.of(new Gear("minecraft:iron_chestplate",5,240,true)));
        need=settled(m,g,f,9_000);
        check(need.kind()==Kind.ARMOR && need.advice().instruction().contains("repair"),"Armor damage matters even while armor points remain high");
        f.gear.put(Kind.ARMOR,List.of(new Gear("minecraft:iron_chestplate",200,240,true)));f.armor=0;
        check(sample(m,g,f,12_000).kind()==Kind.ARMOR,"Missing protection does not clear merely because a healthy item exists");
        f.armor=15;sample(m,g,f,13_000);f.gear.put(Kind.SHIELD,List.of(new Gear("minecraft:shield",1,336,false)));
        check(settled(m,g,f,14_000).kind()==Kind.SHIELD,"Nearly broken shields are checked before a dangerous trip");
        f.gear.put(Kind.SHIELD,List.of(new Gear("minecraft:shield",300,336,false)));sample(m,g,f,17_000);f.blocks=15;
        check(settled(m,g,f,18_000).kind()==Kind.BLOCKS,"Building supplies are rechecked rather than permanently marked ready");
        f.blocks=20;check(sample(m,g,f,21_000).kind()==Kind.BLOCKS,"Building stocks also have separate low and restored thresholds");
        f.blocks=32;check(sample(m,g,f,22_000)==null,"Restored building stock resumes progression");
    }
    private static void lifecycleAndPersistence()throws Exception{
        Frame f=new Frame();var g=dragon();var m=new SurvivalMonitor();f.nutrition=8;
        settled(m,g,f,0);g.survivalResumeStep("stronghold");g.survivalNeed("FOOD");g.pause(true);
        check(sample(m,g,f,3_000)==null,"Pause stops a running survival interruption");
        f.hazard=Hazard.LAVA;
        check(sample(m,g,f,4_000)==null,"Immediate hazards never automatically resume a paused goal");
        g.pause(false);f.hazard=Hazard.NONE;f.nutrition=20;
        check(settled(m,g,f,5_000).kind()==Kind.FOOD,"Resume restores a partially resolved food need using fresh evidence");
        Path dir=Files.createTempDirectory("survival-save-");Path file=dir.resolve("goals.properties");
        try{
            g.save(file);var loaded=GoalSelection.load(file);
            check(loaded.selected().equals("dragon") && loaded.survivalResumeStep().equals("stronghold"),"Interrupted step persists separately from the main goal");
            check(loaded.survivalNeed().equals("FOOD"),"Food hysteresis survives save/rejoin without persisting a fake observation");
            var fresh=new SurvivalMonitor();check(settled(fresh,loaded,f,0).kind()==Kind.FOOD,"Loaded need is revalidated against actual fresh food state");
            f.nutrition=32;check(sample(fresh,loaded,f,3_000)==null,"Fresh replenishment resolves the restored need");
            check(AdaptiveRecommendations.next(loaded,f.snapshot(),true).taskId().equals("stronghold"),"A saved automatic unfinished step resumes after resupply");
            loaded.requestCancel();loaded.confirmCancel(true);loaded.save(file);loaded=GoalSelection.load(file);
            check(!loaded.active() && loaded.survivalResumeStep().isEmpty() && loaded.survivalNeed().isEmpty(),"Confirmed cancellation clears transient tasks in saved state");
            f.health=3;check(sample(fresh,loaded,f,4_000)==null,"Canceled goals cannot be restarted by a critical health observation");
            Files.writeString(file,"format=accessibility-goals-v1\nselected=dragon\npaused.dragon=true\nreminders=true\n");
            loaded=GoalSelection.load(file);
            check(loaded.paused() && loaded.reminders() && loaded.survivalNeed().isEmpty(),"Original goal saves retain pause/preferences with safe new-field defaults");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        check(g.selected().equals("dragon"),"Monitoring never changes the main goal identifier");
    }
    private static void priorityAndRejoin()throws Exception{
        var f=new Frame();var g=dragon();var m=new SurvivalMonitor();f.nutrition=8;
        check(sample(m,g,f,0).kind()==Kind.FOOD,"First readiness sample does not briefly send an unprepared player into danger");
        g.foodResupplyPending(m.foodLatched());g.survivalNeed("FOOD");
        f.health=12;sample(m,g,f,1_000);
        check(sample(m,g,f,3_000).kind()==Kind.RECOVERY,"Health needs can supersede a current food task");
        g.survivalNeed("RECOVERY");f.nutrition=24;f.health=18;
        check(settled(m,g,f,4_000).kind()==Kind.FOOD,"Resolving recovery returns to the unfinished food shortage, not an early readiness reset");
        check(m.foodLatched(),"Food resupply remains pending across a higher-priority interruption");
        Path dir=Files.createTempDirectory("survival-priority-");Path file=dir.resolve("goals.properties");
        try{
            g.survivalNeed("RECOVERY");g.save(file);var loaded=GoalSelection.load(file);
            check(loaded.foodResupplyPending(),"Underlying food hysteresis persists even when another need is in front");
            var fresh=new SurvivalMonitor();
            check(sample(fresh,loaded,f,0).kind()==Kind.FOOD,"Fresh real state restores the pending food task after rejoin");
            check(fresh.dismiss(1_000),"Restored noncritical tasks can still be dismissed");
            loaded.foodResupplyPending(fresh.foodLatched());loaded.survivalNeed("");
            check(sample(fresh,loaded,f,2_000)==null,"Dismissing a latched task does not immediately revive it from saved flags");
            loaded.requestCancel();loaded.confirmCancel(true);
            check(!loaded.foodResupplyPending(),"Cancel clears the underlying resupply latch");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        f=new Frame();g=dragon();m=new SurvivalMonitor();f.food=4;f.nutrition=32;
        check(AdaptiveRecommendations.suggestions(g,f.snapshot(),f.observation(),null).stream().noneMatch(t->t.id().equals("food")),"Browse suggestions agree with adequate nutrition instead of reusing the eight-item count");
        check(AdaptiveRecommendations.suggestions(g,f.snapshot(),f.observation(),null).getFirst().id().equals("stronghold"),"Browse retains the actual unfinished long-term step when supplies are adequate");
        f.nutrition=0;f.food=0;sample(m,g,f,0);m.dismiss(1_000);
        check(sample(m,g,f,2_000)==null,"An unchanged empty food inventory does not immediately revoke dismissal");
        f=new Frame();g=dragon();m=new SurvivalMonitor();f.gear.remove(Kind.PICKAXE);sample(m,g,f,0);m.dismiss(1_000);
        check(sample(m,g,f,2_000)==null,"An unchanged missing tool does not immediately revoke dismissal");
        f=new Frame();g=dragon();m=new SurvivalMonitor();f.gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:wooden_pickaxe",50,59,false)));
        var need=sample(m,g,f,0);
        check(need.kind()==Kind.PICKAXE && !need.why().contains("broken"),"A weak but healthy tool is not falsely described as damaged");
        f=new Frame();g=dragon();m=new SurvivalMonitor();f.health=5;f.hunger=12;
        check(sample(m,g,f,0).advice().instruction().contains("Eat"),"Critical health with food and no active hazard gives a practical recovery action");
        f=new Frame();g=dragon();m=new SurvivalMonitor();f.dimension="minecraft:the_end";
        f.gear.put(Kind.ARMOR,List.of(new Gear("minecraft:iron_chestplate",1,240,true)));
        check(sample(m,g,f,0).advice().recipeItem().isEmpty(),"Worn protection in the End produces a sheltered equipment check rather than a crafting detour");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
