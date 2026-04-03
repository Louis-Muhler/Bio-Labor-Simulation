package com.biolab;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Centralized Swing look-and-feel bootstrap shared by all launchers.
 */
final class AppThemeBootstrap {
    private AppThemeBootstrap() {
    }

    static void installDarkTheme() {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        UIManager.put("RootPane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.inactiveBackground", new Color(18, 18, 18));
        UIManager.put("TitlePane.foreground", new Color(200, 200, 200));
        UIManager.put("TitlePane.inactiveForeground", new Color(130, 130, 130));
        UIManager.put("TitlePane.unifiedBackground", true);

        FlatDarkLaf.setup();
    }
}

