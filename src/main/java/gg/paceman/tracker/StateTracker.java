package gg.paceman.tracker;

import com.google.gson.JsonObject;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.PostUtil;
import gg.paceman.tracker.util.SleepUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StateTracker {

    private static final String SUBMIT_STATS_ENDPOINT = "https://paceman.gg/stats/api/submitStats/";
    private final int breakThreshold = 5000;
    // cap each overworld segment to at most 10 minutes in case of afk
    private final int maxPlayTime = 1000 * 60 * 10;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Path lastWorldPath;
    private Path statePath;
    private boolean hasStateFile = false;
    private long stateLastMod = -1;
    private State currentState = State.UNKNOWN;

    private Path resetsPath;
    private boolean hasResetsFile = false;
    private long resetsLastMod = -1;
    private int resets = 0;
    private int lastResets = 0;
    private long lastWallReset = 0;

    private boolean isPracticing = false;
    private boolean isNether = false;

    private int seedsPlayed = 0;
    private long playingStart = 0;
    private long playTime = 0;
    private long wallTime = 0;

    public void start() {
        this.executor.scheduleAtFixedRate(this::tickInstPath, 0, 1, TimeUnit.SECONDS);
        this.executor.scheduleAtFixedRate(this::tryTick, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void tickInstPath() {
        Thread.currentThread().setName("state-tick-inst");
        Path worldPath = PaceManTracker.getInstance().getWorldPath();
        if (worldPath == null || worldPath.equals(this.lastWorldPath)) {
            return;
        }

        this.lastWorldPath = worldPath;

        boolean allowAnyWorldName = PaceManTrackerOptions.getInstance().allowAnyWorldName;
        boolean isRandomSpeedrunWorld = PaceManTracker.RANDOM_WORLD_PATTERN.matcher(worldPath.getFileName().toString()).matches();
        this.isPracticing = !allowAnyWorldName && !isRandomSpeedrunWorld;

        // Random Speedrun #X -> saves -> .minecraft
        Path instFolder = worldPath.getParent().getParent();

        this.statePath = instFolder.resolve("wpstateout.txt");
        this.resetsPath = instFolder.resolve("config/mcsr/atum/rsg-attempts.txt");
        this.hasStateFile = Files.exists(this.statePath);
        this.hasResetsFile = Files.exists(this.resetsPath);
    }

    public void tryTick() {
        try {
            Thread.currentThread().setName("state-tick");
            this.tick();
            this.tickResets();
        } catch (Throwable t) {
            String detailedString = ExceptionUtil.toDetailedString(t);
            PaceManTracker.logWarning("Error while checking state: " + detailedString);
            PaceManTracker.logWarning("The above error only affects the NPH stats tracking, and can be ignored if it happens rarely.");
        }
    }

    public void tick() throws IOException {
        if (!this.hasStateFile) {
            return;
        }
        long newLM = Files.getLastModifiedTime(this.statePath).toMillis();
        if (newLM == this.stateLastMod) {
            return;
        }
        this.stateLastMod = newLM;

        State oldState = this.currentState;
        State newState = State.UNKNOWN;
        String state = "";
        for (int i = 0; i < 5; i++) {
            state = new String(Files.readAllBytes(this.statePath), StandardCharsets.UTF_8);
            switch (state.split(",")[0]) {
                case "wall":
                case "previewing":
                    newState = State.WALL;
                    break;
                case "inworld":
                    newState = State.PLAYING;
                    break;
                case "generating":
                case "waiting":
                    newState = State.LOADING;
                    break;
                case "title":
                    newState = State.IDLE;
                    break;
                default:
                    SleepUtil.sleep(5);
                    continue;
            }
            break;
        }
        if (newState == State.UNKNOWN) {
            PaceManTracker.logWarning("State cannot be determined after 3 attempts: " + state);
            return;
        }

        // joined instance
        if (oldState != State.PLAYING && newState == State.PLAYING) {
            this.playingStart = newLM;
            if (oldState != State.UNKNOWN) {
                // don't increment seeds played counter when tracker is restarted while in a world
                this.seedsPlayed++;
            }
        }

        // left instance
        if (oldState == State.PLAYING && newState != State.PLAYING) {
            if (!this.isPracticing && !this.isNether) {
                // commit playtime
                long playDiff = Math.min(this.maxPlayTime, newLM - this.playingStart);
                this.playTime += playDiff;
            }
            this.isPracticing = false;
            this.isNether = false;
        }

        this.currentState = newState;
    }

    public void tickResets() throws IOException {
        if (!this.hasResetsFile) {
            return;
        }
        long newLM = Files.getLastModifiedTime(this.resetsPath).toMillis();
        if (newLM == this.resetsLastMod) {
            return;
        }
        this.resetsLastMod = newLM;

        int resets = 0;
        for (int i = 0; i < 5; i++) {
            String contents = new String(Files.readAllBytes(this.resetsPath), StandardCharsets.UTF_8);
            if (contents.isEmpty()) {
                SleepUtil.sleep(5);
                continue;
            }
            resets = Integer.parseInt(contents);
            break;
        }

        this.resets = resets;
        if (this.lastResets == 0) {
            this.lastResets = this.resets;
        }

        if (this.currentState != State.WALL) {
            return;
        }

        // first wall reset
        if (this.lastWallReset == 0) {
            this.lastWallReset = newLM;
            return;
        }

        long wallDiff = newLM - this.lastWallReset;
        this.lastWallReset = newLM;
        if (wallDiff < this.breakThreshold) {
            this.wallTime += wallDiff;
        }

    }

    public void dumpStats(JsonObject data) {
        if (!PaceManTrackerOptions.getInstance().resetStatsEnabled) {
            PaceManTracker.logDebug("Not submitting stats since user opted out");
            return;
        }
        JsonObject gameData = data.getAsJsonObject("gameData");
        String mods = gameData.getAsJsonArray("modList").toString();
        if (!mods.contains("seedqueue") || !mods.contains("state-output")) {
            PaceManTracker.logWarning("Could not submit reset stats as either SeedQueue or State Output is missing");
            return;
        }

        int newResets = this.resets - this.lastResets;

        // add the overworld time (capped) spent in this run to playTime
        long diff = Math.min(this.maxPlayTime, System.currentTimeMillis() - this.playingStart);
        this.playTime += diff;

        JsonObject input = new JsonObject();
        input.addProperty("gameData", gameData.toString());
        input.addProperty("accessKey", data.get("accessKey").getAsString());
        input.addProperty("wallTime", this.wallTime);
        input.addProperty("playTime", this.playTime);
        input.addProperty("seedsPlayed", this.seedsPlayed);
        input.addProperty("resets", newResets);
        input.addProperty("totalResets", this.resets);

        this.lastResets = this.resets;
        this.playTime = 0;
        this.wallTime = 0;
        this.seedsPlayed = 0;
        this.isNether = true;

        try {
            PostUtil.PostResponse out = PostUtil.sendData(SUBMIT_STATS_ENDPOINT, input.toString());
            int res = out.getCode();
            PaceManTracker.logDebug("Stats Response " + res + ": " + out.getMessage());
        } catch (Throwable t) {
            String detailedString = ExceptionUtil.toDetailedString(t);
            PaceManTracker.logError("Stats submission encountered an error: " + detailedString);
        }
    }

    public void stop() {
        Thread.currentThread().setName("state-stopping");
        try {
            // Wait for and shutdown executor
            this.executor.shutdownNow();
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private enum State {
        UNKNOWN, IDLE, WALL, LOADING, PLAYING
    }

}
