package org.openwsgr.autowsgrmobile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class FileActiveProfileStore implements ActiveProfileStore {
    private final File file;

    FileActiveProfileStore(File file) {
        this.file = file;
    }

    @Override public String load() throws IOException {
        if (!file.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Override public void save(String profileId) throws IOException {
        AtomicTextFile.write(file, profileId);
    }
}
