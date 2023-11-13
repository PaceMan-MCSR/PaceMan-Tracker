package gg.paceman.tracker.launching;

import com.google.common.io.Resources;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;
import gg.paceman.tracker.gui.PaceManTrackerGUI;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.julti.Julti;
import xyz.duncanruns.julti.JultiAppLaunch;
import xyz.duncanruns.julti.gui.JultiGUI;
import xyz.duncanruns.julti.gui.PluginsGUI;
import xyz.duncanruns.julti.plugin.PluginEvents;
import xyz.duncanruns.julti.plugin.PluginInitializer;
import xyz.duncanruns.julti.plugin.PluginManager;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Launches PaceMan Tracker as a Julti Plugin
 */
public class PaceManTrackerPluginInit implements PluginInitializer {
    public static void main(String[] args) throws IOException {
        // This is only used to test the plugin in the dev environment
        // ExamplePlugin.main itself is never used when users run Julti

        // Run this in dev to test as Julti plugin

        JultiAppLaunch.launchWithDevPlugin(args, PluginManager.JultiPluginData.fromString(
                Resources.toString(Resources.getResource(PaceManTrackerPluginInit.class, "/julti.plugin.json"), Charset.defaultCharset())
        ), new PaceManTrackerPluginInit());
    }

    @Override
    public void initialize() {
        try {
            PaceManTrackerOptions.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PaceManTracker tracker = PaceManTracker.getInstance();
        PaceManTracker.logConsumer = m -> Julti.log(Level.INFO, m);
        PaceManTracker.debugConsumer = m -> Julti.log(Level.DEBUG, m);
        PaceManTracker.errorConsumer = m -> Julti.log(Level.ERROR, m);
        tracker.start(true);
        PluginEvents.RunnableEventType.STOP.register(tracker::stop);
    }

    @Override
    public void onMenuButtonPress() {
        PluginsGUI pluginsGUI = JultiGUI.getPluginsGUI();
        PaceManTrackerGUI.open(true, new Point(pluginsGUI.getX() + pluginsGUI.getWidth(), pluginsGUI.getY()));
    }
}
