package org.openwsgr.autowsgrmobile;

import java.io.IOException;
import java.util.Map;

final class UnifiedAutomationConfig {
    final NormalSortieRunConfig normalSortie;
    final AutoExpeditionConfig autoExpedition;
    final CampaignBattleConfig campaign;

    private UnifiedAutomationConfig(
            NormalSortieRunConfig normalSortie,
            AutoExpeditionConfig autoExpedition,
            CampaignBattleConfig campaign
    ) {
        this.normalSortie = normalSortie;
        this.autoExpedition = autoExpedition;
        this.campaign = campaign;
    }

    static UnifiedAutomationConfig parse(String source, String yaml) throws IOException {
        Map<String, String> mappings = YamlSectionExtractor.topLevelMappings(source, yaml);
        String normalText = mappings.get("normal_sortie");
        if (normalText == null) {
            normalText = NormalSortieConfigStore.defaultConfigText(NormalSortieTarget.of(1, 1));
        }
        String expeditionText = mappings.get("auto_expedition");
        if (expeditionText == null) {
            expeditionText = AutoExpeditionConfig.defaultConfigText();
        }
        String campaignText = mappings.get("campaign");
        if (campaignText == null) {
            campaignText = CampaignBattleConfig.defaultConfigText();
        }

        NormalSortieRunConfig normalSortie;
        try {
            normalSortie = NormalSortieRunConfig.parse(sectionSource(source, "normal_sortie"), normalText);
        } catch (Exception error) {
            throw sectionError(source, "normal_sortie", error);
        }

        AutoExpeditionConfig autoExpedition;
        try {
            autoExpedition = AutoExpeditionConfig.parseText(expeditionText);
        } catch (Exception error) {
            throw sectionError(source, "auto_expedition", error);
        }

        CampaignBattleConfig campaign;
        try {
            campaign = CampaignBattleConfig.parseText(campaignText);
        } catch (Exception error) {
            throw sectionError(source, "campaign", error);
        }
        return new UnifiedAutomationConfig(normalSortie, autoExpedition, campaign);
    }

    private static String sectionSource(String source, String section) {
        return (source == null ? "<unknown>" : source) + " [" + section + "]";
    }

    private static IOException sectionError(String source, String section, Exception error) {
        return new IOException(sectionSource(source, section) + ": " + error.getMessage(), error);
    }
}
