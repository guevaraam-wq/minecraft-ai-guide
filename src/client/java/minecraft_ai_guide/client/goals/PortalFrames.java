package minecraft_ai_guide.client.goals;

import net.minecraft.core.BlockPos;
import java.util.Map;

/** Count empty eyes only for a fully observed, correctly oriented 12-frame ring. */
public final class PortalFrames {
    public record Frame(String facing,boolean eye){}
    public record Ring(boolean known,int missingEyes){}
    public static Ring find(Map<BlockPos,Frame> frames){
        for(BlockPos frame:frames.keySet())for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            BlockPos center=frame.offset(dx,0,dz);int count=0,missing=0;
            for(int i=-1;i<=1;i++){
                BlockPos[] positions={center.offset(i,0,-2),center.offset(i,0,2),center.offset(-2,0,i),center.offset(2,0,i)};
                String[] facing={"south","north","east","west"};
                for(int j=0;j<4;j++){
                    Frame value=frames.get(positions[j]);
                    if(value!=null && value.facing().equals(facing[j])){count++;if(!value.eye())missing++;}
                }
            }
            if(count==12)return new Ring(true,missing);
        }
        return new Ring(false,-1);
    }
    private PortalFrames(){}
}
