package minecraft_ai_guide.client.goals;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import minecraft_ai_guide.client.guide.*;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;
import static minecraft_ai_guide.client.goals.EnvironmentalSurvival.*;

/** Loaded-world evidence only; bounded shelter searches reuse existing walking-only pathfinding. */
public final class EnvironmentObserver {
    private final EmergencyShelterPlanner.Session builder=new EmergencyShelterPlanner.Session();
    public void shelterStrategy(EmergencyShelterPlanner.Strategy preference){builder.preference(preference);cachedAdvice=null;}
    public EmergencyShelterPlanner.Strategy shelterStrategy(){return builder.preference();}
    public void anotherShelter(){builder.another();cachedAdvice=null;}
    private final DayClock clock=new DayClock();
    private final ThreatTracker tracker=new ThreatTracker();
    private Object world;
    private List<FoodSource> nearbyFood=List.of();
    public List<FoodSource> nearbyFood(){return nearbyFood;}
    private ShelterCheck.Result room;
    private Cell roomAt;
    private long checked;
    private ShelterAdvice cachedAdvice;
    public void reset(){world=null;room=null;roomAt=null;checked=0;cachedAdvice=null;builder.reset();clock.reset();tracker.reset();}
    public Environment observe(Minecraft mc,long now,Map<SurvivalMonitor.Kind,List<SurvivalMonitor.Gear>> gear){
        if(world!=mc.level){reset();world=mc.level;}
        var level=mc.level;var player=mc.player;Cell feet=Pathfinder.cell(player.blockPosition());
        boolean normalCycle=level.dimension().equals(Level.OVERWORLD) && level.dimensionType().hasSkyLight()
                && !level.dimensionType().hasFixedTime() && !level.dimensionType().hasCeiling();
        TimeBand time=clock.update(normalCycle,normalCycle?level.getOverworldClockTime():0);
        boolean woodenDoorRisk=level.getDifficulty()==net.minecraft.world.Difficulty.HARD
                || !level.getEntitiesOfClass(net.minecraft.world.entity.monster.illager.Vindicator.class,
                    player.getBoundingBox().inflate(12),v->v.isAlive()).isEmpty();
        var nav=Pathfinder.guideWorld(mc);
        ShelterCheck.World terrain=new ShelterCheck.World(){
            public NavigationPlanner.Voxel voxel(Cell c){return nav.voxel(c);}
            public int blockLight(Cell c){return level.hasChunkAt(Pathfinder.block(c))?level.getBrightness(LightLayer.BLOCK,Pathfinder.block(c)):-1;}
            public boolean cookingFixture(Cell c){
                var pos=Pathfinder.block(c);if(!level.hasChunkAt(pos))return false;
                var block=level.getBlockState(pos).getBlock();
                return block instanceof net.minecraft.world.level.block.FurnaceBlock || block instanceof net.minecraft.world.level.block.SmokerBlock;
            }
            public boolean bedCell(Cell c){
                var pos=Pathfinder.block(c);return level.dimension().equals(Level.OVERWORLD) && level.hasChunkAt(pos)
                        && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
            }
            public boolean closedEntrance(Cell c){
                BlockPos pos=Pathfinder.block(c);if(!level.hasChunkAt(pos))return false;
                var state=level.getBlockState(pos);
                return state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                        && closedDoorProtection(state.is(net.minecraft.world.level.block.Blocks.IRON_DOOR),state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS),
                            woodenDoorRisk);
            }
            public boolean allowSearch(boolean excavation){return nav.allowSearch(excavation);}
        };
        boolean refreshed=builder.plan()!=null || room==null || !feet.equals(roomAt) || now-checked>=750;
        if(refreshed){room=ShelterCheck.inspect(terrain,feet);roomAt=feet;checked=now;cachedAdvice=null;}
        boolean enclosed=room.status()==ShelterCheck.Status.ENCLOSED_LIT || EmergencyShelterPlanner.occupiedCover(room,feet);
        List<EnvironmentalSurvival.Mob> mobs=new ArrayList<>();
        for(Mob mob:level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(20),m->m.isAlive())){
            boolean targeting=mob.getTarget()==player;
            boolean daylightSpider=mob instanceof net.minecraft.world.entity.monster.spider.Spider
                    && !(mob instanceof net.minecraft.world.entity.monster.spider.CaveSpider)
                    && time==TimeBand.DAY && level.getMaxLocalRawBrightness(mob.blockPosition())>=12;
            boolean neutral=mob instanceof NeutralMob || mob instanceof net.minecraft.world.entity.monster.piglin.Piglin;
            var disposition=classify(mob instanceof Enemy,mob.getType().getCategory()==MobCategory.MONSTER,
                    neutral,mob instanceof Animal,targeting,daylightSpider);
            FoodAnimal food=mob instanceof net.minecraft.world.entity.animal.cow.AbstractCow?FoodAnimal.COW
                    :mob instanceof net.minecraft.world.entity.animal.pig.Pig?FoodAnimal.PIG
                    :mob instanceof net.minecraft.world.entity.animal.chicken.Chicken?FoodAnimal.CHICKEN
                    :mob instanceof net.minecraft.world.entity.animal.sheep.Sheep?FoodAnimal.SHEEP:FoodAnimal.NONE;
            boolean inside=(room.status()==ShelterCheck.Status.ENCLOSED_LIT || room.status()==ShelterCheck.Status.DARK
                    || room.status()==ShelterCheck.Status.CLOSE_GAP) && room.interior().contains(Pathfinder.cell(mob.blockPosition()));
            mobs.add(new EnvironmentalSurvival.Mob(mob.getUUID().toString(),mob.getType().getDescription().getString(),disposition,
                    player.distanceTo(mob),player.hasLineOfSight(mob),targeting,inside,
                    mob instanceof NeutralMob n && n.isAngry(),food,direction(player.getX(),player.getZ(),player.getYRot(),mob.getX(),mob.getZ())));
        }
        nearbyFood=EnvironmentalSurvival.nearbyFoodSources(mobs);
        Threat threat=tracker.choose(mobs,enclosed);
        boolean protectedHere=enclosed && (threat==null || !threat.immediate() && !threat.mob().insideShelter() && !threat.mob().targeting());
        Map<String,Integer> materials=EmergencyShelterWorld.materials(mc);
        int blocks=materials.values().stream().mapToInt(Integer::intValue).sum();
        boolean peaceful=level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL;
        boolean nightHold=protectedHere && !peaceful && time==TimeBand.NIGHT;
        boolean grace=builder.continuationGrace(feet,now,threat!=null);
        boolean needsCover=!grace && !protectedHere && (!peaceful && (time==TimeBand.DUSK || time==TimeBand.NIGHT) || threat!=null);
        ShelterAdvice advice=null;
        if(needsCover){
            if(refreshed || cachedAdvice==null)cachedAdvice=shelterAdvice(mc,terrain,feet,room,materials,blocks,threat,time);
            // Never show a stale block placement after the terrain changed, or while a threat is close.
            advice=cachedAdvice;
            if(threat!=null || advice.placement()!=null && !advice.placement().equals(ShelterCheck.nextPlacement(terrain,
                    ShelterCheck.inspect(terrain,feet),feet,blocks,false)) || !validPath(terrain,advice.path())){
                cachedAdvice=shelterAdvice(mc,terrain,feet,ShelterCheck.inspect(terrain,feet),materials,blocks,threat,time);advice=cachedAdvice;
            }
        }
        if(nightHold){
            builder.observeProtected(new EmergencyShelterWorld(mc,materials),feet);
            int total=builder.plan().operations().size();
            var hold=new EmergencyShelterPlanner.Instruction(EmergencyShelterPlanner.Action.COMPLETE,null,null,false,total,total,
                    "Wait inside this checked shelter until daylight.","Your main goal is saved.",builder.plan().strategy());
            String waiting=usableBed(mc,room,threat)?"Use the bed inside this room to sleep; you can also wait here.":hold.text();
            advice=new ShelterAdvice(waiting,room.status()==ShelterCheck.Status.DARK
                    ?"This occupied dark refuge is temporary cover, not a permanent safe base.":hold.why(),null,List.of(),null,"",0,hold);
        }else if(needsCover && threat==null && (builder.plan()!=null || builder.preference()!=EmergencyShelterPlanner.Strategy.AUTO || advice==null
                || advice.placement()==null && advice.destination()==null && advice.recipeItem().isEmpty())){
            advice=emergencyAdvice(mc,materials,gear,feet,blocks);
            if(advice.build().action()==EmergencyShelterPlanner.Action.COMPLETE)protectedHere=true;
        }
        if(time==TimeBand.DAY && threat==null){
            if(builder.plan()==null && EmergencyShelterPlanner.occupiedCover(room,feet))builder.observeProtected(new EmergencyShelterWorld(mc,materials),feet);
            var exit=builder.exitStep(new EmergencyShelterWorld(mc,materials),feet,false);
            if(exit!=null)advice=buildAdvice(mc,new EmergencyShelterWorld(mc,materials),exit);
        }
        return new Environment(time,protectedHere,threat,advice,foodSource(mobs,threat),blocks,peaceful);
    }
    private ShelterAdvice shelterAdvice(Minecraft mc,ShelterCheck.World terrain,Cell feet,ShelterCheck.Result current,
            Map<String,Integer> materials,int blocks,Threat threat,TimeBand time){
        String why=threat!=null?threat.mob().name()+" may be dangerous. Avoid combat.":time==TimeBand.DUSK
                ?"Night is approaching. Prepare cover before it gets dark.":"It is night. Enclosed, lit cover reduces exposure to monsters.";
        if(threat!=null && threat.mob().disposition()==Disposition.CONDITIONAL && !threat.mob().targeting())
            return message(threatInstruction(threat),"This animal can attack when upset. It is not a safe food recommendation.");
        if(threat!=null)return message(threatInstruction(threat),why);
        Cell placement=ShelterCheck.nextPlacement(terrain,current,feet,blocks,threat!=null);
        if(placement!=null){
            String held=BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString();
            String material=materials.containsKey(held)?held:materials.keySet().iterator().next();
            boolean hotbar=false;for(int slot=0;slot<9;slot++)if(BuiltInRegistries.ITEM.getKey(mc.player.getInventory().getItem(slot).getItem()).toString().equals(material))hotbar=true;
            if(!hotbar)return message("Move "+RecipeGuidance.name(material)+" to your hotbar.",why);
            if(!BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString().equals(material))
                return message("Hold "+RecipeGuidance.name(material)+" from your hotbar.",why);
            return new ShelterAdvice("Place one "+RecipeGuidance.name(material)+" in the "+(placement.y()==feet.y()?"lower":"upper")+" opening "+direction(mc.player.getX(),mc.player.getZ(),mc.player.getYRot(),placement.x()+.5,placement.z()+.5)+" ("+coordinates(placement)+").",
                    why+" The room is not enclosed yet.",placement,List.of(),null);
        }
        if(current.status()==ShelterCheck.Status.DARK){
            int torches=mc.player.getInventory().countItem(net.minecraft.world.item.Items.TORCH);
            return torches>0?message("Place a torch on an inside wall to light this room.",why+" The enclosed room is too dark.")
                    :new ShelterAdvice("",why+" The room needs light before protection can be verified.",null,List.of(),null,"minecraft:torch",1);
        }
        if(current.status()==ShelterCheck.Status.CLOSE_GAP && blocks<current.gaps().size())
            return message("Look for an enclosed building; you do not carry enough usable blocks to seal this opening.",why);
        // At most six nearby flat candidates; no excavation, drops or door-opening assumptions.
        List<Cell> candidates=new ArrayList<>();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
            Cell c=feet.offset(x,0,z);var v=terrain.voxel(c);
            if(v.known() && v.passable() && terrain.voxel(c.offset(0,2,0)).support())candidates.add(c);
        }
        candidates.sort(Comparator.comparingInt(c->Math.abs(c.x()-feet.x())+Math.abs(c.z()-feet.z())));
        for(Cell c:candidates.stream().limit(6).toList()){
            if(c.equals(feet))continue;
            var candidate=ShelterCheck.inspect(terrain,c);
            if(candidate.status()!=ShelterCheck.Status.ENCLOSED_LIT && candidate.status()!=ShelterCheck.Status.DARK
                    && !(candidate.status()==ShelterCheck.Status.CLOSE_GAP && blocks>=candidate.gaps().size()))continue;
            var route=NavigationPlanner.plan(terrain,feet,c,NavigationPlanner.Goal.WALK,false);
            if(route.status()!=NavigationPlanner.Status.REACHABLE_NOW)continue;
            var next=NavigationPlanner.next(terrain,route,feet);
            if(next.action()!=NavigationPlanner.Action.WALK)continue;
            return new ShelterAdvice("Walk toward the covered space at "+coordinates(c)+"; it still needs a protection check.",
                    why,null,next.walkPath(),c);
        }
        return message("Look for an enclosed building; I cannot safely guide a new shelter at this spot.",why+" No checked shelter route is available.");
    }
    private static boolean usableBed(Minecraft mc,ShelterCheck.Result room,Threat threat){
        if(!mc.level.dimension().equals(Level.OVERWORLD) || threat!=null || room.status()!=ShelterCheck.Status.ENCLOSED_LIT)return false;
        if(!mc.level.getEntitiesOfClass(Mob.class,mc.player.getBoundingBox().inflate(8,5,8),m->m.isAlive()
                && (m instanceof Enemy || m.getTarget()==mc.player || m instanceof NeutralMob n && n.isAngry())).isEmpty())return false;
        for(Cell c:room.interior()){
            BlockPos pos=Pathfinder.block(c);var state=mc.level.getBlockState(pos);
            if(!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                    || state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED))continue;
            var bedRule=mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE,pos);
            if(bedRule.explodes() || !bedRule.canSleep(mc.level))continue;
            var facing=state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            var part=state.getValue(net.minecraft.world.level.block.BedBlock.PART);
            BlockPos other=pos.relative(part==net.minecraft.world.level.block.state.properties.BedPart.FOOT?facing:facing.getOpposite());
            if(!room.interior().contains(Pathfinder.cell(other)) || !mc.level.hasChunkAt(other))continue;
            var otherState=mc.level.getBlockState(other);
            if(otherState.getBlock()!=state.getBlock() || otherState.getValue(net.minecraft.world.level.block.BedBlock.PART)==part
                    || otherState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)!=facing
                    || otherState.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED))continue;
            var eye=mc.player.getEyePosition();var target=net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0,-.15,0);
            if(eye.distanceTo(target)>mc.player.blockInteractionRange())continue;
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,target,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY,mc.player));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK && (hit.getBlockPos().equals(pos) || hit.getBlockPos().equals(other)))return true;
        }return false;
    }
    private static EmergencyShelterPlanner.Supplies supplies(Minecraft mc,Map<SurvivalMonitor.Kind,List<SurvivalMonitor.Gear>> gear,int blocks){
        boolean pickaxe=gear.getOrDefault(SurvivalMonitor.Kind.PICKAXE,List.of()).stream().anyMatch(g->!g.worn());
        boolean food=false;for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
            var stack=mc.player.getInventory().getItem(i);food|=stack.get(net.minecraft.core.component.DataComponents.FOOD)!=null && SurvivalObserver.usableFood(EmergencyShelterWorld.id(stack)) && !FoodPreparation.raw(EmergencyShelterWorld.id(stack),List.of());
        }
        return new EmergencyShelterPlanner.Supplies(blocks,pickaxe,food,mc.player.getHealth(),mc.player.getFoodData().getFoodLevel());
    }
    private ShelterAdvice emergencyAdvice(Minecraft mc,Map<String,Integer> materials,Map<SurvivalMonitor.Kind,List<SurvivalMonitor.Gear>> gear,Cell feet,int blocks){
        var world=new EmergencyShelterWorld(mc,materials);
        var step=builder.next(world,feet,supplies(mc,gear,blocks),false,mc.player.onGround());
        return buildAdvice(mc,world,step);
    }
    private static ShelterAdvice buildAdvice(Minecraft mc,EmergencyShelterWorld world,EmergencyShelterPlanner.Instruction step){
        String text=step.text();Cell marker=step.target();
        if(step.action()==EmergencyShelterPlanner.Action.PLACE || step.action()==EmergencyShelterPlanner.Action.JUMP_PLACE){
            String material=world.material();
            if(!world.hotbar(material))text="Move "+RecipeGuidance.name(material)+" to your hotbar.";
            else if(!world.held().equals(material))text="Hold "+RecipeGuidance.name(material)+" from your hotbar.";
            else text=step.jump()?"Jump and place a "+RecipeGuidance.name(material)+" block on the white square.":"Place a "+RecipeGuidance.name(material)+" block on the white square.";
        }else if(step.action()==EmergencyShelterPlanner.Action.MINE){
            String tool=world.tool(step.target());
            if(!tool.isEmpty() && !world.hotbar(tool))text="Move your "+RecipeGuidance.name(tool)+" to your hotbar.";
            else if(!tool.isEmpty() && !world.held().equals(tool))text="Hold your "+RecipeGuidance.name(tool)+" to mine the outlined block.";
        }
        return new ShelterAdvice(text,step.why(),marker,List.of(),null,"",0,step);
    }
    private static boolean validPath(ShelterCheck.World terrain,List<Cell> path){
        for(Cell c:path){var feet=terrain.voxel(c);var head=terrain.voxel(c.above());var floor=terrain.voxel(c.below());
            if(!feet.known() || !head.known() || !floor.known() || !feet.passable() || !head.passable()
                    || feet.hazard() || head.hazard() || floor.hazard() || floor.falling() || !floor.support())return false;
        }return true;
    }
    private static ShelterAdvice message(String instruction,String why){return new ShelterAdvice(instruction,why,null,List.of(),null);}
    private static String coordinates(Cell c){return "X "+c.x()+", Y "+c.y()+", Z "+c.z();}
    static String direction(double x,double z,float yaw,double targetX,double targetZ){
        double radians=Math.toRadians(yaw),dx=targetX-x,dz=targetZ-z;
        double forward=-Math.sin(radians)*dx+Math.cos(radians)*dz,right=-Math.cos(radians)*dx-Math.sin(radians)*dz;
        if(Math.abs(forward)>=Math.abs(right))return forward>=0?"ahead of you":"behind you";
        return right>=0?"to your right":"to your left";
    }
}
