package minecraft_ai_guide.client.goals;

import java.util.*;

/** Minecraft goals and evidence, not an ordered crafting script or player controller. */
public final class ProgressionTasks {
    public enum Category { SURVIVAL, CRAFTING, EXPLORATION, BUILDING, NETHER, THE_END }
    public enum Milestone { NETHER_PORTAL, ENTER_NETHER, BLAZE_SUPPLY, PEARL_SUPPLY, EYES_READY,
        FIND_STRONGHOLD, ACTIVATE_END_PORTAL, ENTER_END, CLEAR_CRYSTALS, DEFEAT_DRAGON }
    public enum State { DONE, NEEDED, UNKNOWN }
    public record Evidence(State state, String reason) {}
    public record Task(String id, String title, Category category, String item, int amount, String why) {}
    public record Snapshot(Map<String,Integer> inventory, String dimension, float health, int hunger, int food,
            int armor, int buildingBlocks, boolean nearbyTable, boolean netherPortal, boolean endPortal,
            boolean frameRingKnown, int missingEyes, boolean authoritative, Set<String> advancements,
            boolean inStronghold, boolean inFortress, boolean crystalsKnown, int crystals,
            boolean dragonAlive, boolean worldDragonDefeated, String biome) {
        public Snapshot { inventory=Map.copyOf(inventory); advancements=Set.copyOf(advancements); }
        public int count(String id){return inventory.getOrDefault("minecraft:"+id,0);}
        public boolean nether(){return dimension.equals("minecraft:the_nether");}
        public boolean end(){return dimension.equals("minecraft:the_end");}
        public boolean advancement(String id){return advancements.contains("minecraft:"+id);}
    }
    public record Advice(String instruction, String recipeItem, int recipeAmount, String note) {}
    public static final List<Task> TASKS=List.of(
        new Task("food","Gather food",Category.SURVIVAL,"",8,"Food helps you recover and keep exploring."),
        new Task("eat","Eat and recover",Category.SURVIVAL,"",18,"Hunger and health affect what is safe to do next."),
        new Task("bed","Make a white bed",Category.SURVIVAL,"minecraft:white_bed",1,"A bed can set your spawn in the Overworld."),
        new Task("stone_axe","Make a stone axe",Category.CRAFTING,"minecraft:stone_axe",1,"An axe helps you collect wood."),
        new Task("table","Make a crafting table",Category.CRAFTING,"minecraft:crafting_table",1,"A table allows larger recipes."),
        new Task("furnace","Make a furnace",Category.CRAFTING,"minecraft:furnace",1,"A furnace supports cooking and smelting."),
        new Task("pickaxe","Make a stone pickaxe",Category.CRAFTING,"minecraft:stone_pickaxe",1,"A pickaxe helps you gather stone and ores."),
        new Task("weapon","Make a stone sword",Category.CRAFTING,"minecraft:stone_sword",1,"A weapon helps you defend yourself."),
        new Task("armor","Make an iron chestplate",Category.CRAFTING,"minecraft:iron_chestplate",1,"Armor reduces damage on dangerous trips."),
        new Task("shield","Make a shield",Category.CRAFTING,"minecraft:shield",1,"A shield can help you defend yourself."),
        new Task("biomes","Visit three Overworld biomes",Category.EXPLORATION,"",3,"Exploring can reveal food and resources."),
        new Task("blocks","Gather building blocks",Category.BUILDING,"",32,"Blocks are useful for paths and small builds."),
        new Task("nether_portal","Find a working Nether portal",Category.NETHER,"",1,"Portal access is checked separately from a past dimension visit."),
        new Task("nether","Visit the Nether",Category.NETHER,"",1,"The Nether contains resources for reaching the End."),
        new Task("blaze","Gather blaze rods",Category.NETHER,"",6,"Blaze rods can become powder for Eyes of Ender."),
        new Task("pearls","Gather ender pearls",Category.THE_END,"",12,"Pearls are one ingredient for Eyes of Ender."),
        new Task("eyes","Prepare Eyes of Ender",Category.THE_END,"minecraft:ender_eye",12,"Eyes can locate a stronghold and fill portal frames."),
        new Task("stronghold","Find a stronghold",Category.THE_END,"",1,"Strongholds contain End portal rooms."),
        new Task("portal","Activate an End portal",Category.THE_END,"",1,"A working End portal provides entry to the End."),
        new Task("enter_end","Enter the End",Category.THE_END,"",1,"Entry is verified by dimension or advancement, not by owning Eyes."),
        new Task("crystals","Clear the End crystals",Category.THE_END,"",1,"Arena crystals can heal the dragon."),
        new Task("dragon","Defeat the Ender Dragon",Category.THE_END,"",1,"A long-term goal with milestones you can work toward at your own pace.")
    );
    public static Task task(String id){return TASKS.stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown goal: "+id));}

    public static List<Task> recommendations(Snapshot s) { return recommendations(s,0); }
    public static List<Task> recommendations(Snapshot s,int visitedBiomes) {
        String prep=AdaptiveRecommendations.preparation(s);
        List<Task> result=new ArrayList<>(TASKS.stream().filter(t->(prep.isEmpty() || !Set.of("nether","blaze","pearls","eyes","stronghold","portal","enter_end","crystals").contains(t.id())) && !AdaptiveRecommendations.complete(t.id(),s,visitedBiomes) && priority(t,s)>0
                && !(t.id().equals("biomes") && visitedBiomes>=3)).toList());
        result.sort(Comparator.<Task>comparingInt(t->priority(t,s)).reversed().thenComparing(Task::id));
        if(!prep.isEmpty()){result.removeIf(t->t.id().equals(prep));result.addFirst(task(prep));}
        return result.stream().limit(3).toList();
    }
    private static int priority(Task t,Snapshot s){
        if(!t.item().isEmpty() && s.inventory().getOrDefault(t.item(),0)>=t.amount())return -1;
        if(t.id().equals("stone_axe") && s.inventory().entrySet().stream().anyMatch(e->e.getKey().endsWith("_axe") && e.getValue()>0))return -1;
        if((t.id().equals("blaze") || t.id().equals("pearls")) && s.count("ender_eye")>=12)return -1;
        if(t.id().equals("dragon") && s.advancement("end/kill_dragon"))return -1;
        return switch(t.id()){
            case "eat" -> (s.hunger()<18 || s.health()<18) && (s.food()>0 || s.hunger()>=18) ? 100:0;
            case "food" -> s.food()<8?90:0;
            case "stone_axe" -> s.count("stone_axe")==0 && !s.nether() && !s.end()?70:0;
            case "pickaxe", "weapon", "armor" -> 0;
            case "shield" -> s.armor()<10 && s.count("shield")==0?55:0;
            case "table" -> !s.nearbyTable() && s.count("crafting_table")==0?65:0;
            case "blocks" -> s.buildingBlocks()<32?40:0;
            case "blaze" -> s.count("blaze_rod")>=6?0:s.nether()?80:10;
            case "crystals" -> s.end() && s.dragonAlive() && (!s.crystalsKnown() || s.crystals()>0)?95:0;
            case "dragon" -> s.end()?85:25;
            case "portal" -> s.frameRingKnown() || s.inStronghold()?80:5;
            case "eyes" -> s.count("ender_pearl")>0 && (s.count("blaze_powder")>0 || s.count("blaze_rod")>0)?75:15;
            case "stronghold" -> s.inStronghold()?0:s.count("ender_eye")>0?75:5;
            case "nether_portal" -> !s.netherPortal() && !s.end()?s.nether()?55:10:0;
            case "enter_end" -> s.end()?0:s.endPortal()?80:5;
            case "nether" -> s.nether()?0:s.netherPortal() || (s.armor()>=10 && s.food()>=8)?60:10;
            case "pearls" -> s.count("ender_pearl")>=12?0:s.count("blaze_rod")>0 || s.count("blaze_powder")>0?65:10;
            case "bed" -> s.nether() || s.end() || s.inventory().entrySet().stream().anyMatch(e->e.getKey().endsWith("_bed") && e.getValue()>0)?0:15;
            default -> 15;
        };
    }
    public static String recommendationReason(Task task,Snapshot s){
        return switch(task.id()){
            case "eat" -> s.hunger()<14?"Your hunger is low.":s.health()<12?"Your health is low. Rest and recovery may help.":task.why();
            case "food" -> "You currently carry "+s.food()+" food items.";
            case "table" -> s.nearbyTable()?"A placed crafting table is already nearby.":task.why();
            case "bed" -> s.nether() || s.end()?"Beds explode here. This task only crafts a bed for later Overworld use.":task.why();
            case "blaze" -> s.inFortress()?"A Nether fortress is verified at your location.":s.nether()?"You are in the Nether; fortress location is not confirmed.":task.why();
            case "crystals" -> s.crystalsKnown()?s.crystals()+" arena crystals are currently verified.":"The End arena is not fully verified yet.";
            case "portal" -> s.frameRingKnown()?"A complete portal frame ring is visible; "+s.missingEyes()+" eyes are missing.":task.why();
            case "stone_axe" -> s.count("stone_axe")==0?"You do not currently carry a stone axe.":"You already carry a stone axe.";
            default -> task.why();
        };
    }
    public static Evidence milestone(Milestone m,Snapshot s){
        boolean done=switch(m){
            case NETHER_PORTAL -> s.netherPortal();
            case ENTER_NETHER -> s.nether() || s.advancement("story/enter_the_nether");
            case BLAZE_SUPPLY -> s.count("blaze_rod")>=6 || s.count("ender_eye")>=12;
            case PEARL_SUPPLY -> s.count("ender_pearl")>=12 || s.count("ender_eye")>=12;
            case EYES_READY -> s.count("ender_eye")>=12;
            case FIND_STRONGHOLD -> s.inStronghold() || s.advancement("story/follow_ender_eye");
            case ACTIVATE_END_PORTAL -> s.endPortal();
            case ENTER_END -> s.end() || s.advancement("story/enter_the_end");
            case CLEAR_CRYSTALS -> s.end() && s.crystalsKnown() && s.crystals()==0 && s.dragonAlive();
            case DEFEAT_DRAGON -> s.advancement("end/kill_dragon");
        };
        if(done) return new Evidence(State.DONE,switch(m){
            case ENTER_NETHER -> s.nether()?"You are currently in the Nether.":"Your player advancement records a previous Nether entry.";
            case ENTER_END -> s.end()?"You are currently in the End.":"Your player advancement records a previous End entry.";
            case FIND_STRONGHOLD -> s.inStronghold()?"The server structure check places you inside a stronghold.":"Your player advancement records a past stronghold visit; it does not verify your current location.";
            case DEFEAT_DRAGON -> "Your personal end/kill_dragon advancement is complete.";
            case CLEAR_CRYSTALS -> "The loaded active arena has no remaining crystals. This does not identify who destroyed them.";
            case NETHER_PORTAL, ACTIVATE_END_PORTAL -> "A working portal is currently observed nearby.";
            case BLAZE_SUPPLY, PEARL_SUPPLY -> s.count("ender_eye")>=12?"Current prepared Eyes already satisfy this supply need.":"Required items are in your inventory now.";
            default -> "Required items are in your inventory now.";
        });
        if((m==Milestone.ACTIVATE_END_PORTAL && !s.dimension().equals("minecraft:overworld"))
                || (m==Milestone.CLEAR_CRYSTALS && (!s.end() || !s.crystalsKnown()))
                || (m==Milestone.DEFEAT_DRAGON || m==Milestone.FIND_STRONGHOLD
                    || m==Milestone.ENTER_NETHER || m==Milestone.ENTER_END) && !s.authoritative())
            return new Evidence(State.UNKNOWN,"Required world or advancement evidence is unavailable; completion is not assumed.");
        return new Evidence(State.NEEDED,switch(m){
            case NETHER_PORTAL, ACTIVATE_END_PORTAL -> "No working portal is currently observed here; a portal elsewhere is not ruled out.";
            case BLAZE_SUPPLY, PEARL_SUPPLY, EYES_READY -> "Current inventory is insufficient. Past ownership does not satisfy this requirement.";
            default -> "No current verifying evidence. Historical item ownership is not enough.";
        });
    }
    public static Advice advice(Task task,Snapshot s,int biomes){
        if((task.id().equals("portal") || task.id().equals("enter_end")) && s.frameRingKnown() && s.count("ender_eye")<s.missingEyes())
            return new Advice("","minecraft:ender_eye",s.missingEyes(),"Prepare only the Eyes needed by this verified ring.");
        if(task.id().equals("armor") && s.armor()<10){
            String piece=s.armor()>=8?"iron_leggings":"iron_chestplate";
            if(s.count(piece)>0)return new Advice("Put on your "+piece.replace('_',' ')+".","",0,task.why());
            return new Advice("","minecraft:"+piece,1,task.why());
        }
        if(!task.item().isEmpty()) return new Advice("",task.item(),task.amount(),task.why());
        String text=switch(task.id()){
            case "food" -> s.food()>=8?"You have enough food for this goal.":"Gather more food. Keep at least eight food items for your trip.";
            case "eat" -> s.hunger()>=18 && s.health()>=18?"Your hunger and health are ready.":s.food()>0 && s.hunger()<20?"Eat some food to help recovery.":s.health()<18 && s.hunger()>=18?"Rest in a safe place while your health recovers.":"Find food before continuing.";
            case "blocks" -> s.buildingBlocks()>=32?"You have enough building blocks.":"Gather solid building blocks until you have 32.";
            case "biomes" -> biomes>=3?"You have visited three Overworld biomes.":"Explore the Overworld to find another biome.";
            case "nether_portal" -> s.netherPortal()?"A working Nether portal is nearby.":s.end()?"Nether portals are not available in the End.":"Look for a working Nether portal. A previously used portal is not verified here.";
            case "enter_end" -> s.end()?"You are in the End.":s.endPortal()?"Enter the active End portal when you are prepared.":portalAdvice(s);
            case "nether" -> s.nether()?"You are in the Nether.":s.netherPortal()?"Enter the nearby Nether portal when you are ready.":"Find or build a Nether portal. A lit portal is not visible here.";
            case "blaze" -> s.count("blaze_rod")>=6?"You have six blaze rods.":!s.nether()?"Travel to the Nether to look for blaze rods.":!s.inFortress()?"Look for a Nether fortress. Its location is not confirmed.":"Gather blaze rods from blazes. Detailed combat guidance is not available yet.";
            case "pearls" -> s.count("ender_pearl")>=12?"You have twelve ender pearls.":"Gather ender pearls. Trading with a cleric is one possible approach.";
            case "stronghold" -> !s.dimension().equals("minecraft:overworld")?"Return to the Overworld before using an Eye of Ender.":s.inStronghold()?"You are in a verified stronghold.":s.count("ender_eye")>0?"Use an Eye of Ender to find or return to a stronghold.":"Prepare an Eye of Ender before looking for a stronghold.";
            case "portal" -> portalAdvice(s);
            case "crystals" -> s.end() && s.crystalsKnown() && s.crystals()==0 && s.dragonAlive()
                    ?"No End crystals remain in the verified arena.":crystalsAdvice(s);
            case "dragon" -> "Choose a dragon milestone to see its next action.";
            default -> "Open Tasks to choose a goal.";
        };
        return new Advice(text,"",0,task.why());
    }
    public static Advice dragonAdvice(Snapshot s){
        String preparation=AdaptiveRecommendations.preparation(s);
        if(!s.advancement("end/kill_dragon") && !preparation.isEmpty())return advice(task(preparation),s,0);
        return dragonProgressAdvice(s);
    }
    static Advice dragonProgressAdvice(Snapshot s){
        if(milestone(Milestone.DEFEAT_DRAGON,s).state()==State.DONE)
            return new Advice("Your advancement confirms that you defeated the Ender Dragon.","",0,"");
        if(s.end()) return new Advice(crystalsAdvice(s),"",0,"Dragon combat coaching is an initial outline, not a complete combat assistant.");
        if(s.endPortal()) return new Advice("Enter the active End portal when you are prepared.","",0,"Food, armor, a weapon and building blocks are useful. You decide when to enter.");
        if(s.frameRingKnown()) {
            if(s.count("ender_eye")<s.missingEyes()) return new Advice("","minecraft:ender_eye",s.missingEyes(),"Only the empty frames in this observed ring need eyes.");
            return new Advice(portalAdvice(s),"",0,"");
        }
        if(s.count("ender_eye")>=12) return advice(task("stronghold"),s,0);
        // These are world-acquisition milestones; actual transformations and batch counts come from recipes.
        return new Advice("","minecraft:ender_eye",12,"Prepare supplies through recipe knowledge. You can choose another milestone in Tasks at any time.");
    }
    private static String portalAdvice(Snapshot s){
        if(s.endPortal()) return "An active End portal is nearby.";
        if(!s.dimension().equals("minecraft:overworld"))return "The Overworld portal frame is not observable in this dimension.";
        if(!s.frameRingKnown()) return "Find the complete End portal frame in a stronghold. Its state is not fully known here.";
        if(s.missingEyes()==0) return "Check the center of the frame. An active portal has not been observed yet.";
        if(s.count("ender_eye")==0) return "Prepare another Eye of Ender for an empty portal frame.";
        return "Place one Eye of Ender in an empty portal frame.";
    }
    private static String crystalsAdvice(Snapshot s){
        if(!s.end()) return "Enter the End before working on arena crystals.";
        if(s.worldDragonDefeated() && !s.dragonAlive())return "This world has a dragon victory; your own completion is not verified. Respawn guidance is not implemented.";
        if(!s.crystalsKnown()) return "Check the End arena from a safe position. Remaining crystals are not verified.";
        if(s.crystals()>0) return "Destroy one End crystal from a safe distance. Detailed aiming and cage guidance is not implemented yet.";
        if(s.dragonAlive()) return "The crystals are gone. Attack the dragon when it lands; detailed combat coaching is not implemented yet.";
        return s.worldDragonDefeated()?"This world has a past dragon victory; your own completion is not verified.":"Wait in a safe place while the dragon state is checked.";
    }
    private ProgressionTasks(){}
}
