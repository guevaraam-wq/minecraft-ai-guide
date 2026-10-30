package minecraft_ai_guide.client.goals;

import java.util.ArrayDeque;
import java.util.Deque;

/** Shared screen transitions are independent of Minecraft rendering for regression checks. */
public final class TaskMenuModel {
    public enum Page { HOME, CATEGORIES, BROWSE, DETAIL, CONTROLS, MILESTONES, CANCEL, SHELTER, FOOD_SOURCES }
    private Page page=Page.HOME;
    private final Deque<Page> history=new ArrayDeque<>();
    public Page page(){return page;}
    public void open(Page next){history.push(page);page=next;}
    public void back(){page=history.isEmpty()?Page.HOME:history.pop();}
    public void home(){history.clear();page=Page.HOME;}
    public static int choicesPerPage(int height){return height>=300?2:1;}
}
