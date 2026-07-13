package org.openwsgr.autowsgrmobile;

import android.os.Handler;
import android.os.Looper;

final class AndroidMainThreadDispatcher implements CallbackDispatcher {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void dispatch(Runnable action) {
        handler.post(action);
    }
}
