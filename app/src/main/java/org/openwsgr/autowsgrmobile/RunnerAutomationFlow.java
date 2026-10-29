package org.openwsgr.autowsgrmobile;

final class RunnerAutomationFlow implements AutomationFlow {
    interface Start { boolean start(); }
    interface Pause { void pause(); }
    interface Resume { void resume(); }
    interface Cancel { void cancel(); }
    interface Running { boolean isRunning(); }

    private final String id;
    private final String label;
    private final boolean supportsPause;
    private final Start start;
    private final Pause pause;
    private final Resume resume;
    private final Cancel cancel;
    private final Running running;

    RunnerAutomationFlow(String id, String label, boolean supportsPause, Start start, Pause pause,
                         Resume resume, Cancel cancel, Running running) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.supportsPause = supportsPause;
        this.start = start;
        this.pause = pause;
        this.resume = resume;
        this.cancel = cancel;
        this.running = running;
    }

    @Override public String id() { return id; }
    @Override public String label() { return label; }
    @Override public boolean start() { return start != null && start.start(); }
    @Override public boolean supportsPause() { return supportsPause; }

    @Override public void pause() {
        if (supportsPause && pause != null) {
            pause.pause();
        }
    }

    @Override public void resume() {
        if (supportsPause && resume != null) {
            resume.resume();
        }
    }

    @Override public void cancel() {
        if (cancel != null) {
            cancel.cancel();
        }
    }

    @Override public boolean isRunning() {
        return running != null && running.isRunning();
    }
}
