package gg.paceman.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.PostUtil;
import gg.paceman.tracker.util.SleepUtil;
import gg.paceman.tracker.util.VersionUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Set<String> DEFAULT_START_EVENTS = Collections.singleton("rsg.enter_nether");
    private static final Map<Integer, Set<String>> START_EVENTS_MAP = new HashMap<>();

    // Unimportant events are not considered when determining if an event is recent enough to send the run to PaceMan
    private static final List<String> UNIMPORTANT_EVENTS = Arrays.asList("common.leave_world", "common.rejoin_world");

    private static final Set<String> IMPORTANT_ITEM_COUNTS = new HashSet<>(Arrays.asList("minecraft:ender_pearl", "minecraft:obsidian", "minecraft:blaze_rod"));
    private static final Set<String> IMPORTANT_ITEM_USAGES = new HashSet<>(Arrays.asList("minecraft:ender_pearl", "minecraft:obsidian"));

    public static final Pattern RANDOM_WORLD_PATTERN = Pattern.compile("^Random Speedrun #\\d+$");
    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("1\\.(\\d+)(?:\\.\\d+)?");

    private static final long RUN_TOO_LONG_MILLIS = 3_600_000; // 1 hour
    private static final long EVENT_RECENT_ENOUGH_MILLIS = 60_000; // 1 minute

    public static final Queue<Runnable> MAIN_THREAD_TODO = new ConcurrentLinkedQueue<>(); // wtf did I want this for?

    public static Consumer<String> logConsumer = System.out::println;
    public static Consumer<String> debugConsumer = System.out::println;
    public static Consumer<String> errorConsumer = System.out::println;
    public static Consumer<String> warningConsumer = System.out::println;


    public static final String PACEMANGG_EVENT_ENDPOINT = "https://paceman.gg/api/sendevent";
    private static final String PACEMANGG_TEST_ENDPOINT = "https://paceman.gg/api/test";
    private static final int MIN_DENY_CODE = 400;

    static {
        START_EVENTS_MAP.put(8, new HashSet<>(Arrays.asList("rsg.enter_nether", "rsg.trade"))); // 1.8
        START_EVENTS_MAP.put(14, new HashSet<>(Arrays.asList("rsg.enter_nether", "rsg.trade", "rsg.obtain_gold_block"))); // 1.14
        START_EVENTS_MAP.put(15, new HashSet<>(Arrays.asList("rsg.enter_nether", "rsg.trade", "rsg.obtain_gold_block"))); // 1.15
    }

    private final EventTracker eventTracker = new EventTracker(Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("latest_world.json").toAbsolutePath());
    private final ItemTracker itemTracker = new ItemTracker();
    private final StateTracker stateTracker = new StateTracker();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean asPlugin;

    private String headerToSend = null;
    private boolean runOnPaceMan = false;
    private RunProgress runProgress = RunProgress.NONE;
    private final List<String> eventsToSend = new ArrayList<>();
    private String worldUniquifier = "";

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

    private static boolean areAtumSettingsGood(Path worldPath) {
        // .minecraft/saves/x -> .minecraft/saves -> .minecraft/config
        Path configPath = worldPath.getParent().resolveSibling("config");
        // .minecraft/config -> .minecraft/config/atum -> .minecraft/config/atum/atum.properties
        Path oldAtumPropPath = configPath.resolve("atum").resolve("atum.properties");
        // .minecraft/config -> .minecraft/config/mcsr -> .minecraft/config/mcsr/atum.json
        Path newAtumJsonPath = configPath.resolve("mcsr").resolve("atum.json");

        boolean oldExists = Files.exists(oldAtumPropPath);
        boolean newExists = Files.exists(newAtumJsonPath);
        if (!(oldExists || newExists)) {
            PaceManTracker.logWarning("You must use the Atum mod " + oldAtumPropPath);
            return false; // no settings exist
        }

        if (oldExists) {
            try {
                if (!PaceManTracker.areOldAtumSettingsGood(oldAtumPropPath)) {
                    PaceManTracker.logWarning("Illegal Atum settings found in " + oldAtumPropPath);
                    PaceManTracker.logWarning("Make sure your Atum settings are set to defaults with no set seed and above peaceful difficulty.");
                    PaceManTracker.logWarning("If you are using the newer Atum with more world generation options, you should delete the old config file.");
                    return false; // old settings exist and are bad
                }
            } catch (Exception e) {
                PaceManTracker.logWarning("Invalid/Corrupted Atum settings found in " + oldAtumPropPath);
                PaceManTracker.logWarning("If you are using the newer Atum with more world generation options, you should delete the old config file.");
                return false;
            }
        }

        if (newExists) {
            try {
                if (!PaceManTracker.areNewAtumSettingsGood(newAtumJsonPath)) {
                    PaceManTracker.logWarning("Illegal Atum settings found in " + newAtumJsonPath);
                    PaceManTracker.logWarning("Make sure your Atum settings are set to defaults with no set seed and above peaceful difficulty.");
                    PaceManTracker.logWarning("If you are using the older Atum with less world generation options, you should delete the new config file.");
                    return false; // new settings exist and are bad
                }
            } catch (Exception e) {
                PaceManTracker.logWarning("Invalid/Corrupted Atum settings found in " + newAtumJsonPath);
                PaceManTracker.logWarning("If you are using the older Atum with less world generation options, you should delete the new config file.");
                return false; // new settings exist and are bad
            }
        }

        return true; // settings exists, no settings are bad
    }

    private static boolean areOldAtumSettingsGood(Path atumPropPath) throws IOException {
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


    private static boolean areNewAtumSettingsGood(Path atumJsonPath) throws IOException, JsonSyntaxException {
        String atumJsonText = new String(Files.readAllBytes(atumJsonPath));
        JsonObject json = new Gson().fromJson(atumJsonText, JsonObject.class);
        return json.has("hasLegalSettings")
                && json.get("hasLegalSettings").getAsBoolean()
                && json.has("seed")
                && json.get("seed").getAsString().isEmpty()
                && json.has("difficulty")
                && !json.get("difficulty").getAsString().equalsIgnoreCase("peaceful");
    }

    private static PaceManResponse sendToPacemanGG(String toSend) {
        PostUtil.PostResponse response;
        try {
            response = PostUtil.sendData(PACEMANGG_EVENT_ENDPOINT, toSend);
        } catch (IOException e) {
            return new PaceManResponse(PaceManResponse.Type.SEND_ERROR, e);
        }

        if (response.code < MIN_DENY_CODE) {
            return new PaceManResponse(PaceManResponse.Type.SUCCESS, response.message);
        } else {
            return new PaceManResponse(PaceManResponse.Type.DENIED, response.message);
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

    public static PostUtil.PostResponse testAccessKey(String accessKey) {
        JsonObject testModelInput = new JsonObject();
        testModelInput.addProperty("accessKey", accessKey);
        try {
            return PostUtil.sendData(PACEMANGG_TEST_ENDPOINT, testModelInput.toString());
        } catch (IOException e) {
            return null;
        }
    }

    private PaceManResponse sendEventsToPacemanGG() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();

        JsonObject eventModelInput = new JsonObject();
        eventModelInput.addProperty("accessKey", options.accessKey);

        if (this.headerToSend != null) {
            JsonObject latestWorldJson = new Gson().fromJson(this.headerToSend, JsonObject.class);
            JsonArray mods = latestWorldJson.getAsJsonArray("mods");
            String worldId = PaceManTracker.sha256Hash(latestWorldJson.get("world_path").getAsString() + this.worldUniquifier);
            String gameVersion = latestWorldJson.get("version").getAsString();
            String srIGTVersion = latestWorldJson.has("mod_version") ? (latestWorldJson.get("mod_version").getAsString().split("\\+")[0]) : "14.0";
            String category = latestWorldJson.get("category").getAsString();

            JsonObject gameData = new JsonObject();
            gameData.addProperty("worldId", worldId);
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

        Optional<JsonObject> itemDataOpt = this.constructItemData();
        itemDataOpt.ifPresent(itemData -> {
            if (!itemData.keySet().isEmpty()) {
                eventModelInput.add("itemData", itemData);
            }
        });

        String toSend = eventModelInput.toString();
        PaceManTracker.logDebug("Sending exactly: " + toSend.replace(options.accessKey, "KEY_HIDDEN"));

        PaceManResponse response = PaceManTracker.sendToPacemanGG(toSend);

        if (response.type == PaceManResponse.Type.SUCCESS && !this.runOnPaceMan && this.headerToSend != null) {
            PaceManTracker.logDebug("Submitting reset stats");
            try {
                this.stateTracker.dumpStats(eventModelInput);
            } catch (Throwable t) {
                String detailedString = ExceptionUtil.toDetailedString(t);
                PaceManTracker.logWarning("Error while submitting stats: " + detailedString);
                PaceManTracker.logWarning("The above error only affects the NPH stats tracking.");
            }
        }

        return response;
    }

    private Optional<JsonObject> constructItemData() {
        if (this.eventTracker.getGameVersion().equals("1.16.1")) {
            return this.itemTracker.constructItemData(IMPORTANT_ITEM_COUNTS, IMPORTANT_ITEM_USAGES);
        }
        return Optional.empty();
    }

    private PaceManResponse sendCancelToPacemanGG() {
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
        this.stateTracker.start();
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

            if (isRandomSpeedrunWorld && !PaceManTracker.areAtumSettingsGood(this.eventTracker.getWorldPath())) {
                this.setRunProgress(RunProgress.ENDED);
            }

            // If 14.2 is a newer version than the current one
            if (VersionUtil.tryCompare("14.2", this.eventTracker.getSRIGTVersion(), 0) > 0) {
                PaceManTracker.logWarning("Your SpeedRunIGT version is " + this.eventTracker.getSRIGTVersion() + "! This means some tracking features will be missing, consider updating SpeedRunIGT to the latest version.");
            }
        }

        if (this.eventTracker.getGameVersion().equals("1.16.1")) {
            this.itemTracker.tryUpdate(this.eventTracker.getWorldPath());
        }

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

        Set<String> startEvents = this.getStartEvents();
        if (startEvents == null) { // startEvents is null when mc version is invalid (snapshot/april fools probably)
            this.endRun();
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
            } else if (this.runProgress != RunProgress.PACING && startEvents.contains(eventName)) {
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

    @Nullable
    private Set<String> getStartEvents() {
        Matcher matcher = GAME_VERSION_PATTERN.matcher(this.eventTracker.getGameVersion());
        if (!matcher.matches()) {
            return null; // lol snapshot?
        }
        int mcMajorRelease = Integer.parseInt(matcher.group(1));
        return START_EVENTS_MAP.getOrDefault(mcMajorRelease, DEFAULT_START_EVENTS);
    }

    private void sendCancel() {
        PaceManTracker.logDebug("Telling Paceman to cancel the run.");
        int tries = 0;
        // While sending gives back an error
        while (PaceManResponse.Type.SEND_ERROR == (
                this.sendCancelToPacemanGG()
        ).type) {
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
        while (PaceManResponse.Type.SEND_ERROR == (response = this.sendEventsToPacemanGG()).type) {
            if (++tries < 5) {
                // Wait 5 seconds on failure before retry.
                PaceManTracker.logError("Failed to send to PaceMan.gg, retrying in 5 seconds...");
                SleepUtil.sleep(5000);
            } else {
                break;
            }
        }
        if (response.type == PaceManResponse.Type.DENIED) {
            // Deny response = cancel the run
            PaceManTracker.logError("PaceMan.gg denied run data, no more data will be sent for this run.");
            this.endRun();
        } else if (response.type == PaceManResponse.Type.SEND_ERROR) {
            PaceManTracker.logError("Failed to send to PaceMan.gg after a couple tries, no more data will be sent for this run.");
            this.endRun();
        } else {
            PaceManTracker.logDebug("Successfully sent to PaceMan.gg");
            this.headerToSend = null;
            this.eventsToSend.clear();
            this.runOnPaceMan = true;
        }
    }

    public Path getWorldPath() {
        return this.eventTracker.getWorldPath();
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
        this.stateTracker.stop();
    }

    private enum RunProgress {
        NONE, STARTING, PACING, ENDED
    }

    @SuppressWarnings("")
    static class PaceManResponse {
        Type type;
        @Nullable String message = null;
        @Nullable Throwable error = null;

        PaceManResponse(Type type, String message) {
            this.type = type;
            this.message = message;
        }

        PaceManResponse(Type type, Throwable t) {
            this.type = type;
            this.error = t;
        }

        enum Type {
            SUCCESS, // < 400 response
            DENIED, // >= 400 response
            SEND_ERROR // error while trying to send
        }
    }

}
