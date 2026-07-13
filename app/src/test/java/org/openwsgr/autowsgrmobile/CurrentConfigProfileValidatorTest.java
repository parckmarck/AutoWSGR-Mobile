package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class CurrentConfigProfileValidatorTest {
    private final CurrentConfigProfileValidator validator = new CurrentConfigProfileValidator();

    @Test public void emptyYamlAndTabsAreRejected() {
        assertThrows(IOException.class, () -> validator.validate("empty.yaml", " \r\n# comment\r\n"));
        assertThrows(IOException.class, () -> validator.validate("tabs.yaml", "normal_sortie:\n\tplan_path: x\n"));
        IOException malformed = assertThrows(
                IOException.class,
                () -> validator.validate("malformed.yaml", "not-a-key-value\n")
        );
        assertTrue(malformed.getMessage().contains("malformed.yaml"));
        assertTrue(malformed.getMessage().contains("line 1"));
        IOException duplicate = assertThrows(
                IOException.class,
                () -> validator.validate("duplicate.yaml", "version: 1\nversion: 2\n")
        );
        assertTrue(duplicate.getMessage().contains("duplicate.yaml"));
        assertTrue(duplicate.getMessage().contains("line 2"));
    }

    @Test public void recognizedLegacySectionsUseExistingParsers() throws Exception {
        validator.validate("valid.yaml", "normal_sortie:\n"
                + "  plan_path: autowsgr/data/plan/normal_fight/2-1.yaml\n"
                + "auto_expedition:\n"
                + "  interval_seconds: 60\n"
                + "campaign:\n"
                + "  battle_type: 简单驱逐\n"
                + "  times: 1\n"
                + "  formation: 1\n"
                + "  night: false\n"
                + "  repair_mode: none\n");

        IOException invalid = assertThrows(
                IOException.class,
                () -> validator.validate("broken.yaml", "auto_expedition:\n  interval_seconds: nope\n")
        );
        assertTrue(invalid.getMessage().contains("broken.yaml"));
        assertTrue(invalid.getMessage().contains("auto_expedition"));
    }

    @Test public void unknownTopLevelSectionsArePreservedAndAccepted() throws Exception {
        String yaml = "version: 1\r\n"
                + "upstream_unknown:\r\n"
                + "  keep: exactly # including comment\r\n"
                + "normal_sortie:\r\n"
                + "  plan_path: autowsgr/data/plan/normal_fight/1-1.yaml\r\n";
        String unchanged = yaml;

        validator.validate("unknown.yaml", yaml);
        Map<String, String> mappings = YamlSectionExtractor.topLevelMappings("unknown.yaml", yaml);

        assertEquals(unchanged, yaml);
        assertEquals("1", mappings.get("version"));
        assertEquals("keep: exactly # including comment\n", mappings.get("upstream_unknown"));
    }
}
