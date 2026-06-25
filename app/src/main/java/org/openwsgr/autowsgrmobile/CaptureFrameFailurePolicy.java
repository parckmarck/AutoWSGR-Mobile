package org.openwsgr.autowsgrmobile;

final class CaptureFrameFailurePolicy {
    private CaptureFrameFailurePolicy() {
    }

    static boolean isRecoverable(Throwable error) {
        if (!(error instanceof IllegalStateException)) {
            return false;
        }
        String message = error.getMessage();
        if (message == null) {
            return true;
        }
        String lower = message.toLowerCase();
        return lower.contains("buffer is inaccessible")
                || lower.contains("image is already closed")
                || lower.contains("image is closed")
                || lower.contains("reader is closed");
    }
}
