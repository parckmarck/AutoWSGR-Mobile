package org.openwsgr.autowsgrmobile;

import android.app.Application;

public final class AutoWsgrApplication extends Application {
    private volatile AutomationRuntime runtime;

    @Override public void onCreate() {
        super.onCreate();
        automationRuntime();
    }

    public AutomationRuntime automationRuntime() {
        AutomationRuntime current = runtime;
        if (current == null) {
            synchronized (this) {
                current = runtime;
                if (current == null) {
                    current = new AutomationRuntime(new AndroidMainThreadDispatcher());
                    runtime = current;
                }
            }
        }
        return current;
    }
}
