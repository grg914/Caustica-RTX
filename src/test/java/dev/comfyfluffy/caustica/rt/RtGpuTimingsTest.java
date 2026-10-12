package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RtGpuTimingsTest {
    @Test void p95UsesOnlyTheCompletedGpuWindow() {
        double[] durations = new double[120];
        for (int i = 0; i < durations.length; i++) {
            durations[i] = i + 1.0;
        }
        assertEquals(114.0, RtGpuTimings.percentile95(durations, durations.length));
    }

    @Test void p95RejectsInvalidSampleCounts() {
        double[] durations = { 1.0, 2.0, 100.0, 0.0 };
        assertEquals(100.0, RtGpuTimings.percentile95(durations, 3));
        assertThrows(IllegalArgumentException.class, () -> RtGpuTimings.percentile95(durations, 0));
        assertThrows(IllegalArgumentException.class, () -> RtGpuTimings.percentile95(durations, 5));
    }
}
