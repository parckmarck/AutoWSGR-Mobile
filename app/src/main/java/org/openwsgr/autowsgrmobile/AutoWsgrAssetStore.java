package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AutoWsgrAssetStore {
    private static final String ZIP_ASSET = "autowsgr-data.zip";
    private static final String ZIP_ROOT = "data/";

    private final AssetManager assets;

    AutoWsgrAssetStore(Context context) {
        this.assets = context.getAssets();
    }

    byte[] read(String originalPath) throws IOException {
        String normalized = normalize(originalPath);
        InputStream stream = assets.open(ZIP_ASSET);
        try {
            ZipInputStream zip = new ZipInputStream(stream);
            try {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory() && normalized.equals(entry.getName())) {
                        return readAll(zip);
                    }
                }
            } finally {
                zip.close();
            }
        } finally {
            stream.close();
        }
        throw new IOException("missing asset: " + originalPath);
    }

    String readText(String originalPath) throws IOException {
        return new String(read(originalPath), StandardCharsets.UTF_8);
    }

    Bitmap readBitmap(String originalPath) throws IOException {
        byte[] data = read(originalPath);
        Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(data));
        if (bitmap == null) {
            throw new IOException("failed to decode bitmap: " + originalPath);
        }
        return bitmap;
    }

    List<String> listFirst(int maxItems) throws IOException {
        List<String> paths = new ArrayList<>();
        InputStream stream = assets.open(ZIP_ASSET);
        try {
            ZipInputStream zip = new ZipInputStream(stream);
            try {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && paths.size() < maxItems) {
                    if (!entry.isDirectory()) {
                        paths.add("autowsgr/" + entry.getName());
                    }
                }
            } finally {
                zip.close();
            }
        } finally {
            stream.close();
        }
        return paths;
    }

    private static String normalize(String originalPath) {
        String path = originalPath.replace('\\', '/');
        if (path.startsWith("autowsgr/")) {
            path = path.substring("autowsgr/".length());
        }
        if (!path.startsWith(ZIP_ROOT)) {
            path = ZIP_ROOT + path;
        }
        return path;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = input.read(buffer)) >= 0) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }
}
