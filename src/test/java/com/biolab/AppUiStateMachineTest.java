package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppUiStateMachineTest {

    @Test
    void saveBrowserCanGoToGameplayAndBackToPreview() {
        AppUiStateMachine sm = new AppUiStateMachine(AppUiState.PREVIEW_MENU);

        assertTrue(sm.transitionTo(AppUiState.SAVE_BROWSER));
        assertTrue(sm.transitionTo(AppUiState.GAMEPLAY));
        assertTrue(sm.transitionTo(AppUiState.PREVIEW_MENU));
        assertEquals(AppUiState.PREVIEW_MENU, sm.current());
    }

    @Test
    void shutdownStateRejectsFurtherTransitions() {
        AppUiStateMachine sm = new AppUiStateMachine(AppUiState.BOOT);

        assertTrue(sm.transitionTo(AppUiState.SHUTDOWN));
        assertFalse(sm.transitionTo(AppUiState.PREVIEW_MENU));
        assertEquals(AppUiState.SHUTDOWN, sm.current());
    }
}

