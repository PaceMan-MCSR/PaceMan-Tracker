package gg.paceman.tracker.launching;

import com.formdev.flatlaf.FlatDarkLaf;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import gg.paceman.tracker.util.ExceptionUtil;
import gg.paceman.tracker.util.LockUtil;
import gg.paceman.tracker.util.UpdateUtil;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Launches PaceMan as a standalone program.
 */
public class PaceManTrackerJarLaunch {
    private static LockUtil.LockStuff lockStuff;
    private static List<String> args;

    public static void main(String[] args) throws IOException {
        PaceManTrackerJarLaunch.args = Arrays.asList(args);
        FlatDarkLaf.setup();

        if (!PaceManTrackerJarLaunch.args.contains("--skiplocks")) {
            PaceManTrackerJarLaunch.checkLock();
        }

        PaceManTrackerOptions.load().save();
        PaceManTrackerGUI gui = null;
        if (!PaceManTrackerJarLaunch.args.contains("--nogui")) {
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

    private static void checkLock() {
        PaceManTrackerOptions.ensurePaceManDir();
        Path lockPath = PaceManTrackerOptions.getPaceManDir().resolve("LOCK");
        if (LockUtil.isLocked(lockPath)) {
            if (PaceManTrackerJarLaunch.args.contains("--nogui")) {
                System.out.println("PaceMan Tracker is already opened, you cannot run another instance. (Not recommended: use --skiplocks to bypass)");
                System.exit(0);
            } else {
                PaceManTrackerJarLaunch.showMultiTrackerWarning();
            }
        } else {
            lockStuff = LockUtil.lock(lockPath);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> LockUtil.releaseLock(lockStuff)));
        }
    }

    private static void showMultiTrackerWarning() {
        boolean isJulti = LockUtil.isLocked(Paths.get(System.getProperty("user.home")).resolve(".Julti").resolve("LOCK").toAbsolutePath());
        boolean isJingle = LockUtil.isLocked(Paths.get(System.getProperty("user.home")).resolve(".config").resolve("Jingle").resolve("LOCK").toAbsolutePath());
        int ans = JOptionPane.showConfirmDialog(null, "PaceMan Tracker is already opened" + (isJulti ? " in Julti" : (isJingle ? " in Jingle" : "")) + "! Are you sure you want to open the tracker again?", "PaceMan Tracker: Already Opened", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ans != 0) {
            System.exit(0);
        }
    }
}
