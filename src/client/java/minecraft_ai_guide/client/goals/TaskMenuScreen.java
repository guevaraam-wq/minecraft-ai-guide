package minecraft_ai_guide.client.goals;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Small, predictable pages; all actions are native focusable/narrated buttons. */
public final class TaskMenuScreen extends Screen {
    private final TaskGoalController controller;
    private final TaskMenuModel navigation=new TaskMenuModel();
    private String candidate="dragon";
    private ProgressionTasks.Category category;
    private int page;
    private final List<String> lines=new ArrayList<>();
    private final List<Button> buttons=new ArrayList<>();
    private int left,wide;
    public TaskMenuScreen(TaskGoalController controller){super(Component.literal("Minecraft guide"));this.controller=controller;}
    private String heading(){return switch(navigation.page()){
        case HOME->"Your goal";case CATEGORIES->"Browse all goals";case BROWSE->category==null?"Suggested goals":label(category.name());
        case DETAIL->"Choose a goal";case CONTROLS->"Goal controls";case MILESTONES->"Steps toward the dragon";case CANCEL->"Cancel your goal?";case SHELTER->"Emergency shelter options";case FOOD_SOURCES->"Nearby food sources";};}
    private static String label(String value){String s=value.toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private void open(TaskMenuModel.Page target){navigation.open(target);page=0;rebuildWidgets();}
    private void button(String text,int y,Runnable action){
        Button b=new GuideButton(text,left,y,wide,ignored->action.run());
        b.setTooltip(Tooltip.create(Component.literal(text)));
        buttons.add(addRenderableWidget(b));
    }
    private void explain(String text){lines.add(text);}
    @Override protected void init(){
        controller.refreshMenuAdvice(net.minecraft.client.Minecraft.getInstance());
        lines.clear();buttons.clear();left=Math.max(12,(width-400)/2);wide=Math.min(400,width-24);
        int y=height-148;
        switch(navigation.page()){
            case HOME -> {
                explain(controller.title());
                explain(controller.selection().paused()?"Paused. Your progress is saved.":!controller.selection().active()?"Choose a goal when you are ready.":(controller.temporary()?"Temporary: ":"Next: ")+controller.nextStep().title());
                if(controller.selection().active() && !controller.selection().paused())explain("Why: "+controller.why());
                button(!controller.selection().active()?"Start Goal":controller.selection().paused()?"Resume Goal":"Pause Goal",y,()->{
                    if(!controller.selection().active())open(TaskMenuModel.Page.CATEGORIES);else{controller.pause();rebuildWidgets();}
                });
                button(controller.selection().active()?"Change Goal":"Browse All Goals",y+28,()->open(TaskMenuModel.Page.CATEGORIES));
                if(controller.shelterOptionsAvailable())button("Emergency shelter options",y+56,()->open(TaskMenuModel.Page.SHELTER));
                else button("Ender Dragon milestones",y+56,()->open(TaskMenuModel.Page.MILESTONES));
                button("More controls / suggestions",y+84,()->open(TaskMenuModel.Page.CONTROLS));
            }
            case CONTROLS -> {
                explain(controller.title());explain(controller.temporary()?controller.canDismissSurvival()?"You can set this suggestion aside for two minutes.":"Safety warnings stay visible while danger remains.":"Cancel stops guidance. Your world progress stays saved.");
                if(controller.selection().active())button(controller.selection().paused()?"Resume Goal":"Pause Goal",y,()->{controller.pause();rebuildWidgets();});
                if(controller.canDismissSurvival())button("Dismiss suggestion for 2 minutes",y+28,()->{controller.dismissSurvival();navigation.home();rebuildWidgets();});
                else button("Change Goal",y+28,()->open(TaskMenuModel.Page.CATEGORIES));
                if(controller.selection().active())button("Cancel Goal...",y+56,()->{controller.selection().requestCancel();open(TaskMenuModel.Page.CANCEL);});
                button("Equipment reminders: "+(controller.selection().reminders()?"On":"Off"),y+84,()->{controller.toggleReminders();rebuildWidgets();});
            }
            case FOOD_SOURCES -> {
                var sources=controller.nearbyFoodSources();
                if(sources.isEmpty()){explain("No supported food animals are visible within 16 blocks.");explain("You can look around from cover. This does not mean there are no animals elsewhere.");}
                else{page=Math.min(page,sources.size()-1);var source=sources.get(page);explain(source.instruction());explain(source.explanation());}
                explain("Follow safety warnings before collecting food. This view does not change your goal.");
                button("Read current guide instruction",height-132,()->controller.readCurrentInstruction(net.minecraft.client.Minecraft.getInstance()));
                button("Refresh nearby food sources",height-100,()->rebuildWidgets());
                paging(Math.max(1,sources.size()));
            }
            case SHELTER -> {
                explain("You place and mine each block. Your main goal stays saved.");
                explain("Choose another design if jumping or digging is unsuitable.");
                button("Choose safest available design",y,()->{controller.shelterStrategy(EmergencyShelterPlanner.Strategy.AUTO);navigation.home();rebuildWidgets();});
                button("Try a compact box",y+28,()->{controller.shelterStrategy(EmergencyShelterPlanner.Strategy.FREESTANDING);navigation.home();rebuildWidgets();});
                button("Try a hillside recess",y+56,()->{controller.shelterStrategy(EmergencyShelterPlanner.Strategy.HILLSIDE);navigation.home();rebuildWidgets();});
                button("Keep my current plan",y+84,()->{navigation.home();rebuildWidgets();});
            }
            case CANCEL -> {
                explain("Cancel "+ProgressionTasks.task(controller.selection().selected()).title()+"?");
                explain("This stops guidance. It keeps your verified progress and preferences.");
                button("Keep my goal",y+28,()->{controller.cancel(false);back();});
                button("Yes, cancel goal",y+64,()->{controller.cancel(true);navigation.home();rebuildWidgets();});
            }
            case CATEGORIES -> {
                explain("Choose a group. You can change your choice later.");
                var categories=ProgressionTasks.Category.values();int count=2,total=(categories.length+1+count-1)/count;
                for(int row=0;row<count;row++){
                    int index=page*count+row;if(index>categories.length)break;
                    final var chosen=index==0?null:categories[index-1];
                    button(index==0?"Suggested goals":label(chosen.name()),96+row*40,()->{category=chosen;open(TaskMenuModel.Page.BROWSE);});
                }
                paging(total);
            }
            case BROWSE -> {
                explain("Choose a goal to read about it before starting.");
                List<ProgressionTasks.Task> tasks=category==null?controller.suggestions()
                        :ProgressionTasks.TASKS.stream().filter(t->t.category()==category).toList();
                int count=TaskMenuModel.choicesPerPage(height),pages=Math.max(1,(tasks.size()+count-1)/count);page=Math.min(page,pages-1);
                for(int row=0;row<count && page*count+row<tasks.size();row++){
                    var task=tasks.get(page*count+row);
                    button(task.title(),96+row*40,()->{candidate=task.id();open(TaskMenuModel.Page.DETAIL);});
                    buttons.getLast().setTooltip(Tooltip.create(Component.literal(ProgressionTasks.recommendationReason(task,controller.snapshot()))));
                }
                paging(pages);
            }
            case DETAIL -> {
                var task=ProgressionTasks.task(candidate);explain(task.title());explain(ProgressionTasks.recommendationReason(task,controller.snapshot()));
                button("Start Goal: "+task.title(),y+28,()->{controller.choose(candidate);navigation.home();rebuildWidgets();});
                if(controller.selection().active() && controller.selection().selected().equals("dragon") && !candidate.equals("dragon"))
                    button("Use as next step; keep dragon goal",y+64,()->{controller.preferStep(candidate);navigation.home();rebuildWidgets();});
            }
            case MILESTONES -> {
                var milestones=ProgressionTasks.Milestone.values();page=Math.min(page,milestones.length-1);
                var view=AdaptiveRecommendations.display(milestones[page],controller.snapshot());
                explain(view.title());explain("Status: "+switch(view.state()){case COMPLETED->"Completed";case NEXT->"Next — available choice";case NOT_READY->"Not Ready";case UNKNOWN->"Unknown";});
                explain(view.explanation());
                button("Read about this step",height-132,()->{candidate=view.taskId();open(TaskMenuModel.Page.DETAIL);});
                button(controller.selection().active() && controller.selection().selected().equals("dragon")?"Keep my dragon goal":"Start Goal: Defeat the Ender Dragon",height-100,()->{
                    if(!controller.selection().active() || !controller.selection().selected().equals("dragon"))controller.choose("dragon");navigation.home();rebuildWidgets();
                });
                paging(milestones.length);
            }
        }
        // Footer positions and order never change across screens.
        var back=addRenderableWidget(Button.builder(Component.literal(navigation.page()==TaskMenuModel.Page.HOME?"Nearby food sources":"Back"),b->{
            if(navigation.page()==TaskMenuModel.Page.HOME)open(TaskMenuModel.Page.FOOD_SOURCES);else back();
        }).bounds(left,height-28,(wide-12)/2,24).build());
        back.active=true;
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(left+(wide+12)/2,height-28,(wide-12)/2,24).build());
        if(!buttons.isEmpty())setInitialFocus(buttons.getFirst());
    }
    private void paging(int total){
        int half=(wide-12)/2;
        var prev=addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;rebuildWidgets();}).bounds(left,height-60,half,24).build());prev.active=page>0;
        var next=addRenderableWidget(Button.builder(Component.literal("Next ("+(page+1)+" / "+total+")"),b->{page++;rebuildWidgets();}).bounds(left+half+12,height-60,half,24).build());next.active=page+1<total;
    }
    private void back(){if(navigation.page()==TaskMenuModel.Page.CANCEL)controller.cancel(false);navigation.back();page=0;rebuildWidgets();}
    @Override public void onClose(){controller.cancel(false);super.onClose();}
    @Override public Component getNarrationMessage(){return Component.literal(heading()+". "+String.join(". ",lines)+". "+(controller.temporary()?controller.nextStep().advice().instruction():"")+". "+controller.notice()+". Tab or arrows to move. Enter to choose. Escape to close.");}
    @Override public boolean isPauseScreen(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fill(0,0,width,height,0xF0101824);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        graphics.pose().pushMatrix();graphics.pose().scale(1.3f,1.3f);
        graphics.text(font,Component.literal(heading()),(int)(left/1.3f),8,0xFFFFFFFF,true);
        graphics.pose().popMatrix();
        int bottom=switch(navigation.page()){case HOME,CONTROLS,CANCEL,DETAIL,SHELTER->height-154;case MILESTONES->height-140;case FOOD_SOURCES->height-140;default->88;};
        int y=34;
        for(String text:lines)for(var line:font.split(Component.literal(text),wide)){
            if(y+10>bottom)break;
            graphics.text(font,line,left,y,0xFFFFFFFF,true);y+=12;
        }
        if(!controller.notice().isEmpty()){
            // The full notice is included in screen narration.
            graphics.text(font,Component.literal("Save notice: "+font.plainSubstrByWidth(controller.notice(),wide-70)),left,24,0xFFFFFF80,true);
        }
    }
}
