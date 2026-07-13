package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ConfigProfileUiStateTest {
    @Test public void editingMarksStateDirtyButSelectionResetsIt() {
        ConfigProfileUiState state = new ConfigProfileUiState();

        state.edited();

        assertTrue(state.dirty());
        state.select(profile("日常.yaml", false));
        assertEquals("日常.yaml", state.selectedId());
        assertFalse(state.dirty());
    }

    @Test public void builtinRequiresSaveAsInsteadOfSave() {
        ConfigProfileUiState state = new ConfigProfileUiState();

        state.edited();

        assertFalse(state.canSaveInPlace());
        assertTrue(state.requiresSaveAs());
    }

    @Test public void successfulSaveAsSelectsNewProfileAndClearsDirty() {
        ConfigProfileUiState state = new ConfigProfileUiState();
        state.edited();

        state.saved(profile("新配置.yaml", false));

        assertEquals("新配置.yaml", state.selectedId());
        assertFalse(state.dirty());
        assertFalse(state.canSaveInPlace());
        assertFalse(state.requiresSaveAs());
    }

    private static ConfigProfile profile(String id, boolean builtIn) {
        return new ConfigProfile(id, id, null, builtIn, "version: 1\n");
    }
}
