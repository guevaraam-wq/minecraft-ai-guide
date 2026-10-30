package minecraft_ai_guide.client.goals;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import minecraft_ai_guide.client.guide.*;
import static minecraft_ai_guide.client.goals.FoodPreparation.*;
import minecraft_ai_guide.client.goals.FoodPreparation.Recipe;

/** Actual recipe/fuel data and visible slots; never transfers items or operates a cooking block. */
public final class FoodCookingObserver {
    private Object world;
    private List<Recipe> recipes=List.of();
    private RecipeGuidance.Knowledge crafting;
    private final Map<String,BlockPos> positions=new HashMap<>();
    private final Map<String,NavigationPlanner.Instruction> routes=new HashMap<>();
    private String lastStation="",lastInput="";
    private final Set<String> emptyChecked=new HashSet<>();
    private int pendingFuelTicks;
    private Facts facts;
    private Step current;
    private BlockPos placement;
    public void reset(){world=null;recipes=List.of();crafting=null;emptyChecked.clear();positions.clear();routes.clear();lastStation="";lastInput="";pendingFuelTicks=0;facts=null;current=null;placement=null;}
    public Facts facts(){return facts;}
    public static boolean ready(String id,List<Recipe> recipes){return SurvivalObserver.usableFood(id) && !FoodPreparation.raw(id,recipes);}
    public List<Recipe> recipes(){return recipes;}
    static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private void load(Minecraft mc){
        if(mc.getSingleplayerServer()==null || !recipes.isEmpty())return;
        List<Recipe> loaded=new ArrayList<>();
        for(var holder:mc.getSingleplayerServer().getRecipeManager().getRecipes()){
            if(!(holder.value() instanceof AbstractCookingRecipe cooking))continue;
            Method method=cooking instanceof CampfireCookingRecipe?Method.CAMPFIRE:cooking instanceof SmokingRecipe?Method.SMOKER
                    :cooking instanceof SmeltingRecipe?Method.FURNACE:null;
            if(method==null)continue;
            cooking.input().items().forEach(item->{
                var output=cooking.assemble(new SingleRecipeInput(new ItemStack(item.value())));
                var food=output.get(DataComponents.FOOD);
                if(food!=null && food.nutrition()>0 && SurvivalObserver.usableFood(id(output)))
                    loaded.add(new Recipe(BuiltInRegistries.ITEM.getKey(item.value()).toString(),id(output),output.getCount(),food.nutrition(),cooking.cookingTime(),method));
            });
        }
        recipes=List.copyOf(loaded);
        crafting=minecraft_ai_guide.agent.MinecraftRecipePlanner.guideKnowledge(mc);
    }
    public Facts observe(Minecraft mc,SurvivalMonitor.Observation survival){
        if(world!=mc.level){reset();world=mc.level;}
        load(mc);positions.clear();routes.clear();placement=null;
        Map<String,Integer> inventory=new HashMap<>(),nutrition=new HashMap<>(),fuels=new HashMap<>();
        String readyItem="",emergencyRaw="";int readyNutrition=0;
        Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());List<ItemStack> items=new ArrayList<>();
        for(int i=0;i<mc.player.getInventory().getContainerSize();i++)items.add(mc.player.getInventory().getItem(i));
        items.add(mc.player.getOffhandItem());
        for(var stack:items){
            if(stack.isEmpty() || !seen.add(stack))continue;
            String id=id(stack);inventory.merge(id,stack.getCount(),Integer::sum);
            int burn=mc.level.fuelValues().burnDuration(stack);if(burn>0)fuels.put(id,burn);
            var food=stack.get(DataComponents.FOOD);if(food==null || food.nutrition()<=0)continue;
            nutrition.put(id,food.nutrition());
            if(ready(id,recipes)){readyNutrition+=food.nutrition()*stack.getCount();if(readyItem.isEmpty() || food.nutrition()>nutrition.get(readyItem))readyItem=id;}
            else if(SurvivalObserver.usableFood(id) && FoodPreparation.raw(id,recipes))emergencyRaw=id;
        }
        for(var recipe:recipes)nutrition.put(recipe.output(),recipe.nutrition());
        Menu menu=null;
        if(mc.player.containerMenu instanceof AbstractFurnaceMenu furnace){
            Method method=furnace instanceof SmokerMenu?Method.SMOKER: furnace instanceof FurnaceMenu?Method.FURNACE:null;
            if(method!=null){
                var input=furnace.getSlot(0).getItem();var fuel=furnace.getSlot(1).getItem();var output=furnace.getSlot(2).getItem();
                if(!output.isEmpty() && output.get(DataComponents.FOOD)!=null)nutrition.put(id(output),output.get(DataComponents.FOOD).nutrition());
                menu=new Menu(method,input.isEmpty()?"":id(input),input.getCount(),fuel.isEmpty()?"":id(fuel),fuel.getCount(),
                        output.isEmpty()?"":id(output),output.getCount(),furnace.isLit(),furnace.getBurnProgress(),true);
                if(!fuel.isEmpty()){int burn=mc.level.fuelValues().burnDuration(fuel);if(burn>0)fuels.put(id(fuel),burn);}
                if(!input.isEmpty()){
                    lastInput=id(input);
                    if(fuel.isEmpty() && !furnace.isLit())pendingFuelTicks=recipes.stream().filter(r->r.method()==method && r.input().equals(lastInput)).findFirst().map(Recipe::ticks).orElse(0);
                }else if(output.isEmpty()){lastInput="";pendingFuelTicks=0;}
            }
        }
        List<Station> stations=new ArrayList<>();BlockPos origin=mc.player.blockPosition();
        List<BlockPos> candidates=new ArrayList<>();
        for(int x=-6;x<=6;x++)for(int y=-3;y<=3;y++)for(int z=-6;z<=6;z++){
            BlockPos pos=origin.offset(x,y,z);if(!mc.level.hasChunkAt(pos))continue;
            var state=mc.level.getBlockState(pos);if(state.getBlock() instanceof FurnaceBlock || state.getBlock() instanceof SmokerBlock || state.getBlock() instanceof CampfireBlock)candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));
        for(var pos:candidates.stream().limit(6).toList()){
            var state=mc.level.getBlockState(pos);Method method=state.getBlock() instanceof CampfireBlock?Method.CAMPFIRE:state.getBlock() instanceof SmokerBlock?Method.SMOKER:Method.FURNACE;
            boolean lit=method==Method.CAMPFIRE && state.getValue(CampfireBlock.LIT) && mc.level.getFluidState(pos).isEmpty();
            int cooking=method==Method.CAMPFIRE && mc.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity fire
                    ?fire.getItems().stream().filter(s->!s.isEmpty()).mapToInt(ItemStack::getCount).sum():0;
            var base=Pathfinder.guideWorld(mc);
            // Campfire is an interaction obstacle, never a walking surface or passable tile.
            NavigationPlanner.World approach=new NavigationPlanner.World(){
                public NavigationPlanner.Voxel voxel(NavigationPlanner.Cell c){
                    var v=base.voxel(c);return method==Method.CAMPFIRE && c.equals(Pathfinder.cell(pos))
                            ?new NavigationPlanner.Voxel(v.identity(),v.known(),false,false,false,false,false):v;
                }
                public boolean allowSearch(boolean excavation){return base.allowSearch(false);}
                public double reach(){return base.reach();}public double eyeHeight(){return base.eyeHeight();}
            };
            var route=NavigationPlanner.plan(approach,Pathfinder.cell(origin),Pathfinder.cell(pos),NavigationPlanner.Goal.INTERACT,false);
            boolean reachable=route.status()==NavigationPlanner.Status.REACHABLE_NOW;
            boolean inReach=reachable && NavigationPlanner.visible(approach,Pathfinder.cell(origin),Pathfinder.cell(pos),Set.of());
            String key=pos.asLong()+":"+method;positions.put(key,pos);
            if(reachable)routes.put(key,NavigationPlanner.next(approach,route,Pathfinder.cell(origin)));
            String cookingInput="";
            if(method==Method.CAMPFIRE && mc.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity fire)
                cookingInput=fire.getItems().stream().filter(s->!s.isEmpty()).map(FoodCookingObserver::id).findFirst().orElse("");
            int drops=0;String dropItem="";
            if(method==Method.CAMPFIRE)for(var dropped:mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(pos).inflate(2.5),e->e.isAlive())){
                var stack=dropped.getItem();String item=id(stack);
                if(stack.get(DataComponents.FOOD)!=null && ready(item,recipes) && recipes.stream().anyMatch(r->r.method()==Method.CAMPFIRE && r.output().equals(item))){
                    drops+=stack.getCount();dropItem=item;
                }
            }
            stations.add(new Station(key,method,reachable,inReach,lit,false,cooking,reachable?"":route.reason(),cookingInput,drops,dropItem));
        }
        if(menu!=null){
            // Only bind a newly opened screen to an observed target if we previously guided its opening.
            if(current!=null && (current.action()==Action.OPEN || current.action()==Action.APPROACH) && positions.containsKey(current.station()) && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(positions.get(current.station())))lastStation=current.station();
        }
        if(menu!=null && !lastStation.isEmpty()){
            if(menu.inputCount()==0 && menu.outputCount()==0)emptyChecked.add(lastStation);else emptyChecked.remove(lastStation);
        }
        boolean safe=survival.hazard()==SurvivalMonitor.Hazard.NONE && survival.environment().threat()==null;
        boolean gatheringSafe=safe && !survival.environment().nightRisk() && survival.health()>6 && !survival.end();
        if(inventory.getOrDefault("minecraft:furnace",0)>0 || inventory.getOrDefault("minecraft:campfire",0)>0){
            placement=findPlacement(mc,inventory.getOrDefault("minecraft:furnace",0)>0?Items.FURNACE:Items.CAMPFIRE);
            if(placement!=null){String key="place:"+placement.asLong();positions.put(key,placement);stations.add(new Station(key,Method.FURNACE,false,true,false,true,0,""));}
        }
        Set<Method> craftable=new HashSet<>();
        if(crafting!=null && stations.stream().noneMatch(s->s.reachable()))for(var method:List.of(Method.FURNACE,Method.CAMPFIRE)){
            String item=method==Method.FURNACE?"minecraft:furnace":"minecraft:campfire";
            var context=new RecipeGuidance.Context(inventory,Map.of(),RecipeGuidance.Table.NONE,false,false,false,Set.of(),"","");
            var step=new RecipeGuidance(crafting,item,1).next(context);
            if(step.action()!=RecipeGuidance.Action.COLLECT && step.action()!=RecipeGuidance.Action.BLOCKED)craftable.add(method);
        }
        facts=new Facts(inventory,nutrition,recipes,fuels,stations,menu,readyNutrition,readyItem,emergencyRaw,gatheringSafe,safe,recipes.isEmpty(),craftable);return facts;
    }
    public Step plan(Minecraft mc,int hunger,boolean urgent,int target){
        if(facts==null)return null;
        if(!urgent && pendingFuelTicks>0 && facts.readyNutrition()<target && FoodPreparation.chooseFuel(facts).isEmpty()){
            if(facts.stations().stream().anyMatch(s->s.method()==Method.CAMPFIRE && s.lit() && s.reachable()))pendingFuelTicks=0;
            else return current=fuelCollection(mc);
        }
        Step step=FoodPreparation.next(facts,hunger,urgent,target);
        if(step.action()==Action.FIND_FUEL)step=fuelCollection(mc);
        if(step.action()==Action.BLOCKED && !lastInput.isEmpty() && facts.menu()==null && positions.containsKey(lastStation))
            step=new Step(Action.OPEN,"Open the cooking block to check food last seen there.","Closed furnace contents are not verified; output has not been confirmed.","",0,lastStation,0);
        if(step.action()==Action.BLOCKED && facts.menu()==null && lastInput.isEmpty() && facts.readyNutrition()<target){
            var unchecked=facts.stations().stream().filter(s->s.method()!=Method.CAMPFIRE && s.reachable() && !emptyChecked.contains(s.id())).findFirst().orElse(null);
            if(unchecked!=null)step=new Step(unchecked.inReach()?Action.OPEN:Action.APPROACH,"Check the nearby cooking block for food.",
                    "Closed contents are unknown until you open it.","",0,unchecked.id(),0);
        }
        if(step.action()==Action.BLOCKED && !lastInput.isEmpty() && facts.menu()==null && lastStation.isEmpty())
            step=new Step(Action.BLOCKED,"Open the cooking block you used to check the food.","Its closed contents and location are not verified.","",0,"",0);
        return current=step;
    }
    private Step fuelCollection(Minecraft mc){
        if(!facts.gatheringSafe())return new Step(Action.BLOCKED,"Keep cover; no carried fuel is available. Gather it when conditions are safe.","Coal is not required, but current fuel is missing.","",0,"",0);
        Set<String> logs=new HashSet<>();Map<String,String> drops=new HashMap<>();
        for(Item item:BuiltInRegistries.ITEM){
            var stack=new ItemStack(item);
            if(stack.is(net.minecraft.tags.ItemTags.LOGS) && item instanceof BlockItem block && mc.level.fuelValues().isFuel(stack)){
                String source=BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();logs.add(source);drops.put(source,BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        var target=TargetFinder.findGuideTarget(mc,logs,12,6,false,Pathfinder.guideWorld(mc));
        if(target==null || target.route().status()!=NavigationPlanner.Status.REACHABLE_NOW)return new Step(Action.BLOCKED,
                "Look for another fuel source; no checked nearby wood route is available.","No destination is shown for an inaccessible source.","",0,"",0);
        String source=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target.position()).getBlock()).toString(),item=drops.get(source);
        int burn=mc.level.fuelValues().burnDuration(new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(item)).orElseThrow()));
        int count=Math.max(1,(Math.max(1,pendingFuelTicks)+Math.max(1,burn)-1)/Math.max(1,burn));
        return new Step(Action.CRAFT,"","Collect nearby reachable wood fuel. Its burn time comes from this world's fuel data.",item,count,"",count);
    }
    private static BlockPos findPlacement(Minecraft mc,Item item){
        var terrain=Pathfinder.guideWorld(mc);BlockPos center=mc.player.blockPosition();
        for(int radius=2;radius<=3;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            BlockPos pos=center.offset(x,0,z),support=pos.below();
            var cell=terrain.voxel(Pathfinder.cell(pos));var floor=terrain.voxel(Pathfinder.cell(support));
            if(!cell.known() || !cell.passable() || cell.hazard() || !floor.support() || floor.hazard() || floor.falling()
                    || !mc.level.getBlockState(pos).canBeReplaced() || !mc.level.getWorldBorder().isWithinBounds(pos))continue;
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(mc.player.getEyePosition(),Vec3.atCenterOf(support).add(0,.499,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,mc.player));
            if(hit.getType()!=HitResult.Type.BLOCK || !hit.getBlockPos().equals(support) || hit.getDirection()!=Direction.UP
                    || mc.player.getEyePosition().distanceTo(hit.getLocation())>mc.player.blockInteractionRange()-.25)continue;
            boolean openArea=true;
            for(var direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
                var side=pos.relative(direction);var foot=terrain.voxel(Pathfinder.cell(side));var head=terrain.voxel(Pathfinder.cell(side.above()));var under=terrain.voxel(Pathfinder.cell(side.below()));
                if(!foot.known() || !head.known() || !foot.passable() || !head.passable() || foot.hazard() || head.hazard() || !under.support() || under.hazard() || under.falling())openArea=false;
            }
            if(!openArea)continue;
            var context=new net.minecraft.world.item.context.BlockPlaceContext(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(item),hit);
            var state=((BlockItem)item).getBlock().defaultBlockState();
            if(context.getClickedPos().equals(pos) && context.canPlace() && state.canSurvive(mc.level,pos)
                    && mc.level.isUnobstructed(state,pos,net.minecraft.world.phys.shapes.CollisionContext.of(mc.player)))return pos;
        }return null;
    }
    public void visuals(Minecraft mc,Step step){
        if(step==null || !positions.containsKey(step.station()) || mc.gui.screen()!=null)return;
        BlockPos pos=positions.get(step.station());
        if(step.action()==Action.PLACE){
            ShelterBuildMarker.show(new EmergencyShelterPlanner.Instruction(EmergencyShelterPlanner.Action.PLACE,Pathfinder.cell(pos),Pathfinder.cell(pos.below()),false,1,1,step.instruction(),step.why(),EmergencyShelterPlanner.Strategy.AUTO));
        }else if(step.action()==Action.APPROACH){
            var route=routes.get(step.station());if(route!=null)PathGuide.showRoute(mc,route.walkPath().stream().map(Pathfinder::block).toList(),pos,route.marker()==null?null:Pathfinder.block(route.marker()));
        }else TargetHighlighter.setTarget(pos);
    }
}
