package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtReservoirShaderContractTest {
    private static final Path LIGHTING = Path.of(System.getProperty("user.dir"),
            "shaders", "pipelines", "world", "lighting.slang");

    @Test
    void weightedReservoirRetainsOriginalRisEstimatorAndOneVisibilityRay() throws IOException {
        String source = Files.readString(LIGHTING).replaceAll("\\s+", " ");

        assertTrue(source.contains("r.M += candidateM;"));
        assertTrue(source.contains("r.M += 1.0;"));
        assertFalse(source.contains("r.M = float(candidateCount);"));
        assertTrue(source.contains("r.wSum += weight;"));
        assertTrue(source.contains("if (selection * r.wSum < weight)"));
        assertTrue(source.contains("r.phat = target;"));
        assertTrue(source.contains("r.W = r.phat > 0.0 && r.M > 0.0 ? r.wSum / (r.M * r.phat) : 0.0;"));
        assertTrue(source.contains("reservoirOffer(r, sp, lightNormal, le, area, phat, 1.0, w,"));
        assertTrue(source.contains("reservoirFinalize(r);"));
        assertTrue(source.contains("float w = phat / max(sourcePdf, 1.0e-20);"));
        assertEquals(1, occurrences(source, "VisibilityResult shadow = visibility(origin, toL / dist"));
        assertFalse(source.contains("r.W = r.phat > 0.0 ?"));
    }

    @Test
    void zeroWeightCandidatesStillCountTowardNormalization() {
        double count = 0, sumWeights = 0, target = 0;
        double[] targets = {2, 0, 0, 0};
        double[] pdf = {0.5, 1, 1, 1};
        for (int i = 0; i < targets.length; i++) {
            count += 1;
            if (targets[i] <= 0) continue;
            sumWeights += targets[i] / pdf[i];
            target = targets[i];
        }
        assertEquals(4, count, 0);
        assertEquals(0.5, sumWeights / (count * target), 1e-10);
    }

    @Test
    void streamingWeightsRetainUnbiasedOneOverPdfNormalization() {
        double[] targets = {4.0, 3.0, 0.0, 1.0};
        double[] proposals = {0.4, 0.3, 0.6, 0.2};
        double[] uniforms = {0.8, 0.1, 0.4, 0.1};
        double sum = 0.0;
        double selectedTarget = 0.0;

        for (int i = 0; i < targets.length; i++) {
            if (targets[i] <= 0.0) continue;
            double weight = targets[i] / proposals[i];
            sum += weight;
            if (uniforms[i] * sum < weight) selectedTarget = targets[i];
        }

        assertEquals(24.999999999999996, sum, 1e-10);
        assertEquals(1.0, selectedTarget, 1e-10);
        double finalWeight = sum / (targets.length * selectedTarget);
        assertEquals(6.25, finalWeight, 1e-10);
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int from = 0; (from = text.indexOf(part, from)) >= 0; from += part.length()) count++;
        return count;
    }
}
