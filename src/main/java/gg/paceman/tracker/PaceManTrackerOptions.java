package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Handles the save options for the tracker
 */
public class PaceManTrackerOptions {
    public static final Path OLD_SAVE_PATH = Paths.get(System.getProperty("user.home")).resolve(".PaceMan").resolve("options.json").toAbsolutePath();
    public static final Path SAVE_PATH = PaceManTrackerOptions.getPaceManDir().resolve("options.json").toAbsolutePath();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PaceManTrackerOptions instance;

    public String accessKey = "";
    public boolean enabledForPlugin = false;
    public boolean allowAnyWorldName = false;
    public boolean resetStatsEnabled = true;

    /**
     * Load and return the options file
     */
    public static PaceManTrackerOptions load() throws IOException {
        if (Files.exists(SAVE_PATH)) {
            instance = GSON.fromJson(new String(Files.readAllBytes(SAVE_PATH)), PaceManTrackerOptions.class);
        } else if (Files.exists(OLD_SAVE_PATH)) {
            PaceManTracker.logWarning("Loaded options from old location '" + OLD_SAVE_PATH + "'! It will now save to a new location: '" + SAVE_PATH + "'.");
            instance = GSON.fromJson(new String(Files.readAllBytes(OLD_SAVE_PATH)), PaceManTrackerOptions.class);
        } else {
            instance = new PaceManTrackerOptions();
        }
        return instance;
    }

    public static PaceManTrackerOptions getInstance() {
        return instance;
    }

    public static void ensurePaceManDir() {
        new File((PaceManTrackerOptions.getConfigHome() + "/PaceMan/").replace("\\", "/").replace("//", "/")).mkdirs();
    }

    public static Path getPaceManDir() {
        return Paths.get(PaceManTrackerOptions.getConfigHome()).resolve("PaceMan").toAbsolutePath();
    }

    private static String getConfigHome() {
        return Optional.ofNullable(System.getenv("XDG_CONFIG_HOME")).orElse(System.getProperty("user.home") + "/.config/");
    }

    public void save() throws IOException {
        PaceManTrackerOptions.ensurePaceManDir();
        FileWriter writer = new FileWriter(SAVE_PATH.toFile());
        GSON.toJson(this, writer);
        writer.close();
    }
}
