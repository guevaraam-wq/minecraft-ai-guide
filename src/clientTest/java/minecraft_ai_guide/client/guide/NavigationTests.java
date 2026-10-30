package minecraft_ai_guide.client.guide;

import java.util.*;
import static minecraft_ai_guide.client.guide.NavigationPlanner.*;

/** Synthetic loaded-world fixtures exercise the production route planner, without game actions. */
public final class NavigationTests {
    private static int checks;
    private static final Voxel AIR = new Voxel("air", true, true, false, false, false, false);
    private static final Voxel ROCK = new Voxel("stone", true, false, true, false, true, false);
    private static final Voxel WALL = new Voxel("protected", true, false, true, false, false, false);
    private static final Voxel LAVA = new Voxel("lava", true, true, false, true, false, false);
    private static final Voxel GRAVEL = new Voxel("gravel", true, false, true, false, false, true);
    private static class Grid implements World {
        final Map<Cell, Voxel> cells = new HashMap<>();
        public Voxel voxel(Cell p) {
            if (Math.abs(p.x()) > 10 || Math.abs(p.z()) > 10 || p.y() < -5 || p.y() > 12) return Voxel.UNKNOWN;
            return cells.getOrDefault(p, p.y() <= 0 ? WALL : AIR);
        }
        Grid set(int x, int y, int z, Voxel v) { cells.put(new Cell(x, y, z), v); return this; }
    }
    private static Cell c(int x, int y, int z) { return new Cell(x,y,z); }
    private static Route plan(Grid w, Cell start, Cell target, boolean dig) { return NavigationPlanner.plan(w, start, target, Goal.HARVEST, dig); }

    public static void run() {
        elevation(); excavation(); hazards(); recalculation(); continuation(); markers();
        System.out.println("Navigation regression checks passed: " + checks);
    }
    private static void elevation() {
        Grid flat = new Grid().set(6,1,0,ROCK);
        var route = plan(flat,c(0,1,0),c(6,1,0),false);
        check(route.status() == Status.REACHABLE_NOW && route.stages().size() > 1, "Flat route reaches a real interaction approach");
        check(next(flat,route,c(0,1,0)).action() == Action.WALK, "Next walking action has its own marker");
        check(route.stages().stream().allMatch(s -> s.removeBefore().isEmpty()), "Walking route never invents excavation");
        Grid mountain = new Grid();
        for (int x=-5;x<=8;x++) for(int z=-5;z<=5;z++) for(int y=1;y<=Math.max(0,4-x);y++) mountain.set(x,y,z,WALL);
        mountain.set(6,1,0,ROCK);
        route = plan(mountain,c(0,5,0),c(6,1,0),false);
        check(route.status() == Status.REACHABLE_NOW, "Stone below a mountain can be reached by a real descending approach");
        check(route.stages().getLast().feet().y() < 5, "Vertical proximity is not confused with arrival");
        for(int i=1;i<route.stages().size();i++) check(Math.abs(route.stages().get(i).feet().y()-route.stages().get(i-1).feet().y())<=1,
                "Every elevation change is at most one block");
        Grid ascent = new Grid();
        for(int x=1;x<=8;x++) for(int z=-5;z<=5;z++) for(int y=1;y<=Math.min(x,3);y++) ascent.set(x,y,z,WALL);
        ascent.set(6,4,0,ROCK);
        route = plan(ascent,c(0,1,0),c(6,4,0),false);
        check(route.status()==Status.REACHABLE_NOW && route.stages().stream().anyMatch(s->s.feet().y()>1), "One-block ascent is supported");
        Grid cliff = new Grid();
        for(int x=-5;x<=0;x++) for(int z=-5;z<=5;z++) for(int y=1;y<=5;y++) cliff.set(x,y,z,WALL);
        cliff.set(6,1,0,ROCK);
        check(plan(cliff,c(0,6,0),c(6,1,0),true).status()==Status.INACCESSIBLE, "Dangerous unguarded drop is refused");
        Grid underfoot = new Grid().set(0,0,0,ROCK);
        route=plan(underfoot,c(0,1,0),c(0,0,0),false);
        check(route.status()==Status.REACHABLE_NOW && route.stages().size()>1, "Resource directly below requires stepping aside first");
        check(!safeRemoval(underfoot,c(0,1,0),c(0,0,0),Set.of()), "Never remove the player's supporting block");
        Grid ceiling=corridor().set(2,1,0,WALL).set(2,2,0,WALL);
        check(plan(ceiling,c(0,1,0),c(7,1,0),true).status()==Status.INACCESSIBLE, "Low ceiling and protected barrier cannot be jumped or mined through");
    }
    private static Grid corridor() {
        Grid w=new Grid();
        for(int x=-10;x<=10;x++) {
            for(int y=1;y<=5;y++) {w.set(x,y,-1,WALL); w.set(x,y,1,WALL);}
            w.set(x,3,0,WALL);
        }
        w.set(7,1,0,ROCK);
        return w;
    }
    private static void excavation() {
        Grid w=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK);
        Cell start=c(0,1,0),target=c(7,1,0);
        check(plan(w,start,target,false).status()==Status.INACCESSIBLE, "A solid barrier is not a currently walkable route");
        Map<Cell,Voxel> original=Map.copyOf(w.cells);
        var route=plan(w,start,target,true);
        check(route.status()==Status.AFTER_EXCAVATION, "A small removable barrier is classified separately");
        check(w.cells.equals(original), "Planning never changes terrain");
        var instruction=next(w,route,start);
        check(instruction.action()==Action.WALK && instruction.marker().equals(c(1,1,0)), "Walk to a safe staging position before mining");
        check(instruction.walkPath().getLast().equals(c(1,1,0)), "Visible path stops before the unmined barrier");
        start=c(1,1,0); route=plan(w,start,target,true); instruction=next(w,route,start);
        check(instruction.action()==Action.MINE && instruction.marker().equals(c(2,2,0)), "Only the exact first visible clearance block is requested");
        check(instruction.walkPath().equals(List.of(start)), "No path is drawn through a solid barrier");
        w.set(2,2,0,new Voxel("dirt",true,false,true,false,true,false));
        route=plan(w,start,target,true);
        check(next(w,route,start).marker().equals(c(2,2,0)), "Replacing a block is not counted as removal");
        w.set(2,2,0,AIR);
        check(!route.valid(w,start) && next(w,route,start).action()==Action.BLOCKED, "Changed terrain invalidates a stale excavation instruction");
        route=plan(w,start,target,true); instruction=next(w,route,start);
        check(instruction.action()==Action.MINE && instruction.marker().equals(c(2,1,0)), "Verified removal advances to the next block, not both at once");
        w.set(2,1,0,AIR);
        route=plan(w,start,target,true);
        check(route.status()==Status.REACHABLE_NOW && next(w,route,start).action()==Action.WALK, "Cleared terrain changes the route to walking");
        Grid hill=new Grid();
        for(int x=-5;x<=1;x++) for(int y=1;y<=3;y++) hill.set(x,y,0,WALL);
        for(int x=-10;x<=10;x++) for(int y=1;y<=8;y++){ hill.set(x,y,-1,WALL); hill.set(x,y,1,WALL); }
        for(int x=2;x<=4;x++) for(int y=1;y<=7-x;y++) hill.set(x,y,0,ROCK);
        hill.set(7,1,0,ROCK);
        route=plan(hill,c(1,4,0),c(7,1,0),true);
        check(route.status()==Status.AFTER_EXCAVATION, "A short checked hillside cutting route is recognized");
        check(route.stages().stream().anyMatch(s->s.feet().y()<4), "Hillside cutting actually descends toward the resource");
        check(route.stages().stream().mapToInt(s->s.removeBefore().size()).sum()<=6, "Excavation stays within the few-block budget");
    }
    private static void hazards() {
        Cell start=c(0,1,0),target=c(7,1,0);
        Grid fluid=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK).set(3,2,0,LAVA);
        check(plan(fluid,start,target,true).status()==Status.INACCESSIBLE, "Never open a barrier adjacent to lava or route into it");
        Grid gravel=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK).set(2,3,0,GRAVEL);
        check(plan(gravel,start,target,true).status()==Status.INACCESSIBLE, "Never excavate below falling blocks");
        Grid unknown=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK).set(2,2,1,Voxel.UNKNOWN);
        check(plan(unknown,start,target,true).status()==Status.INACCESSIBLE, "Unknown terrain beside excavation is refused");
        Grid depth=corridor();
        for(int x=2;x<=5;x++){depth.set(x,1,0,ROCK);depth.set(x,2,0,ROCK);}
        check(plan(depth,start,target,true).status()==Status.INACCESSIBLE, "Excessive tunneling is refused");
        Grid wet=new Grid().set(0,0,0,LAVA);
        check(!standing(wet,c(0,1,0),Set.of()), "Liquid or hazardous support is never valid footing");
        Grid slab=new Grid().set(0,0,0,new Voxel("slab",true,false,false,false,false,false));
        check(!standing(slab,c(0,1,0),Set.of()), "Unsupported partial collision shapes are not assumed full safe ground");
        Grid adjacency=new Grid().set(1,1,0,LAVA);
        check(!standing(adjacency,c(0,1,0),Set.of()), "A damaging neighboring cell is not treated as a safe standing approach");
        Grid exposedDrop=new Grid().set(1,0,0,ROCK).set(1,-1,0,AIR).set(1,-2,0,AIR);
        check(standing(exposedDrop,c(0,1,0),Set.of())
                && !safeRemoval(exposedDrop,c(0,1,0),c(1,0,0),Set.of()),
                "Mining a neighboring resource must not expose a dangerous drop beside the standing player");
        var bounded=new Grid(){public boolean allowSearch(boolean excavation){return false;}};
        bounded.set(7,1,0,ROCK);
        check(plan(bounded,start,target,true).status()==Status.INACCESSIBLE, "Exhausted search budget gives an explanation, not an invented route");
    }
    private static void recalculation() {
        Grid w=new Grid().set(7,1,0,ROCK); Cell start=c(0,1,0),target=c(7,1,0);
        var route=plan(w,start,target,false);
        check(route.valid(w,start), "Unchanged route snapshot remains valid");
        Cell moved=route.stages().get(1).feet();
        check(!route.valid(w,moved), "Player movement immediately requires directions from the new position");
        var replanned=plan(w,moved,target,false);
        check(replanned.stages().getFirst().feet().equals(moved), "Replanning uses current position, including elevation");
        Cell nextFloor=route.stages().get(1).feet().below();
        w.cells.put(nextFloor,LAVA);
        check(!route.valid(w,start), "New hazard on the route invalidates the old direction");
        check(next(w,route,start).action()==Action.BLOCKED, "Stale navigation never tells the player to continue into a new hazard");
        w.cells.put(nextFloor,WALL); w.set(7,1,0,AIR);
        check(!route.valid(w,start), "Destination removal also invalidates the resource approach");
        Grid ledge=new Grid().set(7,1,0,ROCK).set(0,0,1,AIR);
        var edgeRoute=plan(ledge,start,target,false);
        check(edgeRoute.status()==Status.REACHABLE_NOW, "A guarded one-block side step is not confused with a dangerous drop");
        ledge.set(0,-1,1,AIR);
        check(!edgeRoute.valid(ledge,start), "Removing deeper side footing immediately invalidates the route");
    }
    private static void continuation() {
        Grid original = corridor().set(2,1,0,ROCK).set(2,2,0,ROCK);
        Cell start = c(0,1,0), target = c(7,1,0), staging = c(1,1,0);
        Route planned = plan(original,start,target,true);
        check(planned.status()==Status.AFTER_EXCAVATION, "Regression fixture starts with an available excavation plan");
        var budgetCalls = new int[1];
        Grid exhausted = new Grid(){public boolean allowSearch(boolean excavation){budgetCalls[0]++; return false;}};
        exhausted.cells.putAll(original.cells);
        check(plan(exhausted,staging,target,true).status()==Status.INACCESSIBLE,
                "Reproduces the old failure: a fresh search after movement can exhaust its budget");
        int before = budgetCalls[0];
        Route continued = NavigationPlanner.resume(exhausted,planned,staging,Goal.HARVEST);
        check(continued.status()==Status.AFTER_EXCAVATION && continued.target().equals(target),
                "Approaching a clearance block preserves the same destination and excavation plan");
        check(budgetCalls[0]==before, "Continuation validates the existing route without any new search budget");
        check(next(exhausted,continued,staging).action()==Action.MINE
                && next(exhausted,continued,staging).marker().equals(c(2,2,0)),
                "Reaching the staging block switches walking directly to the exact mining action");
        Route repeated = NavigationPlanner.resume(exhausted,continued,staging,Goal.HARVEST);
        check(repeated.stages().equals(continued.stages()), "Repeated updates do not change an unchanged remaining plan");
        exhausted.set(2,2,0,AIR);
        continued = NavigationPlanner.resume(exhausted,continued,staging,Goal.HARVEST);
        check(continued.status()==Status.AFTER_EXCAVATION
                && next(exhausted,continued,staging).marker().equals(c(2,1,0)),
                "One verified removal advances without a fresh path search");
        exhausted.set(2,1,0,AIR);
        continued = NavigationPlanner.resume(exhausted,continued,staging,Goal.HARVEST);
        check(continued.status()==Status.REACHABLE_NOW && next(exhausted,continued,staging).action()==Action.WALK,
                "A cleared route transitions back to walking without losing the destination");
        Cell player = staging;
        for(int i=0;i<15;i++) {
            continued = NavigationPlanner.resume(exhausted,continued,player,Goal.HARVEST);
            var action = next(exhausted,continued,player);
            if(action.action()==Action.ARRIVED) break;
            check(action.action()==Action.WALK, "Every retained open-route action remains a walking step");
            player = action.marker();
        }
        check(next(exhausted,continued,player).action()==Action.ARRIVED && continued.target().equals(target),
                "Continuation reaches the resource interaction approach");

        Grid hill=new Grid();
        for(int x=-5;x<=1;x++) for(int y=1;y<=3;y++) hill.set(x,y,0,WALL);
        for(int x=-10;x<=10;x++) for(int y=1;y<=8;y++){ hill.set(x,y,-1,WALL); hill.set(x,y,1,WALL); }
        for(int x=2;x<=4;x++) for(int y=1;y<=7-x;y++) hill.set(x,y,0,ROCK);
        hill.set(7,1,0,ROCK);
        Route descent=plan(hill,c(1,4,0),target,true);
        Grid descentBudget = new Grid(){public boolean allowSearch(boolean excavation){return false;}};
        descentBudget.cells.putAll(hill.cells);
        player=c(1,4,0); boolean wentDown=false,arrived=false; int cuts=0;
        for(int i=0;i<30;i++) {
            descent=NavigationPlanner.resume(descentBudget,descent,player,Goal.HARVEST);
            check(descent.status()!=Status.INACCESSIBLE, "Mountain descent keeps its checked plan on every approach update");
            var action=next(descentBudget,descent,player);
            if(action.action()==Action.ARRIVED){arrived=true;break;}
            if(action.action()==Action.MINE){
                check(safeRemoval(descentBudget,player,action.marker(),Set.of()), "Every continued excavation still passes unchanged safety checks");
                descentBudget.cells.put(action.marker(),AIR); cuts++;
            } else {
                check(action.action()==Action.WALK, "A descending transition is walking, not an inaccessible-route report");
                wentDown |= action.marker().y()<player.y(); player=action.marker();
            }
        }
        check(arrived && wentDown && cuts>0 && cuts<=6, "Reproduced hillside cut reaches lower stone with bounded player-performed removal");

        Grid danger=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK);
        Route safe=plan(danger,start,target,true);
        danger.set(3,2,0,LAVA);
        check(NavigationPlanner.resume(danger,safe,staging,Goal.HARVEST).status()==Status.INACCESSIBLE,
                "Preserving a plan never ignores newly exposed lava");
        Grid tooFar=new Grid(){public double reach(){return 1;}};
        tooFar.cells.putAll(original.cells);
        check(NavigationPlanner.resume(tooFar,planned,staging,Goal.HARVEST).status()==Status.INACCESSIBLE,
                "Continuation does not weaken real interaction range or excavation reach");
        Grid changed=corridor().set(2,1,0,ROCK).set(2,2,0,ROCK);
        Route unchanged=plan(changed,start,target,true); changed.set(7,1,0,AIR);
        check(NavigationPlanner.resume(changed,unchanged,staging,Goal.HARVEST).reason().contains("destination"),
                "Removed destination produces a reason rather than stale mining directions");
        Grid flat=new Grid().set(9,1,0,ROCK);
        Route walking=plan(flat,start,c(9,1,0),false);
        Cell progressed=walking.stages().get(Math.min(3,walking.stages().size()-1)).feet();
        flat.set(0,0,0,LAVA);
        check(NavigationPlanner.resume(flat,walking,progressed,Goal.HARVEST).status()==Status.REACHABLE_NOW,
                "A change far behind the player does not discard a still-safe remaining route");
    }

    private static void markers() {
        Cell step = c(5,64,-8), lower = c(5,63,-8);
        var marker = NavigationMarkers.footstep(step);
        check(marker.equals(NavigationMarkers.footstep(step)), "Identical footstep state has stable geometry, without particle drift or randomness");
        check(marker.origin().equals(step), "White marker is anchored to the next feet block, not the destination block");
        check(marker.boxes().stream().allMatch(b->b.minY()>0 && b.maxY()<.1), "Footstep geometry sits just above the ground");
        check(marker.boxes().stream().allMatch(b->b.minX()>=.2 && b.maxX()<=.8 && b.minZ()>=.2 && b.maxZ()<=.8),
                "Ground marker stays small and inside one walking cell");
        check(NavigationMarkers.footstep(lower).origin().y()==63, "Descending white marker uses the lower step elevation");
        Cell destination = c(12,40,-3);
        var beacon=NavigationMarkers.destination(destination,64);
        check(beacon.origin().equals(destination), "Cyan geometry stays on the intended resource even when it is far below the player");
        double top=beacon.boxes().stream().mapToDouble(NavigationMarkers.Box::maxY).max().orElseThrow();
        check(destination.y()+top>66, "Below-ground beacon extends above the player's viewing level");
        check(beacon.boxes().getFirst().minY()<0 && beacon.boxes().getFirst().maxY()>1,
                "Destination cube expands slightly around the resource to avoid surface z-fighting");
        check(beacon.boxes().get(1).minX()==.46 && beacon.boxes().get(1).maxX()==.54,
                "Destination pillar is centered on the target rather than on the next action");
        check(beacon.color()!=marker.color() && beacon.color()!=0xFF249CFF, "Destination, white footstep, and blue path are visually distinct");
        check(beacon.width()>=3 && marker.width()>=2.5F, "Geometric markers have readable line widths");
        var above=NavigationMarkers.destination(c(12,80,-3),64);
        check(above.origin().y()==80 && above.boxes().getLast().maxY()==3, "Above-player destination is not incorrectly moved down to player height");
    }

    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message); checks++;}
}
