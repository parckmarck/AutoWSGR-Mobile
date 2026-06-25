package org.openwsgr.autowsgrmobile;

import android.graphics.Bitmap;

final class LiveSpotDiagnostics {
    private LiveSpotDiagnostics() {
    }

    static Result run() {
        ScreenFrameStore.Snapshot snapshot = ScreenFrameStore.snapshot();
        Bitmap frame = ScreenFrameStore.latestBitmap();
        if (frame == null) {
            return new Result(
                    false,
                    "Live spot diagnostics: no frame",
                    "live spot diagnostics=unavailable, reason=missing MediaProjection frame, "
                            + snapshot.toStatusLine()
                            + '\n'
                            + LiveSpotCalibrationTuner.noFrameBaseline().detail
                            + '\n'
                            + CalibrationSampleSetAnalyzer.noFrameSummary()
            );
        }

        try {
            return analyzeFrame(
                    "live spot diagnostics",
                    "Live spot diagnostics: ready",
                    "Live spot diagnostics: sampled",
                    frame,
                    snapshot
            );
        } finally {
            frame.recycle();
        }
    }

    static Result probe() {
        Bitmap frame = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        frame.eraseColor(0xff1c1c1c);
        EnemyCompositionRecognizer.paintSyntheticFightSlots(
                frame,
                "DD",
                "CVL",
                "BB",
                "BB",
                "AP",
                "AP"
        );
        EnemyFormationRecognizer.paintSyntheticFormationRoi(frame);

        ScreenFrameStore.Snapshot snapshot = new ScreenFrameStore.Snapshot(
                frame.getWidth(),
                frame.getHeight(),
                System.currentTimeMillis(),
                1,
                frame.getWidth() * frame.getHeight() * 4
        );
        try {
            Result analyzed = analyzeFrame(
                    "live spot diagnostics",
                    "Live spot diagnostics: ready",
                    "Live spot diagnostics: sampled",
                    frame,
                    snapshot
            );
            boolean passed = analyzed.ok
                    && analyzed.detail.contains("runnerSpotContextReady=true")
                    && analyzed.detail.contains("compositionReliable=true")
                    && analyzed.detail.contains("formationReliable=true")
                    && analyzed.detail.contains("calibrationTuning=v1")
                    && analyzed.detail.contains("shipPrototypeTuningReady=true")
                    && analyzed.detail.contains("formationSignatureTuningReady=true")
                    && analyzed.detail.contains("calibrationSampleSet=v1");
            return new Result(
                    passed,
                    "Live spot diagnostics self-test: " + (passed ? "passed" : "failed"),
                    "live spot diagnostics self-test=" + (passed ? "passed" : "failed")
                            + '\n'
                            + analyzed.detail
            );
        } finally {
            frame.recycle();
        }
    }

    static String calibrationRecord(String status, String detail) {
        String safeStatus = status == null || status.trim().length() == 0
                ? "none"
                : status.trim();
        String safeDetail = detail == null ? "" : detail.trim();
        return "autowsgrLiveSpotCalibration=v1"
                + "\nsource=last-live-spot-diagnostics"
                + "\nstatus=" + safeStatus
                + (safeDetail.length() == 0 ? "" : "\n" + safeDetail);
    }

    private static Result analyzeFrame(
            String label,
            String readyStatus,
            String sampledStatus,
            Bitmap frame,
            ScreenFrameStore.Snapshot snapshot
    ) {
        EnemyCompositionRecognizer.Detection composition = EnemyCompositionRecognizer.detectFight(frame);
        EnemyFormationRecognizer.Detection formation = EnemyFormationRecognizer.detectFight(frame);
        boolean ready = composition.reliable && formation.reliable;
        StringBuilder detail = new StringBuilder();
        detail.append(label).append("=sampled, frame=")
                .append(frame.getWidth()).append('x').append(frame.getHeight())
                .append(", ").append(snapshot.toStatusLine())
                .append(", compositionReliable=").append(composition.reliable)
                .append(", formationReliable=").append(formation.reliable)
                .append(", runnerSpotContextReady=").append(ready)
                .append('\n')
                .append("live composition=").append(composition.detailLine())
                .append('\n')
                .append("live formation=").append(formation.detailLine())
                .append('\n')
                .append(LiveSpotCalibrationTuner.forDetections(composition, formation).detail)
                .append('\n')
                .append(CalibrationSampleSetAnalyzer.record(composition, formation));
        return new Result(
                ready,
                ready ? readyStatus : sampledStatus,
                detail.toString()
        );
    }

    static final class Result {
        final boolean ok;
        final String status;
        final String detail;

        Result(boolean ok, String status, String detail) {
            this.ok = ok;
            this.status = status;
            this.detail = detail;
        }
    }
}
