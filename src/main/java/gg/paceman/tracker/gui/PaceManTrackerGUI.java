package gg.paceman.tracker.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import gg.paceman.tracker.PaceManTracker;
import gg.paceman.tracker.PaceManTrackerOptions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class PaceManTrackerGUI extends JFrame {
    private static PaceManTrackerGUI instance = null;
    private static boolean RESET_STATS_OPTION_USABLE = false;
    private JCheckBox enabledCheckBox;
    private JPasswordField accessKeyField;
    private JPanel mainPanel;
    private JButton saveButton;
    private JButton testButton;
    private JCheckBox resetStatsEnabled;
    private boolean closed = false;
    private final boolean asPlugin;

    public PaceManTrackerGUI(boolean asPlugin) {
        this.setTitle("PaceMan Tracker");
        this.asPlugin = asPlugin;

        this.setContentPane(this.mainPanel);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                PaceManTrackerGUI.this.onClose();
            }
        });

        if (!asPlugin) {
            this.mainPanel.remove(this.enabledCheckBox);
        }
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
        this.enabledCheckBox.setSelected(options.enabledForPlugin);
        this.enabledCheckBox.addActionListener(e -> {
            this.saveButton.setEnabled(this.hasChanges());
            if (asPlugin) {
                this.updateEnabledFields();
            }
        });
        this.resetStatsEnabled.setSelected(options.resetStatsEnabled);
        this.resetStatsEnabled.addActionListener(e -> {
            this.saveButton.setEnabled(this.hasChanges());
        });
        this.accessKeyField.setText(options.accessKey);
        this.accessKeyField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    PaceManTrackerGUI.this.save();
                }
                PaceManTrackerGUI.this.updateButtons();
            }

        });
        if (!RESET_STATS_OPTION_USABLE) {
            this.remove(this.resetStatsEnabled);
        }
        if (asPlugin) {
            this.updateEnabledFields();
        }
        this.saveButton.addActionListener(e -> this.save());
        this.saveButton.setEnabled(this.hasChanges());

        this.testButton.addActionListener(e -> this.onPressTest());

        this.revalidate();
        this.setMinimumSize(new Dimension(300, (asPlugin ? 140 : 120) + (RESET_STATS_OPTION_USABLE ? 20 : 0)));
        this.pack();
        this.setResizable(false);
        this.setVisible(true);
    }

    public static PaceManTrackerGUI open(boolean asPlugin, Point initialLocation) {
        if (instance == null || instance.isClosed()) {
            instance = new PaceManTrackerGUI(asPlugin);
            if (initialLocation != null) {
                instance.setLocation(initialLocation);
            }
        } else {
            instance.requestFocus();
        }
        return instance;
    }

    private void updateEnabledFields() {
        this.accessKeyField.setEnabled(this.checkBoxEnabled());
        if (RESET_STATS_OPTION_USABLE) {
            this.resetStatsEnabled.setEnabled(this.checkBoxEnabled());
        }
    }

    private void onPressTest() {
        this.save();
        this.testButton.setEnabled(false);
        new Thread(() -> {
            this.testKey();
            this.testButton.setEnabled(true);
        }, "test-button").start();
    }

    private void testKey() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
        final Consumer<String> onFailure = s -> {
            JOptionPane.showMessageDialog(this, s, "PaceMan Tracker: Test Failed", JOptionPane.ERROR_MESSAGE);
        };
        final Consumer<String> onSuccess = s -> {
            JOptionPane.showMessageDialog(this, s, "PaceMan Tracker: Test Successful", JOptionPane.INFORMATION_MESSAGE);
        };

        boolean keyEmpty = options.accessKey.trim().isEmpty();

        if (this.asPlugin && !options.enabledForPlugin) {
            onFailure.accept("Please press the enabled checkbox" + (keyEmpty ? " and enter your access key in the text box" : "") + "!");
            return;
        }

        if (keyEmpty) {
            onFailure.accept("Please input an access key!");
            return;
        }

        PaceManTracker.PostResponse response = PaceManTracker.testAccessKey(options.accessKey);
        if (response == null || response.getCode() >= 300) {
            onFailure.accept(response == null ? "Access key is not valid! (no response)" : "Access key is not valid! (" + response.getCode() + ": " + response.getMessage() + ")");
            return;
        }

        onSuccess.accept("Your access key is valid! Please make sure you have SpeedRunIGT 14.2+ installed on all your instances!");
    }

    private boolean hasChanges() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
        return (this.asPlugin && this.checkBoxEnabled() != options.enabledForPlugin) || (this.resetStatsEnabled() != options.resetStatsEnabled) || (!Objects.equals(this.getKeyBoxText(), options.accessKey));
    }

    private void save() {
        PaceManTrackerOptions options = PaceManTrackerOptions.getInstance();
        options.enabledForPlugin = this.checkBoxEnabled();
        options.resetStatsEnabled = this.resetStatsEnabled();
        options.accessKey = this.getKeyBoxText().trim();
        try {
            options.save();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        this.updateButtons();
    }

    private void updateButtons() {
        boolean hasChanges = this.hasChanges();
        this.saveButton.setEnabled(hasChanges);
        this.testButton.setText(hasChanges ? "Save and Test" : "Test");
    }

    private boolean checkBoxEnabled() {
        return this.enabledCheckBox.isSelected();
    }

    private boolean resetStatsEnabled() {
        return this.resetStatsEnabled.isSelected();
    }

    private String getKeyBoxText() {
        return new String(this.accessKeyField.getPassword());
    }

    public boolean isClosed() {
        return this.closed;
    }

    private void onClose() {
        // If not running as plugin, closing the GUI should stop the tracker.
        if (!this.asPlugin) {
            PaceManTracker.getInstance().stop();
        }
        this.closed = true;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(6, 2, new Insets(5, 5, 5, 5), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("PaceMan Tracker");
        mainPanel.add(label1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        enabledCheckBox = new JCheckBox();
        enabledCheckBox.setText("Enabled");
        mainPanel.add(enabledCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resetStatsEnabled = new JCheckBox();
        resetStatsEnabled.setText("Include resetting stats");
        mainPanel.add(resetStatsEnabled, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        accessKeyField = new JPasswordField();
        accessKeyField.setText("");
        panel1.add(accessKeyField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Access Key:");
        panel1.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        saveButton = new JButton();
        saveButton.setText("Save");
        mainPanel.add(saveButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        testButton = new JButton();
        testButton.setText("Test");
        mainPanel.add(testButton, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
