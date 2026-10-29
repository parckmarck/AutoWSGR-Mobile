package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UnifiedAutomationConfigTest {
    @Test public void unifiedProfileMapsNestedSectionsToExistingRuntimeConfigs() throws Exception {
        UnifiedAutomationConfig config = UnifiedAutomationConfig.parse(
                "typed.yaml",
                "normal_sortie:\n"
                        + "  plan_path: ./autowsgr/data/plan/normal_fight/3-2.yaml\n"
                        + "auto_expedition:\n"
                        + "  interval_seconds: 45\n"
                        + "campaign:\n"
                        + "  battle_type: 简单巡洋\n"
                        + "  times: 5\n"
                        + "  formation: 单横\n"
                        + "  night: false\n"
                        + "  repair_mode: severe\n"
        );

        assertEquals("autowsgr/data/plan/normal_fight/3-2.yaml", config.normalSortie.planPath);
        assertEquals(45L, config.autoExpedition.intervalSeconds);
        assertEquals("简单巡洋", config.campaign.battleType);
        assertEquals(5, config.campaign.times);
        assertEquals(5, config.campaign.formation);
        assertFalse(config.campaign.night);
        assertEquals(CampaignBattleConfig.REPAIR_SEVERE, config.campaign.repairMode);
    }

    @Test public void missingOptionalSectionsUseDocumentedSafeDefaults() throws Exception {
        UnifiedAutomationConfig config = UnifiedAutomationConfig.parse("minimal.yaml", "version: 1\n");

        assertEquals("autowsgr/data/plan/normal_fight/1-1.yaml", config.normalSortie.planPath);
        assertEquals(AutoExpeditionConfig.DEFAULT_INTERVAL_SECONDS, config.autoExpedition.intervalSeconds);
        assertEquals(CampaignBattleConfig.DEFAULT_BATTLE_TYPE, config.campaign.battleType);
        assertEquals(CampaignBattleConfig.DEFAULT_TIMES, config.campaign.times);
        assertEquals(CampaignBattleConfig.DEFAULT_FORMATION, config.campaign.formation);
        assertTrue(config.campaign.night);
        assertEquals(CampaignBattleConfig.DEFAULT_REPAIR_MODE, config.campaign.repairMode);
    }
}
