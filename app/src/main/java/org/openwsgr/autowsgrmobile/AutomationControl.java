package org.openwsgr.autowsgrmobile;

final class AutomationControl {
    private boolean paused;
    private boolean cancelled;

    synchronized void pause() {
        if (!cancelled) {
            paused = true;
        }
    }

    synchronized void resume() {
        paused = false;
        notifyAll();
    }

    synchronized void cancel() {
        cancelled = true;
        paused = false;
        notifyAll();
    }

    synchronized boolean cancelled() {
        return cancelled;
    }

    synchronized boolean paused() {
        return paused;
    }

    synchronized boolean checkpoint() throws InterruptedException {
        while (paused && !cancelled) {
            wait();
        }
        return !cancelled;
    }
}
