package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings overlay panel that appears on top of the simulation with a semi-transparent dark overlay.
 * Pauses the simulation while settings are being adjusted.
 */
public class SettingsOverlay extends JPanel {
    private final SettingsManager settingsManager;
    private final Runnable onClose;
    
    // UI Components
    private JComboBox<String> resolutionCombo;
    private JCheckBox fullscreenCheck;
    
    // Resolution options with their dimensions
    private static final Map<String, Dimension> RESOLUTIONS = new LinkedHashMap<>();
    static {
        // 16:9 resolutions
        RESOLUTIONS.put("1280x720 (HD)", new Dimension(1280, 720));
        RESOLUTIONS.put("1366x768", new Dimension(1366, 768));
        RESOLUTIONS.put("1600x900", new Dimension(1600, 900));
        RESOLUTIONS.put("1920x1080 (Full HD)", new Dimension(1920, 1080));
        RESOLUTIONS.put("2560x1440 (QHD)", new Dimension(2560, 1440));
        RESOLUTIONS.put("3840x2160 (4K UHD)", new Dimension(3840, 2160));
        
        // 21:9 ultrawide resolutions
        RESOLUTIONS.put("2560x1080 (21:9)", new Dimension(2560, 1080));
        RESOLUTIONS.put("3440x1440 (WQHD 21:9)", new Dimension(3440, 1440));
        
        // 16:10 resolutions
        RESOLUTIONS.put("1440x900 (16:10)", new Dimension(1440, 900));
        RESOLUTIONS.put("1680x1050 (16:10)", new Dimension(1680, 1050));
        RESOLUTIONS.put("1920x1200 (16:10)", new Dimension(1920, 1200));
        
        // 4:3 resolutions
        RESOLUTIONS.put("1024x768 (4:3)", new Dimension(1024, 768));
        RESOLUTIONS.put("1280x960 (4:3)", new Dimension(1280, 960));
        RESOLUTIONS.put("1600x1200 (4:3)", new Dimension(1600, 1200));
    }
    
    public SettingsOverlay(SettingsManager settingsManager, Runnable onClose) {
        this.settingsManager = settingsManager;
        this.onClose = onClose;
        
        setupUI();
        loadCurrentSettings();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false); // Make transparent for semi-transparent overlay effect
        
        // Create semi-transparent background with blur effect
        JPanel backgroundPanel = createBackgroundPanel();

        // Create settings panel
        JPanel settingsPanel = createSettingsPanel();

        // Add settings panel to background
        backgroundPanel.add(settingsPanel);

        // Add background panel to overlay
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(backgroundPanel, gbc);

        // Add key binding for ESC to close
        setupKeyBindings();
    }

    private JPanel createBackgroundPanel() {
        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                
                // Draw semi-transparent dark overlay - darker for sci-fi look
                g2d.setColor(new Color(0, 0, 0, 220));
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        backgroundPanel.setOpaque(false);
        backgroundPanel.setLayout(new GridBagLayout());
        return backgroundPanel;
    }

    private JPanel createSettingsPanel() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(new Color(18, 18, 18)); // Dark sci-fi #121212
        settingsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 255, 255), 2), // Neon cyan border
            new EmptyBorder(20, 30, 20, 30)
        ));
        
        // Title
        JLabel titleLabel = new JLabel("SYSTEM SETTINGS");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(0, 255, 255)); // Neon cyan
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        settingsPanel.add(titleLabel);
        settingsPanel.add(Box.createVerticalStrut(20));
        
        // Display Mode Section
        settingsPanel.add(createSectionLabel("DISPLAY MODE"));
        fullscreenCheck = new JCheckBox("Fullscreen");
        fullscreenCheck.setBackground(new Color(18, 18, 18));
        fullscreenCheck.setForeground(new Color(220, 220, 220)); // Light gray text
        fullscreenCheck.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fullscreenCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsPanel.add(fullscreenCheck);
        settingsPanel.add(Box.createVerticalStrut(15));
        
        // Resolution Section
        settingsPanel.add(createSectionLabel("RESOLUTION"));
        resolutionCombo = new JComboBox<>(RESOLUTIONS.keySet().toArray(new String[0]));
        resolutionCombo.setMaximumSize(new Dimension(400, 30));
        resolutionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleComboBox(resolutionCombo);
        settingsPanel.add(resolutionCombo);
        settingsPanel.add(Box.createVerticalStrut(25));
        
        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonsPanel.setBackground(new Color(18, 18, 18));
        buttonsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        ModernButton applyButton = new ModernButton("APPLY");
        applyButton.setPreferredSize(new Dimension(120, 40));
        applyButton.addActionListener(e -> applySettings());
        buttonsPanel.add(applyButton);
        
        ModernButton cancelButton = new ModernButton("CANCEL");
        cancelButton.setPreferredSize(new Dimension(120, 40));
        cancelButton.addActionListener(e -> closeOverlay());
        buttonsPanel.add(cancelButton);
        
        settingsPanel.add(buttonsPanel);
        return settingsPanel;
    }

    private void setupKeyBindings() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSettings");
        getActionMap().put("closeSettings", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeOverlay();
            }
        });
    }
    
    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel("▸ " + text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(new Color(0, 255, 255)); // Neon cyan
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    
    private void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(new Color(30, 30, 35)); // Darker background
        combo.setForeground(new Color(0, 255, 255)); // Neon cyan text
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setBorder(BorderFactory.createLineBorder(new Color(0, 255, 255, 100), 1));
    }
    

    private void loadCurrentSettings() {
        // Load fullscreen setting
        fullscreenCheck.setSelected(settingsManager.isFullscreen());
        
        // Load resolution
        int currentWidth = settingsManager.getWindowWidth();
        int currentHeight = settingsManager.getWindowHeight();
        String currentResKey = findResolutionKey(currentWidth, currentHeight);
        if (currentResKey != null) {
            resolutionCombo.setSelectedItem(currentResKey);
        }
    }
    
    private String findResolutionKey(int width, int height) {
        for (Map.Entry<String, Dimension> entry : RESOLUTIONS.entrySet()) {
            Dimension dim = entry.getValue();
            if (dim.width == width && dim.height == height) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private void applySettings() {
        // Apply fullscreen setting
        settingsManager.setFullscreen(fullscreenCheck.isSelected());
        
        // Apply resolution
        String selectedRes = (String) resolutionCombo.getSelectedItem();
        if (selectedRes != null) {
            Dimension dim = RESOLUTIONS.get(selectedRes);
            if (dim != null) {
                settingsManager.setWindowWidth(dim.width);
                settingsManager.setWindowHeight(dim.height);
            }
        }
        
        // Save settings
        settingsManager.saveSettings();
        
        // Close overlay
        closeOverlay();
    }
    
    private void closeOverlay() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
