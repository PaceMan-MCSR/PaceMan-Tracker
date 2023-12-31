package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles the save options for the tracker
 */
public class PaceManTrackerOptions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_PATH = Paths.get(System.getProperty("user.home")).resolve(".PaceMan").resolve("options.json").toAbsolutePath();
    private static PaceManTrackerOptions instance;


    public String accessKey = "";
    public boolean enabledForPlugin = false;

    public static void save() throws IOException {
        PaceManTrackerOptions.ensurePaceManDir();
        FileWriter writer = new FileWriter(SAVE_PATH.toFile());
        GSON.toJson(instance, writer);
        writer.close();
    }

    /**
     * Load and return the options file
     */
    public static PaceManTrackerOptions load() throws IOException {
        if (Files.exists(SAVE_PATH)) {
            instance = GSON.fromJson(new String(Files.readAllBytes(SAVE_PATH)), PaceManTrackerOptions.class);
        } else {
            instance = new PaceManTrackerOptions();
        }
        return instance;
    }

    public static PaceManTrackerOptions getInstance() {
        return instance;
    }

    public static void ensurePaceManDir() {
        new File((System.getProperty("user.home") + "/.PaceMan/").replace("\\", "/").replace("//", "/")).mkdirs();
    }
}
