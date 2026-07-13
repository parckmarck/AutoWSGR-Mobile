package org.openwsgr.autowsgrmobile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ConfigProfileRepository {
    static final String BUILTIN_ID = "builtin-safe";

    private final File directory;
    private final String builtInYaml;
    private final ActiveProfileStore activeStore;
    private final ConfigProfileValidator validator;

    ConfigProfileRepository(
            File directory,
            String builtInYaml,
            ActiveProfileStore activeStore,
            ConfigProfileValidator validator
    ) {
        this.directory = directory;
        this.builtInYaml = builtInYaml;
        this.activeStore = activeStore;
        this.validator = validator;
    }

    synchronized List<ConfigProfile> list() throws IOException {
        List<ConfigProfile> profiles = new ArrayList<>();
        profiles.add(builtInProfile());
        File configDirectory = canonicalDirectory();
        File[] files = configDirectory.listFiles();
        if (files == null) {
            return profiles;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        for (File file : files) {
            String id = file.getName();
            if (file.isFile() && id.endsWith(".yaml")) {
                profiles.add(load(id));
            }
        }
        return Collections.unmodifiableList(profiles);
    }

    synchronized LoadResult loadActive() throws IOException {
        String id = activeStore.load();
        if (id == null || id.isEmpty()) {
            return new LoadResult(builtInProfile(), true,
                    "No active profile is configured; using the built-in safe profile.");
        }
        try {
            return new LoadResult(load(id), false, "");
        } catch (IOException error) {
            return new LoadResult(builtInProfile(), true,
                    "Active profile " + id + " is missing or invalid: " + error.getMessage());
        }
    }

    synchronized ConfigProfile load(String id) throws IOException {
        if (BUILTIN_ID.equals(id)) {
            return builtInProfile();
        }
        String displayName = displayNameForId(id);
        File file = fileForId(id);
        if (!file.isFile()) {
            throw new IOException("profile file is missing: " + id);
        }
        String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        validator.validate(id, yaml);
        return new ConfigProfile(id, displayName, file, false, yaml);
    }

    synchronized ConfigProfile saveAs(
            String displayName,
            String yaml,
            boolean makeDefault,
            boolean overwrite
    ) throws IOException {
        validateDisplayName(displayName);
        String id = displayName + ".yaml";
        File file = fileForId(id);
        if (!overwrite && file.exists()) {
            throw new IOException("profile already exists: " + id);
        }
        validator.validate(id, yaml);
        if (overwrite) {
            AtomicTextFile.write(file, yaml);
        } else {
            AtomicTextFile.writeWithoutReplacing(file, yaml);
        }
        ConfigProfile profile = new ConfigProfile(id, displayName, file, false, yaml);
        if (makeDefault) {
            activeStore.save(id);
        }
        return profile;
    }

    synchronized ConfigProfile save(String id, String yaml) throws IOException {
        rejectBuiltIn(id);
        String displayName = displayNameForId(id);
        File file = fileForId(id);
        if (!file.isFile()) {
            throw new IOException("profile file is missing: " + id);
        }
        validator.validate(id, yaml);
        AtomicTextFile.write(file, yaml);
        return new ConfigProfile(id, displayName, file, false, yaml);
    }

    synchronized ConfigProfile rename(String id, String newDisplayName) throws IOException {
        rejectBuiltIn(id);
        ConfigProfile profile = load(id);
        validateDisplayName(newDisplayName);
        String newId = newDisplayName + ".yaml";
        File target = fileForId(newId);
        if (profile.file.equals(target)) {
            return new ConfigProfile(newId, newDisplayName, target, false, profile.yaml);
        }
        if (target.exists()) {
            throw new IOException("profile already exists: " + newId);
        }
        moveWithoutReplacing(profile.file, target);
        if (id.equals(activeStore.load())) {
            activeStore.save(newId);
        }
        return new ConfigProfile(newId, newDisplayName, target, false, profile.yaml);
    }

    synchronized void delete(String id) throws IOException {
        rejectBuiltIn(id);
        File file = fileForId(id);
        if (!file.isFile()) {
            throw new IOException("profile file is missing: " + id);
        }
        boolean wasActive = id.equals(activeStore.load());
        Files.delete(file.toPath());
        if (wasActive) {
            activeStore.save(BUILTIN_ID);
        }
    }

    synchronized ConfigProfile setDefault(String id) throws IOException {
        ConfigProfile profile = load(id);
        activeStore.save(profile.id);
        return profile;
    }

    synchronized boolean hasExplicitDefault() throws IOException {
        String id = activeStore.load();
        return id != null && !id.isEmpty();
    }

    private ConfigProfile builtInProfile() {
        return new ConfigProfile(BUILTIN_ID, "内置安全配置", null, true, builtInYaml);
    }

    private File canonicalDirectory() throws IOException {
        if (directory == null) {
            throw new IOException("configuration directory is required");
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("unable to create configuration directory: " + directory);
        }
        if (!directory.isDirectory()) {
            throw new IOException("configuration path is not a directory: " + directory);
        }
        return directory.getCanonicalFile();
    }

    private File fileForId(String id) throws IOException {
        displayNameForId(id);
        File configDirectory = canonicalDirectory();
        File file = new File(configDirectory, id).getCanonicalFile();
        if (!configDirectory.equals(file.getParentFile())) {
            throw new IOException("invalid profile name: " + id);
        }
        return file;
    }

    private String displayNameForId(String id) throws IOException {
        if (id == null || !id.endsWith(".yaml")) {
            throw new IOException("invalid profile name: " + id);
        }
        String displayName = id.substring(0, id.length() - ".yaml".length());
        validateDisplayName(displayName);
        return displayName;
    }

    private void validateDisplayName(String displayName) throws IOException {
        if (displayName == null || displayName.trim().isEmpty()
                || ".".equals(displayName) || "..".equals(displayName)) {
            throw new IOException("invalid profile name: " + displayName);
        }
        for (int index = 0; index < displayName.length(); index++) {
            char character = displayName.charAt(index);
            if (Character.isISOControl(character)
                    || character == '/' || character == '\\'
                    || character == '<' || character == '>' || character == ':'
                    || character == '"' || character == '|' || character == '?'
                    || character == '*') {
                throw new IOException("invalid profile name: " + displayName);
            }
        }
    }

    private void rejectBuiltIn(String id) throws IOException {
        if (BUILTIN_ID.equals(id)) {
            throw new IOException("built-in profile is read-only");
        }
    }

    private void moveWithoutReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath());
        }
    }

    static final class LoadResult {
        final ConfigProfile profile;
        final boolean fallbackUsed;
        final String warning;

        LoadResult(ConfigProfile profile, boolean fallbackUsed, String warning) {
            this.profile = profile;
            this.fallbackUsed = fallbackUsed;
            this.warning = warning;
        }
    }
}
