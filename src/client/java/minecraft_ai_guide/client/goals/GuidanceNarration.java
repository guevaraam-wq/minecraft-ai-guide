package minecraft_ai_guide.client.goals;

/** Deduplicate changed guidance; no per-tick voice flood. Minecraft still owns narrator preferences. */
public final class GuidanceNarration {
    private String last="";
    private long spoken=Long.MIN_VALUE/2;
    public void reset(){last="";spoken=Long.MIN_VALUE/2;}
    public boolean shouldSpeak(String message,boolean critical,long now){
        if(message.isBlank() || message.equals(last) || now-spoken<(critical?2_000:8_000))return false;
        last=message;spoken=now;return true;
    }
}
