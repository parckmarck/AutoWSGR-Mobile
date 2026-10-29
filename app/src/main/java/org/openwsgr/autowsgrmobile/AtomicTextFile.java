package org.openwsgr.autowsgrmobile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class AtomicTextFile {
    private AtomicTextFile() {
    }

    static void write(File target, String text) throws IOException {
        write(target, text, true);
    }

    static void writeWithoutReplacing(File target, String text) throws IOException {
        write(target, text, false);
    }

    static void moveWithoutReplacing(File source, File target) throws IOException {
        if (source == null || target == null) {
            throw new IOException("source and target are required");
        }
        Files.move(source.toPath(), target.toPath());
    }

    private static void write(File target, String text, boolean replaceExisting) throws IOException {
        if (target == null || text == null) {
            throw new IOException("target and text are required");
        }
        File parent = target.getParentFile();
        if (parent == null) {
            throw new IOException("target must have a parent directory");
        }
        Files.createDirectories(parent.toPath());
        if (!parent.isDirectory()) {
            throw new IOException("unable to create parent directory: " + parent);
        }

        File temporary = new File(parent, target.getName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            if (replaceExisting) {
                try {
                    Files.move(temporary.toPath(), target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                moveWithoutReplacing(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}
