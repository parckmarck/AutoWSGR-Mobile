package org.openwsgr.autowsgrmobile;

final class AutomationControl {
    interface TimeSource {
        long nanoTime();
    }

    interface Action {
        boolean run();
    }

    private final SharedState sharedState;
    private final AutomationControl parent;
    private boolean cancelled;

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
        this.sharedState = new SharedState(timeSource);
        this.parent = null;
    }

    private AutomationControl(SharedState sharedState, AutomationControl parent) {
        this.sharedState = sharedState;
        this.parent = parent;
    }

    AutomationControl childControl() {
        return new AutomationControl(sharedState, this);
    }

    void pause() {
        synchronized (sharedState) {
            if (!cancelledLocked() && !sharedState.paused) {
                sharedState.paused = true;
                sharedState.pausedAtNanos = sharedState.timeSource.nanoTime();
            }
        }
    }

    void resume() {
        synchronized (sharedState) {
            finishPauseLocked();
            sharedState.paused = false;
            sharedState.notifyAll();
        }
    }

    void cancel() {
        synchronized (sharedState) {
            cancelled = true;
            if (parent == null) {
                finishPauseLocked();
                sharedState.paused = false;
            }
            sharedState.notifyAll();
        }
    }

    boolean cancelled() {
        synchronized (sharedState) {
            return cancelledLocked();
        }
    }

    boolean paused() {
        synchronized (sharedState) {
            return sharedState.paused;
        }
    }

    long activeTimeNanos() {
        synchronized (sharedState) {
            long now = sharedState.paused
                    ? sharedState.pausedAtNanos : sharedState.timeSource.nanoTime();
            return now - sharedState.pausedDurationNanos;
        }
    }

    boolean checkpoint() throws InterruptedException {
        synchronized (sharedState) {
            while (sharedState.paused && !cancelledLocked()) {
                sharedState.wait();
            }
            return !cancelledLocked();
        }
    }

    boolean performAction(Action action) throws InterruptedException {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        synchronized (sharedState) {
            while (sharedState.paused && !cancelledLocked()) {
                sharedState.wait();
            }
            if (cancelledLocked()) {
                return false;
            }
            return action.run();
        }
    }

    private boolean cancelledLocked() {
        return cancelled || (parent != null && parent.cancelledLocked());
    }

    private void finishPauseLocked() {
        if (!sharedState.paused) {
            return;
        }
        long pausedNanos = sharedState.timeSource.nanoTime() - sharedState.pausedAtNanos;
        if (pausedNanos > 0L) {
            sharedState.pausedDurationNanos += pausedNanos;
        }
    }

    private static final class SharedState {
        final TimeSource timeSource;
        boolean paused;
        long pausedAtNanos;
        long pausedDurationNanos;

        SharedState(TimeSource timeSource) {
            this.timeSource = timeSource;
        }
    }
}
