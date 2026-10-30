package minecraft_ai_guide.client.goals;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static minecraft_ai_guide.client.goals.SurvivalMonitor.*;

/** Cheap client-thread samples; no scans for routes, world writes, or chunk loading. */
public final class SurvivalObserver {
    private float lastHealth=Float.NaN;
    private long damagedUntil;
    private java.util.List<FoodPreparation.Recipe> cookingRecipes=java.util.List.of();
    public void cookingRecipes(java.util.List<FoodPreparation.Recipe> recipes){cookingRecipes=recipes;}
    public java.util.List<EnvironmentalSurvival.FoodSource> nearbyFood(){return environment.nearbyFood();}
    private final EnvironmentObserver environment=new EnvironmentObserver();
    public void reset(){lastHealth=Float.NaN;damagedUntil=0;environment.reset();}
    public void shelterStrategy(EmergencyShelterPlanner.Strategy strategy){environment.shelterStrategy(strategy);}
    public static boolean usableFood(String id){return !Set.of("minecraft:rotten_flesh","minecraft:spider_eye",
            "minecraft:poisonous_potato","minecraft:pufferfish","minecraft:chicken","minecraft:suspicious_stew",
            "minecraft:chorus_fruit").contains(id);}
    public Observation observe(Minecraft mc,long now){
        var player=mc.player;
        List<ItemStack> stacks=new ArrayList<>();Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);if(!stack.isEmpty() && seen.add(stack))stacks.add(stack);
        }
        Set<ItemStack> worn=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var slot:EquipmentSlot.values()){
            var stack=player.getItemBySlot(slot);if(stack.isEmpty())continue;
            if(slot==EquipmentSlot.HEAD || slot==EquipmentSlot.CHEST || slot==EquipmentSlot.LEGS || slot==EquipmentSlot.FEET)worn.add(stack);
            if(seen.add(stack))stacks.add(stack);
        }
        int food=0,nutrition=0,blocks=0;Map<Kind,List<Gear>> gear=new EnumMap<>(Kind.class);
        for(var stack:stacks){
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            var properties=stack.get(DataComponents.FOOD);
            if(properties!=null && properties.nutrition()>0 && usableFood(id) && !FoodPreparation.raw(id,cookingRecipes)){
                food+=stack.getCount();nutrition+=properties.nutrition()*stack.getCount();
            }
            if(stack.is(net.minecraft.tags.ItemTags.PLANKS) || Set.of("minecraft:stone","minecraft:cobblestone","minecraft:dirt",
                    "minecraft:granite","minecraft:andesite","minecraft:diorite","minecraft:deepslate","minecraft:cobbled_deepslate",
                    "minecraft:netherrack","minecraft:end_stone","minecraft:bricks").contains(id))blocks+=stack.getCount();
            Kind kind=id.endsWith("_pickaxe")?Kind.PICKAXE:id.endsWith("_sword") || id.endsWith("_axe")?Kind.WEAPON
                    :id.equals("minecraft:shield")?Kind.SHIELD:worn.contains(stack)?Kind.ARMOR:null;
            if(kind!=null)gear.computeIfAbsent(kind,k->new ArrayList<>()).add(new Gear(id,
                    stack.isDamageableItem()?Math.max(0,stack.getMaxDamage()-stack.getDamageValue()):Integer.MAX_VALUE,
                    stack.isDamageableItem()?stack.getMaxDamage():0,worn.contains(stack)));
        }
        float health=player.getHealth();
        if(!Float.isNaN(lastHealth) && health<lastHealth)damagedUntil=now+8_000;
        lastHealth=health;
        Hazard hazard=player.isInLava()?Hazard.LAVA:player.isUnderWater() && player.getAirSupply()<40?Hazard.DROWNING
                :player.isOnFire()?Hazard.FIRE:player.fallDistance>3?Hazard.FALL:now<damagedUntil?Hazard.DAMAGE:Hazard.NONE;
        var surroundings=environment.observe(mc,now,gear);
        return new Observation(health,player.getFoodData().getFoodLevel(),food,nutrition,player.getArmorValue(),blocks,gear,hazard,
                (mc.gui.screen()!=null && !(mc.gui.screen() instanceof TaskMenuScreen)) || player.isUsingItem() || mc.gameMode!=null && mc.gameMode.isDestroying(),
                !player.isCreative() && !player.isSpectator(),player.isAlive(),mc.level.dimension().identifier().toString(),surroundings);
    }
}
