package org.openwsgr.autowsgrmobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ZipAssetIndex {
    private static final String OPTIONAL_ROOT = "autowsgr/";

    private final LinkedHashMap<String, byte[]> entries;

    private ZipAssetIndex(LinkedHashMap<String, byte[]> entries) {
        this.entries = entries;
    }

    static ZipAssetIndex load(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        LinkedHashMap<String, byte[]> indexed = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    String path = normalize(entry.getName());
                    if (entry.isDirectory() || path.isEmpty() || path.endsWith("/")
                            || indexed.containsKey(path)) {
                        continue;
                    }
                    indexed.put(path, readAll(zip));
                } finally {
                    zip.closeEntry();
                }
            }
        }
        return new ZipAssetIndex(indexed);
    }

    byte[] read(String normalizedPath) throws IOException {
        String path = normalize(normalizedPath);
        byte[] data = entries.get(path);
        if (data == null) {
            throw new IOException("missing asset: " + normalizedPath);
        }
        return data.clone();
    }

    List<String> listFirst(int maxItems) {
        if (maxItems < 0) {
            throw new IllegalArgumentException("maxItems must not be negative");
        }
        List<String> paths = new ArrayList<>(Math.min(maxItems, entries.size()));
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (paths.size() >= maxItems) {
                break;
            }
            paths.add(entry.getKey());
        }
        return paths;
    }

    private static String normalize(String originalPath) {
        if (originalPath == null) {
            return "";
        }
        String path = originalPath.replace('\\', '/');
        if (path.startsWith(OPTIONAL_ROOT)) {
            path = path.substring(OPTIONAL_ROOT.length());
        }
        return path;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                output.write(value);
            } else {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }
}
