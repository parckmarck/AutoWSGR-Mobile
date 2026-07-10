package org.openwsgr.autowsgrmobile;

import java.io.IOException;

interface ConfigProfileValidator {
    void validate(String source, String yaml) throws IOException;
}
