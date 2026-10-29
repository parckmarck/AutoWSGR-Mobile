package org.openwsgr.autowsgrmobile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LegacyConfigProfileImporterTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void legacyFlatFilesImportOnceAndBecomeDefault() throws Exception {
        File base = temporaryFolder.newFolder("migration");
        File legacy = new File(base, "config");
        assertTrue(legacy.mkdirs());
        write(new File(legacy, "normal-sortie.yaml"),
                "# keep source comments\nplan_path: autowsgr/data/plan/normal_fight/4-3.yaml\n");
        write(new File(legacy, "auto-expedition.yaml"), "interval_seconds: 120\n");
        write(new File(legacy, "campaign-battle.yaml"),
                "battle_type: 困难战列\ntimes: 2\nformation: 3\nnight: false\nrepair_mode: none\n");
        ConfigProfileRepository repository = repository(new File(base, "configs"));

        ConfigProfile imported = LegacyConfigProfileImporter.importIfNeeded(repository, legacy);

        assertEquals("从旧版配置迁移.yaml", imported.id);
        assertEquals(imported.id, repository.loadActive().profile.id);
        assertTrue(imported.yaml.contains("normal_sortie:\n  plan_path:"));
        assertFalse(imported.yaml.contains("keep source comments"));
        UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(imported.id, imported.yaml);
        assertEquals("autowsgr/data/plan/normal_fight/4-3.yaml", config.normalSortie.planPath);
        assertEquals(120L, config.autoExpedition.intervalSeconds);
        assertEquals("困难战列", config.campaign.battleType);
        assertNull(LegacyConfigProfileImporter.importIfNeeded(repository, legacy));
    }

    @Test public void existingExplicitDefaultPreventsLegacyReimport() throws Exception {
        File base = temporaryFolder.newFolder("explicit");
        File configs = new File(base, "configs");
        ConfigProfileRepository repository = repository(configs);
        ConfigProfile explicit = repository.saveAs("用户默认", DefaultUnifiedConfig.text(), true, false);
        File legacy = new File(base, "config");
        assertTrue(legacy.mkdirs());
        File legacyNormal = new File(legacy, "normal-sortie.yaml");
        write(legacyNormal, "plan_path: autowsgr/data/plan/normal_fight/9-9.yaml\n");

        assertNull(LegacyConfigProfileImporter.importIfNeeded(repository, legacy));

        assertEquals(explicit.id, repository.loadActive().profile.id);
        assertFalse(new File(configs, "从旧版配置迁移.yaml").exists());
    }

    @Test public void utf8BomIsStrippedFromFirstLegacySetting() throws Exception {
        File base = temporaryFolder.newFolder("bom");
        File legacy = new File(base, "config");
        assertTrue(legacy.mkdirs());
        write(new File(legacy, "normal-sortie.yaml"),
                "\uFEFFplan_path: autowsgr/data/plan/normal_fight/5-2.yaml\n");

        ConfigProfile imported = LegacyConfigProfileImporter.importIfNeeded(
                repository(new File(base, "configs")),
                legacy
        );
        UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(imported.id, imported.yaml);

        assertEquals("autowsgr/data/plan/normal_fight/5-2.yaml", config.normalSortie.planPath);
        assertFalse(imported.yaml.contains("\uFEFF"));
    }

    private static ConfigProfileRepository repository(File configs) {
        return new ConfigProfileRepository(
                configs,
                DefaultUnifiedConfig.text(),
                new FileActiveProfileStore(new File(configs, "active-profile.txt")),
                new CurrentConfigProfileValidator()
        );
    }

    private static void write(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
