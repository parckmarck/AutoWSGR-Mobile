package org.openwsgr.autowsgrmobile;

interface AutomationFlow {
    String id();
    String label();
    boolean start();
    boolean supportsPause();
    void pause();
    void resume();
    void cancel();
    boolean isRunning();
}
