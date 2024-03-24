package gg.paceman.tracker.fabricfail;

public class PaceManTrackerFabricFailModInit {
    public void onInitialize() {
        throw new RuntimeException("PaceMan Tracker is not supposed to be ran as a mod!");
    }
}
