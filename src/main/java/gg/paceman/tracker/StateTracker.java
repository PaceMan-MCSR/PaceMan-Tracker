package gg.paceman.tracker;

import com.google.gson.JsonObject;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.SleepUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StateTracker {

    private enum State {
        UNKNOWN, IDLE, WALL, LOADING, PLAYING
    }

    private final int breakThreshold = 5000;

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

    private int seedsPlayed = 0;
    private long playingStart = 0;
    private long playTime = 0;
    private long wallTime = 0;

    private static final String SUBMIT_STATS_ENDPOINT = "https://paceman.gg/stats/api/submitStats";

    public void start(){
        this.executor.scheduleAtFixedRate(this::tickInstPath, 0, 1, TimeUnit.SECONDS);
        this.executor.scheduleAtFixedRate(this::tryTick, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void tickInstPath(){
        Thread.currentThread().setName("state-tick-inst");
        Path worldPath = PaceManTracker.getInstance().getWorldPath();
        if(worldPath == null || worldPath.equals(this.lastWorldPath)){
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

    public void tryTick(){
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
        if(!this.hasStateFile){
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
        for(int i = 0; i < 5; i++) {
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
        if(newState == State.UNKNOWN){
            PaceManTracker.logWarning("State cannot be determined after 3 attempts: " + state);
            return;
        }

        // joined instance
        if(oldState != State.PLAYING && newState == State.PLAYING){
            this.playingStart = newLM;
            if(oldState != State.UNKNOWN){
                // don't increment seeds played counter when tracker is restarted while in a world
                this.seedsPlayed++;
            }
        }

        // left instance
        if(oldState == State.PLAYING && newState != State.PLAYING){
            if(!this.isPracticing){
                // commit playtime
                long playDiff = newLM - this.playingStart;
                this.playTime += playDiff;
            }
            this.isPracticing = false;
        }

        this.currentState = newState;
    }

    public void tickResets() throws IOException {
        if(!this.hasResetsFile){
            return;
        }
        long newLM = Files.getLastModifiedTime(this.resetsPath).toMillis();
        if (newLM == this.resetsLastMod) {
            return;
        }
        this.resetsLastMod = newLM;

        if(this.currentState != State.WALL){
            return;
        }

        int resets = 0;
        for(int i = 0; i < 5; i++){
            String contents = new String(Files.readAllBytes(this.resetsPath), StandardCharsets.UTF_8);
            if(contents.isEmpty()){
                SleepUtil.sleep(5);
                continue;
            }
            resets = Integer.parseInt(contents);
            break;
        }

        // first wall reset
        if(this.lastWallReset == 0){
            this.lastWallReset = newLM;
            return;
        }

        long wallDiff = newLM - this.lastWallReset;
        this.resets = resets;
        this.lastWallReset = newLM;
        if(wallDiff < this.breakThreshold){
            this.wallTime += wallDiff;
        }

    }

    public void dumpStats(String worldId){
        int newResets = this.resets - this.lastResets;
        JsonObject input = new JsonObject();
        input.addProperty("accessKey", PaceManTrackerOptions.getInstance().accessKey);
        input.addProperty("worldId", worldId);
        input.addProperty("wallTime", this.wallTime);
        input.addProperty("playTime", this.playTime);
        input.addProperty("seedsPlayed", this.seedsPlayed);
        input.addProperty("resets", newResets);
        input.addProperty("totalResets", this.resets);

        this.lastResets = this.resets;
        this.playTime = 0;
        this.wallTime = 0;
        this.seedsPlayed = 0;

        try {
            PaceManTracker.PostResponse out = PaceManTracker.sendData(SUBMIT_STATS_ENDPOINT, input.toString());
            int res = out.getCode();
            PaceManTracker.logDebug("Stats Response " + res + ": " + out.getMessage());
        } catch (Throwable t) {
            String detailedString = ExceptionUtil.toDetailedString(t);
            PaceManTracker.logError("Stats submission encountered an error: " + detailedString);
        }
    }

    public void stop(){
        Thread.currentThread().setName("state-stopping");
        try {
            // Wait for and shutdown executor
            this.executor.shutdownNow();
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
