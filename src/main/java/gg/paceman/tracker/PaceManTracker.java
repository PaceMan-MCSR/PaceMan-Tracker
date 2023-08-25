package gg.paceman.tracker;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The actual logic and stuff for the PaceMan Tracker
 */
public class PaceManTracker {
    private static final PaceManTracker INSTANCE = new PaceManTracker();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private boolean asPlugin;

    public static PaceManTracker getInstance() {
        return INSTANCE;
    }

    private boolean shouldRun() {
        return !this.asPlugin || PaceManTrackerOptions.getInstance().enabledForPlugin;
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

        // TODO: Main tracker loop logic
        // Access key can be obtained through PaceManTrackerOptions.getInstance().accessKey
    }

    //
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
