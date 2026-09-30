package minecraft_ai_guide.agent;

import java.util.ArrayList;
import java.util.List;

public class ExperienceMemory {

    private final List<AgentExperience> experiences =
            new ArrayList<>();

    public void add(AgentExperience experience) {
        experiences.add(experience);
    }

    public List<AgentExperience> getExperiences() {
        return new ArrayList<>(experiences);
    }

    public int size() {
        return experiences.size();
    }

    public void clear() {
        experiences.clear();
    }
}
