package dev.comfyfluffy.caustica.rt.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite-state three-frame RIS reference. Enumerates every local/global
 * emitter draw and every reservoir survivor at each frame. The proposals
 * are independent; this is not a model of correlated GPU path samples.
 */
final class RtRestirThreeFrameOracleTest {
    private static final double[][] LOCAL = {
            {0.70, 0.20, 0.10}, {0.15, 0.45, 0.40}, {0.25, 0.25, 0.50}
    };
    private static final double[][] GLOBAL = {
            {0.20, 0.45, 0.35}, {0.50, 0.20, 0.30}, {0.40, 0.30, 0.30}
    };
    private static final double[][] PHYSICAL = {
            {2.5, 0.0, 4.0}, {1.5, 4.5, 0.2}, {0.1, 2.2, 5.0}
    };
    private static final double[] VISIBILITY = {1.0, 0.25, 0.75};

    private record ReservoirState(double probability, int light, double weight, double count) {}
    private record Moments(double mean, double variance, double probability) {}

    @Test
    void threeFrameReweightingPreservesEachCurrentIntegralInTheIndependentOracle() {
        for (double selectionFloor : new double[] {0.001, 0.1, 1.0}) {
            for (int historyCap : new int[] {1, 2, 4}) {
                Moments[] frames = enumerate(selectionFloor, historyCap);
                for (int frame = 0; frame < 3; frame++) {
                    assertEquals(1.0, frames[frame].probability(), 1e-10);
                    assertEquals(direct(PHYSICAL[frame]), frames[frame].mean(), 1e-10);
                    assertTrue(frames[frame].variance() >= 0.0);
                }
            }
        }
    }

    @Test
    void normalizingTheMeanDoesNotControlThreeFrameVariance() {
        Moments[] lowFloor = enumerate(0.001, 4);
        Moments[] regularized = enumerate(0.1, 4);
        Moments[] shortHistory = enumerate(0.001, 1);

        assertEquals(4.4, lowFloor[2].mean(), 1e-10);
        assertEquals(225.29470567297858, lowFloor[2].variance(), 1e-8);
        assertEquals(122.4987187520388, regularized[2].variance(), 1e-8);
        assertEquals(48.56297096034014, shortHistory[2].variance(), 1e-8);
        assertTrue(lowFloor[2].variance() > shortHistory[2].variance() * 4.0);
        assertTrue(regularized[2].variance() > 100.0);
    }

    private static double direct(double[] physical) {
        double result = 0.0;
        for (int i = 0; i < physical.length; i++) result += physical[i] * VISIBILITY[i];
        return result;
    }

    /**
     * For each independent pair of local/global proposals, enumerate the
     * stream-survivor distributions. The previous reservoir contributes
     * targetCurrent * weightPrevious * min(MPrevious, historyCap), exactly
     * the mass prescribed by the experimental shader. Its old target is
     * never incorrectly reused as a new-frame target.
     */
    private static Moments[] enumerate(double selectionFloor, int historyCap) {
        List<ReservoirState> previous = List.of(new ReservoirState(1.0, -1, 0.0, 0.0));
        Moments[] result = new Moments[3];

        for (int frame = 0; frame < 3; frame++) {
            double[] target = new double[3];
            double[] proposalPdf = new double[3];
            for (int i = 0; i < 3; i++) {
                target[i] = Math.max(PHYSICAL[frame][i], selectionFloor);
                proposalPdf[i] = (LOCAL[frame][i] + GLOBAL[frame][i]) * 0.5;
            }

            List<ReservoirState> next = new ArrayList<>();
            double mean = 0.0;
            double secondMoment = 0.0;
            double probability = 0.0;

            for (ReservoirState old : previous) {
                for (int localLight = 0; localLight < 3; localLight++) {
                    for (int globalLight = 0; globalLight < 3; globalLight++) {
                        double drawProbability = old.probability()
                                * LOCAL[frame][localLight] * GLOBAL[frame][globalLight];
                        int[] lights = {localLight, globalLight, old.light()};
                        double[] weights = {
                                target[localLight] / proposalPdf[localLight],
                                target[globalLight] / proposalPdf[globalLight],
                                0.0
                        };
                        double newCount = 2.0;
                        if (old.count() > 0.0) {
                            double reusedM = Math.min(old.count(), historyCap);
                            weights[2] = target[old.light()] * old.weight() * reusedM;
                            newCount += reusedM;
                        }
                        double totalWeight = weights[0] + weights[1] + weights[2];
                        int candidateCount = old.count() > 0.0 ? 3 : 2;
                        for (int selected = 0; selected < candidateCount; selected++) {
                            if (weights[selected] <= 0.0) continue;
                            int light = lights[selected];
                            double selectedProbability = weights[selected] / totalWeight;
                            double stateProbability = drawProbability * selectedProbability;
                            double finalWeight = totalWeight / (newCount * target[light]);
                            double luminance = finalWeight * PHYSICAL[frame][light]
                                    * VISIBILITY[light];
                            probability += stateProbability;
                            mean += stateProbability * luminance;
                            secondMoment += stateProbability * luminance * luminance;
                            next.add(new ReservoirState(
                                    stateProbability, light, finalWeight, newCount));
                        }
                    }
                }
            }
            result[frame] = new Moments(mean, secondMoment - mean * mean, probability);
            previous = next;
        }
        return result;
    }
}
