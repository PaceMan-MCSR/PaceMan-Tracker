package gg.paceman.tracker.launching;

import com.google.common.io.Resources;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import gg.paceman.tracker.util.LockUtil;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiAppLaunch;
import xyz.duncanruns.julti.gui.JultiGUI;
import xyz.duncanruns.julti.gui.PluginsGUI;
import xyz.duncanruns.julti.plugin.PluginEvents;
import xyz.duncanruns.julti.plugin.PluginInitializer;
import xyz.duncanruns.julti.plugin.PluginManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Launches PaceMan Tracker as a Julti Plugin
 */
public class PaceManTrackerJultiPluginInit implements PluginInitializer {
    private static LockUtil.LockStuff lockStuff;
    private static boolean shouldRun = true;

    public static void main(String[] args) throws IOException {
        // This is only used to test the plugin in the dev environment
        // PaceManTrackerJultiPluginInit.main itself is never used when users run Julti

        // Run this in dev to test as Julti plugin

        PluginManager.JultiPluginData pluginData = PluginManager.JultiPluginData.fromString(
                Resources.toString(Resources.getResource(PaceManTrackerJultiPluginInit.class, "/julti.plugin.json"), Charset.defaultCharset())
        );
        PaceManTracker.VERSION = pluginData.version;
        JultiAppLaunch.launchWithDevPlugin(args, pluginData, new PaceManTrackerJultiPluginInit());
    }

    private static void checkLock() {
        Path lockPath = PaceManTrackerOptions.getPaceManDir().resolve("LOCK");
        if (LockUtil.isLocked(lockPath)) {
            PaceManTracker.logError("PaceMan Tracker will not run as it is already open elsewhere!");
            shouldRun = false;
        } else {
            lockStuff = LockUtil.lock(lockPath);
            PluginEvents.RunnableEventType.PRE_UPDATE.register(() -> LockUtil.releaseLock(lockStuff));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> LockUtil.releaseLock(lockStuff)));
        }
    }

    private static void setLoggers() {
        PaceManTracker.logConsumer = m -> Julti.log(Level.INFO, "(PaceMan Tracker) " + m);
        PaceManTracker.debugConsumer = m -> Julti.log(Level.DEBUG, "(PaceMan Tracker) " + m);
        PaceManTracker.errorConsumer = m -> Julti.log(Level.ERROR, "(PaceMan Tracker) " + m);
        PaceManTracker.warningConsumer = m -> Julti.log(Level.WARN, "(PaceMan Tracker) " + m);
    }

    @Override
    public void initialize() {
        PaceManTrackerOptions.ensurePaceManDir();
        PaceManTrackerJultiPluginInit.setLoggers();
        PaceManTrackerJultiPluginInit.checkLock();
        if (!shouldRun) {
            return;
        }
        try {
            PaceManTrackerOptions.tryLoad().save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Optional<PluginManager.LoadedJultiPlugin> pluginData = PluginManager.getPluginManager().getLoadedPlugins().stream().filter(loadedJultiPlugin -> loadedJultiPlugin.pluginData.id.equals("paceman-tracker")).findAny();
        if (pluginData.isPresent()) {
            String version = pluginData.get().pluginData.version;
            PaceManTracker.VERSION = version.equals("${version}") ? "DEV" : version;
            PaceManTracker.log("Loaded PaceMan Tracker v" + PaceManTracker.VERSION);
        }
        PaceManTracker tracker = PaceManTracker.getInstance();
        tracker.start(true);
        PluginEvents.RunnableEventType.STOP.register(tracker::stop);
        PluginEvents.RunnableEventType.PRE_UPDATE.register(tracker::stop);
    }

    @Override
    public void onMenuButtonPress() {
        if (!shouldRun) {
            JOptionPane.showMessageDialog(JultiGUI.getPluginsGUI(), "PaceMan Tracker is already opened elsewhere! Please make sure it is closed, then restart Julti to continue.", "PaceMan Tracker: Already Opened", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PluginsGUI pluginsGUI = JultiGUI.getPluginsGUI();
        PaceManTrackerGUI.open(true, new Point(pluginsGUI.getX() + pluginsGUI.getWidth(), pluginsGUI.getY()));
    }
}
