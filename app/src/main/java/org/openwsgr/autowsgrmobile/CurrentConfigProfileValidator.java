package org.openwsgr.autowsgrmobile;

import java.io.IOException;

final class CurrentConfigProfileValidator implements ConfigProfileValidator {
    @Override public void validate(String source, String yaml) throws IOException {
        String location = source == null ? "<unknown>" : source;
        String normalized = yaml == null ? "" : yaml.replace("\r\n", "\n");
        boolean hasContent = false;
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                hasContent = true;
                break;
            }
        }
        if (!hasContent) {
            throw new IOException(location + ": configuration is empty");
        }
        YamlSectionExtractor.topLevelMappings(location, normalized);
        UnifiedAutomationConfig.parse(location, normalized);
    }
}
