package gg.paceman.tracker;

import gg.paceman.tracker.util.ExceptionUtil;

import java.io.IOException;
import java.nio.file.Paths;
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

    public static Consumer<String> logConsumer = System.out::println;
    public static Consumer<String> errorConsumer = System.out::println;
    private final EventTracker eventTracker = new EventTracker(Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("events.latest").toAbsolutePath());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean asPlugin;

    public static PaceManTracker getInstance() {
        return INSTANCE;
    }

    public static void log(String message) {
        logConsumer.accept(message);
    }

    public static void logError(String error) {
        errorConsumer.accept(error);
    }

    private boolean shouldRun() {
        return !this.asPlugin || PaceManTrackerOptions.getInstance().enabledForPlugin;
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
                ExceptionUtil.showExceptionAndExit(t, "Paceman Tracker has crashed! Please report this bug to the developers.\n" + t);
            } else {
                String detailedString = ExceptionUtil.toDetailedString(t);
                logError("Exception in Paceman Tracker: " + detailedString);
            }
        }
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
            // log("New Header: " + this.eventTracker.getCurrentHeader());
            // TODO: send new header
        }

        List<String> latestNewLines = this.eventTracker.getLatestNewLines();
        if (latestNewLines.isEmpty()) {
            return;
        }

        // log("New Lines: " + latestNewLines);
        // TODO send new latest lines

        // Access key can be obtained through PaceManTrackerOptions.getInstance().accessKey
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
}
