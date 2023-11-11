package gg.paceman.tracker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EventFileTracker {
    private final Path eventFilePath;

    private long lastMTime = -1;
    private long readProgress = 0;
    private String currentHeader = "";
    private boolean headerChanged = false;
    private List<String> latestNewLines = Collections.emptyList();

    public EventFileTracker(Path eventFilePath) {
        this.eventFilePath = eventFilePath;
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
        if (!Files.exists(this.eventFilePath)) {
            return false;
        }
        long newMTime = Files.getLastModifiedTime(this.eventFilePath).toMillis();
        if (newMTime == this.lastMTime) {
            return false;
        }
        this.lastMTime = newMTime;
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
        try (InputStream inputStream = Files.newInputStream(this.eventFilePath)) {
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
        int headerLength = 0;
        try (InputStream inputStream = Files.newInputStream(this.eventFilePath)) {
            List<Byte> byteList = new ArrayList<>();
            int next;
            while ((next = inputStream.read()) != '\n' && next != -1) {
                byteList.add((byte) next);
                headerLength++;
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
            this.headerChanged = true;
            this.currentHeader = newHeader;
            this.readProgress = headerLength;
        }
        return true;
    }
}
