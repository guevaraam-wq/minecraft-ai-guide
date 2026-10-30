package minecraft_ai_guide.client.goals;

import java.util.*;

/** Pure read-only cooking decisions. Recipes/fuels/menu contents are supplied by Minecraft observation. */
public final class FoodPreparation {
    public enum Method { FURNACE, SMOKER, CAMPFIRE }
    public record Recipe(String input,String output,int outputCount,int nutrition,int ticks,Method method){}
    public record Station(String id,Method method,boolean reachable,boolean inReach,boolean lit,boolean safePlacement,
            int cookingItems,String reason,String cookingInput,int readyDrops,String dropItem){
        public Station(String id,Method method,boolean reachable,boolean inReach,boolean lit,boolean safePlacement,int cookingItems,String reason,String cookingInput){this(id,method,reachable,inReach,lit,safePlacement,cookingItems,reason,cookingInput,0,"");}
        public Station(String id,Method method,boolean reachable,boolean inReach,boolean lit,boolean safePlacement,int cookingItems,String reason){this(id,method,reachable,inReach,lit,safePlacement,cookingItems,reason,"",0,"");}
    }
    public record Menu(Method method,String input,int inputCount,String fuel,int fuelCount,String output,int outputCount,
            boolean lit,float progress,boolean known){}
    public record Facts(Map<String,Integer> inventory,Map<String,Integer> nutrition,List<Recipe> recipes,Map<String,Integer> fuels,
            List<Station> stations,Menu menu,int readyNutrition,String readyItem,String emergencyRaw,boolean gatheringSafe,
            boolean cookingSafe,boolean unknownRecipes,Set<Method> craftable){
        public Facts(Map<String,Integer> inventory,Map<String,Integer> nutrition,List<Recipe> recipes,Map<String,Integer> fuels,
                List<Station> stations,Menu menu,int readyNutrition,String readyItem,String emergencyRaw,boolean gatheringSafe,boolean cookingSafe,boolean unknownRecipes){
            this(inventory,nutrition,recipes,fuels,stations,menu,readyNutrition,readyItem,emergencyRaw,gatheringSafe,cookingSafe,unknownRecipes,Set.of());
        }
        public Facts {inventory=Map.copyOf(inventory);nutrition=Map.copyOf(nutrition);recipes=List.copyOf(recipes);fuels=Map.copyOf(fuels);stations=List.copyOf(stations);craftable=Set.copyOf(craftable);}
        public int count(String id){return inventory.getOrDefault(id,0);}
    }
    public enum Action { EAT, CRAFT, PLACE, APPROACH, OPEN, INPUT, FUEL, FIND_FUEL, WAIT, TAKE, TAKE_INPUT, CAMPFIRE_INPUT, PICK_UP, BLOCKED, COMPLETE }
    public record Step(Action action,String instruction,String why,String recipeItem,int recipeAmount,String station,int amount){}
    public static final Set<String> RAW_FALLBACK=Set.of("minecraft:beef","minecraft:porkchop","minecraft:mutton","minecraft:rabbit",
            "minecraft:chicken","minecraft:cod","minecraft:salmon","minecraft:potato","minecraft:kelp");
    public static boolean raw(String id,List<Recipe> recipes){return RAW_FALLBACK.contains(id) || recipes.stream().anyMatch(r->r.input().equals(id));}
    public static int quantity(int ready,int target,Recipe recipe){return Math.max(1,(Math.max(0,target-ready)+recipe.nutrition()*recipe.outputCount()-1)/(recipe.nutrition()*recipe.outputCount()));}
    private static Step text(Action action,String instruction,String why,String station,int amount){return new Step(action,instruction,why,"",0,station,amount);}
    private static Step craft(String item,String why){return new Step(Action.CRAFT,"",why,item,1,"",1);}
    public static Step next(Facts f,int hunger,boolean urgent,int target){
        if(hunger<18 && !f.readyItem().isEmpty())return text(Action.EAT,"Eat your "+name(f.readyItem())+".","This food is ready to eat; cooking can wait.","",1);
        if(urgent){
            if(!f.emergencyRaw().isEmpty())return text(Action.EAT,"Eat your raw "+name(f.emergencyRaw())+" from cover if you cannot wait.","Raw food restores less hunger; cook it when conditions improve.","",1);
            return text(Action.BLOCKED,"Stay in cover and look for ready-to-eat food; cooking takes time.","No ready food is currently carried.","",0);
        }
        if(!f.cookingSafe())return text(Action.BLOCKED,"Find cover before preparing food.","Cooking should not distract you from danger.","",0);
        Menu menu=f.menu();
        // A visible output is evidence. Opening a station is never completion.
        if(menu!=null && menu.known() && menu.outputCount()>0 && f.nutrition().getOrDefault(menu.output(),0)>0 && !raw(menu.output(),f.recipes()))
            return text(Action.TAKE,"Take the "+name(menu.output())+" from the right-hand result slot.","The cooked output is visible. Collect it before continuing.","",menu.outputCount());
        if(f.readyNutrition()>=target)return text(Action.COMPLETE,"Your ready-to-eat food supply is prepared.","Current inventory confirms the food quantity.","",0);
        Station dropped=f.stations().stream().filter(s->s.readyDrops()>0 && s.reachable()).findFirst().orElse(null);
        if(dropped!=null)return text(Action.PICK_UP,"Collect the "+name(dropped.dropItem())+" by the campfire without stepping on it.",
                "Ready food items are visible on the ground. Supply is confirmed after pickup.",dropped.id(),dropped.readyDrops());
        List<Recipe> possible=f.recipes().stream().filter(r->r.nutrition()>0 && (f.count(r.input())>0
                || menu!=null && menu.known() && menu.input().equals(r.input()) && menu.inputCount()>0
                || f.stations().stream().anyMatch(s->s.method()==r.method() && s.cookingItems()>0 && s.cookingInput().equals(r.input()))))
                .sorted(Comparator.<Recipe>comparingInt(r->f.stations().stream().anyMatch(s->s.method()==r.method() && s.reachable() && (s.method()!=Method.CAMPFIRE || s.lit()))?0
                        :f.count(r.method()==Method.CAMPFIRE?"minecraft:campfire":"minecraft:furnace")>0?1:2)
                        .thenComparingInt(r->f.craftable().contains(r.method())?0:1)
                        .thenComparingInt(r->r.method()==Method.CAMPFIRE && chooseFuel(f).isEmpty()?0:r.method()==Method.FURNACE?1:2).thenComparing(Recipe::input)).toList();
        if(possible.isEmpty())return text(Action.BLOCKED,f.unknownRecipes()?"Cooking recipes are not available here. Check the game's recipe book.":"Look for ready food or a food source nearby.",
                f.unknownRecipes()?"Full cooking guidance currently needs local recipe access.":"No supported raw ingredient or visible cooking input is available.","",0);
        Recipe recipe=possible.stream().filter(r->menu!=null && menu.known() && r.method()==menu.method() && r.input().equals(menu.input())).findFirst().orElse(possible.getFirst());
        int needed=quantity(f.readyNutrition(),target,recipe);
        if(menu!=null && menu.known() && recipe.method()==menu.method()){
            if(!menu.input().isEmpty() && !menu.input().equals(recipe.input()))return text(Action.BLOCKED,"Check the left-hand input slot; it holds a different item.","The guide will not assume it can replace someone else's cooking input.","",0);
            if(menu.inputCount()==0)return text(Action.INPUT,"Put "+Math.min(needed,f.count(recipe.input()))+" "+name(recipe.input())+" in the left-hand input slot.",
                    "Raw food goes in the top-left slot. Cooked food restores more hunger.","",Math.min(needed,f.count(recipe.input())));
            if(menu.lit() || menu.progress()>0 && menu.fuelCount()>0)return text(Action.WAIT,"Wait while the food cooks.","The open cooking screen confirms heat or cooking progress.","",Math.min(needed,menu.inputCount()));
            if(menu.fuelCount()>0 && f.fuels().getOrDefault(menu.fuel(),0)>0)return text(Action.WAIT,"Wait for the furnace to start cooking.","Valid fuel and raw input are visible. If it stays stopped, check the slots.","",0);
            String fuel=chooseFuel(f);
            if(fuel.isEmpty())return fuelNeed(f);
            if(menu.fuelCount()>0)return text(Action.BLOCKED,"Take the unusable item out of the bottom-left fuel slot.","That item is not valid fuel here.","",0);
            int amount=Math.max(1,(recipe.ticks()*Math.min(needed,menu.inputCount())+f.fuels().get(fuel)-1)/f.fuels().get(fuel));
            return text(Action.FUEL,"Put "+Math.min(amount,f.count(fuel))+" "+name(fuel)+" in the bottom-left fuel slot.","This item is valid furnace fuel in this world; coal is not required.","",Math.min(amount,f.count(fuel)));
        }
        if(menu!=null && menu.known())return text(Action.BLOCKED,"Close this cooking screen before choosing another station.","This station does not match the food recipe being prepared.","",0);
        Station station=f.stations().stream().filter(s->s.method()==recipe.method() && s.reachable() && (s.method()!=Method.CAMPFIRE || s.lit())).findFirst().orElse(null);
        if(station!=null){
            if(!station.inReach())return text(Action.APPROACH,"Walk toward the checked "+stationName(station.method())+".","Only a checked reachable approach is shown.",station.id(),needed);
            if(station.method()==Method.CAMPFIRE){
                int batch=Math.min(4,needed);
                if(station.cookingItems()>=batch || f.count(recipe.input())==0)return text(Action.PICK_UP,"Wait beside the campfire, then collect cooked food when it drops.","Food is visible cooking; remaining time is not exposed. Completion needs cooked food in inventory.",station.id(),needed);
                return text(Action.CAMPFIRE_INPUT,"Put one "+name(recipe.input())+" on the lit campfire.","This batch needs "+batch+" pieces; "+station.cookingItems()+" are visible cooking. Do not stand on the fire.",station.id(),Math.min(f.count(recipe.input()),batch-station.cookingItems()));
            }
            return text(Action.OPEN,"Open the nearby "+stationName(station.method())+".","The cooking slots can be verified after you open it.",station.id(),needed);
        }
        String equipment=recipe.method()==Method.CAMPFIRE?"minecraft:campfire":"minecraft:furnace";
        if(f.count(equipment)>0){
            if(recipe.method()==Method.CAMPFIRE && !f.gatheringSafe())return text(Action.BLOCKED,"Keep cover; do not place a fire inside your shelter.","Use an already suitable cooking block or wait until it is safe outside.","",0);
            Station place=f.stations().stream().filter(s->s.id().startsWith("place:") && s.safePlacement()).findFirst().orElse(null);
            if(place==null)return text(Action.BLOCKED,"Keep cover; no safe placement space is verified nearby.","The cooking block must not block your body, shelter or exit.","",0);
            return text(Action.PLACE,"Place your "+name(equipment)+" at the marked safe position.","Verify the placed block before using it.",place.id(),1);
        }
        return craft(equipment,"Use the loaded Minecraft recipe for this cooking block; skip ingredients already carried.");
    }
    public static String chooseFuel(Facts f){return f.inventory().entrySet().stream().filter(e->e.getValue()>0 && f.fuels().getOrDefault(e.getKey(),0)>0)
            .sorted(Comparator.<Map.Entry<String,Integer>>comparingInt(e->e.getKey().contains("lava_bucket")?1:0)
                    .thenComparing(Comparator.comparingInt((Map.Entry<String,Integer> e)->f.fuels().get(e.getKey())).reversed()).thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey).findFirst().orElse("");}
    private static Step fuelNeed(Facts f){
        if(f.stations().stream().anyMatch(s->s.method()==Method.CAMPFIRE && s.lit() && s.reachable()))return text(Action.TAKE_INPUT,
                "Take the raw food out of the top-left slot to use the lit campfire instead.","A lit campfire needs no added furnace fuel.","",f.menu()==null?0:f.menu().inputCount());
        return text(f.gatheringSafe()?Action.FIND_FUEL:Action.BLOCKED,f.gatheringSafe()?"Gather a usable wood fuel, such as planks, or use coal or charcoal.":"Keep cover; no usable fuel is carried. Wait until gathering is safe.",
                "Available fuel is checked using this world's fuel rules. Coal is not assumed.","",0);
    }
    private static String stationName(Method m){return m==Method.SMOKER?"smoker":m==Method.CAMPFIRE?"campfire":"furnace";}
    public static String name(String id){return id.replace("minecraft:","").replace('_',' ');}
    private FoodPreparation(){}
}
