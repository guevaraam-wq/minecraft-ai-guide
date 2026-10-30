package minecraft_ai_guide.client.goals;

import java.util.*;
import minecraft_ai_guide.client.guide.NavigationPlanner.Cell;

/** Pure interpretation of observed time, entities and shelter. No player controls. */
public final class EnvironmentalSurvival {
    public enum TimeBand { DAY, DUSK, NIGHT, NO_CYCLE }
    public enum Disposition { HOSTILE, CONDITIONAL, PASSIVE, UNKNOWN }
    public enum FoodAnimal { NONE, COW, PIG, CHICKEN, SHEEP }
    public record Mob(String id,String name,Disposition disposition,double distance,boolean visible,
            boolean targeting,boolean insideShelter,boolean agitated,FoodAnimal food,String direction) {}
    public record Threat(Mob mob,int score,boolean immediate) {}
    public record FoodSource(String instruction,String explanation) {}
    public record ShelterAdvice(String instruction,String reason,Cell placement,List<Cell> path,Cell destination,String recipeItem,int recipeAmount,EmergencyShelterPlanner.Instruction build){
        public ShelterAdvice(String instruction,String reason,Cell placement,List<Cell> path,Cell destination){this(instruction,reason,placement,path,destination,"",0,null);}
        public ShelterAdvice(String instruction,String reason,Cell placement,List<Cell> path,Cell destination,String recipeItem,int recipeAmount){this(instruction,reason,placement,path,destination,recipeItem,recipeAmount,null);}
        public ShelterAdvice {path=List.copyOf(path);}
    }
    public record Environment(TimeBand time,boolean protectedHere,Threat threat,ShelterAdvice shelter,
            FoodSource food,int buildingMaterials,boolean peaceful){
        public Environment(TimeBand time,boolean protectedHere,Threat threat,ShelterAdvice shelter,FoodSource food,int buildingMaterials){
            this(time,protectedHere,threat,shelter,food,buildingMaterials,false);
        }
        public static Environment quiet(){return new Environment(TimeBand.NO_CYCLE,false,null,null,null,0);}
        public boolean needsShelter(){return shelter!=null;}
        public boolean immediate(){return threat!=null && threat.immediate();}
        public boolean nightRisk(){return !peaceful && (time==TimeBand.DUSK || time==TimeBand.NIGHT);}
    }
    /** Only the normal Overworld cycle is interpreted; fixed/custom dimension clocks remain unknown. */
    public static final class DayClock {
        private TimeBand previous=TimeBand.DAY;
        public void reset(){previous=TimeBand.DAY;}
        public TimeBand update(boolean normalCycle,long ticks){
            if(!normalCycle){previous=TimeBand.NO_CYCLE;return previous;}
            long time=Math.floorMod(ticks,24_000);
            if(previous==TimeBand.NIGHT && (time<1_000 || time>=13_000))return previous;
            if(previous==TimeBand.DUSK && time>=10_500 && time<13_000)return previous;
            return previous=time>=13_000?TimeBand.NIGHT:time>=11_000?TimeBand.DUSK:TimeBand.DAY;
        }
    }
    public static boolean closedDoorProtection(boolean iron,boolean wooden,boolean hardDifficulty){
        return iron || wooden && !hardDifficulty;
    }
    public static Disposition classify(boolean enemy,boolean monsterCategory,boolean neutral,
            boolean animal,boolean targetsPlayer,boolean daylightSpider){
        if(targetsPlayer)return Disposition.HOSTILE;
        if(neutral || daylightSpider)return Disposition.CONDITIONAL;
        if(enemy || monsterCategory)return Disposition.HOSTILE;
        return animal?Disposition.PASSIVE:Disposition.UNKNOWN;
    }
    public static Threat threat(Mob m,boolean protectedHere){
        if(m.distance()>20)return null;
        if(m.disposition()==Disposition.PASSIVE && !m.targeting())return null;
        boolean immediate=(m.targeting() && (m.visible() && m.distance()<=16 || m.insideShelter() || m.distance()<=4 && !protectedHere))
                || m.disposition()==Disposition.HOSTILE && (m.insideShelter() || m.visible() && m.distance()<=8);
        if(protectedHere && !m.insideShelter() && !m.visible() && !m.targeting())return null;
        if(!immediate && !m.visible() && !(m.disposition()==Disposition.HOSTILE && m.distance()<=8 && !protectedHere))return null;
        if(m.disposition()==Disposition.UNKNOWN && !m.targeting())return null;
        if(m.disposition()==Disposition.CONDITIONAL && !m.targeting() && !m.agitated() && m.distance()>4)return null;
        if(m.disposition()==Disposition.CONDITIONAL && !m.targeting() && m.distance()>8)return null;
        int score=(immediate?100:0)+(m.targeting()?40:0)+(m.disposition()==Disposition.HOSTILE?30:0)
                +(m.agitated()?15:0)+(int)Math.max(0,20-m.distance());
        return new Threat(m,score,immediate);
    }
    public static final class ThreatTracker {
        private String chosen="";
        public void reset(){chosen="";}
        public Threat choose(List<Mob> mobs,boolean protectedHere){
            var threats=mobs.stream().map(m->threat(m,protectedHere)).filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(Threat::score).reversed().thenComparing(t->t.mob().id())).toList();
            if(threats.isEmpty()){chosen="";return null;}
            var best=threats.getFirst();
            var old=threats.stream().filter(t->t.mob().id().equals(chosen)).findFirst().orElse(null);
            if(old!=null && old.immediate()==best.immediate() && best.score()-old.score()<20)best=old;
            chosen=best.mob().id();return best;
        }
    }
    public static FoodSource foodSource(List<Mob> mobs,Threat threat){
        var sources=nearbyFoodSources(mobs);return threat!=null || sources.isEmpty()?null:sources.getFirst();
    }
    public static List<FoodSource> nearbyFoodSources(List<Mob> mobs){
        // Information remains available independently of HUD survival priority.
        var unique=new java.util.HashSet<FoodAnimal>(); // Do not invite animal collection beside a potential attacker.
        return mobs.stream().filter(m->m.food()!=FoodAnimal.NONE && m.disposition()==Disposition.PASSIVE
                        && !m.targeting() && !m.agitated() && m.visible() && m.distance()<=16)
                .sorted(Comparator.comparingDouble(Mob::distance).thenComparing(Mob::id)).filter(m->unique.add(m.food())).map(m->new FoodSource("Look for the "+m.name()+" "+m.direction()+", about "+Math.max(1,(int)Math.ceil(m.distance()))+" blocks away.",switch(m.food()){
                    case COW->"Cows can provide beef. Cook raw beef before eating.";
                    case PIG->"Pigs can provide pork. Cook raw pork before eating.";
                    case CHICKEN->"Chickens can provide chicken. Cook raw chicken before eating.";
                    case SHEEP->"Sheep can provide mutton. Cook raw mutton before eating.";
                    default->"";
                })).toList();
    }
    /** Do not send a sheltered player outdoors, or mine their shelter, for a temporary recipe. */
    public static String coveredRecipeInstruction(Environment environment,minecraft_ai_guide.client.guide.RecipeGuidance.Step step){
        if(step==null || !environment.nightRisk() || !(environment.protectedHere()
                || environment.shelter()!=null && environment.shelter().recipeItem().equals("minecraft:torch")))return "";
        if(step.action()==minecraft_ai_guide.client.guide.RecipeGuidance.Action.COLLECT
                || step.action()==minecraft_ai_guide.client.guide.RecipeGuidance.Action.BLOCKED)
            return "Keep cover; this recipe still needs "+minecraft_ai_guide.client.guide.RecipeGuidance.name(step.item())+". Gather it when you can leave safely.";
        if(step.action()==minecraft_ai_guide.client.guide.RecipeGuidance.Action.GO_TO_TABLE)
            return "Keep cover; the crafting table is not in reach here.";
        return "";
    }
    public static String threatInstruction(Threat t){
        return t.mob().name()+" nearby. "+(t.mob().disposition()==Disposition.CONDITIONAL && !t.mob().targeting()
                ?"Leave it alone and keep your distance.":"Move away toward cover; avoid fighting.");
    }
    private EnvironmentalSurvival(){}
}
