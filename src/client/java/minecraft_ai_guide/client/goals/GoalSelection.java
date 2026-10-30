package minecraft_ai_guide.client.goals;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Independent accessibility save data; never reads/writes Q-tables or experiment metrics. */
public final class GoalSelection {
    private String selected="stone_axe";
    private final Set<String> paused=new HashSet<>();
    private final Set<String> biomes=new TreeSet<>();
    private final Map<String,String> observations=new TreeMap<>();
    private boolean reminders;
    private boolean active=true, cancelRequested;
    private String preferredStep="";
    private String survivalResumeStep="",survivalNeed="";
    private boolean foodResupplyPending;
    private EmergencyShelterPlanner.Strategy shelterStrategy=EmergencyShelterPlanner.Strategy.AUTO;
    public EmergencyShelterPlanner.Strategy shelterStrategy(){return shelterStrategy;}
    public void shelterStrategy(EmergencyShelterPlanner.Strategy strategy){shelterStrategy=java.util.Objects.requireNonNull(strategy);}
    public boolean foodResupplyPending(){return foodResupplyPending;}
    public void foodResupplyPending(boolean value){foodResupplyPending=value;}
    public String survivalNeed(){return survivalNeed;}
    public void survivalNeed(String kind){if(!kind.isEmpty())SurvivalMonitor.Kind.valueOf(kind);survivalNeed=kind;}
    public String survivalResumeStep(){return survivalResumeStep;}
    public void survivalResumeStep(String id){if(!id.isEmpty())ProgressionTasks.task(id);survivalResumeStep=id;}
    public boolean active(){return active;}
    public String preferredStep(){return preferredStep;}
    public void preferStep(String id){ProgressionTasks.task(id);preferredStep=id;}
    public void clearStep(){preferredStep="";survivalResumeStep="";survivalNeed="";foodResupplyPending=false;}
    public void start(String id){select(id);active=true;pause(false);clearStep();cancelRequested=false;}
    public void requestCancel(){cancelRequested=active;}
    public boolean cancelRequested(){return cancelRequested;}
    public boolean confirmCancel(boolean confirmed){
        boolean cancel=cancelRequested && confirmed;cancelRequested=false;
        if(cancel){active=false;clearStep();}return cancel;
    }
    public String selected(){return selected;}
    public boolean paused(){return paused.contains(selected);}
    public boolean reminders(){return reminders;}
    public int biomeCount(){return biomes.size();}
    public Map<String,String> observations(){return Map.copyOf(observations);}
    public void select(String id){ProgressionTasks.task(id);selected=id;}
    public void pause(boolean value){if(value)paused.add(selected);else paused.remove(selected);}
    public void reminders(boolean value){reminders=value;}
    public boolean observeBiome(String biome){return !biome.isBlank() && biomes.add(biome);}
    public boolean observe(String id,String evidence){return observations.putIfAbsent(id,evidence)==null;}
    public static GoalSelection load(Path file)throws IOException{
        GoalSelection state=new GoalSelection();
        if(!Files.exists(file))return state;
        Properties p=new Properties();
        try(var reader=Files.newBufferedReader(file)){p.load(reader);}
        catch(IllegalArgumentException e){throw new IOException("Malformed accessibility save; original preserved",e);}
        if(!"accessibility-goals-v1".equals(p.getProperty("format")))throw new IOException("Unknown accessibility save format; original preserved");
        try{
            state.select(p.getProperty("selected","stone_axe"));
            state.active=Boolean.parseBoolean(p.getProperty("active","true"));
            state.shelterStrategy(EmergencyShelterPlanner.Strategy.valueOf(p.getProperty("shelterStrategy","AUTO")));
            state.foodResupplyPending=Boolean.parseBoolean(p.getProperty("foodResupplyPending","false"));
            state.survivalNeed(p.getProperty("survivalNeed", ""));
            state.survivalResumeStep(p.getProperty("survivalResumeStep", ""));
            String step=p.getProperty("preferredStep", "");if(!step.isEmpty())state.preferStep(step);
            state.reminders=Boolean.parseBoolean(p.getProperty("reminders","false"));
            for(String key:p.stringPropertyNames()){
                if(key.startsWith("paused.")){String id=key.substring(7);ProgressionTasks.task(id);if(Boolean.parseBoolean(p.getProperty(key)))state.paused.add(id);}
                if(key.startsWith("biome."))state.biomes.add(p.getProperty(key));
                if(key.startsWith("observed."))state.observations.put(key.substring(9),p.getProperty(key));
            }
        }catch(RuntimeException e){throw new IOException("Invalid accessibility save; original preserved",e);}
        return state;
    }
    public void save(Path file)throws IOException{
        Properties p=new Properties();p.setProperty("format","accessibility-goals-v1");p.setProperty("selected",selected);
        p.setProperty("reminders",Boolean.toString(reminders));
        p.setProperty("shelterStrategy",shelterStrategy.name());
        p.setProperty("foodResupplyPending",Boolean.toString(foodResupplyPending));
        p.setProperty("survivalResumeStep",survivalResumeStep);p.setProperty("survivalNeed",survivalNeed);
        p.setProperty("active",Boolean.toString(active));p.setProperty("preferredStep",preferredStep);
        paused.forEach(id->p.setProperty("paused."+id,"true"));
        int index=0;for(String biome:biomes)p.setProperty("biome."+index++,biome);
        observations.forEach((id,evidence)->p.setProperty("observed."+id,evidence));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"goals-",".tmp");
        try{
            try(var writer=Files.newBufferedWriter(temp)){p.store(writer,"Accessibility goals; observations are history, not current readiness");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
