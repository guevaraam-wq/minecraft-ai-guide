package minecraft_ai_guide.agent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record QLearningConfig(double epsilon, double learningRate, double discountFactor) {
    public static final QLearningConfig DEFAULT = new QLearningConfig(0.25, 0.2, 0.95);

    public QLearningConfig {
        if (!Double.isFinite(epsilon) || epsilon < 0 || epsilon > 1
                || !Double.isFinite(learningRate) || learningRate <= 0 || learningRate > 1
                || !Double.isFinite(discountFactor) || discountFactor < 0 || discountFactor > 1) {
            throw new IllegalArgumentException("epsilon/gamma must be 0..1; alpha must be >0 and <=1");
        }
    }

    /** Read only on explicit activation; defaults are written on first activation. */
    public static QLearningConfig load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
            return new QLearningConfig(Double.parseDouble(p.getProperty("epsilon", "0.25")),
                    Double.parseDouble(p.getProperty("learningRate", "0.2")),
                    Double.parseDouble(p.getProperty("discountFactor", "0.95")));
        }
        Files.createDirectories(file.getParent());
        p.setProperty("epsilon", "0.25");
        p.setProperty("learningRate", "0.2");
        p.setProperty("discountFactor", "0.95");
        try (Writer writer = Files.newBufferedWriter(file)) {
            p.store(writer, "Experimental one-log Q-learning; loaded when F7 starts an episode");
        }
        return DEFAULT;
    }
}
