package org.openwsgr.autowsgrmobile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

    @Test public void saveAsDoesNotOverwriteTargetCreatedAfterExistenceCheck() throws Exception {
        final File dir = temporaryFolder.newFolder("no-replace-race");
        final File target = new File(dir, "raced.yaml");
        ConfigProfileRepository repo = new ConfigProfileRepository(
                dir,
                DefaultUnifiedConfig.text(),
                new FileActiveProfileStore(new File(dir, "active-profile.txt")),
                new ConfigProfileValidator() {
                    @Override public void validate(String source, String yaml) throws IOException {
                        if ("raced.yaml".equals(source)) {
                            Files.write(target.toPath(), "automation:\n  existing: true\n"
                                    .getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
        );

        try {
            repo.saveAs("raced", "automation:\n  replacement: true\n", false, false);
            fail("a target created after the existence check must not be overwritten");
        } catch (IOException expected) {
            // The final no-replace move must reject the concurrently created target.
        }
        assertEquals("automation:\n  existing: true\n",
                new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void renameMoveBoundaryPreservesDestinationCreatedAfterInitialCheck() throws Exception {
        File dir = temporaryFolder.newFolder("rename-no-replace-race");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile source = repo.saveAs("source", "automation:\n  source: true\n", false, false);
        File destination = new File(dir, "destination.yaml");
        assertFalse(destination.exists());

        byte[] sourceBytes = Files.readAllBytes(source.file.toPath());
        byte[] destinationBytes = "automation:\n  destination: true\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(destination.toPath(), destinationBytes);

        boolean moveFailed = false;
        try {
            AtomicTextFile.moveWithoutReplacing(source.file, destination);
        } catch (IOException expected) {
            moveFailed = true;
        }

        assertArrayEquals(destinationBytes, Files.readAllBytes(destination.toPath()));
        assertArrayEquals(sourceBytes, Files.readAllBytes(source.file.toPath()));
        assertTrue("the final no-replace move must fail", moveFailed);
    }

    @Test public void builtinCannotBeOverwrittenRenamedOrDeleted() throws Exception {
        ConfigProfileRepository repo = repository(temporaryFolder.newFolder("readonly"));
        assertThrows(IOException.class, () -> repo.save("builtin-safe", "automation:\n"));
        assertThrows(IOException.class, () -> repo.rename("builtin-safe", "new"));
        assertThrows(IOException.class, () -> repo.delete("builtin-safe"));
    }

    @Test public void deletedActiveProfileFallsBackWithoutOverwritingIt() throws Exception {
        File dir = temporaryFolder.newFolder("fallback");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("broken", "automation:\n", true, false);
        assertTrue(saved.file.delete());
        ConfigProfileRepository.LoadResult result = repository(dir).loadActive();
        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.fallbackUsed);
        assertTrue(result.warning.contains("missing"));
    }

    @Test public void invalidActiveProfileFallsBackWithoutChangingProfileOrPointer() throws Exception {
        File dir = temporaryFolder.newFolder("invalid-fallback");
        ConfigProfileRepository repo = repository(dir);
        ConfigProfile saved = repo.saveAs("broken", "automation:\n", true, false);
        byte[] corruptYaml = "invalid: true\n".getBytes(StandardCharsets.UTF_8);
        Files.write(saved.file.toPath(), corruptYaml);
        File pointer = new File(dir, "active-profile.txt");
        byte[] activeProfileBefore = Files.readAllBytes(pointer.toPath());

        ConfigProfileRepository.LoadResult result = repository(dir).loadActive();

        assertEquals("builtin-safe", result.profile.id);
        assertTrue(result.fallbackUsed);
        assertTrue(result.warning.contains("invalid"));
        assertArrayEquals(corruptYaml, Files.readAllBytes(saved.file.toPath()));
        assertArrayEquals(activeProfileBefore, Files.readAllBytes(pointer.toPath()));
    }
}
