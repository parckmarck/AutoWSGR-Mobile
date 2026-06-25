package org.openwsgr.autowsgrmobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

final class TargetGameLauncher {
    static final String PACKAGE_NAME = "com.huanmeng.zhanjian2";
    static final String ACTIVITY_NAME = "com.unity3d.player.UnityPlayerActivity";

    private TargetGameLauncher() {
    }

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static LaunchResult launch(Context context) {
        if (!isInstalled(context)) {
            return LaunchResult.failed("target-package-missing");
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(PACKAGE_NAME, ACTIVITY_NAME));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return LaunchResult.started(PACKAGE_NAME + "/" + ACTIVITY_NAME);
        } catch (RuntimeException e) {
            return LaunchResult.failed("launch-failed:" + e.getMessage());
        }
    }

    static boolean isTargetForeground() {
        return PACKAGE_NAME.equals(AutoWsgrAccessibilityService.latestForegroundPackage());
    }

    static final class LaunchResult {
        final boolean started;
        final String detail;

        private LaunchResult(boolean started, String detail) {
            this.started = started;
            this.detail = detail;
        }

        static LaunchResult started(String detail) {
            return new LaunchResult(true, detail);
        }

        static LaunchResult failed(String detail) {
            return new LaunchResult(false, detail);
        }
    }
}
