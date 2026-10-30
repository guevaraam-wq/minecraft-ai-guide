package minecraft_ai_guide.client.goals;

import java.util.*;
import minecraft_ai_guide.client.guide.NavigationPlanner;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;
import minecraft_ai_guide.client.guide.NavigationPlanner.Voxel;

/** Bounded flat, two-block-high room checks. Partial/unknown structures never become verified shelter. */
public final class ShelterCheck {
    public interface World extends NavigationPlanner.World {int blockLight(Cell cell);default boolean closedEntrance(Cell cell){return false;}default boolean bedCell(Cell cell){return false;}default boolean cookingFixture(Cell cell){return false;}}
    public enum Status { ENCLOSED_LIT, CLOSE_GAP, DARK, EXPOSED, UNKNOWN, HAZARD }
    public record Result(Status status,Set<Cell> interior,List<Cell> gaps,String explanation){
        public Result {interior=Set.copyOf(interior);gaps=List.copyOf(gaps);}
    }
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    private static boolean wall(Voxel v){return v.known() && v.support() && !v.passable() && !v.hazard() && !v.falling();}
    private static boolean air(Voxel v){return v.known() && v.passable() && !v.hazard() && !v.falling();}
    public static Result inspect(World world,Cell start){
        Set<Cell> room=new LinkedHashSet<>();Set<Cell> gaps=new TreeSet<>(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        ArrayDeque<Cell> queue=new ArrayDeque<>();queue.add(start);boolean dark=false;
        while(!queue.isEmpty()){
            Cell cell=queue.remove();if(!room.add(cell))continue;
            if(room.size()>25 || Math.abs(cell.x()-start.x())>4 || Math.abs(cell.z()-start.z())>4)return result(Status.EXPOSED,room,gaps,"The covered area is too large to verify with this local check.");
            var feet=world.voxel(cell);var head=world.voxel(cell.above());var floor=world.voxel(cell.below());var roof=world.voxel(cell.offset(0,2,0));
            if(!feet.known() || !head.known() || !floor.known() || !roof.known())return result(Status.UNKNOWN,room,gaps,"Part of the room is not loaded or verified.");
            if(feet.hazard() || head.hazard() || floor.hazard() || roof.hazard() || floor.falling() || roof.falling())return result(Status.HAZARD,room,gaps,"The room has a hazard or unstable floor/roof.");
            if(!(air(feet) || world.bedCell(cell) || world.cookingFixture(cell)) || !air(head) || !wall(floor) || !wall(roof))return result(Status.EXPOSED,room,gaps,"A solid floor, roof and clear standing space are not verified.");
            dark|=world.blockLight(cell)<8;
            for(var side:SIDES){
                Cell next=cell.offset(side[0],0,side[1]);var low=world.voxel(next);var high=world.voxel(next.above());
                if(!low.known() || !high.known())return result(Status.UNKNOWN,room,gaps,"A boundary is not fully loaded.");
                if(low.hazard() || high.hazard() || low.falling() || high.falling())return result(Status.HAZARD,room,gaps,"A boundary has a hazard or falling block.");
                if((wall(low) || world.closedEntrance(next)) && (wall(high) || world.closedEntrance(next.above())))continue;
                if((air(low) || world.bedCell(next) || world.cookingFixture(next)) && air(high) && wall(world.voxel(next.offset(0,2,0)))){queue.add(next);continue;}
                // At most one two-block entrance or one window can be sealed. Never propose excavation.
                if(!(air(low) || wall(low)) || !(air(high) || wall(high)))return result(Status.EXPOSED,room,gaps,"This entrance or block shape cannot be checked reliably.");
                var outsideFloor=world.voxel(next.below());var outsideRoof=world.voxel(next.offset(0,2,0));
                if(!wall(outsideFloor) || !outsideRoof.known() || outsideRoof.hazard() || outsideRoof.falling())
                    return result(Status.HAZARD,room,gaps,"The opening is beside an unknown drop or hazard.");
                if(air(low))gaps.add(next);if(air(high))gaps.add(next.above());
                if(gaps.size()>2)return result(Status.EXPOSED,room,gaps,"More than one small opening needs work.");
            }
        }
        if(!gaps.isEmpty()){
            Cell first=gaps.iterator().next();
            if(gaps.stream().anyMatch(c->c.x()!=first.x() || c.z()!=first.z()))return result(Status.EXPOSED,room,gaps,"The openings do not form one simple entrance.");
            return result(Status.CLOSE_GAP,room,gaps,"One small opening remains; the room is not enclosed yet.");
        }
        return result(dark?Status.DARK:Status.ENCLOSED_LIT,room,gaps,dark?"The enclosed room is too dark to verify spawn protection.":"A small lit enclosure has a checked floor, roof and no open entrance.");
    }
    private static Result result(Status status,Set<Cell> room,Set<Cell> gaps,String why){return new Result(status,room,new ArrayList<>(gaps),why);}
    public static Cell nextPlacement(World world,Result room,Cell player,int materials,boolean threatened){
        if(room.status()!=Status.CLOSE_GAP || materials<room.gaps().size() || threatened)return null;
        Cell next=room.gaps().getFirst();
        if(next.equals(player) || next.equals(player.above()) || !air(world.voxel(next)) || !wall(world.voxel(next.below())))return null;
        if(Math.abs(next.x()-player.x())>3 || Math.abs(next.z()-player.z())>3 || Math.abs(next.y()-player.y())>1)return null;
        // Stay inside the room; never build over the player's body or use an unverified route.
        if(!room.interior().contains(player) || !NavigationPlanner.visible(world,player,next,Set.of()))return null;
        return next;
    }
    private ShelterCheck(){}
}
