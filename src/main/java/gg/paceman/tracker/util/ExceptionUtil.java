package gg.paceman.tracker.util;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import xyz.duncanruns.julti.Julti;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A copy of ExceptionUtil from Julti so that it can be used outside Julti.
 */
public final class ExceptionUtil {
    private ExceptionUtil() {
    }

    public static String toDetailedString(Throwable t) {
        StringWriter out = new StringWriter();
        out.write(t.toString() + "\n");
        t.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    public static void showExceptionAndExit(Throwable t, String message) {
        String detailedException = ExceptionUtil.toDetailedString(t);
        int ans = JOptionPane.showOptionDialog(null, message, "Julti: Crash", JOptionPane.OK_CANCEL_OPTION, JOptionPane.ERROR_MESSAGE, null, new Object[]{"Copy Error", "OK"}, "Copy Error");
        if (ans == 0) {
            ExceptionUtil.copyToClipboard("Error during startup or main loop: " + detailedException);
        }
        LogManager.getLogger("Julti-Crash").error(detailedException); // We don't want to use Julti.log because it has a couple more steps.
        System.exit(1);
    }

    private static void copyToClipboard(String string) {
        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(
                            new StringSelection(string),
                            null
                    );
        } catch (Exception e) {
            Julti.log(Level.ERROR, "Failed to copy string to clipboard:\n" + xyz.duncanruns.julti.util.ExceptionUtil.toDetailedString(e));
        }
    }
}
