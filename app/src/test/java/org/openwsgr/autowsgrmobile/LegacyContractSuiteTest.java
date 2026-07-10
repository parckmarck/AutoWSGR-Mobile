package org.openwsgr.autowsgrmobile;

import org.junit.Test;

public final class LegacyContractSuiteTest {
    @Test
    public void minimumRunContractsRemainGreen() {
        MinimumRunContractsTest.main(new String[0]);
    }

    @Test
    public void calibrationImportContractsRemainGreen() {
        CalibrationRecordImportAnalyzerTest.main(new String[0]);
    }
}
