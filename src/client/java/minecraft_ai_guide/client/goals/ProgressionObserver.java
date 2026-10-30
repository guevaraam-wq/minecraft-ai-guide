package minecraft_ai_guide.client.goals;

import minecraft_ai_guide.agent.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import java.util.*;

/** Client inventory/local blocks plus read-only progression queries on the integrated server thread. */
public final class ProgressionObserver {
    private record Facts(Set<String> advancements, boolean stronghold, boolean fortress,
            boolean crystalsKnown,int crystals,boolean dragonAlive,boolean worldDragonDefeated){}
    public record Equipment(BlockPos position,String item,String dimension){}
    private final Map<BlockPos,Equipment> equipment=new LinkedHashMap<>();
    public List<Equipment> equipment(){return List.copyOf(equipment.values());}
    private volatile Facts facts;
    private long factsTime;
    private boolean pending;
    private Object serverIdentity;
    private String dimension="";
    private UUID playerIdentity;
    public void reset(){equipment.clear();facts=null;factsTime=0;pending=false;serverIdentity=null;playerIdentity=null;dimension="";}

    public ProgressionTasks.Snapshot observe(Minecraft mc){
        var player=mc.player;var level=mc.level;
        String currentDimension=level.dimension().identifier().toString();
        var server=mc.getSingleplayerServer();
        if(serverIdentity!=server || !currentDimension.equals(dimension) || !player.getUUID().equals(playerIdentity)){
            reset();serverIdentity=server;dimension=currentDimension;playerIdentity=player.getUUID();
        }
        if(server!=null && !pending && System.nanoTime()-factsTime>1_000_000_000L){
            pending=true;UUID id=player.getUUID();String dim=currentDimension;
            server.execute(()->{
                Facts result=null;
                try{
                    var serverPlayer=server.getPlayerList().getPlayer(id);
                    if(serverPlayer!=null && serverPlayer.level().dimension().identifier().toString().equals(dim)){
                        Set<String> advancements=new HashSet<>();
                        for(String key:List.of("story/enter_the_nether","story/follow_ender_eye","story/enter_the_end","end/kill_dragon","nether/find_fortress")){
                            var holder=server.getAdvancements().get(Identifier.fromNamespaceAndPath("minecraft",key));
                            if(holder!=null && serverPlayer.getAdvancements().getOrStartProgress(holder).isDone())advancements.add("minecraft:"+key);
                        }
                        var sl=serverPlayer.level();boolean stronghold=false,fortress=false;
                        if(sl.hasChunkAt(serverPlayer.blockPosition())){
                            var registry=sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                            var strong=registry.get(net.minecraft.world.level.levelgen.structure.BuiltinStructures.STRONGHOLD);
                            var fort=registry.get(net.minecraft.world.level.levelgen.structure.BuiltinStructures.FORTRESS);
                            stronghold=strong.isPresent() && sl.structureManager().getStructureWithPieceAt(serverPlayer.blockPosition(),strong.get().value()).isValid();
                            fortress=fort.isPresent() && sl.structureManager().getStructureWithPieceAt(serverPlayer.blockPosition(),fort.get().value()).isValid();
                        }
                        var fight=sl.getDragonFight();boolean alive=false,known=false,worldKilled=fight!=null && fight.hasPreviouslyKilledDragon();int crystals=-1;
                        if(sl.dimension().equals(Level.END) && fight!=null && fight.dragonUUID()!=null
                                && sl.getEntity(fight.dragonUUID()) instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon && dragon.isAlive()){
                            alive=true;known=true;
                            // Do not force-load the arena or interpret an unloaded crystal count as zero.
                            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(!sl.getChunkSource().hasChunk(x,z))known=false;
                            if(known)crystals=sl.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
                                    new net.minecraft.world.phys.AABB(-96,sl.getMinY(),-96,96,sl.getMaxY()+1,96),e->e.isAlive()).size();
                        }
                        result=new Facts(Set.copyOf(advancements),stronghold,fortress,known,crystals,alive,worldKilled);
                    }
                }catch(RuntimeException ignored){ /* Unknown evidence is preferable to a fabricated milestone. */ }
                Facts captured=result;
                mc.execute(()->{
                    if(mc.getSingleplayerServer()==server && mc.player!=null && mc.player.getUUID().equals(id)
                            && mc.level!=null && mc.level.dimension().identifier().toString().equals(dim)){
                        facts=captured;factsTime=System.nanoTime();pending=false;
                    }
                });
            });
        }
        int food=0,building=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);
            String itemId=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(stack.get(net.minecraft.core.component.DataComponents.FOOD)!=null && SurvivalObserver.usableFood(itemId) && !FoodPreparation.raw(itemId,java.util.List.of()))food+=stack.getCount();
            if(stack.is(net.minecraft.tags.ItemTags.PLANKS) || Set.of("minecraft:stone","minecraft:cobblestone",
                    "minecraft:dirt","minecraft:granite","minecraft:andesite","minecraft:diorite","minecraft:deepslate",
                    "minecraft:cobbled_deepslate","minecraft:netherrack","minecraft:end_stone","minecraft:bricks").contains(itemId))building+=stack.getCount();
        }
        boolean table=false,netherPortal=false,endPortal=false;Map<BlockPos,PortalFrames.Frame> frames=new HashMap<>();
        BlockPos center=player.blockPosition();
        for(int x=-8;x<=8;x++)for(int y=-4;y<=4;y++)for(int z=-8;z<=8;z++){
            BlockPos pos=center.offset(x,y,z);if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)){
                equipment.put(pos.immutable(),new Equipment(pos.immutable(),BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),currentDimension));
                if(equipment.size()>32)equipment.remove(equipment.keySet().iterator().next());
            }
            table|=state.is(Blocks.CRAFTING_TABLE);netherPortal|=state.is(Blocks.NETHER_PORTAL);endPortal|=currentDimension.equals("minecraft:overworld") && state.is(Blocks.END_PORTAL);
            if(state.is(Blocks.END_PORTAL_FRAME))frames.put(pos.immutable(),new PortalFrames.Frame(state.getValue(EndPortalFrameBlock.FACING).getName(),state.getValue(EndPortalFrameBlock.HAS_EYE)));
        }
        var ring=PortalFrames.find(frames);
        Facts current=System.nanoTime()-factsTime<3_000_000_000L?facts:null;
        String biome=level.getBiome(center).unwrapKey().map(key->key.identifier().toString()).orElse("");
        Map<String,Integer> inventory=new HashMap<>(GameState.observe(mc).getInventory());
        // Equipment storage differs across Minecraft versions; count each equipped stack once.
        // In particular, an offhand shield must not trigger repeated shield crafting.
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values()){
            var stack=player.getItemBySlot(slot);if(stack.isEmpty())continue;
            boolean counted=false;
            for(int i=0;i<player.getInventory().getContainerSize();i++)
                if(player.getInventory().getItem(i)==stack){counted=true;break;}
            if(!counted)inventory.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
        }
        return new ProgressionTasks.Snapshot(inventory,currentDimension,player.getHealth(),
                player.getFoodData().getFoodLevel(),food,player.getArmorValue(),building,table,netherPortal,endPortal,
                currentDimension.equals("minecraft:overworld") && ring.known(),currentDimension.equals("minecraft:overworld")?ring.missingEyes():-1,current!=null,current==null?Set.of():current.advancements(),
                current!=null&&current.stronghold(),current!=null&&current.fortress(),current!=null&&current.crystalsKnown(),
                current==null?-1:current.crystals(),current!=null&&current.dragonAlive(),current!=null&&current.worldDragonDefeated(),biome);
    }
}
