package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class FailureEvidenceStore {
    private FailureEvidenceStore() {
    }

    static SaveResult saveLatestFailure(File baseDir, String runId) {
        return saveLatestFailure(baseDir, runId, "minimum-normal");
    }

    static SaveResult saveLatestFailure(File baseDir, String runId, String prefix) {
        Bitmap bitmap = ScreenFrameStore.latestBitmap();
        if (bitmap == null) {
            return SaveResult.unavailable("screenshot=unavailable:no-frame");
        }
        File output = MinimumRunLogStore.failureScreenshotFile(baseDir, prefix, runId);
        try {
            File parent = output.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileOutputStream stream = new FileOutputStream(output);
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    return SaveResult.unavailable("screenshot=unavailable:compress-failed");
                }
            } finally {
                stream.close();
            }
            return SaveResult.saved(output);
        } catch (IOException e) {
            return SaveResult.unavailable("screenshot=unavailable:" + e.getMessage());
        } finally {
            bitmap.recycle();
        }
    }

    static final class SaveResult {
        final boolean saved;
        final File file;
        final String detail;

        private SaveResult(boolean saved, File file, String detail) {
            this.saved = saved;
            this.file = file;
            this.detail = detail;
        }

        static SaveResult saved(File file) {
            return new SaveResult(true, file, "screenshot=" + file.getAbsolutePath());
        }

        static SaveResult unavailable(String detail) {
            return new SaveResult(false, null, detail);
        }
    }
}
