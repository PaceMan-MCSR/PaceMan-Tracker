package gg.paceman.tracker;

import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.PacemanGGUtil;
import gg.paceman.tracker.util.SleepUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The actual logic and stuff for the PaceMan Tracker
 */
public class PaceManTracker {
    private static final PaceManTracker INSTANCE = new PaceManTracker();

    // If any end events are reached and no events have been sent for the current run, then prevent sending anything.
    // If events have already been sent for this run, then send the end event and then send no more events
    private static final List<String> END_EVENTS = Arrays.asList("common.open_to_lan","common.enable_cheats","common.view_seed", "rsg.credits");
    // If any start event is reached for the first time for this run, enable sending events for this run, send the header and all events so far.
    private static final List<String> START_EVENTS = Collections.singletonList("rsg.enter_nether");
    // Unimportant events are not considered when determining if an event is recent enough to send the run to PaceMan
    private static final List<String> UNIMPORTANT_EVENTS = Arrays.asList("common.leave_world", "common.rejoin_world");

    private static final long RUN_TOO_LONG_MILLIS = 3_600_000; // 1 hour
    private static final long EVENT_RECENT_ENOUGH_MILLIS = 60_000; // 1 minute

    public static Consumer<String> logConsumer = System.out::println;
    public static Consumer<String> debugConsumer = System.out::println;
    public static Consumer<String> errorConsumer = System.out::println;

    private final EventTracker eventTracker = new EventTracker(Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("latest_world.json").toAbsolutePath());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean asPlugin;

    private String headerToSend = null;
    private boolean runOnPaceMan = false;
    private RunProgress runProgress = RunProgress.NONE;
    private final List<String> eventsToSend = new ArrayList<>();

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
                logError("PaceMan Tracker has crashed! Please report this bug to the developers. " + detailedString);
                logError("PaceMan Tracker will now shutdown, Julti will need to be restarted to use PaceMan Tracker.");
                this.stop();
            }
        }
    }

    private void setRunProgress(RunProgress runProgress) {
        logDebug("Run Progress set to " + runProgress);
        this.runProgress = runProgress;
    }

    private void tick() {
        if (!this.shouldRun()) {
            return;
        }

        try {
            if (!this.eventTracker.update()) {
                return;
            }
        } catch (IOException e) {
            logError("Exception while updating event tracker: " + e);
            return;
        }

        if (this.eventTracker.hasHeaderChanged()) {
            this.headerToSend = this.eventTracker.getCurrentHeader();
            this.eventsToSend.clear();
            this.runOnPaceMan = false;
            this.setRunProgress(RunProgress.STARTING);
        }

        List<String> latestNewLines = this.eventTracker.getLatestNewLines();

        if (this.getTimeSinceRunStart() > RUN_TOO_LONG_MILLIS) {
            PaceManTracker.logDebug("Run happened too long ago, this run won't be sent to PaceMan.gg");
            this.setRunProgress(RunProgress.ENDED);
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
                this.setRunProgress(RunProgress.ENDED);
                break;
            } else if (this.runProgress != RunProgress.PACING && START_EVENTS.contains(eventName)) {
                log("PaceMan Tracker start event reached, sending to PaceMan.gg should be enabled if an event is recent enough.");
                this.setRunProgress(RunProgress.PACING);
            }
            // Determine if an event is recent enough to dump
            if (!shouldDump && this.runProgress == RunProgress.PACING && !UNIMPORTANT_EVENTS.contains(eventName)) {
                long timeOfEvent = Long.parseLong(parts[1]) + this.eventTracker.getEventsLogCreationMillis();
                if (Math.abs(System.currentTimeMillis() - timeOfEvent) < EVENT_RECENT_ENOUGH_MILLIS) {
                    shouldDump = true;
                }
            }
        }
        if (shouldDump) {
            this.dumpToPacemanGG();
        }
    }

    private long getTimeSinceRunStart() {
        return Math.abs(System.currentTimeMillis() - this.eventTracker.getEventsLogCreationMillis());
    }

    private void dumpToPacemanGG() {
        logDebug("Dumping to paceman:");
        PacemanGGUtil.PaceManResponse response;
        int tries = 0;
        // While sending gives back an error
        while (PacemanGGUtil.PaceManResponse.SEND_ERROR == (
                response = PacemanGGUtil.sendToPacemanGG(
                        PaceManTrackerOptions.getInstance().accessKey,
                        this.headerToSend,
                        this.eventsToSend,
                        this.getTimeSinceRunStart()
                )
        )) {
            if (++tries < 5) {
                // Wait 5 seconds on failure before retry.
                PaceManTracker.logError("Failed to send to PaceMan.gg, retrying in 5 seconds...");
                SleepUtil.sleep(5000);
            } else {
                break;
            }
        }
        if (response == PacemanGGUtil.PaceManResponse.DENIED) {
            // Deny response = cancel the run
            PaceManTracker.logError("PaceMan.gg denied run data, no more data will be sent for this run.");
            this.setRunProgress(RunProgress.ENDED);
        } else if (response == PacemanGGUtil.PaceManResponse.SEND_ERROR) {
            PaceManTracker.logError("Failed to send to PaceMan.gg after a couple tries, no more data will be sent for this run.");
            this.setRunProgress(RunProgress.ENDED);
        } else {
            PaceManTracker.logDebug("Successfully sent to PaceMan.gg");
            this.headerToSend = null;
            this.eventsToSend.clear();
            this.runOnPaceMan = true;
        }
    }

    public void stop() {
        // Wait for and shutdown executor
        this.executor.shutdownNow();
        try {
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Do cleanup
        // TODO: Tell PaceMan.gg that we have stopped (?)
    }

    private enum RunProgress {
        NONE, STARTING, PACING, ENDED
    }
}
