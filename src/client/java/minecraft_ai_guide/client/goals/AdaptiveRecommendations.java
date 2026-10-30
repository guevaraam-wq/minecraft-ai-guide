package minecraft_ai_guide.client.goals;

import java.util.*;
import static minecraft_ai_guide.client.goals.ProgressionTasks.*;

/** Read-only readiness rules. Crafting dependencies remain in Minecraft's recipe solver. */
public final class AdaptiveRecommendations {
    public record Step(String taskId, String title, String why, Advice advice) {}
    public enum DisplayState { COMPLETED, NEXT, NOT_READY, UNKNOWN }
    public record MilestoneView(String title, String explanation, DisplayState state, String taskId) {}
    private static boolean tool(Snapshot s,String suffix){
        return s.inventory().entrySet().stream().anyMatch(e->e.getValue()>0 && e.getKey().endsWith(suffix));
    }
    public static boolean complete(String id,Snapshot s,int biomes){
        return switch(id){
            case "food" -> s.food()>=8;
            case "eat" -> s.hunger()>=18 && s.health()>=18;
            case "blocks" -> s.buildingBlocks()>=32;
            case "pickaxe" -> toolTier(s,"pickaxe");
            case "weapon" -> toolTier(s,"sword") || toolTier(s,"axe");
            case "armor" -> s.armor()>=10;
            case "stone_axe" -> toolTier(s,"axe");
            case "table" -> s.nearbyTable() || s.count("crafting_table")>0;
            case "bed" -> tool(s,"_bed");
            case "biomes" -> biomes>=3;
            default -> {
                Milestone m=milestoneFor(id);
                if(m!=null)yield milestone(m,s).state()==State.DONE;
                Task t=task(id);yield !t.item().isEmpty() && s.inventory().getOrDefault(t.item(),0)>=t.amount();
            }
        };
    }
    private static boolean toolTier(Snapshot s,String kind){
        return List.of("stone","copper","iron","diamond","netherite").stream().anyMatch(t->s.count(t+"_"+kind)>0);
    }
    /** Readiness is a recommendation, never permission to control the player or proof of safety. */
    public static String preparation(Snapshot s){
        if((s.hunger()<18 || s.health()<18) && (s.food()>0 || s.hunger()>=18))return "eat";
        if(s.food()<8)return "food";
        // In an arena, do not send the player into a crafting checklist during a fight.
        if(s.end())return "";
        if(!complete("pickaxe",s,0))return "pickaxe";
        if(!complete("weapon",s,0))return "weapon";
        if(s.armor()<10)return "armor";
        if(s.count("shield")==0)return "shield";
        if(s.buildingBlocks()<32)return "blocks";
        return "";
    }
    public static Step next(GoalSelection selection,Snapshot s){return next(selection,s,false);}
    public static Step next(GoalSelection selection,Snapshot s,boolean survivalManaged){
        if(!selection.active())return new Step("","No active goal","Choose a goal when you are ready.",new Advice("Open Tasks to start a goal.","",0,""));
        if(selection.paused())return new Step("","Goal paused","Your progress is saved.",new Advice("Your goal is paused. Open Tasks to resume.","",0,""));
        String main=selection.selected();
        if(complete(main,s,selection.biomeCount()))return step(main,new Advice("This goal is complete. You can choose another goal.","",0,""),"Your current evidence confirms this goal.");
        String preferred=selection.preferredStep();
        if(!preferred.isEmpty() && !complete(preferred,s,selection.biomeCount()))return step(preferred,advice(task(preferred),s,selection.biomeCount()),"You chose this step. Your main goal stays the same.");
        String resume=selection.survivalResumeStep();
        if(survivalManaged && !resume.isEmpty() && !complete(resume,s,selection.biomeCount()))
            return step(resume,resume.equals("dragon")?dragonProgressAdvice(s):advice(task(resume),s,selection.biomeCount()),"Continue your unfinished step.");
        if(main.equals("dragon")){
            String prep=survivalManaged?"":preparation(s);
            if(!prep.isEmpty())return step(prep,advice(task(prep),s,selection.biomeCount()),reason(prep));
            String id=s.end()?"crystals":s.endPortal()?"enter_end":s.frameRingKnown()?"portal":s.count("ender_eye")>=12?"stronghold"
                    :s.count("blaze_rod")*2+s.count("blaze_powder")>=12?"pearls":s.nether()?"blaze":"nether";
            int needed=Math.max(0,(s.frameRingKnown()?s.missingEyes():12)-s.count("ender_eye"));
            if(!s.end() && !s.endPortal() && !s.frameRingKnown() && needed>0){
                if(s.count("ender_pearl")>=needed && s.count("blaze_rod")*2+s.count("blaze_powder")>=needed)id="eyes";
                else if(s.count("blaze_rod")*2+s.count("blaze_powder")>=needed)id="pearls";
                else id=s.nether()?"blaze":"nether";
            }
            if(s.end() && s.crystalsKnown() && s.crystals()==0)id="dragon";
            return step(id,id.equals("dragon")?dragonProgressAdvice(s):advice(task(id),s,selection.biomeCount()),task(id).why());
        }
        return step(main,advice(task(main),s,selection.biomeCount()),task(main).why());
    }
    /** Browse suggestions use the same live nutrition policy as the active HUD. */
    public static List<Task> suggestions(GoalSelection goal,Snapshot s,SurvivalMonitor.Observation observation,
            SurvivalMonitor.Interruption interruption){
        if(observation==null)return ProgressionTasks.recommendations(s,goal.biomeCount());
        var result=new LinkedHashMap<String,Task>();
        if(interruption!=null){
            String id=switch(interruption.kind()){
                case FOOD->"food";case EAT,RECOVERY->"eat";case PICKAXE->"pickaxe";case WEAPON->"weapon";
                case ARMOR->"armor";case SHIELD->"shield";case BLOCKS->"blocks";default->"";
            };
            if(!id.isEmpty())result.put(id,task(id));
        }else if(goal.active() && !goal.paused()){
            var base=next(goal,s,true);
            if(!base.taskId().isEmpty() && !complete(base.taskId(),s,goal.biomeCount()))result.put(base.taskId(),task(base.taskId()));
        }
        for(var t:ProgressionTasks.recommendations(s,goal.biomeCount())){
            if(t.id().equals("food") && observation.nutrition()>SurvivalMonitor.LOW_FOOD && !goal.foodResupplyPending())continue;
            if(t.id().equals("eat") && observation.hunger()>SurvivalMonitor.HUNGRY && observation.health()>12)continue;
            result.putIfAbsent(t.id(),t);
        }
        return result.values().stream().limit(3).toList();
    }
    private static Step step(String id,Advice advice,String why){return new Step(id,task(id).title(),why,advice);}
    private static String reason(String id){return switch(id){
        case "eat" -> "Recover before taking a dangerous trip.";
        case "food" -> "You need food before exploring dangerous areas.";
        case "pickaxe" -> "A sturdy pickaxe helps you gather supplies for the trip.";
        case "weapon" -> "A weapon helps you defend yourself while exploring.";
        case "armor" -> "Armor reduces damage during dangerous exploration.";
        case "shield" -> "A shield helps protect you from attacks.";
        default -> "Building blocks help you cross gaps and make shelter.";
    };}
    public static Milestone milestoneFor(String id){return switch(id){
        case "nether_portal"->Milestone.NETHER_PORTAL;case "nether"->Milestone.ENTER_NETHER;case "blaze"->Milestone.BLAZE_SUPPLY;
        case "pearls"->Milestone.PEARL_SUPPLY;case "eyes"->Milestone.EYES_READY;case "stronghold"->Milestone.FIND_STRONGHOLD;
        case "portal"->Milestone.ACTIVATE_END_PORTAL;case "enter_end"->Milestone.ENTER_END;
        case "crystals"->Milestone.CLEAR_CRYSTALS;case "dragon"->Milestone.DEFEAT_DRAGON;default->null;};}
    public static String goalFor(Milestone m){return TASKS.stream().filter(t->milestoneFor(t.id())==m).findFirst().orElseThrow().id();}
    public static MilestoneView display(Milestone m,Snapshot s){
        String id=goalFor(m);Task t=task(id);Evidence e=milestone(m,s);
        if(e.state()==State.DONE)return new MilestoneView(t.title(),e.reason(),DisplayState.COMPLETED,id);
        if(e.state()==State.UNKNOWN)return new MilestoneView(t.title(),"We cannot verify this yet. "+t.why(),DisplayState.UNKNOWN,id);
        boolean ready=preparation(s).isEmpty() && switch(m){
            case NETHER_PORTAL -> !s.end();case ENTER_NETHER -> s.netherPortal();case BLAZE_SUPPLY -> s.nether();
            case PEARL_SUPPLY -> !s.end();case EYES_READY -> s.count("ender_pearl")>0 && (s.count("blaze_rod")>0 || s.count("blaze_powder")>0);
            case FIND_STRONGHOLD -> s.count("ender_eye")>0 && !s.nether() && !s.end();
            case ACTIVATE_END_PORTAL -> s.frameRingKnown();case ENTER_END -> s.endPortal();
            case CLEAR_CRYSTALS, DEFEAT_DRAGON -> s.end();};
        return new MilestoneView(t.title(),t.why()+(ready?"":" Prepare supplies or find the needed place first."),ready?DisplayState.NEXT:DisplayState.NOT_READY,id);
    }
    private AdaptiveRecommendations(){}
}
