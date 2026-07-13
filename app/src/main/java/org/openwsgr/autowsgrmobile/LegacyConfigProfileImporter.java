package org.openwsgr.autowsgrmobile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class LegacyConfigProfileImporter {
    private static final String MIGRATED_NAME = "从旧版配置迁移";
    private static final String MIGRATED_ID = MIGRATED_NAME + ".yaml";

    private LegacyConfigProfileImporter() {
    }

    static ConfigProfile importIfNeeded(
            ConfigProfileRepository repository,
            File legacyConfigDirectory
    ) throws IOException {
        if (repository.hasExplicitDefault()) {
            return null;
        }

        try {
            ConfigProfile existing = repository.load(MIGRATED_ID);
            return repository.setDefault(existing.id);
        } catch (IOException error) {
            String message = error.getMessage();
            if (message == null || !message.contains("profile file is missing")) {
                throw error;
            }
        }

        String normal = legacyOrDefault(
                legacyConfigDirectory,
                "normal-sortie.yaml",
                NormalSortieConfigStore.defaultConfigText(NormalSortieTarget.of(1, 1))
        );
        String expedition = legacyOrDefault(
                legacyConfigDirectory,
                "auto-expedition.yaml",
                AutoExpeditionConfig.defaultConfigText()
        );
        String campaign = legacyOrDefault(
                legacyConfigDirectory,
                "campaign-battle.yaml",
                CampaignBattleConfig.defaultConfigText()
        );
        String yaml = "version: 1\n"
                + "profile_name: " + MIGRATED_NAME + "\n"
                + section("normal_sortie", normal)
                + section("auto_expedition", expedition)
                + section("campaign", campaign);
        return repository.saveAs(MIGRATED_NAME, yaml, true, false);
    }

    private static String legacyOrDefault(File directory, String fileName, String defaultText)
            throws IOException {
        if (directory == null) {
            return defaultText;
        }
        File file = new File(directory, fileName);
        if (!file.isFile()) {
            return defaultText;
        }
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String section(String name, String text) {
        String normalized = (text == null ? "" : text).replace("\r\n", "\n");
        StringBuilder output = new StringBuilder(name).append(":\n");
        for (String line : normalized.split("\n", -1)) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            output.append("  ").append(line).append('\n');
        }
        return output.toString();
    }
}
