package gg.paceman.tracker.launching;

import com.google.common.io.Resources;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import gg.paceman.tracker.gui.PaceManTrackerPanel;
import gg.paceman.tracker.util.LockUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Launches PaceMan Tracker as a Julti Plugin
 */
public class PaceManTrackerJinglePluginInit {
    private static LockUtil.LockStuff lockStuff;
    private static boolean shouldRun = true;

    public static void main(String[] args) throws IOException {
        // This is only used to test the plugin in the dev environment
        // PaceManTrackerJinglePluginInit.main itself is never used when users run Jingle

        // Run this in dev to test as Jingle plugin

        PluginManager.JinglePluginData pluginData = PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(PaceManTrackerJinglePluginInit.class, "/jingle.plugin.json"), Charset.defaultCharset())
        );
        PaceManTracker.VERSION = pluginData.version;
        JingleAppLaunch.launchWithDevPlugin(args, pluginData, PaceManTrackerJinglePluginInit::initialize);
    }

    private static void checkLock() {
        Path lockPath = PaceManTrackerOptions.getPaceManDir().resolve("LOCK");
        if (LockUtil.isLocked(lockPath)) {
            PaceManTracker.logError("PaceMan Tracker will not run as it is already open elsewhere!");
            shouldRun = false;
        } else {
            lockStuff = LockUtil.lock(lockPath);
            PluginEvents.STOP.register(() -> LockUtil.releaseLock(lockStuff));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> LockUtil.releaseLock(lockStuff)));
        }
    }

    private static void setLoggers() {
        PaceManTracker.logConsumer = m -> Jingle.log(Level.INFO, "(PaceMan Tracker) " + m);
        PaceManTracker.debugConsumer = m -> Jingle.log(Level.DEBUG, "(PaceMan Tracker) " + m);
        PaceManTracker.errorConsumer = m -> Jingle.log(Level.ERROR, "(PaceMan Tracker) " + m);
        PaceManTracker.warningConsumer = m -> Jingle.log(Level.WARN, "(PaceMan Tracker) " + m);
    }

    public static void initialize() {
        PaceManTrackerOptions.ensurePaceManDir();
        PaceManTrackerJinglePluginInit.setLoggers();
        PaceManTrackerJinglePluginInit.checkLock();
        if (!shouldRun) {
            return;
        }
        try {
            PaceManTrackerOptions.tryLoad().save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Optional<PluginManager.LoadedJinglePlugin> pluginData = PluginManager.getLoadedPlugins().stream().filter(loadedJinglePlugin -> loadedJinglePlugin.pluginData.id.equals("paceman-tracker")).findAny();
        if (pluginData.isPresent()) {
            String version = pluginData.get().pluginData.version;
            PaceManTracker.VERSION = version.equals("${version}") ? "DEV" : version;
            PaceManTracker.log("Loaded PaceMan Tracker v" + PaceManTracker.VERSION);
        }
        PaceManTracker tracker = PaceManTracker.getInstance();
        tracker.start(true);
        PluginEvents.STOP.register(tracker::stop);

        Pair<PaceManTrackerGUI, JPanel> guiPair = PaceManTrackerPanel.getNewGUIAsPanel();
        PaceManTrackerGUI paceManTrackerGUI = guiPair.getLeft();
        JPanel pmtPanel = guiPair.getRight();

        JingleGUI.addPluginTab("PaceMan Tracker", pmtPanel);

        JingleGUI.get().registerQuickActionButton(0, () -> {
            PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
            if (options == null) return null;
            if (options.accessKey.isEmpty()) return null;
            return JingleGUI.makeButton(
                    options.enabledForPlugin ? "Disable PaceMan" : "Enable PaceMan",
                    () -> {
                        options.enabledForPlugin = !options.enabledForPlugin;
                        paceManTrackerGUI.enabledCheckBox.setSelected(options.enabledForPlugin);
                        JingleGUI.get().refreshQuickActions();
                        JingleGUI.get().refreshHack();
                        try {
                            options.save();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    },
                    () -> JingleGUI.get().openTab(pmtPanel),
                    "Right Click to Configure",
                    true
            );
        });
        PaceManTracker.jingleQABRefresh = () -> JingleGUI.get().refreshQuickActions();
    }
}
