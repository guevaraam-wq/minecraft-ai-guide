package minecraft_ai_guide.client.goals;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.shapes.Shapes;

/** Stable geometry for an EMPTY placement cell, a mining cell or a standing space; no particles. */
public final class ShelterBuildMarker {
    private static EmergencyShelterPlanner.Instruction instruction;
    private static boolean initialized;
    public static void show(EmergencyShelterPlanner.Instruction value){instruction=value;}
    public static void clear(){instruction=null;}
    public static void initialize(){
        if(initialized)return;initialized=true;
        LevelRenderEvents.BEFORE_GIZMOS.register(context->{
            var step=instruction;Minecraft mc=Minecraft.getInstance();
            if(step==null || step.target()==null || mc.level==null || mc.player==null || mc.gui.screen()!=null)return;
            boolean mine=step.action()==EmergencyShelterPlanner.Action.MINE;
            boolean place=step.action()==EmergencyShelterPlanner.Action.PLACE || step.action()==EmergencyShelterPlanner.Action.JUMP_PLACE;
            var pose=context.poseStack();var camera=context.levelState().cameraRenderState.pos;var c=step.target();
            pose.pushPose();pose.translate(c.x()-camera.x,c.y()-camera.y,c.z()-camera.z);
            var shape=place || mine?Shapes.box(.015,.015,.015,.985,.985,.985):Shapes.box(.12,.02,.12,.88,.08,.88);
            context.submitNodeCollector().submitShapeOutline(pose,shape,RenderTypes.SECONDARY_BLOCK_OUTLINE,0xFF101018,5F,true);
            context.submitNodeCollector().submitShapeOutline(pose,shape,RenderTypes.SECONDARY_BLOCK_OUTLINE,
                    mine?0xFFFFA020:place?0xFF70FFFF:0xFFFFFFFF,2F,true);
            // The white square is the actual supporting face to click, not a request to right-click air.
            if(place && step.support()!=null){
                int dx=c.x()-step.support().x(),dy=c.y()-step.support().y(),dz=c.z()-step.support().z();
                var face=dy!=0?Shapes.box(.2,dy>0?.002:.985,.2,.8,dy>0?.015:.998,.8)
                        :dx!=0?Shapes.box(dx>0?.002:.985,.2,.2,dx>0?.015:.998,.8,.8)
                        :Shapes.box(.2,.2,dz>0?.002:.985,.8,.8,dz>0?.015:.998);
                context.submitNodeCollector().submitShapeOutline(pose,face,RenderTypes.SECONDARY_BLOCK_OUTLINE,0xFFFFFFFF,3F,true);
            }
            pose.popPose();
        });
    }
    private ShelterBuildMarker(){}
}
