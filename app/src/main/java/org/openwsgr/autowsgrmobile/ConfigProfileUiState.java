package org.openwsgr.autowsgrmobile;

final class ConfigProfileUiState {
    private String selectedId = ConfigProfileRepository.BUILTIN_ID;
    private boolean builtIn = true;
    private boolean dirty;

    void select(ConfigProfile profile) {
        selectedId = profile.id;
        builtIn = profile.builtIn;
        dirty = false;
    }

    void edited() {
        dirty = true;
    }

    void saved(ConfigProfile profile) {
        select(profile);
    }

    String selectedId() {
        return selectedId;
    }

    boolean dirty() {
        return dirty;
    }

    boolean canSaveInPlace() {
        return dirty && !builtIn;
    }

    boolean requiresSaveAs() {
        return dirty && builtIn;
    }

    boolean canSetDefault() {
        return !dirty;
    }

    EditorSnapshot snapshot(
            String editorText,
            String defaultProfileId,
            String notice,
            String lastError
    ) {
        return new EditorSnapshot(
                selectedId,
                builtIn,
                dirty,
                editorText,
                defaultProfileId,
                notice,
                lastError
        );
    }

    void restore(EditorSnapshot snapshot) {
        selectedId = snapshot.selectedId;
        builtIn = snapshot.builtIn;
        dirty = snapshot.dirty;
    }

    static final class EditorSnapshot {
        final String selectedId;
        final boolean builtIn;
        final boolean dirty;
        final String editorText;
        final String defaultProfileId;
        final String notice;
        final String lastError;

        EditorSnapshot(
                String selectedId,
                boolean builtIn,
                boolean dirty,
                String editorText,
                String defaultProfileId,
                String notice,
                String lastError
        ) {
            this.selectedId = selectedId;
            this.builtIn = builtIn;
            this.dirty = dirty;
            this.editorText = editorText;
            this.defaultProfileId = defaultProfileId;
            this.notice = notice;
            this.lastError = lastError;
        }
    }

    static final class SelectionGate {
        private static final int NONE = -1;
        private int expectedPosition = NONE;
        private int programmaticPosition = NONE;

        void expectAdapterSelection(int requestedPosition) {
            expectedPosition = requestedPosition;
            programmaticPosition = requestedPosition;
        }

        boolean expectSelection(int currentPosition, int requestedPosition) {
            if (currentPosition == requestedPosition) {
                expectedPosition = NONE;
                programmaticPosition = NONE;
                return false;
            }
            expectedPosition = requestedPosition;
            programmaticPosition = requestedPosition;
            return true;
        }

        boolean shouldSelectProfile(int callbackPosition, int currentPosition) {
            if (programmaticPosition != NONE) {
                if (callbackPosition == expectedPosition) {
                    expectedPosition = NONE;
                }
                return false;
            }
            return callbackPosition == currentPosition;
        }

        void clearExpectedSelection(int requestedPosition) {
            if (programmaticPosition == requestedPosition) {
                expectedPosition = NONE;
                programmaticPosition = NONE;
            }
        }

        boolean hasExpectedSelection() {
            return expectedPosition != NONE;
        }
    }
}
