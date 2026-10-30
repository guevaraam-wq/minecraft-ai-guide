package minecraft_ai_guide.client.goals;

import java.util.*;
import java.nio.file.*;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;
import minecraft_ai_guide.client.guide.NavigationPlanner.Voxel;
import static minecraft_ai_guide.client.goals.EmergencyShelterPlanner.*;
import static minecraft_ai_guide.client.goals.EnvironmentalSurvival.*;

/** Pure, simulated worlds. Only fixture code mutates blocks; production planning is read-only. */
public final class EmergencyShelterTests {
    private static int checks;
    private static final Cell START=new Cell(0,1,0);
    private static final Voxel AIR=new Voxel("air",true,true,false,false,false,false);
    private static final Voxel STONE=new Voxel("minecraft:stone",true,false,true,false,true,false);
    private static final Voxel DIRT=new Voxel("minecraft:dirt",true,false,true,false,true,false);
    private static final Voxel WATER=new Voxel("water",true,true,false,true,false,false);
    private static final Voxel FALLING=new Voxel("sand",true,false,true,false,false,true);
    private static class Terrain implements World {
        Map<Cell,Voxel> blocks=new HashMap<>();boolean placement=true;int light;
        public Voxel voxel(Cell c){return blocks.getOrDefault(c,c.y()<=0?STONE:AIR);}
        public int blockLight(Cell c){return light;}
        public boolean placementAllowed(Cell t,Cell s,boolean jump,Cell actor){return placement;}
        Terrain hill(Voxel material){for(int x=1;x<=4;x++)for(int z=-3;z<=3;z++)for(int y=1;y<=5;y++)blocks.put(new Cell(x,y,z),material);return this;}
    }
    private static Supplies stock(int n){return new Supplies(n,true,true,20,20);}
    public static void run()throws Exception{
        designs();boxExecution();hillExecution();replanningAndHazards();lifecycle();
        System.out.println("Emergency shelter regression checks passed: "+checks);
    }
    private static void designs(){
        Terrain w=new Terrain();Plan box=freestanding(w,START);
        check(box!=null && box.strategy()==Strategy.FREESTANDING,"Flat supported ground supports a freestanding design");
        check(box.materials(w)==10,"A new box budgets eight wall blocks, one roof support and one roof block");
        check(box.operations().stream().filter(o->o.action()==Action.JUMP_PLACE).count()==1,"The roof support explicitly requires one jump placement");
        check(box.operations().getLast().target().equals(START.offset(0,2,0)),"The roof has an exact coordinate above clear head space");
        check(box.operations().stream().noneMatch(o->o.target().equals(START) || o.target().equals(START.above())),"No placed block occupies the player's standing/head space");
        check(choose(w,START,stock(9),Strategy.AUTO,false).plan()==null,"A box cannot start without enough materials to seal it");
        check(choose(w,START,stock(10),Strategy.AUTO,false).plan()!=null,"Ten actual blocks fund the complete flat design");
        check(choose(w,START,stock(10),Strategy.HILLSIDE,false).reason().contains("not feasible"),"An unavailable requested strategy is explained instead of silently claiming to switch designs");
        Terrain hill=new Terrain().hill(DIRT);Plan recess=hillside(hill,START,1,0,stock(2));
        check(recess!=null && recess.materials(hill)==2,"A suitable dirt bank requires only two carried sealing blocks");
        check(recess.operations().size()==5 && recess.operations().getFirst().target().equals(START.offset(1,1,0)),"Hillside steps remove head space first, then feet space, enter and seal");
        check(recess.operations().stream().noneMatch(o->o.action()==Action.JUMP_PLACE),"The hillside option avoids jump placement");
        check(choose(hill,START,stock(2),Strategy.AUTO,false).plan().strategy()==Strategy.HILLSIDE,"A fully funded hillside is preferred when a box is not affordable");
        check(choose(hill,START,stock(1),Strategy.AUTO,false).missing()==1,"A resource shortage reports the actual smallest-design deficit");
        check(choose(hill,START,stock(10),Strategy.FREESTANDING,false).plan().strategy()==Strategy.FREESTANDING,"A player may request the other feasible strategy");
        check(hillside(new Terrain().hill(STONE),START,1,0,new Supplies(2,false,true,20,20))==null,"Rock excavation requires a usable pickaxe");
        check(choose(new Terrain().hill(STONE),START,new Supplies(2,false,true,5,20),Strategy.AUTO,false).reason().contains("pickaxe"),"Unavailable rock excavation explains the missing tool prerequisite");
        check(hillside(new Terrain().hill(STONE),START,1,0,new Supplies(2,true,true,5,20))==null,"Critical health avoids slow rock excavation");
        check(hillside(hill,START,1,0,new Supplies(2,false,true,5,20))!=null,"Low health can still use a short ordinary-dirt recess without a mining tool");
        var poor=choose(w,START,new Supplies(3,false,true,5,10),Strategy.AUTO,false);
        check(poor.reason().contains("Eat") && poor.reason().contains("3"),"An unfunded low-health plan offers carried-food recovery with actual quantities");
        check(choose(w,START,new Supplies(3,false,false,5,20),Strategy.AUTO,false).reason().contains("do not go out"),"Critically injured players are not sent outside on a materials trip");
        check(EmergencyShelterWorld.usableMaterial("minecraft:dirt") && EmergencyShelterWorld.usableMaterial("minecraft:oak_planks"),"Dirt and ordinary planks are usable construction stock");
        check(EmergencyShelterWorld.usableMaterial("minecraft:oak_log") && EmergencyShelterWorld.usableMaterial("minecraft:warped_stem"),"Ordinary solid logs and stems are counted, not ignored as shelter stock");
        check(!EmergencyShelterWorld.usableMaterial("minecraft:sand") && !EmergencyShelterWorld.usableMaterial("minecraft:tnt")
                && !EmergencyShelterWorld.usableMaterial("mod:hazard_planks"),"Falling, explosive and unknown modded materials are excluded");
    }
    private static void boxExecution(){
        Terrain w=new Terrain();Session session=new Session();session.preference(Strategy.FREESTANDING);int blocks=10;
        var first=session.next(w,START,stock(blocks),false,true);
        check(first.action()==Action.PLACE && first.target().equals(START.offset(0,0,-1)),"First placement identifies the exact lower wall cell");
        check(session.next(w,START,stock(blocks),false,true).target().equals(first.target()),"Repeated observations retain a stable placement marker");
        check(session.next(w,START,stock(9),false,true).action()==Action.BLOCKED,"Inventory loss alone never verifies a placement");
        check(session.next(w,START,stock(10),true,true).text().contains("Move away"),"Immediate threats stop construction and prioritize escape");
        int placed=0;
        while(placed<10){
            var next=session.next(w,START,stock(blocks),false,true);
            check(next.action()==Action.PLACE || next.action()==Action.JUMP_PLACE,"Each box step is a single executable placement");
            check(next.step()==placed+1 && next.total()==10,"Step/total counters come from actual observed work");
            check(EmergencyShelterPlanner.solid(w.voxel(next.support())),"Every placement has an existing full-block support");
            if(next.action()==Action.JUMP_PLACE)check(next.jump() && next.text().contains("Jump"),"The roof support does not pretend a normal ground click can reach its top face");
            w.blocks.put(next.target(),placed%2==0?DIRT:STONE);blocks--;placed++;
        }
        var done=session.next(w,START,stock(0),false,true);
        check(done.action()==Action.COMPLETE && done.text().contains("Wait inside"),"Completion is declared only after the real enclosure exists");
        check(ShelterCheck.inspect(w,START).status()==ShelterCheck.Status.DARK && occupiedCover(ShelterCheck.inspect(w,START),START),"A dark single occupied cell is temporary refuge, not a general lit base");
        check(done.why().contains("not a permanent"),"Dark-refuge limitations are stated honestly");
        check(!session.continuationGrace(START,0,false),"Completing shelter does not automatically release nighttime guidance while inside");
        check(session.continuationGrace(START.offset(2,0,0),1_000,false),"Deliberately leaving a completed shelter can restore the unfinished goal temporarily");
        check(!session.continuationGrace(START.offset(2,0,0),1_001,true),"A new hostile threat overrides departure grace");
        check(!session.continuationGrace(START.offset(2,0,0),122_000,false),"Departure grace expires instead of permanently disabling night safety");
        session.preference(Strategy.HILLSIDE);
        check(session.exitStep(w,START,false)!=null,"Changing preference inside completed cover does not discard the known exit");
        var rejoined=new Session();rejoined.observeProtected(w,START);
        check(rejoined.exitStep(w,START,false)!=null,"A rejoined single-cell refuge recovers a checked short exit from actual world blocks");
        var exit=session.exitStep(w,START,false);
        check(exit!=null && exit.exit() && exit.action()==Action.MINE && exit.target().equals(START.offset(0,1,1)),"Daylight starts by reopening the exact upper entrance block");
        check(session.exitStep(w,START,true)==null,"A nearby threat prevents daylight exit digging");
        w.blocks.put(exit.target(),AIR);exit=session.exitStep(w,START,false);
        check(exit.step()==2 && exit.target().equals(START.offset(0,0,1)),"Exit mining advances only after actual upper-block removal");
        w.blocks.put(exit.target(),AIR);exit=session.exitStep(w,START,false);
        check(exit.action()==Action.ENTER && exit.step()==3,"The doorway is checked before a short walk-out instruction");
        check(session.exitStep(w,exit.target(),false)==null,"Once outside, exit guidance releases the previous goal");
        w.blocks.put(START.offset(0,0,-1),AIR);
        check(session.next(w,START,stock(1),false,true).action()!=Action.COMPLETE,"Removing a wall invalidates completion even after previous success");
    }
    private static void hillExecution(){
        Terrain w=new Terrain().hill(DIRT);Session session=new Session();session.preference(Strategy.HILLSIDE);int blocks=2;Cell actor=START;
        var head=session.next(w,actor,stock(blocks),false,true);
        check(head.action()==Action.MINE && head.target().equals(START.offset(1,1,0)),"Hillside starts with the marked head block, never digging straight down");
        check(session.next(w,actor,stock(blocks),false,true).target().equals(head.target()),"Mining instruction waits for actual removal");
        w.blocks.put(head.target(),AIR);
        var feet=session.next(w,actor,stock(blocks),false,true);
        check(feet.action()==Action.MINE && feet.target().equals(START.offset(1,0,0)),"Only verified head removal advances to the feet block");
        w.blocks.put(feet.target(),AIR);
        var enter=session.next(w,actor,stock(blocks),false,true);
        check(enter.action()==Action.ENTER && enter.target().equals(START.offset(1,0,0)),"The player is told to enter before any entrance sealing");
        check(session.next(w,actor,stock(blocks),false,true).action()==Action.ENTER,"No entrance is sealed while the player remains outside");
        actor=enter.target();
        var low=session.next(w,actor,stock(blocks),false,true);
        check(low.action()==Action.PLACE && low.target().equals(START),"After entry the lower entrance position is exact and outside the player's body");
        w.blocks.put(low.target(),STONE);blocks--;
        var high=session.next(w,actor,stock(blocks),false,true);
        check(high.action()==Action.PLACE && high.target().equals(START.above()),"The upper entrance waits for observed lower support");
        w.blocks.put(high.target(),DIRT);blocks--;
        check(session.next(w,actor,stock(blocks),false,true).action()==Action.COMPLETE,"A hillside refuge is checked after both real sealing blocks");
    }
    private static void replanningAndHazards(){
        Terrain w=new Terrain();Session s=new Session();var step=s.next(w,START,stock(10),false,true);
        w.blocks.put(step.target(),STONE);
        check(!s.next(w,START,stock(9),false,true).target().equals(step.target()),"A different safe solid material advances instead of insisting on dirt");
        w.blocks.put(START.offset(2,0,1),WATER);
        check(s.next(w,START,stock(9),false,true).action()==Action.BLOCKED,"New nearby fluid invalidates a retained plan");
        w=new Terrain();w.blocks.put(START.offset(0,-1,1),AIR);
        check(freestanding(w,START)==null,"Drops beneath the building perimeter are rejected");
        w=new Terrain();w.blocks.put(START.offset(1,2,0),FALLING);
        check(freestanding(w,START)==null,"Unstable falling terrain is rejected");
        w=new Terrain();w.blocks.put(START.offset(2,0,0),Voxel.UNKNOWN);
        check(freestanding(w,START)==null,"Unknown nearby terrain is not silently treated as safe air");
        w=new Terrain().hill(DIRT);w.blocks.put(START.offset(2,1,0),WATER);
        check(hillside(w,START,1,0,stock(2))==null,"Excavation next to water is rejected");
        w=new Terrain().hill(DIRT);w.blocks.put(START.offset(1,2,0),FALLING);
        check(hillside(w,START,1,0,stock(2))==null,"A falling hillside ceiling prevents excavation");
        w=new Terrain();w.placement=false;s=new Session();
        var denied=s.next(w,START,stock(10),false,true);
        check(denied.action()==Action.BLOCKED,"Placement-context/collision rejection blocks a proposed instruction");
        check(denied.target().equals(START),"A blocked placement points to the actual clear standing cell rather than an absent marker");
        w=new Terrain();s=new Session();var plan=freestanding(w,START);
        for(var op:plan.operations().subList(0,8))w.blocks.put(op.target(),DIRT);
        var cap=s.next(w,START,stock(2),false,true);
        check(cap.action()==Action.JUMP_PLACE,"Partially completed walls are reused rather than restarted");
        w.blocks.put(cap.target(),DIRT);
        check(s.next(w,START,stock(1),false,false).action()==Action.WAIT,"The roof is not placed through a jumping player's head");
        // An alternate roof can complete the room without the planned extra roof support.
        w=new Terrain();s=new Session();s.next(w,START,stock(10),false,true);
        for(var op:plan.operations().subList(0,8))w.blocks.put(op.target(),STONE);
        w.blocks.put(START.offset(0,2,0),STONE);
        check(s.next(w,START,stock(0),false,true).action()==Action.COMPLETE,"A safe alternate enclosure is accepted instead of blindly following the original operations");
        Terrain large=new Terrain();large.light=10;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            large.blocks.put(new Cell(x,3,z),STONE);
            if(Math.abs(x)==2 || Math.abs(z)==2){large.blocks.put(new Cell(x,1,z),STONE);large.blocks.put(new Cell(x,2,z),STONE);}
        }
        s=new Session();s.next(new Terrain(),START,stock(10),false,true);
        check(s.next(large,START,stock(0),false,true).action()==Action.COMPLETE,"A different larger lit shelter is accepted without forcing the original compact walls");
        large.light=0;
        check(!occupiedCover(ShelterCheck.inspect(large,START),START),"A dark room with spare standing/spawning space is not treated as the occupied single-cell refuge");
    }
    private static ProgressionTasks.Snapshot snapshot(){return new ProgressionTasks.Snapshot(Map.of("minecraft:ender_eye",12),"minecraft:overworld",
            5,20,8,15,32,true,false,false,false,-1,true,Set.of(),false,false,false,-1,false,false,"minecraft:plains");}
    private static SurvivalMonitor.Observation observed(Environment e,float health,int hunger){
        return new SurvivalMonitor.Observation(health,hunger,8,64,15,32,Map.of(),SurvivalMonitor.Hazard.NONE,false,true,true,"minecraft:overworld",e);
    }
    private static void lifecycle()throws Exception{
        Terrain w=new Terrain();var session=new Session();var build=session.next(w,START,stock(10),false,true);
        var shelter=new ShelterAdvice(build.text(),build.why(),build.target(),List.of(),null,"",0,build);
        var env=new Environment(TimeBand.NIGHT,false,null,shelter,null,10);var goal=new GoalSelection();goal.start("dragon");goal.preferStep("stronghold");
        var monitor=new SurvivalMonitor();
        check(monitor.update(goal,snapshot(),observed(env,5,20),0).kind()==SurvivalMonitor.Kind.SHELTER,"A funded short refuge can supersede generic rest-in-shelter advice for an exposed low-health player");
        check(goal.selected().equals("dragon") && goal.preferredStep().equals("stronghold"),"Construction never changes the original goal or its unfinished step");
        var hostile=threat(new Mob("z","Zombie",Disposition.HOSTILE,4,true,true,false,false,FoodAnimal.NONE,"ahead"),false);
        var hidden=threat(new Mob("hidden","Zombie",Disposition.HOSTILE,6,false,false,false,false,FoodAnimal.NONE,"behind you"),false);
        check(hidden!=null && !hidden.immediate(),"A close known hidden hostile still prevents exposed construction without pretending an attack is confirmed");
        var danger=new Environment(TimeBand.NIGHT,false,hostile,shelter,null,10);
        check(monitor.update(goal,snapshot(),observed(danger,5,20),250).critical(),"An immediate mob still takes priority over low-health shelter construction");
        var retreat=new ShelterAdvice("Zombie nearby. Move away toward cover.","Construction must wait.",null,List.of(),null);
        var hiddenEnvironment=new Environment(TimeBand.NIGHT,false,hidden,retreat,null,10);
        var routine=new SurvivalMonitor();routine.update(goal,snapshot(),observed(env,20,20),0);
        var busy=new SurvivalMonitor.Observation(20,20,8,64,15,32,Map.of(),SurvivalMonitor.Hazard.NONE,true,true,true,"minecraft:overworld",hiddenEnvironment);
        check(routine.update(goal,snapshot(),busy,250).advice().instruction().contains("Move away"),"A threat replaces construction advice during mining instead of retaining the old build instruction");
        var blocked=new Instruction(Action.BLOCKED,null,null,false,0,0,"Eat your carried food on this checked level ground; do not go out for blocks.","Not enough blocks.",Strategy.AUTO);
        var poor=new Environment(TimeBand.NIGHT,false,null,new ShelterAdvice(blocked.text(),blocked.why(),null,List.of(),null,"",0,blocked),null,2);
        monitor.reset();check(monitor.update(goal,snapshot(),observed(poor,5,12),0).advice().instruction().contains("do not go out"),"Critical low-health fallback preserves the specific safe alternative instead of generic rest-in-an-unknown-shelter text");
        goal.pause(true);
        check(monitor.update(goal,snapshot(),observed(env,5,20),500)==null,"Pausing stops shelter instructions");
        goal.pause(false);goal.requestCancel();goal.confirmCancel(true);
        check(monitor.update(goal,snapshot(),observed(env,5,20),750)==null,"Cancellation prevents shelter auto-resume");
        goal.start("dragon");goal.preferStep("stronghold");monitor.reset();
        var complete=new Instruction(Action.COMPLETE,null,null,false,10,10,"Wait inside until daylight.","Your main goal is saved.",Strategy.FREESTANDING);
        var wait=new Environment(TimeBand.NIGHT,true,null,new ShelterAdvice(complete.text(),complete.why(),null,List.of(),null,"",0,complete),null,0);
        check(monitor.update(goal,snapshot(),observed(wait,20,20),0).kind()==SurvivalMonitor.Kind.SHELTER,"Verified completion holds nighttime shelter instead of resuming outdoor resource gathering");
        monitor.update(goal,snapshot(),observed(wait,20,12),1_000);
        check(monitor.update(goal,snapshot(),observed(wait,20,12),3_000).kind()==SurvivalMonitor.Kind.EAT,"Eating can interrupt shelter waiting without sending the player outside");
        var quiet=Environment.quiet();monitor.update(goal,snapshot(),observed(quiet,20,20),12_000);
        check(AdaptiveRecommendations.next(goal,snapshot(),true).taskId().equals("stronghold"),"Daylight can return guidance to the exact unfinished goal");
        var nav=new TaskMenuModel();nav.open(TaskMenuModel.Page.SHELTER);nav.back();
        check(nav.page()==TaskMenuModel.Page.HOME,"Shelter strategy controls retain predictable Back navigation");
        Path dir=Files.createTempDirectory("emergency-shelter-save-");Path file=dir.resolve("goals.properties");
        try{
            goal.shelterStrategy(Strategy.HILLSIDE);goal.survivalResumeStep("stronghold");goal.save(file);var loaded=GoalSelection.load(file);
            check(loaded.shelterStrategy()==Strategy.HILLSIDE && loaded.selected().equals("dragon") && loaded.survivalResumeStep().equals("stronghold"),"Strategy preference persists without replacing goal or progress");
            Files.writeString(file,"format=accessibility-goals-v1\nselected=stone_axe\npaused.stone_axe=true\nreminders=true\n");loaded=GoalSelection.load(file);
            check(loaded.shelterStrategy()==Strategy.AUTO && loaded.paused() && loaded.reminders(),"Original saves preserve preferences with a compatible AUTO strategy default");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
