package org.openwsgr.autowsgrmobile;

import java.io.IOException;

interface ActiveProfileStore {
    String load() throws IOException;
    void save(String profileId) throws IOException;
}
