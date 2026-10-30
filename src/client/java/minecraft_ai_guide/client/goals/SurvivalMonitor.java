package minecraft_ai_guide.client.goals;

import java.util.*;
import static minecraft_ai_guide.client.goals.ProgressionTasks.*;

/** Stateful, read-only interruptions. No game controls, world writes, or research dependencies. */
public final class SurvivalMonitor {
    public enum Hazard { NONE, LAVA, FIRE, DROWNING, FALL, THREAT, DAMAGE }
    public enum Kind { SAFETY, SHELTER, RECOVERY, EAT, FOOD, PICKAXE, WEAPON, ARMOR, SHIELD, BLOCKS }
    public record Gear(String item,int remaining,int maximum,boolean equipped){
        public boolean worn(){return maximum>0 && (remaining<=10 || remaining/(double)maximum<=0.10);}
    }
    public record Observation(float health,int hunger,int foodItems,int nutrition,int armor,int blocks,
            Map<Kind,List<Gear>> gear,Hazard hazard,boolean busy,boolean survival,boolean alive,String dimension,EnvironmentalSurvival.Environment environment){
        public Observation(float health,int hunger,int foodItems,int nutrition,int armor,int blocks,Map<Kind,List<Gear>> gear,
                Hazard hazard,boolean busy,boolean survival,boolean alive,String dimension){
            this(health,hunger,foodItems,nutrition,armor,blocks,gear,hazard,busy,survival,alive,dimension,EnvironmentalSurvival.Environment.quiet());
        }
        public Observation{
            var copy=new EnumMap<Kind,List<Gear>>(Kind.class);gear.forEach((k,v)->copy.put(k,List.copyOf(v)));gear=Map.copyOf(copy);
        }
        public List<Gear> gear(Kind kind){return gear.getOrDefault(kind,List.of());}
        public boolean end(){return dimension.equals("minecraft:the_end");}
    }
    public record Interruption(Kind kind,String title,String why,Advice advice,boolean critical){
        public AdaptiveRecommendations.Step step(){return new AdaptiveRecommendations.Step("",title,why,advice);}
    }
    // Nutrition is hunger points across usable stacks, not occupied slots or a permanent stock flag.
    public static final int LOW_FOOD=12,RESTOCK_FOOD=32,HUNGRY=14,FED=18;
    public static final long SETTLE_MS=2_000,SAFE_MS=8_000,DISMISS_MS=120_000;
    private record Dismissed(long until,Observation at){}
    private final Map<Kind,Dismissed> dismissed=new EnumMap<>(Kind.class);
    private Interruption current;
    private Kind candidate;
    private long candidateSince,lastDanger=Long.MIN_VALUE/2,shelteredSince=-1;
    private Observation latest;
    private int foodDecrease;
    private boolean likelyAte,foodLatched;
    public boolean foodLatched(){return foodLatched;}
    public Interruption current(){return current;}
    public int foodDecrease(){return foodDecrease;}
    public boolean likelyAte(){return likelyAte;}
    public void suspend(){current=null;candidate=null;latest=null;shelteredSince=-1;}
    public void reset(){current=null;candidate=null;latest=null;dismissed.clear();lastDanger=Long.MIN_VALUE/2;foodDecrease=0;likelyAte=false;foodLatched=false;shelteredSince=-1;}
    public boolean dismiss(long now){
        if(current==null || current.critical() || latest==null)return false;
        if(current.kind()==Kind.FOOD)foodLatched=false;
        dismissed.put(current.kind(),new Dismissed(now+DISMISS_MS,latest));current=null;candidate=null;return true;
    }
    public Interruption update(GoalSelection goal,Snapshot snapshot,Observation o,long now){
        boolean first=latest==null;
        foodDecrease=latest==null?0:Math.max(0,latest.foodItems()-o.foodItems());
        likelyAte=latest!=null && foodDecrease>0 && o.hunger()>latest.hunger();latest=o;
        if(!goal.active() || goal.paused() || !o.survival() || !o.alive()){
            current=null;candidate=null;return null;
        }
        foodLatched=(foodLatched || goal.foodResupplyPending()) && o.nutrition()<RESTOCK_FOOD;
        boolean buildCover=canBuildCover(o.environment());
        if(o.hazard()!=Hazard.NONE || o.environment().immediate() || o.health()<=6 && !buildCover || o.hunger()<=6 && o.nutrition()==0){
            lastDanger=now;current=safety(o);candidate=null;return current;
        }
        if(current!=null && current.critical() && !buildCover && (now-lastDanger<SAFE_MS || o.health()<10))return current;
        if(o.environment().protectedHere() && o.environment().nightRisk() && o.environment().needsShelter()
                && current!=null && current.kind()!=Kind.SAFETY && current.kind()!=Kind.RECOVERY && current.kind()!=Kind.EAT && current.kind()!=Kind.SHELTER)current=null;
        if(current!=null && current.kind()==Kind.SHELTER && !o.environment().needsShelter()){
            if(shelteredSince<0)shelteredSince=now;
            if(now-shelteredSince<SAFE_MS)return current=text(Kind.SHELTER,o.environment().protectedHere()?"Stay in cover":"Check surroundings","Conditions are settling after the shelter warning.",
                    o.environment().protectedHere()?"Stay in the checked cover while nearby conditions settle.":"Pause briefly while nearby conditions settle.");
        }else shelteredSince=-1;
        if(current!=null && resolved(current.kind(),o,snapshot,goal))current=null;
        if(current!=null && current.critical())current=null;
        // A known threat stops a construction instruction even while the player is using an item.
        if(o.environment().threat()!=null && o.environment().shelter()!=null && current!=null && current.kind()==Kind.SHELTER)
            return current=shelterInterruption(o.environment().shelter());
        // Ordinary needs wait until using/crafting/mining finishes. Critical warnings never wait.
        if(o.busy())return current;
        Interruption next=choose(goal,snapshot,o);
        if(next!=null && suppressed(next.kind(),o,now))next=null;
        if(current!=null && (next==null || priority(next.kind())<=priority(current.kind()))){
            // Refresh quantities after a damaged item breaks, while retaining the latched need.
            if(next!=null && next.kind()==current.kind())current=next;
            return current;
        }
        if(next==null){candidate=null;return null;}
        if(candidate!=next.kind()){candidate=next.kind();candidateSince=first?now-SETTLE_MS:now;}
        if(now-candidateSince>=SETTLE_MS){current=next;candidate=null;if(current.kind()==Kind.FOOD)foodLatched=true;}
        return current;
    }
    private boolean suppressed(Kind kind,Observation o,long now){
        var d=dismissed.get(kind);if(d==null)return false;
        var old=d.at();boolean worse=switch(kind){
            case FOOD -> (o.nutrition()<=4 && old.nutrition()>4) || (old.nutrition()>0 && o.nutrition()<=old.nutrition()/2) || o.hunger()<=old.hunger()-4;
            case SHELTER -> o.environment().time()==EnvironmentalSurvival.TimeBand.NIGHT && old.environment().time()!=EnvironmentalSurvival.TimeBand.NIGHT
                    || o.environment().threat()!=null && (old.environment().threat()==null || o.environment().threat().score()>old.environment().threat().score()+20)
                    || old.environment().protectedHere() && !o.environment().protectedHere();
            case EAT,RECOVERY -> o.health()<=old.health()-4 || o.hunger()<=old.hunger()-4;
            case PICKAXE,WEAPON,SHIELD -> bestRemaining(old,kind)>0 && bestRemaining(o,kind)<=bestRemaining(old,kind)/2;
            case ARMOR -> o.armor()<old.armor() || worstArmor(o)<worstArmor(old)/2;
            case BLOCKS -> old.blocks()>0 && o.blocks()<=old.blocks()/2;
            default -> true;
        };
        if(now>=d.until() || worse){dismissed.remove(kind);return false;}return true;
    }
    private static int worstArmor(Observation o){return o.gear(Kind.ARMOR).stream().filter(Gear::equipped).mapToInt(Gear::remaining).min().orElse(0);}
    private static int bestRemaining(Observation o,Kind kind){return o.gear(kind).stream().mapToInt(Gear::remaining).max().orElse(0);}
    private static boolean healthy(Observation o,Kind kind){return o.gear(kind).stream().anyMatch(g->!g.worn());}
    private int priority(Kind kind){return switch(kind){case SAFETY->100;case SHELTER->latest!=null && latest.environment().protectedHere()?65:95;case RECOVERY->90;case EAT->80;case WEAPON->75;case FOOD->70;default->40;};}
    public boolean resolved(Kind kind,Observation o,Snapshot s,GoalSelection goal){return switch(kind){
        case SAFETY -> o.hazard()==Hazard.NONE && !o.environment().immediate() && o.health()>=10;
        case SHELTER -> !o.environment().needsShelter();
        case RECOVERY -> o.health()>=18;
        case EAT -> o.hunger()>=FED;
        case FOOD -> o.nutrition()>=RESTOCK_FOOD;
        case PICKAXE -> healthy(o,kind) && (!dangerous(goal,s) || o.gear(kind).stream()
                .anyMatch(g->!g.worn() && !g.item().contains("wooden_") && !g.item().contains("golden_")));
        case WEAPON,SHIELD -> healthy(o,kind);
        case ARMOR -> o.armor()>=10 && o.gear(Kind.ARMOR).stream().filter(Gear::equipped).noneMatch(Gear::worn);
        case BLOCKS -> o.blocks()>=32;
    };}
    private static boolean dangerous(GoalSelection goal,Snapshot s){
        return goal.selected().equals("dragon") || s.nether() || s.end()
                || task(goal.selected()).category()==Category.NETHER || task(goal.selected()).category()==Category.THE_END;
    }
    private Interruption choose(GoalSelection goal,Snapshot s,Observation o){
        var environment=o.environment();
        boolean holding=environment.needsShelter() && environment.protectedHere() && environment.shelter().build()!=null
                && environment.shelter().build().action()==EmergencyShelterPlanner.Action.COMPLETE;
        if(environment.needsShelter() && !holding)return shelterInterruption(environment.shelter());
        if(o.health()<=12 || (current!=null && current.kind()==Kind.RECOVERY || goal.survivalNeed().equals("RECOVERY")) && o.health()<18){
            String instruction=o.hunger()>=FED?"Rest in a sheltered place while your health recovers."
                    :o.foodItems()>0?"Eat food from a sheltered place to help recovery.":o.end()?"Check your supplies from shelter; food sources here are limited.":"Gather usable food in a safe area to help recovery.";
            return text(Kind.RECOVERY,"Recover health","Your health is low.",instruction);
        }
        if((o.hunger()<=HUNGRY || goal.survivalNeed().equals("EAT") && o.hunger()<FED) && o.foodItems()>0)return text(Kind.EAT,"Eat food","Your hunger is low.","Eat some food from a safe place.");
        if(holding)return shelterInterruption(environment.shelter());
        if(o.nutrition()<=LOW_FOOD || (foodLatched || goal.survivalNeed().equals("FOOD")) && o.nutrition()<RESTOCK_FOOD){
            if(environment.nightRisk() && environment.protectedHere()){
                if(!healthy(o,Kind.WEAPON))return weapon(s,"A pickaxe is for mining. Prepare a weapon from cover; avoid fighting.");
                return text(Kind.FOOD,"Plan food resupply","Food is low, but leaving cover at night may be dangerous.",
                        "Stay in cover until daylight before gathering more food.");
            }
            if(environment.food()!=null && !healthy(o,Kind.WEAPON))return weapon(s,"A pickaxe is for mining. Prepare a weapon before choosing to collect meat.");
            if(environment.food()!=null)return text(Kind.FOOD,"Replenish food",environment.food().explanation(),environment.food().instruction());
            return text(Kind.FOOD,"Replenish food","Your food supplies are running low.",
                    o.end()?"Check your carried supplies for food from a sheltered spot; food sources here are limited.":"Gather usable food in a safe area for the rest of your trip.");
        }
        if((environment.nightRisk() || environment.threat()!=null) && !healthy(o,Kind.WEAPON))
            return weapon(s,"A pickaxe is for mining. Prepare a weapon from cover; avoiding combat is safer.");
        boolean danger=dangerous(goal,s);
        // Do not send players into a crafting checklist during an End fight.
        if(o.end()){
            if(o.armor()<10 || o.gear(Kind.ARMOR).stream().filter(Gear::equipped).anyMatch(Gear::worn))
                return text(Kind.ARMOR,"Check protective equipment","Your armor may not protect you well enough.","Check for usable armor from a sheltered spot; crafting may not be practical here.");
            if(!healthy(o,Kind.WEAPON))return text(Kind.WEAPON,"Check your weapon","A usable weapon is needed for this fight.","Check for a usable spare weapon from a sheltered spot.");
            return null;
        }
        for(Kind kind:List.of(Kind.PICKAXE,Kind.WEAPON)){
            boolean usable=healthy(o,kind) && (!danger || kind!=Kind.PICKAXE || o.gear(kind).stream()
                    .anyMatch(g->!g.worn() && !g.item().contains("wooden_") && !g.item().contains("golden_")));
            if((danger || !o.gear(kind).isEmpty()) && !usable){
                String item=kind==Kind.PICKAXE?"minecraft:stone_pickaxe":"minecraft:stone_sword";
                boolean missing=o.gear(kind).isEmpty();
                boolean worn=o.gear(kind).stream().allMatch(Gear::worn);
                return new Interruption(kind,kind==Kind.PICKAXE?"Prepare a usable pickaxe":"Prepare a usable weapon",
                        missing?(kind==Kind.WEAPON?"A pickaxe is for mining. A sword or axe is better for defense.":"You need this equipment before a dangerous trip."):worn?"Your remaining tool is nearly broken.":"A stronger pickaxe is useful for gathering ore.",
                        new Advice("",item,s.inventory().getOrDefault(item,0)+1,"Recipe prerequisites are shown one at a time."),false);
            }
        }
        if(danger && (o.armor()<10 || o.gear(Kind.ARMOR).stream().filter(Gear::equipped).anyMatch(Gear::worn))){
            var worn=o.gear(Kind.ARMOR).stream().filter(g->g.equipped() && g.worn()).findFirst();
            if(worn.isPresent())return text(Kind.ARMOR,"Replace worn armor","A piece of your worn armor may break soon.",
                    "Replace or repair your "+minecraft_ai_guide.client.guide.RecipeGuidance.name(worn.get().item())+" in a safe place.");
            return new Interruption(Kind.ARMOR,"Prepare protective armor","Your equipped armor is low for a dangerous trip.",advice(task("armor"),s,0),false);
        }
        if(danger && !healthy(o,Kind.SHIELD))return new Interruption(Kind.SHIELD,"Prepare a usable shield",
                "A usable shield helps protect you on this trip.",new Advice("","minecraft:shield",s.count("shield")+1,""),false);
        if(danger && o.blocks()<16)return text(Kind.BLOCKS,"Replenish building blocks","Your building supplies are running low.","Gather ordinary solid building blocks until you have 32.");
        return null;
    }
    public static boolean canBuildCover(EnvironmentalSurvival.Environment environment){
        if(environment.protectedHere() || environment.immediate() || environment.shelter()==null || environment.shelter().build()==null || environment.shelter().build().exit())return false;
        return switch(environment.shelter().build().action()){
            case PLACE,JUMP_PLACE,MINE,ENTER,WAIT->true;default->false;
        };
    }
    private static Interruption shelterInterruption(EnvironmentalSurvival.ShelterAdvice shelter){
        String title=shelter.build()!=null?shelter.build().exit()?"Open the shelter exit":shelter.build().action()==EmergencyShelterPlanner.Action.COMPLETE?"Wait in shelter":"Build emergency shelter"
                :shelter.placement()!=null?"Close the shelter opening":shelter.recipeItem().equals("minecraft:torch")?"Light the shelter":"Find shelter";
        return new Interruption(Kind.SHELTER,title,shelter.reason(),new Advice(shelter.instruction(),shelter.recipeItem(),shelter.recipeAmount(),""),false);
    }
    private static Interruption weapon(Snapshot s,String why){return new Interruption(Kind.WEAPON,"Prepare a usable weapon",why,
            new Advice("","minecraft:stone_sword",s.count("stone_sword")+1,"Minecraft recipes provide the crafting prerequisites."),false);}
    private static Interruption text(Kind kind,String title,String why,String instruction){return new Interruption(kind,title,why,new Advice(instruction,"",0,""),false);}
    private static Interruption safety(Observation o){
        String instruction=switch(o.hazard()){
            case LAVA -> "Move out of the lava toward safe ground if you can.";
            case FIRE -> "Move away from fire toward a safe place.";
            case DROWNING -> "Reach air before doing anything else.";
            case FALL -> "Focus on reaching safe footing before continuing.";
            case THREAT,DAMAGE -> "Move to cover away from attacks before continuing.";
            case NONE -> o.health()<=6?o.hunger()>=FED?"Rest in a sheltered place now; your health is very low."
                    :o.foodItems()>0?"Eat food in a sheltered place now; your health is very low."
                    :"Seek food from a sheltered area; your health is very low.":"Seek food in a sheltered area; your hunger is very low.";
        };
        if(o.hazard()==Hazard.NONE && o.environment().threat()!=null)instruction=EnvironmentalSurvival.threatInstruction(o.environment().threat());
        else if(o.hazard()==Hazard.NONE && o.environment().shelter()!=null && o.environment().shelter().build()!=null
                && o.environment().shelter().build().action()==EmergencyShelterPlanner.Action.BLOCKED)
            instruction=o.environment().shelter().instruction();
        return new Interruption(Kind.SAFETY,"Get to safety",o.hazard()==Hazard.NONE && o.environment().immediate()
                ?o.environment().threat().mob().name()+" is a nearby threat. Avoid combat.":"Your safety comes before the current step.",new Advice(instruction,"",0,""),true);
    }
}
