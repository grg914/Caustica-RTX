package dev.comfyfluffy.caustica.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtBalancedPresetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void balancedPresetPreservesCostlyLightingDetailAndDoesNotEnableExperimentalFeatures()
            throws IOException {
        String options = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java"));
        String labels = Files.readString(ROOT.resolve(
                "src/main/resources/assets/caustica/lang/en_us.json"));
        int start = options.indexOf("if (position == 1) {");
        int end = options.indexOf("} else if (position == 2) {", start);
        assertTrue(start >= 0 && end > start);
        String balanced = options.substring(start, end);

        assertTrue(balanced.contains("Rt.Composite.SPP.set(1)"));
        assertTrue(balanced.contains("Rt.Composite.MAX_BOUNCES.set(2)"));
        assertTrue(balanced.contains("Rt.Lights.RIS_CANDIDATES.set(4)"));
        assertTrue(balanced.contains("Rt.Entities.PARTICLES_ENABLED.set(true)"));
        assertTrue(balanced.contains("Rt.Entities.GLOW_ENABLED.set(true)"));
        assertTrue(balanced.contains("Rt.Composite.WATER_WAVES.set(true)"));
        assertTrue(balanced.contains("Rt.DlssRr.QUALITY.set(1)"));
        assertFalse(balanced.contains("Rt.Lights.RESTIR_DI.set("));
        assertFalse(balanced.contains("Rt.Fg.ENABLED.set("));
        assertFalse(balanced.contains("Rt.DlssNr.ENABLED.set("));
        assertTrue(labels.contains("caustica.options.rt.qualityPreset.1"));
    }

    @Test
    void maxFpsPresetRemainsAvailableAndCustomSettingsCanBeRestored()
            throws IOException {
        String options = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java"));
        assertTrue(options.contains("CausticaConfig.Rt.Performance.MODE"));
        assertTrue(options.contains("CausticaConfig.Rt.Performance.BALANCED"));
        assertTrue(options.contains("performanceSnapshot.dlssQuality()"));
        assertTrue(options.contains("Rt.Composite.MAX_BOUNCES.set(1)"));
        assertTrue(options.contains("Rt.Lights.RIS_CANDIDATES.set(2)"));
        assertTrue(options.contains("Rt.DlssRr.QUALITY.set(0)"));
    }
}
