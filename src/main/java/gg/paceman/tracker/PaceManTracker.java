package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.SleepUtil;
import gg.paceman.tracker.util.VersionUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The actual logic and stuff for the PaceMan Tracker
 */
public class PaceManTracker {
    public static String VERSION = "Unknown"; // To be set dependent on launch method
    private static final PaceManTracker INSTANCE = new PaceManTracker();

    // If any end events are reached and no events have been sent for the current run, then prevent sending anything.
    // If events have already been sent for this run, then send the end event and then send no more events
    private static final List<String> END_EVENTS = Arrays.asList("common.multiplayer", "common.old_world", "common.open_to_lan", "common.enable_cheats", "common.view_seed", "rsg.credits");
    // If any start event is reached for the first time for this run, enable sending events for this run, send the header and all events so far.
    private static final List<String> START_EVENTS = Collections.singletonList("rsg.enter_nether");
    // Unimportant events are not considered when determining if an event is recent enough to send the run to PaceMan
    private static final List<String> UNIMPORTANT_EVENTS = Arrays.asList("common.leave_world", "common.rejoin_world");

    private static final Set<String> IMPORTANT_ITEM_COUNTS = new HashSet<>(Arrays.asList("minecraft:ender_pearl", "minecraft:obsidian", "minecraft:blaze_rod"));
    private static final Set<String> IMPORTANT_ITEM_USAGES = new HashSet<>(Arrays.asList("minecraft:ender_pearl", "minecraft:obsidian"));

    private static final Pattern RANDOM_WORLD_PATTERN = Pattern.compile("^Random Speedrun #\\d+$");

    private static final long RUN_TOO_LONG_MILLIS = 3_600_000; // 1 hour
    private static final long EVENT_RECENT_ENOUGH_MILLIS = 60_000; // 1 minute

    public static final Queue<Runnable> MAIN_THREAD_TODO = new ConcurrentLinkedQueue<>();

    public static Consumer<String> logConsumer = System.out::println;
    public static Consumer<String> debugConsumer = System.out::println;
    public static Consumer<String> errorConsumer = System.out::println;
    public static Consumer<String> warningConsumer = System.out::println;


    public static final String PACEMANGG_EVENT_ENDPOINT = "https://paceman.gg/api/sendevent";
    private static final String PACEMANGG_TEST_ENDPOINT = "https://paceman.gg/api/test";
    private static final int MIN_DENY_CODE = 400;

    private final EventTracker eventTracker = new EventTracker(Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("latest_world.json").toAbsolutePath());
    private final ItemTracker itemTracker = new ItemTracker();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean asPlugin;

    private String headerToSend = null;
    private boolean runOnPaceMan = false;
    private RunProgress runProgress = RunProgress.NONE;
    private final List<String> eventsToSend = new ArrayList<>();
    private String worldUniquifier = "";

    private long nextDebugPrint = -1;

    public static PaceManTracker getInstance() {
        return INSTANCE;
    }

    public static void log(String message) {
        logConsumer.accept(message);
    }

    public static void logDebug(String message) {
        debugConsumer.accept(message);
    }

    public static void logError(String error) {
        errorConsumer.accept(error);
    }

    public static void logWarning(String error) {
        warningConsumer.accept(error);
    }

    private static boolean areAtumSettingsGood(Path worldPath) throws IOException {
        // .minecraft/saves/x -> .minecraft/saves -> .minecraft -> .minecraft/config -> .minecraft/config/atum -> .minecraft/config/atum/atum.properties
        Path atumPropPath = worldPath.getParent().getParent().resolve("config").resolve("atum").resolve("atum.properties");
        if (!Files.exists(atumPropPath)) {
            return false;
        }
        String atumPropText = new String(Files.readAllBytes(atumPropPath));
        for (String line : atumPropText.split("\n")) {
            String[] args = line.trim().split("=");
            if (args.length < 2) {
                continue;
            }
            if (args[0].trim().equals("generatorType") && !args[1].trim().equals("0")) {
                return false;
            }
            if (args[0].trim().equals("bonusChest") && args[1].trim().equals("true")) {
                return false;
            }
        }
        return true;
    }

    private static PaceManResponse sendToPacemanGG(String toSend) {
        int responseCode;
        try {
            PostResponse out = PaceManTracker.sendData(PACEMANGG_EVENT_ENDPOINT, toSend);
            responseCode = out.code;
            PaceManTracker.logDebug("Response " + responseCode + ": " + out.message);
        } catch (IOException e) {
            return PaceManResponse.SEND_ERROR;
        }

        if (responseCode < MIN_DENY_CODE) {
            return PaceManResponse.SUCCESS;
        } else {
            return PaceManResponse.DENIED;
        }
    }

    private static String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static PostResponse sendData(String endpointUrl, String jsonData) throws IOException {
        // Create URL object
        URL url = new URL(endpointUrl);
        HttpURLConnection connection = null;
        try {
            // Open connection
            connection = (HttpURLConnection) url.openConnection();

            // Set the necessary properties
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write JSON data to the connection output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int responseCode = connection.getResponseCode();
            String message = responseCode >= 400 ? PaceManTracker.readStream(connection.getErrorStream()) : connection.getResponseMessage();


            // Return the response code
            return new PostResponse(responseCode, message);
        } finally {
            // Close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static PostResponse testAccessKey(String accessKey) {
        JsonObject testModelInput = new JsonObject();
        testModelInput.addProperty("accessKey", accessKey);
        try {
            return PaceManTracker.sendData(PACEMANGG_TEST_ENDPOINT, testModelInput.toString());
        } catch (IOException e) {
            return null;
        }
    }

    private static String readStream(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
    }

    public PaceManResponse sendEventsToPacemanGG() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();

        JsonObject eventModelInput = new JsonObject();
        eventModelInput.addProperty("accessKey", options.accessKey);

        if (this.headerToSend != null) {
            JsonObject latestWorldJson = new Gson().fromJson(this.headerToSend, JsonObject.class);
            JsonArray mods = latestWorldJson.getAsJsonArray("mods");
            String worldId = latestWorldJson.get("world_path").getAsString() + this.worldUniquifier;
            String gameVersion = latestWorldJson.get("version").getAsString();
            String srIGTVersion = latestWorldJson.has("mod_version") ? (latestWorldJson.get("mod_version").getAsString().split("\\+")[0]) : "14.0";
            String category = latestWorldJson.get("category").getAsString();

            JsonObject gameData = new JsonObject();
            gameData.addProperty("worldId", PaceManTracker.sha256Hash(worldId));
            gameData.addProperty("gameVersion", gameVersion);
            gameData.addProperty("modVersion", srIGTVersion);
            gameData.addProperty("category", category);
            gameData.add("modList", mods);
            gameData.addProperty("trackerVersion", VERSION);

            eventModelInput.add("gameData", gameData);
        }

        JsonArray eventList = new JsonArray();
        this.eventsToSend.forEach(eventList::add);
        eventModelInput.add("eventList", eventList);

        eventModelInput.addProperty("timeSinceRunStart", this.getTimeSinceRunStart());

        Optional<JsonObject> itemDataOpt = this.itemTracker.constructItemData(IMPORTANT_ITEM_COUNTS, IMPORTANT_ITEM_USAGES);
        itemDataOpt.ifPresent(itemData -> {
            if (!itemData.keySet().isEmpty()) {
                eventModelInput.add("itemData", itemData);
            }
        });

        String toSend = eventModelInput.toString();
        PaceManTracker.logDebug("Sending exactly: " + toSend.replace(options.accessKey, "KEY_HIDDEN"));
        return PaceManTracker.sendToPacemanGG(toSend);
    }

    public PaceManResponse sendCancelToPacemanGG() {
        JsonObject eventModelInput = new JsonObject();
        // Access Key
        eventModelInput.addProperty("accessKey", PaceManTrackerOptions.getInstance().accessKey);
        // Empty Event List
        eventModelInput.add("eventList", new JsonArray());
        // Kill flag
        eventModelInput.addProperty("kill", true);
        return PaceManTracker.sendToPacemanGG(eventModelInput.toString());
    }

    private boolean shouldRun() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
        if (options.accessKey.isEmpty()) {
            return false;
        }
        return !this.asPlugin || options.enabledForPlugin;
    }

    public void start(boolean asPlugin) {
        this.asPlugin = asPlugin;
        // Run tick every 1 second
        this.executor.scheduleAtFixedRate(this::tryTick, 0, 1, TimeUnit.SECONDS);
    }

    private void tryTick() {
        try {
            Thread.currentThread().setName("paceman-tracker");
            this.tick();
        } catch (Throwable t) {
            if (!this.asPlugin) {
                ExceptionUtil.showExceptionAndExit(t, "PaceMan Tracker has crashed! Please report this bug to the developers.\n" + t);
            } else {
                String detailedString = ExceptionUtil.toDetailedString(t);
                PaceManTracker.logError("PaceMan Tracker has crashed! Please report this bug to the developers. " + detailedString);
                PaceManTracker.logError("PaceMan Tracker will now shutdown, Julti will need to be restarted to use PaceMan Tracker.");
                this.stop();
            }
        }
    }

    private void setRunProgress(RunProgress runProgress) {
        PaceManTracker.logDebug("Run Progress set to " + runProgress);
        this.runProgress = runProgress;
    }

    private void tick() {
        this.showRunningDebug();
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();

        while (!MAIN_THREAD_TODO.isEmpty()) {
            MAIN_THREAD_TODO.remove().run();
        }

        if (!this.shouldRun()) {
            return;
        }

        try {
            if (!this.eventTracker.update()) {
                return;
            }
        } catch (IOException e) {
            PaceManTracker.logError("Exception while updating event tracker: " + e);
            return;
        }

        if (this.eventTracker.hasHeaderChanged()) {
            if (this.runOnPaceMan) {
                this.sendCancel();
            }
            this.headerToSend = this.eventTracker.getCurrentHeader();
            PaceManTracker.logDebug("New Header: " + this.headerToSend);
            this.eventsToSend.clear();
            this.runOnPaceMan = false;
            this.setRunProgress(RunProgress.STARTING);

            boolean isRandomSpeedrunWorld = RANDOM_WORLD_PATTERN.matcher(this.eventTracker.getCurrentWorldName()).matches();
            if (!options.allowAnyWorldName && !isRandomSpeedrunWorld) {
                PaceManTracker.logWarning("World name is not \"Random Speedrun #...\" so this run will not be on PaceMan.gg (this prevents practice maps and tourney worlds). If you want to play manually created worlds (New World) or you are Couriway then you can edit the allowAnyWorldName option in " + PaceManTrackerOptions.SAVE_PATH);
                this.setRunProgress(RunProgress.ENDED);
            }

            try {
                if (isRandomSpeedrunWorld && !PaceManTracker.areAtumSettingsGood(this.eventTracker.getWorldPath())) {
                    PaceManTracker.logWarning("Your atum settings have issues! Please ensure your atum is set to default generation type with bonus chests off!");
                    this.setRunProgress(RunProgress.ENDED);
                }
            } catch (IOException ignored) {
                // Damn that sucks! But it really shouldn't happen because we check if the file exists before reading.
            }

            // If 14.1 is a newer version than the current one
            if (VersionUtil.tryCompare("14.1", this.eventTracker.getSRIGTVersion(), 0) > 0) {
                PaceManTracker.logWarning("Your SpeedRunIGT version is " + this.eventTracker.getSRIGTVersion() + "! This means some tracking features will be missing, consider updating SpeedRunIGT to the latest version.");
            }
        }

        this.itemTracker.tryUpdate(this.eventTracker.getWorldPath());

        List<String> latestNewLines = this.eventTracker.getLatestNewLines();
        if (!latestNewLines.isEmpty()) {
            PaceManTracker.logDebug("New Lines: " + latestNewLines);
        }

        if (this.getTimeSinceRunStart() > RUN_TOO_LONG_MILLIS) {
            PaceManTracker.logDebug("Run started too long ago, this run won't be sent to PaceMan.gg");
            this.endRun();
        }

        if (latestNewLines.isEmpty() || this.runProgress == RunProgress.ENDED) {
            return;
        }

        boolean shouldDump = this.runOnPaceMan;

        for (String line : latestNewLines) {
            this.eventsToSend.add(line);

            String[] parts = line.split(" ");
            String eventName = parts[0];
            if (END_EVENTS.contains(eventName)) {
                if (this.runOnPaceMan) {
                    // This run is already on PaceMan, so we need to dump this last end event before ending
                    this.dumpToPacemanGG();
                } else {
                    this.eventsToSend.clear();
                }
                this.endRun();
                shouldDump = false;
                break;
            } else if (this.runProgress != RunProgress.PACING && START_EVENTS.contains(eventName)) {
                PaceManTracker.logDebug("PaceMan Tracker start event reached!");
                switch (parts.length) {
                    case 3: // should always be this
                        this.worldUniquifier = ";" + eventName + ";" + parts[1] + ";" + parts[2];
                        break;
                    case 2:
                        PaceManTracker.logWarning("Event log contained only 2 parts for an event line! \"" + line + "\"");
                        this.worldUniquifier = ";" + eventName + ";" + parts[1];
                        break;
                    default:
                        PaceManTracker.logWarning("Event log contained a strange number of parts for an event line! \"" + line + "\"");
                        this.worldUniquifier = eventName;
                        break;
                }
                this.setRunProgress(RunProgress.PACING);
            }
            // Determine if the event is recent enough to dump
            if (!shouldDump && this.runProgress == RunProgress.PACING && !UNIMPORTANT_EVENTS.contains(eventName)) {
                long timeDiff = Math.abs(System.currentTimeMillis() - (Long.parseLong(parts[1]) + this.eventTracker.getRunStartTime()));
                if (timeDiff < EVENT_RECENT_ENOUGH_MILLIS) {
                    shouldDump = true;
                    PaceManTracker.log("Run will now be sent to PaceMan.gg!");
                } else {
                    PaceManTracker.logDebug(String.format("Event %s happened %d milliseconds ago (not recent enough).", eventName, timeDiff));
                }
            }
        }
        if (shouldDump) {
            this.dumpToPacemanGG();
        }
    }

    private void showRunningDebug() {
        if (System.currentTimeMillis() < this.nextDebugPrint) return;
        if (this.nextDebugPrint == -1) {
            this.nextDebugPrint = System.currentTimeMillis() + 60_000;
        } else {
            this.nextDebugPrint += 60_000;
        }
        PaceManTracker.logDebug("PaceMan Tracker is running! (" + Instant.now() + ")");
    }

    private void sendCancel() {
        PaceManTracker.logDebug("Telling Paceman to cancel the run.");
        int tries = 0;
        // While sending gives back an error
        while (PaceManResponse.SEND_ERROR == (
                this.sendCancelToPacemanGG()
        )) {
            if (++tries < 5) {
                // Wait 5 seconds on failure before retry.
                PaceManTracker.logError("Failed to tell PaceMan.gg to cancel the run, retrying in 5 seconds...");
                SleepUtil.sleep(5000);
            } else {
                break;
            }
        }
        // If the response was a denial (400+ response code), it is probably because there is no run to cancel, so we have succeeded anyway.
        this.runOnPaceMan = false;
    }

    private long getTimeSinceRunStart() {
        return Math.abs(System.currentTimeMillis() - this.eventTracker.getRunStartTime());
    }

    private void dumpToPacemanGG() {
        PaceManTracker.logDebug("Dumping to paceman");
        PaceManResponse response;
        int tries = 0;
        // While sending gives back an error
        while (PaceManResponse.SEND_ERROR == (response = this.sendEventsToPacemanGG())) {
            if (++tries < 5) {
                // Wait 5 seconds on failure before retry.
                PaceManTracker.logError("Failed to send to PaceMan.gg, retrying in 5 seconds...");
                SleepUtil.sleep(5000);
            } else {
                break;
            }
        }
        if (response == PaceManResponse.DENIED) {
            // Deny response = cancel the run
            PaceManTracker.logError("PaceMan.gg denied run data, no more data will be sent for this run.");
            this.endRun();
        } else if (response == PaceManResponse.SEND_ERROR) {
            PaceManTracker.logError("Failed to send to PaceMan.gg after a couple tries, no more data will be sent for this run.");
            this.endRun();
        } else {
            PaceManTracker.logDebug("Successfully sent to PaceMan.gg");
            this.headerToSend = null;
            this.eventsToSend.clear();
            this.runOnPaceMan = true;
        }
    }

    private void endRun() {
        this.setRunProgress(RunProgress.ENDED);
        this.runOnPaceMan = false;
    }

    public void stop() {
        try {
            // Wait for and shutdown executor
            this.executor.shutdownNow();
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Do cleanup
        if (this.runOnPaceMan) {
            this.sendCancel();
        }
    }

    private enum RunProgress {
        NONE, STARTING, PACING, ENDED
    }

    public enum PaceManResponse {
        SUCCESS, // 201 response
        DENIED, // non 201 response
        SEND_ERROR // error while trying to send
    }

    public static class PostResponse {
        private final int code;
        private final String message;

        private PostResponse(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return this.code;
        }

        public String getMessage() {
            return this.message;
        }
    }
}
