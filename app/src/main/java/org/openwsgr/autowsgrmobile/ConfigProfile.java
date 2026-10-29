package org.openwsgr.autowsgrmobile;

import java.io.File;

final class ConfigProfile {
    final String id;
    final String displayName;
    final File file;
    final boolean builtIn;
    final String yaml;

    ConfigProfile(String id, String displayName, File file, boolean builtIn, String yaml) {
        this.id = id;
        this.displayName = displayName;
        this.file = file;
        this.builtIn = builtIn;
        this.yaml = yaml;
    }
}
