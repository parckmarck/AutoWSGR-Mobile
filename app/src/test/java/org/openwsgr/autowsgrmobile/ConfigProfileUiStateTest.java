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

    @Test public void dirtyStateCannotBeSetDefaultUntilSaved() {
        ConfigProfileUiState state = new ConfigProfileUiState();
        state.select(profile("日常.yaml", false));
        assertTrue(state.canSetDefault());

        state.edited();

        assertFalse(state.canSetDefault());
    }

    @Test public void delayedProgrammaticSelectionCallbacksNeverReplaceDrafts() {
        ConfigProfileUiState.SelectionGate gate = new ConfigProfileUiState.SelectionGate();

        gate.expectAdapterSelection(2);
        assertFalse(gate.shouldSelectProfile(0, 2));
        assertTrue(gate.hasExpectedSelection());
        assertFalse(gate.shouldSelectProfile(2, 2));
        assertFalse(gate.hasExpectedSelection());
        assertFalse(gate.shouldSelectProfile(2, 2));
        gate.clearExpectedSelection(2);
        assertTrue(gate.shouldSelectProfile(1, 1));

        assertFalse(gate.expectSelection(1, 1));
        assertFalse(gate.hasExpectedSelection());
    }

    @Test public void editorSnapshotRoundTripPreservesVisibleDraftAndStatus() {
        ConfigProfileUiState state = new ConfigProfileUiState();
        state.select(profile("草稿.yaml", false));
        state.edited();
        ConfigProfileUiState.EditorSnapshot snapshot = state.snapshot(
                "version: 1\nunknown: draft\n",
                "默认.yaml",
                "保留提示",
                "保留错误"
        );

        ConfigProfileUiState restored = new ConfigProfileUiState();
        restored.restore(snapshot);

        assertEquals("草稿.yaml", restored.selectedId());
        assertTrue(restored.dirty());
        assertFalse(restored.canSetDefault());
        assertEquals("version: 1\nunknown: draft\n", snapshot.editorText);
        assertEquals("默认.yaml", snapshot.defaultProfileId);
        assertEquals("保留提示", snapshot.notice);
        assertEquals("保留错误", snapshot.lastError);
    }

    private static ConfigProfile profile(String id, boolean builtIn) {
        return new ConfigProfile(id, id, null, builtIn, "version: 1\n");
    }
}
