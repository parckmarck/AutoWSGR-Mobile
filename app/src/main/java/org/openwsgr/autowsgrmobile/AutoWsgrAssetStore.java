package org.openwsgr.autowsgrmobile;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class AutoWsgrAssetStore {
    private static final String ZIP_ASSET = "autowsgr-data.zip";
    private static final String ZIP_ROOT = "data/";
    private static final Object INDEX_LOCK = new Object();

    private static volatile ZipAssetIndex cachedIndex;

    private final AssetManager assets;

    AutoWsgrAssetStore(Context context) {
        this.assets = context.getAssets();
    }

    byte[] read(String originalPath) throws IOException {
        return index().read(normalize(originalPath));
    }

    String readText(String originalPath) throws IOException {
        return new String(read(originalPath), StandardCharsets.UTF_8);
    }

    Bitmap readBitmap(String originalPath) throws IOException {
        byte[] data = read(originalPath);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap == null) {
            throw new IOException("failed to decode bitmap: " + originalPath);
        }
        return bitmap;
    }

    List<String> listFirst(int maxItems) throws IOException {
        List<String> indexed = index().listFirst(maxItems);
        java.util.ArrayList<String> paths = new java.util.ArrayList<>(indexed.size());
        for (String path : indexed) {
            paths.add("autowsgr/" + path);
        }
        return paths;
    }

    private ZipAssetIndex index() throws IOException {
        ZipAssetIndex available = cachedIndex;
        if (available != null) {
            return available;
        }
        synchronized (INDEX_LOCK) {
            available = cachedIndex;
            if (available == null) {
                InputStream input = assets.open(ZIP_ASSET);
                available = ZipAssetIndex.load(input);
                cachedIndex = available;
            }
            return available;
        }
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

}
