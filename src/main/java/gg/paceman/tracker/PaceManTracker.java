package gg.paceman.tracker;

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
    private static final EventFileTracker EFT = new EventFileTracker(Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("events.latest").toAbsolutePath());

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean asPlugin;
    public Consumer<String> logConsumer = System.out::println;
    public Consumer<String> errorConsumer = System.out::println;

    public static PaceManTracker getInstance() {
        return INSTANCE;
    }

    private boolean shouldRun() {
        return !this.asPlugin || PaceManTrackerOptions.getInstance().enabledForPlugin;
    }

    public void log(String message) {
        this.logConsumer.accept(message);
    }

    public void logError(String error) {
        this.errorConsumer.accept(error);
    }

    public void start(boolean asPlugin) {
        this.asPlugin = asPlugin;
        // Run tick every 1 second
        this.executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        if (!this.shouldRun()) {
            // No activity to be done if disabled
            return;
        }

        try {
            if (!EFT.update()) {
                return;
            }
        } catch (IOException e) {
            this.logError(e.toString());
            return;
        }

        if (EFT.hasHeaderChanged()) {
            // log("New Header: " + EFT.getCurrentHeader());
            // TODO: send new header
        }

        List<String> latestNewLines = EFT.getLatestNewLines();
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
