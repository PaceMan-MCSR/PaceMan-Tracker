package gg.paceman.tracker.util;

import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import gg.paceman.tracker.util.VersionUtil.Version;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GitHub;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class UpdateUtil {
    private static final String GH_REPO_LOCATION = "PaceMan-MCSR/PaceMan-Tracker";

    public static void checkForUpdates(PaceManTrackerGUI gui, String currentVersionString) throws IOException {
        if (currentVersionString == null || "DEV".equals(currentVersionString)) return;
        PaceManTracker.logDebug("Checking for updates...");
        Version currentVersion = Version.of(currentVersionString.startsWith("v") ? currentVersionString.substring(1) : currentVersionString);
        Version latestVersion = UpdateUtil.getLatestGithubVersion();

        if (latestVersion.compareTo(currentVersion) > 0) {
            if (gui != null) {
                if (0 == JOptionPane.showConfirmDialog(gui, "An update is available (v" + latestVersion + "), would you like to go to the latest release page?", "PaceMan Tracker: Update Available", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE)) {
                    Desktop.getDesktop().browse(URI.create("https://github.com/" + GH_REPO_LOCATION + "/releases/latest"));
                }
            } else {
                PaceManTracker.logWarning("You are not on the latest version! (v" + latestVersion + ")");
            }
        }
    }

    private static Version getLatestGithubVersion() throws IOException {
        GHRelease release = GitHub.connectAnonymously().getRepository(GH_REPO_LOCATION).listReleases().toList().get(0);
        String lv = release.getTagName();
        lv = lv.startsWith("v") ? lv.substring(1) : lv;
        return Version.of(lv);
    }
}
