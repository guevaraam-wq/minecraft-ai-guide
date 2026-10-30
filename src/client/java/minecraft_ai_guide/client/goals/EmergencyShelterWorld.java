package minecraft_ai_guide.client.goals;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import minecraft_ai_guide.client.guide.*;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;

/** Placement contexts, collision and ray checks only. Never calls useItemOn/destroyBlock/setBlock. */
final class EmergencyShelterWorld implements EmergencyShelterPlanner.World {
    private final Minecraft mc;
    private final NavigationPlanner.World terrain;
    private final Map<String,Integer> materials;
    EmergencyShelterWorld(Minecraft mc,Map<String,Integer> materials){this.mc=mc;this.materials=materials;terrain=Pathfinder.guideWorld(mc);}
    public NavigationPlanner.Voxel voxel(Cell c){return terrain.voxel(c);}
    public int blockLight(Cell c){return mc.level.hasChunkAt(Pathfinder.block(c))?mc.level.getBrightness(LightLayer.BLOCK,Pathfinder.block(c)):-1;}
    public double reach(){return terrain.reach();}
    public double eyeHeight(){return terrain.eyeHeight();}
    public boolean allowSearch(boolean excavation){return terrain.allowSearch(excavation);}
    static Map<String,Integer> materials(Minecraft mc){
        Map<String,Integer> result=new TreeMap<>();
        for(int slot=0;slot<mc.player.getInventory().getContainerSize();slot++){
            ItemStack stack=mc.player.getInventory().getItem(slot);String id=id(stack);
            if(usableMaterial(id))result.merge(id,stack.getCount(),Integer::sum);
        }return result;
    }
    static boolean usableMaterial(String id){return id.startsWith("minecraft:") && (id.endsWith("_planks") || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || Set.of(
            "minecraft:dirt","minecraft:cobblestone","minecraft:cobbled_deepslate","minecraft:stone","minecraft:andesite",
            "minecraft:granite","minecraft:diorite","minecraft:tuff","minecraft:bricks").contains(id));}
    static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    String material(){
        String held=id(mc.player.getMainHandItem());if(materials.containsKey(held))return held;
        for(int i=0;i<9;i++){String item=id(mc.player.getInventory().getItem(i));if(materials.containsKey(item))return item;}
        return materials.keySet().stream().findFirst().orElse("");
    }
    private ItemStack stack(String id){
        for(int i=0;i<mc.player.getInventory().getContainerSize();i++){var stack=mc.player.getInventory().getItem(i);if(id(stack).equals(id))return stack;}
        return ItemStack.EMPTY;
    }
    boolean hotbar(String id){for(int i=0;i<9;i++)if(id(mc.player.getInventory().getItem(i)).equals(id))return true;return false;}
    String held(){return id(mc.player.getMainHandItem());}
    public boolean excavatable(Cell c){
        BlockPos pos=Pathfinder.block(c);if(!mc.level.hasChunkAt(pos))return false;
        var state=mc.level.getBlockState(pos);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(!Set.of("minecraft:dirt","minecraft:grass_block","minecraft:coarse_dirt","minecraft:rooted_dirt","minecraft:stone",
                "minecraft:deepslate","minecraft:granite","minecraft:diorite","minecraft:andesite","minecraft:tuff").contains(id)
                || state.hasBlockEntity() || state.getDestroySpeed(mc.level,pos)<0 || !mc.level.mayInteract(mc.player,pos))return false;
        return !state.requiresCorrectToolForDrops() || !tool(c).isEmpty();
    }
    public boolean exitRemovable(Cell c){
        BlockPos pos=Pathfinder.block(c);var state=mc.level.getBlockState(pos);
        return usableMaterial(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()) && !state.hasBlockEntity()
                && state.getDestroySpeed(mc.level,pos)>=0 && mc.level.mayInteract(mc.player,pos);
    }
    String tool(Cell c){
        var state=mc.level.getBlockState(Pathfinder.block(c));if(!state.requiresCorrectToolForDrops())return "";
        for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
            var stack=mc.player.getInventory().getItem(i);
            int remaining=stack.isDamageableItem()?stack.getMaxDamage()-stack.getDamageValue():Integer.MAX_VALUE;
            if(!stack.isEmpty() && stack.isCorrectToolForDrops(state) && remaining>10
                    && (!stack.isDamageableItem() || remaining>stack.getMaxDamage()/10))return id(stack);
        }return "";
    }
    public boolean placementAllowed(Cell target,Cell support,boolean jump,Cell actor){
        String material=material();if(material.isEmpty())return false;
        ItemStack stack=stack(material);if(!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem))return false;
        BlockPos pos=Pathfinder.block(target),attached=Pathfinder.block(support);
        if(!mc.level.hasChunkAt(pos) || !mc.level.getWorldBorder().isWithinBounds(pos) || !mc.level.mayInteract(mc.player,pos))return false;
        int dx=target.x()-support.x(),dy=target.y()-support.y(),dz=target.z()-support.z();
        Direction face=dy>0?Direction.UP:dy<0?Direction.DOWN:dx>0?Direction.EAST:dx<0?Direction.WEST:dz>0?Direction.SOUTH:Direction.NORTH;
        Vec3 hit=Vec3.atCenterOf(attached).add(face.getStepX()*.499,face.getStepY()*.499,face.getStepZ()*.499);
        Vec3 eye=mc.player.getEyePosition();if(jump)eye=eye.add(0,1,0);
        if(eye.distanceTo(hit)>mc.player.blockInteractionRange()-.25)return false;
        var ray=mc.level.clip(new ClipContext(eye,hit,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,mc.player));
        if(ray.getType()!=HitResult.Type.BLOCK || !ray.getBlockPos().equals(attached) || ray.getDirection()!=face)return false;
        var context=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(ray.getLocation(),face,attached,false));
        var state=blockItem.getBlock().defaultBlockState();
        return context.getClickedPos().equals(pos) && context.canPlace() && state.canSurvive(mc.level,pos)
                && mc.level.isUnobstructed(state,pos,CollisionContext.of(mc.player));
    }
}
