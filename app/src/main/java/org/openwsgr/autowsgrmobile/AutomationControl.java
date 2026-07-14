package org.openwsgr.autowsgrmobile;

final class AutomationControl {
    interface TimeSource {
        long nanoTime();
    }

    interface Action {
        boolean run();
    }

    private final TimeSource timeSource;
    private boolean paused;
    private boolean cancelled;
    private long pausedAtNanos;
    private long pausedDurationNanos;

    AutomationControl() {
        this(new TimeSource() {
            @Override public long nanoTime() {
                return System.nanoTime();
            }
        });
    }

    AutomationControl(TimeSource timeSource) {
        if (timeSource == null) {
            throw new IllegalArgumentException("time source is required");
        }
        this.timeSource = timeSource;
    }

    synchronized void pause() {
        if (!cancelled && !paused) {
            paused = true;
            pausedAtNanos = timeSource.nanoTime();
        }
    }

    synchronized void resume() {
        finishPause();
        paused = false;
        notifyAll();
    }

    synchronized void cancel() {
        cancelled = true;
        finishPause();
        paused = false;
        notifyAll();
    }

    synchronized boolean cancelled() {
        return cancelled;
    }

    synchronized boolean paused() {
        return paused;
    }

    synchronized long activeTimeNanos() {
        long now = paused ? pausedAtNanos : timeSource.nanoTime();
        return now - pausedDurationNanos;
    }

    synchronized boolean checkpoint() throws InterruptedException {
        while (paused && !cancelled) {
            wait();
        }
        return !cancelled;
    }

    synchronized boolean performAction(Action action) throws InterruptedException {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        while (paused && !cancelled) {
            wait();
        }
        if (cancelled) {
            return false;
        }
        return action.run();
    }

    private void finishPause() {
        if (!paused) {
            return;
        }
        long pausedNanos = timeSource.nanoTime() - pausedAtNanos;
        if (pausedNanos > 0L) {
            pausedDurationNanos += pausedNanos;
        }
    }
}
