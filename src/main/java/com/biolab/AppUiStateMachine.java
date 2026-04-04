package com.biolab;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Minimal finite-state machine for top-level UI flow safety.
 */
public final class AppUiStateMachine {
    private final Map<AppUiState, EnumSet<AppUiState>> transitions = new EnumMap<>(AppUiState.class);
    private AppUiState current;

    public AppUiStateMachine(AppUiState initial) {
        this.current = initial;
        transitions.put(AppUiState.BOOT, EnumSet.of(AppUiState.PREVIEW_MENU, AppUiState.GAMEPLAY, AppUiState.SHUTDOWN));
        transitions.put(AppUiState.PREVIEW_MENU, EnumSet.of(AppUiState.SAVE_BROWSER, AppUiState.SETTINGS, AppUiState.GAMEPLAY, AppUiState.SHUTDOWN));
        transitions.put(AppUiState.SAVE_BROWSER, EnumSet.of(AppUiState.PREVIEW_MENU, AppUiState.WORLD_SETUP, AppUiState.GAMEPLAY, AppUiState.SETTINGS));
        transitions.put(AppUiState.WORLD_SETUP, EnumSet.of(AppUiState.SAVE_BROWSER, AppUiState.GAMEPLAY, AppUiState.SETTINGS));
        transitions.put(AppUiState.GAMEPLAY, EnumSet.of(AppUiState.PREVIEW_MENU, AppUiState.SAVE_BROWSER, AppUiState.SETTINGS, AppUiState.SHUTDOWN));
        transitions.put(AppUiState.SETTINGS, EnumSet.of(AppUiState.PREVIEW_MENU, AppUiState.SAVE_BROWSER, AppUiState.WORLD_SETUP, AppUiState.GAMEPLAY, AppUiState.SHUTDOWN));
        transitions.put(AppUiState.SHUTDOWN, EnumSet.noneOf(AppUiState.class));
    }

    public synchronized AppUiState current() {
        return current;
    }

    public synchronized boolean canTransitionTo(AppUiState next) {
        if (next == current) {
            return true;
        }
        return transitions.getOrDefault(current, EnumSet.noneOf(AppUiState.class)).contains(next);
    }

    public synchronized boolean transitionTo(AppUiState next) {
        if (!canTransitionTo(next)) {
            return false;
        }
        current = next;
        return true;
    }
}

