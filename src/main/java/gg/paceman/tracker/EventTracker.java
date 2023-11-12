package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EventTracker {
    private final Path globalFile;
    private Path eventLogPath;

    private long lastMod = -1;
    private long readProgress = 0;
    private String currentHeader = "";
    private boolean headerChanged = false;
    private List<String> latestNewLines = Collections.emptyList();

    public EventTracker(Path globalFile) {
        this.globalFile = globalFile;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getLatestNewLines() {
        return this.latestNewLines;
    }

    public String getCurrentHeader() {
        return this.currentHeader;
    }

    public boolean hasHeaderChanged() {
        if (this.headerChanged) {
            this.headerChanged = false;
            return true;
        }
        return false;
    }

    public boolean update() throws IOException {
        if (!Files.exists(this.globalFile)) {
            return false;
        }
        long newLM = Files.getLastModifiedTime(this.globalFile).toMillis();
        if (newLM == this.lastMod) {
            return false;
        }
        this.lastMod = newLM;
        while (!this.tryCheckHeader()) {
            sleep(5);
        }
        while (!this.tryUpdateNewLines()) {
            sleep(5);
        }
        return true;
    }

    private boolean tryUpdateNewLines() throws IOException {
        String newContents;
        try (InputStream inputStream = Files.newInputStream(this.eventLogPath)) {
            inputStream.skip(this.readProgress);
            long newReadProgress = this.readProgress;
            List<Byte> byteList = new ArrayList<>();
            int next;
            int lastChar = '\n';
            while ((next = inputStream.read()) != -1) {
                byteList.add((byte) next);
                newReadProgress++;
                lastChar = next;
            }
            if (newReadProgress == this.readProgress) {
                // No bytes read
                return true;
            }
            if (lastChar != '\n') {
                // Out of new bytes, it didn't end with a newline
                return false;
            }
            this.readProgress = newReadProgress;
            byte[] bytes = new byte[byteList.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = byteList.get(i);
            }
            newContents = new String(bytes);
        }
        // For each string split between newlines, trim, filter out empties, and collect to list.
        this.latestNewLines = Arrays.stream(newContents.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        return true;
    }

    private boolean tryCheckHeader() throws IOException {
        String newHeader;
        try (InputStream inputStream = Files.newInputStream(this.globalFile)) {
            List<Byte> byteList = new ArrayList<>();
            int next;
            while ((next = inputStream.read()) != '\n' && next != -1) {
                byteList.add((byte) next);
            }
            if (next != '\n') {
                return false;
            }
            byte[] bytes = new byte[byteList.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = byteList.get(i);
            }
            newHeader = new String(bytes);
        }
        if (!newHeader.equals(this.currentHeader)) {
            this.loadNewHeader(newHeader);
        }
        return true;
    }

    private void loadNewHeader(String newHeader) {
        this.headerChanged = true;
        this.currentHeader = newHeader;

        JsonObject json = new Gson().fromJson(newHeader, JsonObject.class);
        this.eventLogPath = Paths.get(json.get("world_path").getAsString()).resolve("speedrunigt").resolve("events.log");
        this.readProgress = 0;
    }
}
