package minecraft_ai_guide.client.goals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native button input/narration with a larger label and an explicit focus outline. */
final class GuideButton extends Button {
    GuideButton(String text,int x,int y,int width,OnPress action){super(x,y,width,24,Component.literal(text),action,DEFAULT_NARRATION);}
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xFF23354D);
        graphics.outline(getX(),getY(),getWidth(),getHeight(),0xFFB9C6D8);
        if(isHoveredOrFocused()){
            graphics.outline(getX()+2,getY()+2,getWidth()-4,getHeight()-4,0xFFFFFFFF);
            graphics.text(Minecraft.getInstance().font,">",getX()+6,getY()+8,0xFFFFFFFF,true);
        }
        var font=Minecraft.getInstance().font;float scale=1.2f;
        graphics.pose().pushMatrix();graphics.pose().scale(scale,scale);
        String text=font.plainSubstrByWidth(getMessage().getString(),(int)((getWidth()-36)/scale));
        graphics.centeredText(font,text,(int)((getX()+getWidth()/2f)/scale),(int)((getY()+7)/scale),0xFFFFFFFF);
        graphics.pose().popMatrix();
    }
}
