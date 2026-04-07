package com.biolab;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Overlay that lists all save games and exposes create/play/delete actions.
 */
public class SaveBrowserOverlay extends JPanel {
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 95);
    private static final String VIEW_LIST = "list";
    private static final String VIEW_CREATE = "create";

    private final DefaultListModel<SaveGameMetadata> model = new DefaultListModel<>();
    private final JList<SaveGameMetadata> list = new JList<>(model);
    private final SaveGameListCellRenderer renderer = new SaveGameListCellRenderer();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContent = new JPanel(cardLayout);

    private final JTextField mapName = new JTextField("New World");
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
    private final JSpinner initialPop = new JSpinner(new SpinnerNumberModel(1_500, 0, 100_000, 50));
    private final JSpinner maxPop = new JSpinner(new SpinnerNumberModel(20_000, 100, 500_000, 100));
    private final JSpinner temp = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
    private final JSpinner tox = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
    private final JSpinner food = new JSpinner(new SpinnerNumberModel(0.75, 0.0, 200.0, 0.25));
    private final JLabel title = new JLabel("SELECT GAME", SwingConstants.CENTER);

    private static final Font FORM_LABEL_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FORM_VALUE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font ROW_NAME_FONT = new Font("Segoe UI", Font.BOLD, 17);
    private static final int LIST_META_AREA_WIDTH = 280;

    private final JTextField inlineRenameEditor = new JTextField();
    private int editingIndex = -1;

    public SaveBrowserOverlay(Listener listener) {
        setOpaque(false);
        setLayout(new GridBagLayout());
        installEventBlocker(this);

        NeonCardPanel card = new NeonCardPanel(OverlayTheme.CARD_ARC, new Insets(16, 16, 16, 16));
        card.setLayout(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(850, 520));

        title.setForeground(OverlayTheme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(title, BorderLayout.CENTER);
        card.add(north, BorderLayout.NORTH);

        cardContent.setOpaque(false);
        cardContent.add(createListView(listener), VIEW_LIST);
        cardContent.add(createWorldFormView(listener), VIEW_CREATE);
        card.add(cardContent, BorderLayout.CENTER);

        add(card);
    }

    private static void installEventBlocker(JComponent component) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                e.consume();
            }
        };
        component.addMouseListener(adapter);
        component.addMouseMotionListener(adapter);
        component.addMouseWheelListener(adapter);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(OVERLAY_BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private JPanel createListView(Listener listener) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setBackground(new Color(0, 0, 0, 0));
        list.setForeground(OverlayTheme.ACCENT);
        list.setFixedCellHeight(64);
        list.setCellRenderer(renderer);
        configureInlineRenameEditor(listener);
        list.add(inlineRenameEditor);
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                Rectangle rowBounds = (idx >= 0) ? list.getCellBounds(idx, idx) : null;
                renderer.setHoveredIndex(rowBounds != null && rowBounds.contains(e.getPoint()) ? idx : -1);
                list.repaint();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                renderer.setHoveredIndex(-1);
                list.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) {
                    return;
                }
                int idx = list.locationToIndex(e.getPoint());
                Rectangle rowBounds = (idx >= 0) ? list.getCellBounds(idx, idx) : null;
                if (rowBounds == null || !rowBounds.contains(e.getPoint())) {
                    return;
                }
                SaveGameMetadata selected = model.get(idx);
                if (isRenameClick(rowBounds, e.getPoint(), selected)) {
                    beginInlineRename(idx);
                }
            }
        });

        list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "play-selected-save");
        list.getActionMap().put("play-selected-save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isRenameActive()) {
                    commitInlineRename(listener);
                } else {
                    playSelected(listener);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setBackground(OverlayTheme.PANEL_BG);
        vertical.setPreferredSize(new Dimension(8, Integer.MAX_VALUE));
        vertical.setUI(new RoundedScrollBarUI());

        JPanel listShell = createInnerFrame();
        listShell.add(scrollPane, BorderLayout.CENTER);
        panel.add(listShell, BorderLayout.CENTER);

        ModernButton play = new ModernButton("PLAY");
        ModernButton create = new ModernButton("CREATE");
        ModernButton delete = new ModernButton("DELETE");
        ModernButton back = new ModernButton("BACK");

        play.addActionListener(e -> {
            playSelected(listener);
        });
        delete.addActionListener(e -> {
            SaveGameMetadata selected = list.getSelectedValue();
            if (selected != null) listener.onDeleteRequested(selected);
        });
        create.addActionListener(e -> showCreateForm());
        back.addActionListener(e -> listener.onBackRequested());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(play);
        left.add(Box.createHorizontalStrut(10));
        left.add(create);
        left.add(Box.createHorizontalStrut(10));
        left.add(delete);
        actions.add(left, BorderLayout.WEST);
        actions.add(back, BorderLayout.EAST);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createWorldFormView(Listener listener) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 8));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(4, 6, 4, 6));

        styleField(mapName);
        styleSpinner(width);
        styleSpinner(height);
        styleSpinner(initialPop);
        styleSpinner(maxPop);
        styleSpinner(temp);
        styleSpinner(tox);
        styleSpinner(food);

        addField(form, "Map Name", mapName);
        addField(form, "Map Width", width);
        addField(form, "Map Height", height);
        addField(form, "Initial Microbes", initialPop);
        addField(form, "Max Microbes", maxPop);
        addField(form, "Temperature", temp);
        addField(form, "Toxicity", tox);
        addField(form, "Food Spawn / Tick", food);

        JPanel formShell = createInnerFrame();
        formShell.add(form, BorderLayout.CENTER);
        panel.add(formShell, BorderLayout.CENTER);

        ModernButton play = new ModernButton("PLAY");
        ModernButton back = new ModernButton("BACK");
        play.addActionListener(e -> {
            try {
                listener.onCreateRequested(new WorldConfig(
                        mapName.getText().trim(),
                        (int) width.getValue(),
                        (int) height.getValue(),
                        (int) initialPop.getValue(),
                        (int) maxPop.getValue(),
                        ((Number) temp.getValue()).doubleValue(),
                        ((Number) tox.getValue()).doubleValue(),
                        ((Number) food.getValue()).doubleValue()
                ));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid World Config", JOptionPane.ERROR_MESSAGE);
            }
        });
        back.addActionListener(e -> showListView());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(play);
        actions.add(left, BorderLayout.WEST);
        actions.add(back, BorderLayout.EAST);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private void addField(JPanel panel, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OverlayTheme.ACCENT);
        lbl.setFont(FORM_LABEL_FONT);
        panel.add(lbl);
        panel.add(field);
    }

    private void styleField(JTextField field) {
        field.setFont(FORM_VALUE_FONT);
        field.setForeground(OverlayTheme.ACCENT);
        field.setBackground(OverlayTheme.CONTROL_BG);
        field.setCaretColor(OverlayTheme.ACCENT);
        field.setOpaque(true);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedOutlineBorder(10),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setUI(new NeonSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder());
        spinner.setBackground(OverlayTheme.CONTROL_BG);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField tf = defaultEditor.getTextField();
            styleField(tf);
            tf.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            tf.setOpaque(false);
            tf.setHorizontalAlignment(SwingConstants.LEFT);
            defaultEditor.setBorder(BorderFactory.createEmptyBorder());
            defaultEditor.setOpaque(false);
        }
    }

    private JPanel createInnerFrame() {
        JPanel shell = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                g2.setColor(new Color(12, 12, 14, 190));
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                g2.setColor(OverlayTheme.ACCENT_GLOW);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w, h, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(4, 4, 4, 4));
        return shell;
    }

    public void showCreateForm() {
        cancelInlineRename();
        title.setText("CREATE GAME");
        cardLayout.show(cardContent, VIEW_CREATE);
    }

    public void showListView() {
        cancelInlineRename();
        title.setText("SELECT GAME");
        cardLayout.show(cardContent, VIEW_LIST);
    }

    private void configureInlineRenameEditor(Listener listener) {
        inlineRenameEditor.setVisible(false);
        inlineRenameEditor.setOpaque(false);
        inlineRenameEditor.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        inlineRenameEditor.setForeground(OverlayTheme.ACCENT);
        inlineRenameEditor.setCaretColor(OverlayTheme.ACCENT);
        inlineRenameEditor.setFont(ROW_NAME_FONT);
        inlineRenameEditor.addActionListener(e -> commitInlineRename(listener));
        inlineRenameEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitInlineRename(listener);
            }
        });
        inlineRenameEditor.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-inline-rename");
        inlineRenameEditor.getActionMap().put("cancel-inline-rename", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelInlineRename();
            }
        });
    }

    private boolean isRenameClick(Rectangle rowBounds, Point click, SaveGameMetadata metadata) {
        int textStartX = rowBounds.x + 12;
        FontMetrics fm = list.getFontMetrics(ROW_NAME_FONT);
        int textEndX = textStartX + fm.stringWidth(metadata.listName()) + 20;
        int maxLeftArea = rowBounds.x + Math.max(80, rowBounds.width - LIST_META_AREA_WIDTH);
        int effectiveEndX = Math.min(textEndX, maxLeftArea);
        return click.x >= textStartX && click.x <= effectiveEndX;
    }

    private void beginInlineRename(int index) {
        if (index < 0 || index >= model.size()) {
            return;
        }
        if (isRenameActive() && editingIndex != index) {
            cancelInlineRename();
        }
        editingIndex = index;
        Rectangle rowBounds = list.getCellBounds(index, index);
        if (rowBounds == null) {
            cancelInlineRename();
            return;
        }
        inlineRenameEditor.setBounds(computeInlineRenameBounds(rowBounds));
        inlineRenameEditor.setText(model.get(index).mapName());
        inlineRenameEditor.selectAll();
        inlineRenameEditor.setVisible(true);
        inlineRenameEditor.requestFocusInWindow();
        list.repaint();
    }

    private Rectangle computeInlineRenameBounds(Rectangle rowBounds) {
        int x = rowBounds.x + 12;
        int y = rowBounds.y + 8;
        int w = Math.max(140, rowBounds.width - LIST_META_AREA_WIDTH - 18);
        int h = Math.max(20, rowBounds.height - 16);
        return new Rectangle(x, y, w, h);
    }

    private boolean isRenameActive() {
        return editingIndex >= 0;
    }

    private void commitInlineRename(Listener listener) {
        if (!isRenameActive()) {
            return;
        }
        int idx = editingIndex;
        if (idx < 0 || idx >= model.size()) {
            cancelInlineRename();
            return;
        }
        SaveGameMetadata current = model.get(idx);
        String newName = inlineRenameEditor.getText() == null ? "" : inlineRenameEditor.getText().trim();
        if (newName.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            inlineRenameEditor.requestFocusInWindow();
            return;
        }
        if (!newName.equals(current.mapName())) {
            model.set(idx, current.withMapName(newName));
            listener.onRenameRequested(current, newName);
        }
        list.setSelectedIndex(idx);
        cancelInlineRename();
    }

    private void cancelInlineRename() {
        editingIndex = -1;
        inlineRenameEditor.setVisible(false);
        inlineRenameEditor.setText("");
        list.requestFocusInWindow();
        list.repaint();
    }

    private void playSelected(Listener listener) {
        SaveGameMetadata selected = list.getSelectedValue();
        if (selected != null) {
            listener.onPlayRequested(selected);
        }
    }

    private static final class RoundedOutlineBorder extends AbstractBorder {
        private final int arc;

        private RoundedOutlineBorder(int arc) {
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(OverlayTheme.ACCENT_GLOW);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }

    private static final class RoundedScrollBarUI extends BasicScrollBarUI {
        private static final Color THUMB_IDLE = new Color(0, 255, 255, 70);
        private static final Color THUMB_ACTIVE = new Color(0, 255, 255, 180);

        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            b.setVisible(false);
            return b;
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = OverlayTheme.PANEL_BG;
            thumbColor = THUMB_IDLE;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(OverlayTheme.PANEL_BG);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty()) return;
            Color thumbCol = (isDragging || isThumbRollover()) ? THUMB_ACTIVE : THUMB_IDLE;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = 2;
            int x = thumbBounds.x + inset;
            int y = thumbBounds.y + 2;
            int w = thumbBounds.width - inset * 2;
            int h = thumbBounds.height - 4;
            g2.setColor(thumbCol);
            g2.fillRoundRect(x, y, w, h, w, w);
            g2.dispose();
        }
    }

    private static final class NeonSpinnerUI extends BasicSpinnerUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
        }

        @Override
        protected Component createNextButton() {
            JButton button = createArrowButton(true);
            button.setName("Spinner.nextButton");
            installNextButtonListeners(button);
            return button;
        }

        @Override
        protected Component createPreviousButton() {
            JButton button = createArrowButton(false);
            button.setName("Spinner.previousButton");
            installPreviousButtonListeners(button);
            return button;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(OverlayTheme.ACCENT_GLOW);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            super.paint(g, c);
        }

        private JButton createArrowButton(boolean up) {
            JButton btn = new JButton() {
                private boolean hovered;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(hovered ? new Color(0, 255, 255, 230) : OverlayTheme.ACCENT);
                    g2.setStroke(new BasicStroke(hovered ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int aw = 4;
                    int ah = 3;
                    if (up) {
                        g2.drawLine(cx - aw, cy + ah, cx, cy - ah);
                        g2.drawLine(cx, cy - ah, cx + aw, cy + ah);
                    } else {
                        g2.drawLine(cx - aw, cy - ah, cx, cy + ah);
                        g2.drawLine(cx, cy + ah, cx + aw, cy - ah);
                    }
                    g2.dispose();
                }

                @Override
                protected void processMouseEvent(MouseEvent e) {
                    super.processMouseEvent(e);
                    switch (e.getID()) {
                        case MouseEvent.MOUSE_ENTERED -> {
                            hovered = true;
                            repaint();
                        }
                        case MouseEvent.MOUSE_EXITED -> {
                            hovered = false;
                            repaint();
                        }
                        default -> {
                        }
                    }
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(24, 14));
            return btn;
        }
    }

    public void setSaves(List<SaveGameMetadata> saves) {
        String selectedId = list.getSelectedValue() != null ? list.getSelectedValue().saveId() : null;
        cancelInlineRename();
        model.clear();
        for (SaveGameMetadata save : saves) {
            model.addElement(save);
        }
        if (selectedId != null) {
            for (int i = 0; i < model.size(); i++) {
                if (selectedId.equals(model.get(i).saveId())) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (!model.isEmpty() && list.getSelectedIndex() < 0) {
            list.setSelectedIndex(0);
        }
    }

    public interface Listener {
        void onCreateRequested(WorldConfig config);

        void onPlayRequested(SaveGameMetadata metadata);

        void onDeleteRequested(SaveGameMetadata metadata);

        void onRenameRequested(SaveGameMetadata metadata, String newMapName);

        void onBackRequested();
    }
}


