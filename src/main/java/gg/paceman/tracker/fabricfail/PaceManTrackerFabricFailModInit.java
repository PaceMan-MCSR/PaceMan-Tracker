package gg.paceman.tracker.fabricfail;

import net.fabricmc.api.ModInitializer;

public class PaceManTrackerFabricFailModInit implements ModInitializer {
    @Override
    public void onInitialize() {
        throw new RuntimeException("PaceMan Tracker is not supposed to be ran as a mod!");
    }
}
