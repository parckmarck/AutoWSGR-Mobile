package org.openwsgr.autowsgrmobile;

import java.util.ArrayList;
import java.util.List;

final class AutomationRuntime implements AutomationCallback {
    interface Listener {
        void onAutomationUpdate(AutomationUpdate update);
    }

    interface StateBoundary {
        void afterTerminalRelease(AutomationUpdate update);
    }

    private final Object listenerLock = new Object();
    private final CallbackDispatcher dispatcher;
    private final StateBoundary stateBoundary;
    private final AutomationCoordinator coordinator = new AutomationCoordinator();
    private final List<Registration> listeners = new ArrayList<>();
    private AutomationUpdate latestUpdate;
    private long updateSequence;

    AutomationRuntime(CallbackDispatcher dispatcher) {
        this(dispatcher, new StateBoundary() {
            @Override public void afterTerminalRelease(AutomationUpdate update) {
            }
        });
    }

    AutomationRuntime(CallbackDispatcher dispatcher, StateBoundary stateBoundary) {
        if (dispatcher == null || stateBoundary == null) {
            throw new IllegalArgumentException("dispatcher and state boundary are required");
        }
        this.dispatcher = dispatcher;
        this.stateBoundary = stateBoundary;
    }

    AutomationCoordinator.StartResult start(AutomationFlow flow) {
        return coordinator.start(flow);
    }

    boolean pauseActive() {
        return coordinator.pauseActive();
    }

    boolean resumeActive() {
        return coordinator.resumeActive();
    }

    boolean cancelActive() {
        return coordinator.cancelActive();
    }

    void addListener(Listener listener, boolean replayLatest) {
        if (listener == null) {
            return;
        }
        Registration registration = new Registration(listener);
        AutomationUpdate replay = null;
        long replaySequence = 0;
        synchronized (listenerLock) {
            listeners.add(registration);
            if (replayLatest) {
                replay = latestUpdate;
                replaySequence = updateSequence;
            }
        }
        if (replay != null) {
            dispatch(registration, replaySequence, replay);
        }
    }

    void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listenerLock) {
            for (int index = listeners.size() - 1; index >= 0; index--) {
                Registration registration = listeners.get(index);
                if (registration.listener == listener) {
                    registration.active = false;
                    listeners.remove(index);
                }
            }
        }
    }

    AutomationUpdate latestUpdate() {
        synchronized (listenerLock) {
            return latestUpdate;
        }
    }

    @Override public void onUpdate(AutomationUpdate update) {
        if (update == null) {
            return;
        }
        List<Registration> snapshot;
        long sequence;
        synchronized (listenerLock) {
            if (update.outcome.terminal && coordinator.finish(update.flowId)) {
                stateBoundary.afterTerminalRelease(update);
            }
            latestUpdate = update;
            sequence = ++updateSequence;
            snapshot = new ArrayList<>(listeners);
        }
        for (Registration registration : snapshot) {
            dispatch(registration, sequence, update);
        }
    }

    private void dispatch(final Registration registration, final long sequence,
                          final AutomationUpdate update) {
        dispatcher.dispatch(new Runnable() {
            @Override public void run() {
                synchronized (listenerLock) {
                    if (!registration.active || sequence <= registration.lastDeliveredSequence) {
                        return;
                    }
                    registration.lastDeliveredSequence = sequence;
                }
                registration.listener.onAutomationUpdate(update);
            }
        });
    }

    private static final class Registration {
        final Listener listener;
        boolean active = true;
        long lastDeliveredSequence;

        Registration(Listener listener) {
            this.listener = listener;
        }
    }
}
