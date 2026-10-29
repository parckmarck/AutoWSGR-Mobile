package org.openwsgr.autowsgrmobile;

final class OneShotStartGate {
    enum Decision {
        START,
        PRE_CANCELLED,
        REJECTED
    }

    private boolean claimed;

    synchronized Decision claim(boolean cancelled) {
        if (claimed) {
            return Decision.REJECTED;
        }
        claimed = true;
        return cancelled ? Decision.PRE_CANCELLED : Decision.START;
    }
}
