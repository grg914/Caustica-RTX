package dev.comfyfluffy.caustica.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtBalancedPresetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    private static String options() throws IOException {
        return Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java"));
    }

    private static String preset(String options, int index, int nextIndex) {
        String startToken = "if (position == " + index + ") {";
        String endToken = "} else if (position == " + nextIndex + ") {";
        int start = options.indexOf(startToken);
        int end = options.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, "Missing preset section: " + index);
        return options.substring(start, end);
    }

    @Test
    void qualityPresetUsesHigherLightingDetailWithoutEnablingExperimentalFeatures()
            throws IOException {
        String options = options();
        String quality = preset(options, 1, 2);
        assertTrue(quality.contains("Rt.Composite.SPP.set(1)"));
        assertTrue(quality.contains("Rt.Composite.MAX_BOUNCES.set(3)"));
        assertTrue(quality.contains("Rt.Lights.RIS_CANDIDATES.set(8)"));
        assertTrue(quality.contains("Rt.DlssRr.QUALITY.set(2)"));
        assertTrue(quality.contains("Rt.Entities.PARTICLES_ENABLED.set(true)"));
        assertTrue(quality.contains("Rt.Entities.GLOW_ENABLED.set(true)"));
        assertTrue(quality.contains("Rt.Composite.WATER_WAVES.set(true)"));
        assertFalse(quality.contains("Rt.Lights.RESTIR_DI.set("));
        assertFalse(quality.contains("Rt.Fg.ENABLED.set("));
        assertFalse(quality.contains("Rt.DlssNr.ENABLED.set("));
    }

    @Test
    void balancedPresetPreservesVisualEffectsWithLowerTracingCost() throws IOException {
        String balanced = preset(options(), 2, 3);
        assertTrue(balanced.contains("Rt.Composite.SPP.set(1)"));
        assertTrue(balanced.contains("Rt.Composite.MAX_BOUNCES.set(2)"));
        assertTrue(balanced.contains("Rt.Lights.RIS_CANDIDATES.set(4)"));
        assertTrue(balanced.contains("Rt.Entities.PARTICLES_ENABLED.set(true)"));
        assertTrue(balanced.contains("Rt.Entities.GLOW_ENABLED.set(true)"));
        assertTrue(balanced.contains("Rt.Composite.WATER_WAVES.set(true)"));
        assertTrue(balanced.contains("Rt.DlssRr.QUALITY.set(1)"));
        assertFalse(balanced.contains("Rt.Lights.RESTIR_DI.set("));
        assertFalse(balanced.contains("Rt.Fg.ENABLED.set("));
    }

    @Test
    void manualChangesConvertPresetToCustomWithoutLosingTheEditedValues() throws IOException {
        String options = options();
        int start = options.indexOf("private static void manualPresetOverride()");
        int end = options.indexOf("private static OptionInstance<Boolean> presetBoolean(", start);
        assertTrue(start >= 0 && end > start);
        String manualOverride = options.substring(start, end);
        assertTrue(manualOverride.contains("Rt.Performance.QUALITY.set(false)"));
        assertTrue(manualOverride.contains("Rt.Performance.BALANCED.set(false)"));
        assertTrue(manualOverride.contains("Rt.Performance.MODE.set(false)"));
        assertTrue(manualOverride.contains("performanceSnapshot = null"));
        assertTrue(manualOverride.contains("if (setting.value() != value)"));
        assertTrue(manualOverride.indexOf("setting.set(value)") < manualOverride.indexOf("manualPresetOverride();"));
        assertTrue(options.contains("return presetBoolean(\"caustica.options.rt.particles\""));
        assertTrue(options.contains("return presetBoolean(\"caustica.options.rt.waterWaves\""));
        assertTrue(options.contains("position -> manualPresetInt(setting, steps.get(position))"));
        assertTrue(options.contains("value -> manualPresetInt(setting, value)"));
        assertTrue(options.contains("float sharpen = value / 100.0f"));
    }

    @Test
    void maxFpsAndCustomSettingsStayAvailable() throws IOException {
        String options = options();
        assertTrue(options.contains("new OptionInstance.IntRange(0, 3)"));
        assertTrue(options.contains("CausticaConfig.Rt.Performance.QUALITY"));
        assertTrue(options.contains("CausticaConfig.Rt.Performance.BALANCED"));
        assertTrue(options.contains("CausticaConfig.Rt.Performance.MODE"));
        assertTrue(options.contains("quality.set(position == 1)"));
        assertTrue(options.contains("balanced.set(position == 2)"));
        assertTrue(options.contains("maxFps.set(position == 3)"));
        assertTrue(options.contains("previousPreset == 0 && performanceSnapshot == null"));
        assertTrue(options.contains("performanceSnapshot.dlssQuality()"));
        assertTrue(options.contains("Rt.Composite.MAX_BOUNCES.set(1)"));
        assertTrue(options.contains("Rt.Lights.RIS_CANDIDATES.set(2)"));
        assertTrue(options.contains("Rt.DlssRr.QUALITY.set(0)"));
        String labels = Files.readString(ROOT.resolve(
                "src/main/resources/assets/caustica/lang/en_us.json"));
        assertTrue(labels.contains("\"caustica.options.rt.qualityPreset.0\": \"Custom\""));
        assertTrue(labels.contains("\"caustica.options.rt.qualityPreset.1\": \"Quality\""));
        assertTrue(labels.contains("\"caustica.options.rt.qualityPreset.2\": \"Balanced\""));
        assertTrue(labels.contains("\"caustica.options.rt.qualityPreset.3\": \"Max FPS\""));
    }
}
