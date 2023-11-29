package gg.paceman.tracker.util;

import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
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
        Version currentVersion = new Version(currentVersionString.startsWith("v") ? currentVersionString.substring(1) : currentVersionString);
        Version latestVersion = UpdateUtil.getLatestGithubVersion();

        if (latestVersion.compareTo(currentVersion) > 0) {
            if (0 == JOptionPane.showConfirmDialog(gui, "An update is available (v" + latestVersion + "), would you like to go to the latest release page?", "PaceMan Tracker: Update Available", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE)) {
                Desktop.getDesktop().browse(URI.create("https://github.com/" + GH_REPO_LOCATION + "/releases/latest"));
            }
        }
    }

    private static Version getLatestGithubVersion() throws IOException {
        GHRelease release = GitHub.connectAnonymously().getRepository(GH_REPO_LOCATION).listReleases().toList().get(0);
        String lv = release.getTagName();
        lv = lv.startsWith("v") ? lv.substring(1) : lv;
        return new Version(lv);
    }

    /*
     * Version class from https://stackoverflow.com/questions/198431/how-do-you-compare-two-version-strings-in-java
     */
    public static class Version implements Comparable<Version> {

        private final String version;

        public Version(String version) {
            if (version == null)
                throw new IllegalArgumentException("Version can not be null");
            if (!version.matches("[0-9]+(\\.[0-9]+)*"))
                throw new IllegalArgumentException("Invalid version format");
            this.version = version;
        }

        public final String get() {
            return this.version;
        }

        @Override
        public int compareTo(Version that) {
            if (that == null)
                return 1;
            String[] thisParts = this.get().split("\\.");
            String[] thatParts = that.get().split("\\.");
            int length = Math.max(thisParts.length, thatParts.length);
            for (int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ?
                        Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ?
                        Integer.parseInt(thatParts[i]) : 0;
                if (thisPart < thatPart)
                    return -1;
                if (thisPart > thatPart)
                    return 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object that) {
            if (this == that)
                return true;
            if (that == null)
                return false;
            if (this.getClass() != that.getClass())
                return false;
            return this.compareTo((Version) that) == 0;
        }

        @Override
        public String toString() {
            return this.version;
        }
    }
}
