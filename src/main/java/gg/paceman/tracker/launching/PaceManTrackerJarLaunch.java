package gg.paceman.tracker.launching;

import com.formdev.flatlaf.FlatDarkLaf;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * Launches PaceMan as a standalone program.
 */
public class PaceManTrackerJarLaunch {
    public static void main(String[] args) throws IOException {
        FlatDarkLaf.setup();
        PaceManTrackerOptions.load();
        if (!Arrays.asList(args).contains("--nogui")) {
            PaceManTrackerGUI.open(false, null).setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        }
        PaceManTracker.getInstance().start(false);
    }
}
