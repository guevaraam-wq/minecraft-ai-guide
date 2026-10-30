package minecraft_ai_guide.client.guide;

import java.util.List;

/** Pure geometry in coordinates local to the actual footstep/destination block. */
public final class NavigationMarkers {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Marker(NavigationPlanner.Cell origin, int color, float width, List<Box> boxes) {
        public Marker { boxes = List.copyOf(boxes); }
    }
    public static Marker footstep(NavigationPlanner.Cell feet) {
        return new Marker(feet, 0xFFFFFFFF, 2.5F, List.of(
                new Box(.20, .025, .20, .80, .045, .80),
                new Box(.35, .027, .485, .65, .043, .515),
                new Box(.485, .027, .35, .515, .043, .65)));
    }
    public static Marker destination(NavigationPlanner.Cell target, double playerFeetY) {
        double top = Math.min(48, Math.max(3, playerFeetY - target.y() + 3));
        return new Marker(target, 0xFF00FFFF, 3F, List.of(
                new Box(-.025, -.025, -.025, 1.025, 1.025, 1.025),
                new Box(.46, 1.05, .46, .54, top, .54),
                new Box(.17, top - .05, .17, .83, top, .83)));
    }
    private NavigationMarkers() {}
}
