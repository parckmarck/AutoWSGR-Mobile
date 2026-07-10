package org.openwsgr.autowsgrmobile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public final class ConfigProfileRepositoryTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ConfigProfileRepository repository(File directory) {
        return new ConfigProfileRepository(
                directory,
                DefaultUnifiedConfig.text(),
                new FileActiveProfileStore(new File(directory, "active-profile.txt")),
                new ConfigProfileValidator() {
                    @Override public void validate(String source, String yaml) throws IOException {
                        if (yaml == null || !yaml.contains("automation:")) {
                            throw new IOException(source + ": missing automation root");
                        }
                    }
                }
        );
    }

    @Test public void missingPointerLoadsReadOnlyBuiltin() throws Exception {
        ConfigProfileRepository.LoadResult result = repository(temporaryFolder.newFolder("configs")).loadActive();
        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.profile.builtIn);
        assertTrue(result.fallbackUsed);
    }

    @Test public void saveAsCreatesSeparateUtf8YamlAndCanBecomeDefault() throws Exception {
        File dir = temporaryFolder.newFolder("profiles");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("7-4刷图", "automation:\n  tasks: []\n", true, false);
        assertEquals("7-4刷图.yaml", saved.id);
        assertFalse(saved.builtIn);
        assertEquals(saved.id, repository(dir).loadActive().profile.id);
        assertEquals("automation:\n  tasks: []\n", repository(dir).loadActive().profile.yaml);
    }

    @Test public void pathTraversalAndImplicitOverwriteAreRejected() throws Exception {
        ConfigProfileRepository repo = repository(temporaryFolder.newFolder("safe"));
        try {
            repo.saveAs("../escape", "automation:\n", false, false);
            fail("path traversal must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid profile name"));
        }
        repo.saveAs("日常", "automation:\n", false, false);
        try {
            repo.saveAs("日常", "automation:\n  changed: true\n", false, false);
            fail("implicit overwrite must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already exists"));
        }
    }

    @Test public void builtinCannotBeOverwrittenRenamedOrDeleted() throws Exception {
        ConfigProfileRepository repo = repository(temporaryFolder.newFolder("readonly"));
        assertThrows(IOException.class, () -> repo.save("builtin-safe", "automation:\n"));
        assertThrows(IOException.class, () -> repo.rename("builtin-safe", "new"));
        assertThrows(IOException.class, () -> repo.delete("builtin-safe"));
    }

    @Test public void deletedOrInvalidActiveProfileFallsBackWithoutOverwritingIt() throws Exception {
        File dir = temporaryFolder.newFolder("fallback");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("broken", "automation:\n", true, false);
        assertTrue(saved.file.delete());
        ConfigProfileRepository.LoadResult result = repository(dir).loadActive();
        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.fallbackUsed);
        assertTrue(result.warning.contains("missing"));
    }
}
