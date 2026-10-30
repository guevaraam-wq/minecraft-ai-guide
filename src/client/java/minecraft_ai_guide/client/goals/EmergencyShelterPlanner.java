package minecraft_ai_guide.client.goals;

import java.util.*;
import minecraft_ai_guide.client.guide.NavigationPlanner;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;
import minecraft_ai_guide.client.guide.NavigationPlanner.Voxel;

/** Counterfactual design checks are read-only. Only observed world blocks advance a live plan. */
public final class EmergencyShelterPlanner {
    public enum Strategy { AUTO, FREESTANDING, HILLSIDE }
    public enum Action { PLACE, JUMP_PLACE, MINE, ENTER, WAIT, BLOCKED, COMPLETE }
    public interface World extends ShelterCheck.World {
        default boolean excavatable(Cell c){return voxel(c).removable();}
        default boolean exitRemovable(Cell c){return excavatable(c);}
        default boolean placementAllowed(Cell target,Cell support,boolean jump,Cell actor){return true;}
    }
    public record Supplies(int blocks,boolean usablePickaxe,boolean food,float health,int hunger){}
    public record Operation(Action action,Cell target,Cell support){}
    public record Plan(Strategy strategy,Cell center,Cell outside,List<Operation> operations){
        public Plan {operations=List.copyOf(operations);}
        public int materials(World w){return (int)operations.stream().filter(o->o.action()==Action.PLACE || o.action()==Action.JUMP_PLACE)
                .filter(o->!solid(w.voxel(o.target()))).count();}
    }
    public record Instruction(Action action,Cell target,Cell support,boolean jump,int step,int total,String text,String why,Strategy strategy,boolean exit){
        public Instruction(Action action,Cell target,Cell support,boolean jump,int step,int total,String text,String why,Strategy strategy){
            this(action,target,support,jump,step,total,text,why,strategy,false);
        }
    }
    public record Choice(Plan plan,String reason,int missing){}
    private static final int[][] SIDES={{0,-1},{1,0},{0,1},{-1,0}};
    private static final Voxel BUILT=new Voxel("planned ordinary solid block",true,false,true,false,false,false);
    private static final Voxel AIR=new Voxel("planned air",true,true,false,false,false,false);
    public static boolean solid(Voxel v){return v.known() && v.support() && !v.passable() && !v.hazard() && !v.falling();}
    public static boolean clear(Voxel v){return v.known() && v.passable() && !v.hazard() && !v.falling();}
    public static boolean occupiedCover(ShelterCheck.Result room,Cell actor){
        return (room.status()==ShelterCheck.Status.ENCLOSED_LIT || room.status()==ShelterCheck.Status.DARK)
                && room.interior().equals(Set.of(actor));
    }
    private static boolean floorAndBuffer(World w,Cell center){
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            if(!solid(w.voxel(center.offset(x,-1,z))))return false;
            for(int y=0;y<=4;y++){
                var v=w.voxel(center.offset(x,y,z));if(!v.known() || v.hazard() || v.falling())return false;
            }
        }return true;
    }
    public static Plan freestanding(World w,Cell actor){
        if(!floorAndBuffer(w,actor) || !clear(w.voxel(actor)) || !clear(w.voxel(actor.above())))return null;
        List<Operation> work=new ArrayList<>();
        for(int y=0;y<=1;y++)for(var side:SIDES){
            Cell target=actor.offset(side[0],y,side[1]);
            if(!solid(w.voxel(target)) && !clear(w.voxel(target)))return null;
            work.add(new Operation(Action.PLACE,target,target.below()));
        }
        Cell roof=actor.offset(0,2,0),cap=actor.offset(0,2,-1);
        if(!solid(w.voxel(roof))){
            if(!clear(w.voxel(roof)) || !clear(w.voxel(actor.offset(0,3,0))) || !clear(w.voxel(actor.offset(0,4,0))))return null;
            if(!solid(w.voxel(cap)) && !clear(w.voxel(cap)))return null;
            work.add(new Operation(Action.JUMP_PLACE,cap,cap.below()));
            work.add(new Operation(Action.PLACE,roof,cap));
        }
        return new Plan(Strategy.FREESTANDING,actor,actor.offset(0,0,1),work);
    }
    public static Plan hillside(World w,Cell actor,int dx,int dz,Supplies supplies){
        if(!floorAndBuffer(w,actor) || !clear(w.voxel(actor)) || !clear(w.voxel(actor.above())))return null;
        Cell inside=actor.offset(dx,0,dz);
        if(!floorAndBuffer(w,inside))return null;
        // A one-cell recess: solid sides/back/roof, and a stable floor. Never mine overhead or underfoot.
        for(var side:SIDES){
            Cell boundary=inside.offset(side[0],0,side[1]);if(boundary.equals(actor))continue;
            if(!solid(w.voxel(boundary)) || !solid(w.voxel(boundary.above())))return null;
        }
        if(!solid(w.voxel(inside.below())) || !solid(w.voxel(inside.offset(0,2,0))))return null;
        List<Operation> work=new ArrayList<>();
        for(Cell cut:List.of(inside.above(),inside)){
            var v=w.voxel(cut);
            if(!clear(v)){
                if(!solid(v) || !w.excavatable(cut))return null;
                // Low-health plans avoid slow rock mining. Soft ordinary terrain is still allowed.
                boolean soft=v.identity().contains("dirt") || v.identity().contains("grass_block");
                if(!soft && (!supplies.usablePickaxe() || supplies.health()<=8))return null;
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                    var near=w.voxel(cut.offset(x,y,z));if(!near.known() || near.hazard() || near.falling())return null;
                }
                work.add(new Operation(Action.MINE,cut,null));
            }
        }
        work.add(new Operation(Action.ENTER,inside,null));
        work.add(new Operation(Action.PLACE,actor,actor.below()));
        work.add(new Operation(Action.PLACE,actor.above(),actor));
        return new Plan(Strategy.HILLSIDE,inside,actor,work);
    }
    public static Choice choose(World w,Cell actor,Supplies supplies,Strategy preference,boolean threat){
        if(threat)return new Choice(null,"Move behind solid cover away from the mob; do not build while it is close.",0);
        if(!floorAndBuffer(w,actor))return new Choice(null,"Move back from the edge or hazardous terrain onto solid, level ground before building.",0);
        List<Plan> candidates=new ArrayList<>();Plan free=freestanding(w,actor);if(free!=null)candidates.add(free);
        for(var side:SIDES){Plan hill=hillside(w,actor,side[0],side[1],supplies);if(hill!=null)candidates.add(hill);}
        var feasible=candidates.stream().filter(p->p.materials(w)<=supplies.blocks()).sorted(
                Comparator.<Plan>comparingInt(p->preference==Strategy.AUTO || p.strategy()==preference?0:1)
                        .thenComparingDouble(p->p.materials(w)+p.operations().stream().filter(o->o.action()==Action.MINE).count()*3
                                +p.operations().stream().filter(o->o.action()==Action.JUMP_PLACE).count()*2)).toList();
        if(!feasible.isEmpty()){
            Plan selected=feasible.getFirst();
            String why=preference!=Strategy.AUTO && selected.strategy()!=preference
                    ?"The requested design is not feasible here; this checked alternative fits your blocks."
                    :"This nearby design fits the checked terrain and your carried blocks.";
            return new Choice(selected,why,0);
        }
        int needed=candidates.stream().mapToInt(p->p.materials(w)).min().orElse(10);
        int missing=Math.max(0,needed-supplies.blocks());
        boolean rock=false;for(var side:SIDES){var v=w.voxel(actor.offset(side[0],0,side[1]));rock|=solid(v) && (v.identity().contains("stone") || v.identity().contains("deepslate"));}
        String prerequisite=rock && !supplies.usablePickaxe()?" Rock excavation also needs a usable pickaxe; soft dirt is an alternative."
                :rock && supplies.health()<=8?" Rock digging is avoided while your health is this low.":"";
        String recovery=supplies.health()<=12?supplies.food() && supplies.hunger()<18?"Eat your carried food while staying on this level ground."
                :"Stay on this level ground while you recover; do not go out for materials.":"Keep close to solid cover and watch for mobs before seeking more blocks.";
        return new Choice(null,recovery+" The smallest checked design needs "+needed+" usable blocks here; you carry "+supplies.blocks()+". No funded hillside plan is verified."+prerequisite,missing);
    }
    private static boolean valid(World w,Plan p,Cell actor){
        if(!floorAndBuffer(w,p.center()))return false;
        if(p.strategy()==Strategy.HILLSIDE){
            if(!solid(w.voxel(p.center().offset(0,2,0))))return false;
            for(var side:SIDES){Cell boundary=p.center().offset(side[0],0,side[1]);if(boundary.equals(p.outside()))continue;
                if(!solid(w.voxel(boundary)) || !solid(w.voxel(boundary.above())))return false;
            }
        }
        if(Math.abs(actor.x()-p.center().x())>1 || Math.abs(actor.z()-p.center().z())>1 || Math.abs(actor.y()-p.center().y())>1)return false;
        Set<Cell> cuts=new HashSet<>();p.operations().stream().filter(o->o.action()==Action.MINE).forEach(o->cuts.add(o.target()));
        for(Cell body:List.of(p.center(),p.center().above()))if(!clear(w.voxel(body)) && !(cuts.contains(body) && w.excavatable(body)))return false;
        return true;
    }
    public static final class Session {
        private Strategy preference=Strategy.AUTO;
        private Plan plan;
        private boolean entered,completed;
        private String selectionNote="";
        private long leftAt=-1;
        public Plan plan(){return plan;}
        public void observeProtected(World w,Cell actor){
            if(plan==null || !actor.equals(plan.center())){
                plan=new Plan(Strategy.AUTO,actor,actor,List.of());
                // Rejoin recovery uses current geometry, never assumed construction history.
                if(occupiedCover(ShelterCheck.inspect(w,actor),actor))for(var side:SIDES){
                    Cell doorway=actor.offset(side[0],0,side[1]),beyond=actor.offset(side[0]*2,0,side[1]*2);
                    if(solid(w.voxel(doorway)) && solid(w.voxel(doorway.above())) && w.exitRemovable(doorway) && w.exitRemovable(doorway.above())
                            && solid(w.voxel(doorway.below())) && solid(w.voxel(beyond.below()))
                            && clear(w.voxel(beyond)) && clear(w.voxel(beyond.above()))){
                        plan=new Plan(Strategy.AUTO,actor,doorway,List.of(new Operation(Action.PLACE,doorway,doorway.below()),
                                new Operation(Action.PLACE,doorway.above(),doorway)));break;
                    }
                }
            }
            completed=true;entered=true;leftAt=-1;
        }
        public void reset(){plan=null;entered=false;completed=false;leftAt=-1;selectionNote="";}
        public void preference(Strategy value){if(preference!=value){preference=value;if(!completed)reset();}}
        public Strategy preference(){return preference;}
        public void another(){preference=plan!=null && plan.strategy()==Strategy.HILLSIDE?Strategy.FREESTANDING:Strategy.HILLSIDE;reset();}
        public boolean continuationGrace(Cell actor,long now,boolean threat){
            if(!completed || plan==null || threat)return false;
            if(actor.equals(plan.center())){leftAt=-1;return false;}
            if(leftAt<0)leftAt=now;return now-leftAt<120_000;
        }
        public Instruction next(World w,Cell actor,Supplies supplies,boolean threat,boolean grounded){
            if(threat)return blocked("Move away from the nearby mob toward solid cover; do not build yet.","Escape takes priority over construction.");
            var room=ShelterCheck.inspect(w,actor);
            if(room.status()==ShelterCheck.Status.ENCLOSED_LIT || occupiedCover(room,actor)){
                completed=true;if(plan==null)plan=new Plan(Strategy.FREESTANDING,actor,actor,List.of());
                return new Instruction(Action.COMPLETE,null,null,false,plan.operations().size(),plan.operations().size(),
                        "The enclosure is checked. Wait inside until daylight.",
                        room.status()==ShelterCheck.Status.DARK?"This dark, occupied refuge is temporary cover, not a permanent safe base.":"The floor, roof and entrance are enclosed.",plan.strategy());
            }
            if(plan==null || !valid(w,plan,actor)){
                var choice=choose(w,actor,supplies,preference,false);plan=choice.plan();entered=false;completed=false;leftAt=-1;
                selectionNote=preference!=Strategy.AUTO && plan!=null && plan.strategy()!=preference?choice.reason():"";
                if(plan==null)return blocked(choice.reason(),"A safe standing space and enough materials must be checked first.");
            }
            if(actor.equals(plan.center()))entered=true;
            // Count only real observations; safe alternative solid materials satisfy placements.
            int count=0;
            for(var op:plan.operations()){
                boolean done=switch(op.action()){
                    case MINE->clear(w.voxel(op.target()));case ENTER->entered;
                    case PLACE,JUMP_PLACE->solid(w.voxel(op.target()));default->false;
                };
                if(done){count++;continue;}
                if(plan.materials(w)>supplies.blocks())return blocked("Keep to checked ground; this plan now needs "+plan.materials(w)+" blocks, but you carry "+supplies.blocks()+".","Materials changed; the plan cannot be sealed yet.");
                int step=count+1,total=plan.operations().size();
                if(op.action()==Action.MINE){
                    if(!w.excavatable(op.target()) || op.target().equals(actor) || op.target().equals(actor.below())
                            || !NavigationPlanner.safeRemoval(w,actor,op.target(),Set.of()) || !NavigationPlanner.visible(w,actor,op.target(),Set.of()))
                        return blocked("Stay on the checked ground; that block cannot be safely reached or removed now.","The excavation check changed.");
                    return instruction(op,step,total,"Mine the outlined block.","This opens a two-block-high standing space.");
                }
                if(op.action()==Action.ENTER || !actor.equals(plan.center()) && !(op.action()==Action.JUMP_PLACE && actor.x()==plan.center().x() && actor.z()==plan.center().z())){
                    var route=NavigationPlanner.plan(w,actor,plan.center(),NavigationPlanner.Goal.WALK,false);
                    if(route.status()!=NavigationPlanner.Status.REACHABLE_NOW)return blocked("Stay on checked ground; the entrance route is blocked.","The standing space must be reachable before sealing.");
                    return new Instruction(Action.ENTER,plan.center(),null,false,step,total,"Move into the marked standing space.","Enter before closing the opening behind you.",plan.strategy());
                }
                if(!grounded && op.action()==Action.PLACE)return new Instruction(Action.WAIT,null,null,false,step,total,
                        "Land in the clear standing space before placing the next block.","Keep your head and feet clear.",plan.strategy());
                if(!clear(w.voxel(op.target())) || op.target().equals(actor) || op.target().equals(actor.above())
                        || !solid(w.voxel(op.support())) || !w.placementAllowed(op.target(),op.support(),op.action()==Action.JUMP_PLACE,actor))
                    return blocked("Stand in the marked clear space; this placement is not available yet.","The target, support, reach or another entity blocks placement.");
                return instruction(op,step,total,op.action()==Action.JUMP_PLACE?"Jump and place one block in the outlined space.":"Place one block in the outlined space.",
                        op.action()==Action.JUMP_PLACE?"This support will hold the roof.":op.target().equals(plan.center().offset(0,2,0))?"This finishes the roof.":"This closes a shelter wall.");
            }
            return blocked("Wait inside while the enclosure is checked again.","Completion depends on actual floor, wall, roof and threat evidence.");
        }
        /** Reopen only the known planned entrance, never floors/ceilings or arbitrary terrain. */
        public Instruction exitStep(World w,Cell actor,boolean threat){
            if(plan==null || !completed || plan.operations().isEmpty() || threat || !actor.equals(plan.center()))return null;
            Cell doorway=plan.outside();
            if(!solid(w.voxel(actor.below())) || !solid(w.voxel(doorway.below())))
                return new Instruction(Action.BLOCKED,null,null,false,0,3,"Stay inside; the exit floor is no longer checked.","Do not step into an unknown drop.",plan.strategy(),true);
            int step=1;
            for(Cell cut:List.of(doorway.above(),doorway)){
                if(clear(w.voxel(cut))){step++;continue;}
                boolean plannedEntrance=plan.operations().stream().anyMatch(o->o.target().equals(cut) && o.action()==Action.PLACE);
                if(!plannedEntrance || !w.exitRemovable(cut) || !NavigationPlanner.safeRemoval(w,actor,cut,Set.of())
                        || !NavigationPlanner.visible(w,actor,cut,Set.of()))
                    return new Instruction(Action.BLOCKED,null,null,false,step,3,"Stay inside; this exit block cannot be safely removed now.","The exit check changed.",plan.strategy(),true);
                return new Instruction(Action.MINE,cut,null,false,step,3,"Mine the outlined exit block.","Daylight has returned. Reopen the entrance before continuing your goal.",plan.strategy(),true);
            }
            var route=NavigationPlanner.plan(w,actor,doorway,NavigationPlanner.Goal.WALK,false);
            if(route.status()!=NavigationPlanner.Status.REACHABLE_NOW)
                return new Instruction(Action.BLOCKED,null,null,false,3,3,"Stay inside; the way out is blocked or unsafe.","The exit route must be checked first.",plan.strategy(),true);
            return new Instruction(Action.ENTER,doorway,null,false,3,3,"Walk into the marked doorway to continue your goal.","The entrance and floor are checked again.",plan.strategy(),true);
        }
        private Instruction instruction(Operation op,int step,int total,String text,String why){return new Instruction(op.action(),op.target(),op.support(),op.action()==Action.JUMP_PLACE,step,total,text,selectionNote.isEmpty()?why:selectionNote,plan.strategy());}
        private Instruction blocked(String text,String why){return new Instruction(Action.BLOCKED,plan==null?null:plan.center(),null,false,0,plan==null?0:plan.operations().size(),text,why,plan==null?preference:plan.strategy());}
    }
    private EmergencyShelterPlanner(){}
}
