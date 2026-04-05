package com.biolab;

import javax.swing.*;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates main-menu/save-browser overlay flow and related UI state updates.
 */
final class UiFlowCoordinator {
    private final JFrame hostFrame;
    private final SaveGameRepository saveRepository;
    private final Supplier<Integer> contentTopYSupplier;
    private final Supplier<ModernButton> globalSettingsButtonSupplier;
    private final Predicate<AppUiState> transitionOrRecover;
    private final Runnable onResumeLatestSave;
    private final Consumer<WorldConfig> onCreateWorld;
    private final Consumer<SaveGameMetadata> onLoadSave;
    private final Runnable onEnterGameplay;
    private final Logger logger;

    private MainMenuOverlay mainMenuOverlay;
    private SaveBrowserOverlay saveBrowserOverlay;

    UiFlowCoordinator(JFrame hostFrame,
                      SaveGameRepository saveRepository,
                      Supplier<Integer> contentTopYSupplier,
                      Supplier<ModernButton> globalSettingsButtonSupplier,
                      Predicate<AppUiState> transitionOrRecover,
                      Runnable onResumeLatestSave,
                      Consumer<WorldConfig> onCreateWorld,
                      Consumer<SaveGameMetadata> onLoadSave,
                      Runnable onEnterGameplay,
                      Logger logger) {
        this.hostFrame = hostFrame;
        this.saveRepository = saveRepository;
        this.contentTopYSupplier = contentTopYSupplier;
        this.globalSettingsButtonSupplier = globalSettingsButtonSupplier;
        this.transitionOrRecover = transitionOrRecover;
        this.onResumeLatestSave = onResumeLatestSave;
        this.onCreateWorld = onCreateWorld;
        this.onLoadSave = onLoadSave;
        this.onEnterGameplay = onEnterGameplay;
        this.logger = logger;
    }

    boolean hasBlockingOverlay() {
        return mainMenuOverlay != null || saveBrowserOverlay != null;
    }

    boolean isMainMenuVisible() {
        return mainMenuOverlay != null;
    }

    void showMainMenu() {
        if (mainMenuOverlay != null) return;
        mainMenuOverlay = new MainMenuOverlay(this::showSaveBrowserFromMenu, onResumeLatestSave);
        updateResumeAvailability();
        JLayeredPane layeredPane = hostFrame.getLayeredPane();
        layeredPane.add(mainMenuOverlay, JLayeredPane.POPUP_LAYER);
        mainMenuOverlay.setBounds(0, 0, hostFrame.getWidth(), hostFrame.getHeight());
        mainMenuOverlay.setVisible(true);
        moveGlobalSettingsButtonToFront();
        layeredPane.repaint();
    }

    void removeMainMenu() {
        if (mainMenuOverlay == null) return;
        hostFrame.getLayeredPane().remove(mainMenuOverlay);
        mainMenuOverlay = null;
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    void showSaveBrowser() {
        if (saveBrowserOverlay != null) return;
        if (!transitionOrRecover.test(AppUiState.SAVE_BROWSER)) return;
        removeMainMenu();
        saveBrowserOverlay = new SaveBrowserOverlay(new SaveBrowserOverlay.Listener() {
            @Override
            public void onCreateRequested(WorldConfig config) {
                onCreateWorld.accept(config);
            }

            @Override
            public void onPlayRequested(SaveGameMetadata metadata) {
                onLoadSave.accept(metadata);
            }

            @Override
            public void onDeleteRequested(SaveGameMetadata metadata) {
                deleteSaveAsync(metadata);
            }

            @Override
            public void onBackRequested() {
                removeSaveBrowser(true);
            }
        });
        JLayeredPane layeredPane = hostFrame.getLayeredPane();
        layeredPane.add(saveBrowserOverlay, JLayeredPane.POPUP_LAYER);
        int topY = contentTopYSupplier.get();
        saveBrowserOverlay.setBounds(0, topY, hostFrame.getWidth(), Math.max(0, hostFrame.getHeight() - topY));
        moveGlobalSettingsButtonToFront();
        refreshSaveBrowserAsync();
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    void removeSaveBrowser(boolean reopenMainMenu) {
        if (saveBrowserOverlay != null) {
            hostFrame.getLayeredPane().remove(saveBrowserOverlay);
            saveBrowserOverlay = null;
        }
        if (reopenMainMenu) {
            if (!transitionOrRecover.test(AppUiState.PREVIEW_MENU)) return;
            showMainMenu();
        } else {
            onEnterGameplay.run();
        }
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    void clearSaveBrowser() {
        if (saveBrowserOverlay == null) return;
        hostFrame.getLayeredPane().remove(saveBrowserOverlay);
        saveBrowserOverlay = null;
    }

    void refreshOverlayBounds(int width, int height) {
        if (mainMenuOverlay != null) {
            mainMenuOverlay.setBounds(0, 0, width, height);
            mainMenuOverlay.revalidate();
        }
        if (saveBrowserOverlay != null) {
            int topY = contentTopYSupplier.get();
            saveBrowserOverlay.setBounds(0, topY, width, Math.max(0, height - topY));
            saveBrowserOverlay.revalidate();
        }
    }

    void updateResumeAvailability() {
        if (mainMenuOverlay == null) return;
        mainMenuOverlay.setResumeEnabled(hasResumeTarget());
    }

    private void showSaveBrowserFromMenu() {
        showSaveBrowser();
    }

    private void refreshSaveBrowserAsync() {
        if (saveBrowserOverlay == null) return;
        SwingWorker<java.util.List<SaveGameMetadata>, Void> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<SaveGameMetadata> doInBackground() throws Exception {
                return saveRepository.listSaves();
            }

            @Override
            protected void done() {
                if (saveBrowserOverlay == null) return;
                try {
                    saveBrowserOverlay.setSaves(get());
                    updateResumeAvailability();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(hostFrame,
                            "Failed to list saves: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void deleteSaveAsync(SaveGameMetadata metadata) {
        if (metadata == null) return;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                saveRepository.deleteSave(metadata.saveId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshSaveBrowserAsync();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(hostFrame,
                            "Delete failed: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private boolean hasResumeTarget() {
        try {
            return saveRepository.findMostRecentSave().isPresent();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to query latest save", ex);
            return false;
        }
    }

    private void moveGlobalSettingsButtonToFront() {
        ModernButton button = globalSettingsButtonSupplier.get();
        if (button != null) {
            hostFrame.getLayeredPane().moveToFront(button);
        }
    }
}


