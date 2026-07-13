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
}
