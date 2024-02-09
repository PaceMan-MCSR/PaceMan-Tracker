package gg.paceman.tracker.launching;

import com.formdev.flatlaf.FlatDarkLaf;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.UpdateUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Launches PaceMan as a standalone program.
 */
public class PaceManTrackerJarLaunch {
    public static void main(String[] args) throws IOException {
        FlatDarkLaf.setup();
        PaceManTrackerOptions.load().save();
        PaceManTrackerGUI gui = null;
        if (!Arrays.asList(args).contains("--nogui")) {
            gui = PaceManTrackerGUI.open(false, null);
            gui.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        }
        PaceManTracker.VERSION = Optional.ofNullable(PaceManTrackerJarLaunch.class.getPackage().getImplementationVersion()).orElse("DEV");
        PaceManTracker.log("Running PaceMan Tracker v" + PaceManTracker.VERSION);
        PaceManTracker.getInstance().start(false);
        try {
            UpdateUtil.checkForUpdates(gui, PaceManTracker.VERSION);
        } catch (IOException e) {
            PaceManTracker.logError("Failed to check for an update for PaceMan Tracker:\n" + ExceptionUtil.toDetailedString(e));
        }
    }
}
