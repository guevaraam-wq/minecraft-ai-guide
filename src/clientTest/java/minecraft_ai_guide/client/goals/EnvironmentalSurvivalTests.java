package minecraft_ai_guide.client.goals;

import java.util.*;
import java.nio.file.*;
import minecraft_ai_guide.client.guide.NavigationPlanner;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;
import minecraft_ai_guide.client.guide.NavigationPlanner.Voxel;
import static minecraft_ai_guide.client.goals.EnvironmentalSurvival.*;
import static minecraft_ai_guide.client.goals.SurvivalMonitor.*;

/** Synthetic facts only. These tests never spawn mobs, place blocks or grant progression in Minecraft. */
public final class EnvironmentalSurvivalTests {
    private static int checks;
    private static final Cell PLAYER=new Cell(0,1,0);
    private static final Voxel AIR=new Voxel("air",true,true,false,false,false,false);
    private static final Voxel SOLID=new Voxel("stone",true,false,true,false,false,false);
    private static final Voxel LAVA=new Voxel("lava",true,true,false,true,false,false);
    private static final Voxel GRAVEL=new Voxel("gravel",true,false,true,false,false,true);
    private static final class Room implements ShelterCheck.World {
        final Map<Cell,Voxel> blocks=new HashMap<>();final Set<Cell> closedDoors=new HashSet<>();int light=10;
        Room(){
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                blocks.put(new Cell(x,3,z),SOLID);
                if(Math.abs(x)==2 || Math.abs(z)==2){blocks.put(new Cell(x,1,z),SOLID);blocks.put(new Cell(x,2,z),SOLID);}
            }
        }
        public Voxel voxel(Cell c){return blocks.getOrDefault(c,c.y()<=0?SOLID:AIR);}
        public int blockLight(Cell c){return light;}
        public boolean closedEntrance(Cell c){return closedDoors.contains(c);}
        void entrance(){blocks.put(new Cell(2,1,0),AIR);blocks.put(new Cell(2,2,0),AIR);}
    }
    private static final class Frame {
        Map<Kind,List<Gear>> gear=new EnumMap<>(Kind.class);
        Environment environment=Environment.quiet();Hazard hazard=Hazard.NONE;
        float health=20;int hunger=20,nutrition=64;boolean busy;
        Frame(){gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:wooden_pickaxe",50,59,false)));}
        Observation observation(){return new Observation(health,hunger,8,nutrition,15,32,gear,hazard,busy,true,true,"minecraft:overworld",environment);}
        ProgressionTasks.Snapshot snapshot(){return new ProgressionTasks.Snapshot(Map.of("minecraft:ender_eye",12,"minecraft:wooden_pickaxe",1),
                "minecraft:overworld",health,hunger,8,15,32,true,false,false,false,-1,true,Set.of(),false,false,false,-1,false,false,"minecraft:plains");}
    }
    public static void run()throws Exception{
        time();mobs();food();shelters();prioritiesAndLifecycle();narration();
        System.out.println("Environmental survival regression checks passed: "+checks);
    }
    private static void time(){
        var clock=new DayClock();
        check(clock.update(true,0)==TimeBand.DAY,"A newly started world is not falsely reported as nighttime");
        check(clock.update(true,10_999)==TimeBand.DAY,"Normal daylight does not trigger night advice");
        check(clock.update(true,11_000)==TimeBand.DUSK,"Approaching night is identified before nighttime danger");
        check(clock.update(true,10_950)==TimeBand.DUSK,"Sunset jitter does not flicker preparation advice");
        check(clock.update(true,13_000)==TimeBand.NIGHT,"Current nighttime is recognized");
        check(clock.update(true,24_100)==TimeBand.NIGHT,"A sunrise buffer avoids abrupt release at the day boundary");
        check(clock.update(true,25_000)==TimeBand.DAY,"Morning eventually clears the night state");
        check(clock.update(false,18_000)==TimeBand.NO_CYCLE,"Nether/End/fixed-time context suppresses normal night assumptions");
        check(clock.update(true,18_000)==TimeBand.NIGHT,"Returning to an ordinary Overworld clock re-evaluates time");
        check(clock.update(false,11_500)==TimeBand.NO_CYCLE,"Fixed-time/custom dimension context does not produce dusk advice");
        clock.reset();check(clock.update(true,48_000)==TimeBand.DAY,"Repeated days use floor-modulo time");
    }
    private static Mob mob(String id,Disposition disposition,double distance,boolean visible,boolean targeting){
        return new Mob(id,id,disposition,distance,visible,targeting,false,false,FoodAnimal.NONE,"ahead of you");
    }
    private static void mobs(){
        check(classify(true,true,false,false,false,false)==Disposition.HOSTILE,"Minecraft enemy/category evidence recognizes hostiles without species lists");
        check(classify(false,true,false,false,false,false)==Disposition.HOSTILE,"Monster category catches entities without the enemy interface");
        check(classify(true,true,true,false,false,false)==Disposition.CONDITIONAL,"Neutral behavior takes precedence over broad monster classification");
        check(classify(true,true,false,false,false,true)==Disposition.CONDITIONAL,"Bright daytime spiders are conditional rather than automatically hostile");
        check(classify(true,true,false,false,true,true)==Disposition.HOSTILE,"A targeting spider remains hostile even in daylight");
        check(classify(false,false,false,true,false,false)==Disposition.PASSIVE,"Ordinary animals are generally passive");
        check(classify(false,false,false,true,true,false)==Disposition.HOSTILE,"Actual targeting overrides a normally passive classification");
        check(classify(false,false,false,false,false,false)==Disposition.UNKNOWN,"Unclassified entities are not declared safe");
        var close=mob("Zombie",Disposition.HOSTILE,6,true,false);
        check(threat(close,false).immediate(),"Close visible hostiles are urgent even when client target data is absent");
        check(threat(mob("Zombie",Disposition.HOSTILE,14,true,false),false)!=null,"More distant visible hostiles prompt preparation for cover");
        check(!threat(mob("Zombie",Disposition.HOSTILE,14,true,false),false).immediate(),"Distant sightings differ from immediate attack risk");
        check(threat(mob("Zombie",Disposition.HOSTILE,6,false,false),true)==null,"Occluded outside hostiles do not endlessly warn a protected player");
        check(threat(mob("Cow",Disposition.PASSIVE,2,true,false),false)==null,"Nearby passive animals do not create danger alerts");
        check(threat(mob("Wolf",Disposition.CONDITIONAL,3,true,false),false)!=null,"Nearby neutral mobs get keep-distance guidance");
        check(threat(mob("Wolf",Disposition.CONDITIONAL,12,true,false),false)==null,"Distant unprovoked neutral animals do not flood warnings");
        check(threat(mob("Wolf",Disposition.CONDITIONAL,10,true,true),false).immediate(),"An actual targeting neutral animal becomes an immediate threat");
        var tracker=new ThreatTracker();
        var first=tracker.choose(List.of(close,mob("Skeleton",Disposition.HOSTILE,6.2,true,false)),false);
        var stable=tracker.choose(List.of(mob("Zombie",Disposition.HOSTILE,6.3,true,false),mob("Skeleton",Disposition.HOSTILE,6,true,false)),false);
        check(first.mob().id().equals(stable.mob().id()),"Small distance changes do not alternate mob names every sample");
        var attacking=mob("Attacker",Disposition.HOSTILE,5,true,true);
        check(tracker.choose(List.of(close,attacking),false).mob().id().equals("Attacker"),"A materially stronger attacking threat takes priority");
        check(threatInstruction(new Threat(close,120,true)).contains("avoid fighting"),"Threat guidance favors retreat over combat");
        check(threatInstruction(threat(mob("Wolf",Disposition.CONDITIONAL,3,true,false),false)).contains("Leave it alone"),"Neutral animals get a simple behavior explanation");
        check(tracker.choose(List.of(),false)==null,"A departed threat clears from current observations");
        var inside=new Mob("inside","Zombie",Disposition.HOSTILE,3,false,false,true,false,FoodAnimal.NONE,"ahead");
        check(threat(inside,true).immediate(),"A hostile inside a structurally enclosed room overrides apparent protection even without line of sight");
    }
    private static void food(){
        for(var animal:List.of(FoodAnimal.COW,FoodAnimal.PIG,FoodAnimal.CHICKEN,FoodAnimal.SHEEP)){
            var source=new Mob(animal.name(),animal.name(),Disposition.PASSIVE,6,true,false,false,false,animal,"to your left");
            var result=foodSource(List.of(source),null);
            check(result!=null && result.instruction().contains("left"),"Food animals have accessible identity and direction: "+animal);
            check(result.explanation().contains("Cook"),"Food sources explain cooking: "+animal);
        }
        var cow=new Mob("cow","Cow",Disposition.PASSIVE,5,true,false,false,false,FoodAnimal.COW,"ahead of you");
        var chicken=new Mob("chicken","Chicken",Disposition.PASSIVE,8,true,false,false,false,FoodAnimal.CHICKEN,"behind you");
        check(foodSource(List.of(chicken,cow),null).explanation().contains("beef"),"A nearer observed alternative can be preferred");
        check(foodSource(List.of(cow),threat(mob("Zombie",Disposition.HOSTILE,5,true,false),false))==null,"Food collection is not recommended beside a hostile threat");
        check(foodSource(List.of(new Mob("bad","Cow",Disposition.HOSTILE,2,true,true,false,false,FoodAnimal.COW,"ahead")),null)==null,"Even a food species is excluded when its actual behavior is dangerous");
        check(foodSource(List.of(new Mob("hidden","Cow",Disposition.PASSIVE,2,false,false,false,false,FoodAnimal.COW,"ahead")),null)==null,"Invisible animals are not described as directly accessible food sources");
        check(EnvironmentObserver.direction(0,0,0,0,5).equals("ahead of you"),"Food directions account for the player's facing");
        check(EnvironmentObserver.direction(0,0,0,-5,0).equals("to your right"),"Right/left directions follow Minecraft yaw");
    }
    private static void shelters(){
        Room room=new Room();
        var checked=ShelterCheck.inspect(room,PLAYER);
        check(checked.status()==ShelterCheck.Status.ENCLOSED_LIT,"A small sealed, lit room verifies structural shelter");
        check(checked.interior().size()==9,"Every connected standing cell is checked, not just the player's block");
        room.light=0;check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.DARK,"A sealed dark room is not declared protected against spawning");room.light=10;
        room.blocks.put(PLAYER.offset(0,2,0),AIR);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.EXPOSED,"Missing roof prevents shelter verification");
        room=new Room();room.blocks.put(PLAYER.below(),AIR);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.EXPOSED,"A missing floor or drop is not safe shelter");
        room=new Room();room.blocks.put(PLAYER.below(),LAVA);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.HAZARD,"Lava cannot be accepted as safe shelter footing");
        room=new Room();room.blocks.put(PLAYER.offset(0,2,0),GRAVEL);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.HAZARD,"A falling roof is rejected");
        room=new Room();room.blocks.put(new Cell(2,1,0),Voxel.UNKNOWN);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.UNKNOWN,"Unloaded boundaries stay unknown");
        room=new Room();room.entrance();checked=ShelterCheck.inspect(room,PLAYER);
        check(checked.status()==ShelterCheck.Status.CLOSE_GAP && checked.gaps().size()==2,"An open entrance is an unfinished shelter, not a completed one");
        Cell first=ShelterCheck.nextPlacement(room,checked,PLAYER,2,false);
        check(first!=null && first.y()==1,"One supported low block is proposed first");
        check(ShelterCheck.nextPlacement(room,checked,PLAYER,1,false)==null,"Insufficient actual materials prevent claiming a complete sealing plan");
        check(ShelterCheck.nextPlacement(room,checked,PLAYER,2,true)==null,"Construction does not supersede a nearby threat");
        room.blocks.put(first,SOLID);checked=ShelterCheck.inspect(room,PLAYER);
        check(checked.status()==ShelterCheck.Status.CLOSE_GAP && checked.gaps().size()==1,"Real block evidence advances construction one block at a time");
        Cell second=ShelterCheck.nextPlacement(room,checked,PLAYER,1,false);
        check(second!=null && second.y()==2,"The upper entrance block waits for verified lower support");
        room.blocks.put(second,SOLID);checked=ShelterCheck.inspect(room,PLAYER);
        check(checked.status()==ShelterCheck.Status.ENCLOSED_LIT,"Protection is reported only after both real blocks close the opening");
        room.blocks.put(first,AIR);
        check(ShelterCheck.inspect(room,PLAYER).status()!=ShelterCheck.Status.ENCLOSED_LIT,"Removing a wall invalidates previously verified protection");
        room=new Room();room.entrance();room.blocks.put(new Cell(3,0,0),AIR);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.HAZARD,"An opening beside a drop is not a construction route");
        var self=new ShelterCheck.Result(ShelterCheck.Status.CLOSE_GAP,Set.of(PLAYER),List.of(PLAYER),"fixture");
        check(ShelterCheck.nextPlacement(new Room(),self,PLAYER,8,false)==null,"Placement never fills the player's standing cell");
        room=new Room();room.blocks.put(new Cell(2,2,0),AIR);
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.CLOSE_GAP,"A head-height opening is checked rather than ignored as an inaccessible entrance");
        room=new Room();var door=new Voxel("door",true,false,false,false,false,false);
        room.blocks.put(new Cell(2,1,0),door);room.blocks.put(new Cell(2,2,0),door);
        room.closedDoors.add(new Cell(2,1,0));room.closedDoors.add(new Cell(2,2,0));
        check(ShelterCheck.inspect(room,PLAYER).status()==ShelterCheck.Status.ENCLOSED_LIT,"A verified closed entrance can complete a lit room");
        room.closedDoors.clear();
        check(ShelterCheck.inspect(room,PLAYER).status()!=ShelterCheck.Status.ENCLOSED_LIT,"An unverified or open entrance does not complete the shelter");
        check(closedDoorProtection(false,true,false),"Closed wooden doors count conservatively on ordinary difficulty");
        check(!closedDoorProtection(false,true,true),"Breakable wooden doors do not certify a shelter on Hard difficulty");
        check(closedDoorProtection(true,false,true),"Closed iron doors remain eligible on Hard difficulty");
        Room cave=new Room();cave.blocks.clear();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)cave.blocks.put(new Cell(x,3,z),SOLID);
        check(ShelterCheck.inspect(cave,PLAYER).status()!=ShelterCheck.Status.ENCLOSED_LIT,"Being underground under a roof alone does not establish shelter");
    }
    private static ShelterAdvice cover(){return new ShelterAdvice("Look for an enclosed building; a new shelter cannot be safely planned here.","Night is approaching.",null,List.of(),null);}
    private static SurvivalMonitor.Interruption sample(SurvivalMonitor m,GoalSelection goal,Frame f,long now){return m.update(goal,f.snapshot(),f.observation(),now);}
    private static void prioritiesAndLifecycle()throws Exception{
        Frame f=new Frame();var goal=new GoalSelection();goal.start("dragon");goal.preferStep("stronghold");var monitor=new SurvivalMonitor();
        f.environment=new Environment(TimeBand.DUSK,false,null,cover(),null,0);
        check(sample(monitor,goal,f,0).kind()==Kind.SHELTER,"Approaching night creates a temporary shelter task before an attack");
        check(goal.selected().equals("dragon") && goal.preferredStep().equals("stronghold"),"Shelter never replaces the main goal or unfinished step");
        check(monitor.dismiss(0),"Noncritical dusk preparation respects dismissal controls");
        check(sample(monitor,goal,f,500)==null,"An unchanged dismissed dusk warning stays dismissed");
        f.environment=new Environment(TimeBand.NIGHT,false,null,cover(),null,0);
        sample(monitor,goal,f,1_000);
        check(sample(monitor,goal,f,3_000).kind()==Kind.SHELTER,"Dusk becoming night materially worsens exposure and revokes dismissal");
        var danger=threat(mob("Zombie",Disposition.HOSTILE,5,true,false),false);
        f.environment=new Environment(TimeBand.NIGHT,false,danger,cover(),null,0);f.busy=true;
        var warning=sample(monitor,goal,f,3_250);
        check(warning.critical() && warning.advice().instruction().contains("Zombie"),"A named close hostile bypasses ordinary activity delays");
        check(warning.advice().recipeItem().isEmpty(),"Escape advice does not send an unarmed player into crafting");
        check(!monitor.dismiss(3_250),"Immediate hostile warnings cannot be dismissed as routine preparation");
        f.hazard=Hazard.DROWNING;
        check(sample(monitor,goal,f,3_500).advice().instruction().contains("air"),"A physical emergency takes priority over an accompanying mob warning");
        f.hazard=Hazard.NONE;f.busy=false;f.environment=new Environment(TimeBand.NIGHT,true,null,null,null,32);
        check(sample(monitor,goal,f,5_000).critical(),"A brief line-of-sight loss does not instantly erase danger advice");
        sample(monitor,goal,f,12_000);var protectedAdvice=sample(monitor,goal,f,14_000);
        check(protectedAdvice==null || protectedAdvice.kind()!=Kind.SHELTER,"An observed protected player is not repeatedly sent to find shelter");
        check(protectedAdvice!=null && protectedAdvice.kind()==Kind.WEAPON,"A protected but unarmed player can prepare a weapon after escape");
        check(protectedAdvice.why().contains("pickaxe") && protectedAdvice.advice().recipeItem().equals("minecraft:stone_sword"),"Weapon advice explains pickaxes and uses a real recipe output");
        f.gear.put(Kind.WEAPON,List.of(new Gear("minecraft:iron_sword",200,250,false)));
        f.gear.put(Kind.PICKAXE,List.of(new Gear("minecraft:iron_pickaxe",200,250,false)));
        f.gear.put(Kind.SHIELD,List.of(new Gear("minecraft:shield",200,336,false)));
        check(sample(monitor,goal,f,15_000)==null,"Usable existing equipment prevents unnecessary weapon crafting");
        check(AdaptiveRecommendations.next(goal,f.snapshot(),true).taskId().equals("stronghold"),"The original unfinished task resumes when safety and preparation are resolved");
        monitor.reset();f.environment=new Environment(TimeBand.DUSK,false,null,cover(),null,0);f.health=12;f.nutrition=8;
        check(sample(monitor,goal,f,0).kind()==Kind.SHELTER,"Find safety comes before ordinary recovery and supplies");
        f.health=5;check(sample(monitor,goal,f,250).critical(),"Critical health is never delayed by a routine night task");
        goal.pause(true);check(sample(monitor,goal,f,500)==null,"Night and mobs cannot resume a paused goal");
        goal.pause(false);goal.requestCancel();goal.confirmCancel(true);
        check(sample(monitor,goal,f,750)==null,"Canceled goals remain canceled even during environmental danger");
        goal.start("stone_axe");f=new Frame();monitor.reset();
        var animal=foodSource(List.of(new Mob("cow","Cow",Disposition.PASSIVE,4,true,false,false,false,FoodAnimal.COW,"ahead of you")),null);
        f.nutrition=8;f.environment=new Environment(TimeBand.DAY,false,null,null,animal,32);
        var weapon=sample(monitor,goal,f,0);
        check(weapon.kind()==Kind.WEAPON && weapon.advice().recipeItem().equals("minecraft:stone_sword"),"Food-source guidance accounts for a player carrying only a mining tool");
        f.gear.put(Kind.WEAPON,List.of(new Gear("minecraft:stone_axe",100,131,false)));
        sample(monitor,goal,f,500);var food=sample(monitor,goal,f,2_500);
        check(food.kind()==Kind.FOOD && food.why().contains("Cook"),"An existing usable axe permits optional meat-source guidance with cooking explanation");
        check(food.advice().instruction().contains("Cow"),"Food instructions name the species rather than requiring visual recognition");
        monitor.reset();f.environment=new Environment(TimeBand.NIGHT,true,null,null,null,32);
        check(sample(monitor,goal,f,0).advice().instruction().contains("Stay in cover"),"Low food reserves do not send a protected player outside at night");
        var collecting=new minecraft_ai_guide.client.guide.RecipeGuidance.Step(minecraft_ai_guide.client.guide.RecipeGuidance.Action.COLLECT,
                "minecraft:oak_log",1,Set.of(),"Collect a log");
        check(coveredRecipeInstruction(f.environment,collecting).contains("Keep cover"),"A missing recipe ingredient does not send a sheltered unarmed player into an outdoor hunt");
        var crafting=new minecraft_ai_guide.client.guide.RecipeGuidance.Step(minecraft_ai_guide.client.guide.RecipeGuidance.Action.CRAFT,
                "minecraft:stone_sword",1,Set.of(),"Craft a sword");
        check(coveredRecipeInstruction(f.environment,crafting).isEmpty(),"Crafting with available materials remains possible from shelter");
        check(coveredRecipeInstruction(Environment.quiet(),collecting).isEmpty(),"Daylight resource guidance resumes without permanently blocking the recipe");
        goal.survivalNeed("SHELTER");goal.survivalResumeStep("stronghold");
        Path dir=Files.createTempDirectory("environment-goals-");Path file=dir.resolve("goals.properties");
        try{goal.save(file);var loaded=GoalSelection.load(file);check(loaded.survivalNeed().equals("SHELTER") && loaded.survivalResumeStep().equals("stronghold"),"Shelter interruptions remain compatible with the existing goal save format");}
        finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        check(!new Environment(TimeBand.NIGHT,false,null,null,null,0,true).nightRisk(),"Peaceful difficulty does not demand night combat preparation");
        monitor.reset();goal.start("stone_axe");f=new Frame();f.gear.put(Kind.WEAPON,List.of(new Gear("minecraft:stone_sword",100,131,false)));
        f.environment=new Environment(TimeBand.NIGHT,true,null,null,null,32);
        check(sample(monitor,goal,f,0)==null,"An already protected, equipped player is not repeatedly interrupted solely because it is night");
        f.environment=new Environment(TimeBand.DUSK,false,null,cover(),null,0);sample(monitor,goal,f,0);sample(monitor,goal,f,2_000);
        f.environment=Environment.quiet();var quiet=sample(monitor,goal,f,3_000);
        check(quiet!=null && !quiet.advice().instruction().contains("checked cover"),"Dawn outside never falsely claims the player is sheltered");
        check(sample(monitor,goal,f,12_000)==null,"A stable clear morning releases the shelter interruption");
    }
    private static void narration(){
        var n=new GuidanceNarration();
        check(n.shouldSpeak("Zombie nearby",true,0),"New critical guidance can be narrated immediately");
        check(!n.shouldSpeak("Zombie nearby",true,10_000),"An unchanged warning is not narrated on every tick");
        check(!n.shouldSpeak("Reach air",true,100),"Changed urgent narration still has a minimum interval");
        check(n.shouldSpeak("Reach air",true,2_000),"A materially changed urgent instruction can interrupt ordinary narration");
        check(!n.shouldSpeak("Find shelter",false,3_000),"Ordinary voice changes are throttled");
        check(n.shouldSpeak("Find shelter",false,10_000),"Stable ordinary guidance can be narrated after the interval");
        n.reset();check(n.shouldSpeak("Find shelter",false,10_001),"A new guidance session resets narration deduplication");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
