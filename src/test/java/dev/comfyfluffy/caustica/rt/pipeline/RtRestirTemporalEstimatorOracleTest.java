package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact finite-state reference for temporal reservoir weighting. Two independent
 * light proposals in each of two frames and every survivor are enumerated:
 * this does not depend on a lucky RNG seed or noisy Monte Carlo tolerances.
 *
 * <p>The target represents an unshadowed direct-light contribution. Visibility
 * is traced on the final selected sample only, as in the GPU pipeline.
 * This validates the mathematical reference, not Vulkan synchronization or
 * temporal/disocclusion correctness in an actual Minecraft scene.
 */
final class RtRestirTemporalEstimatorOracleTest {
    private static final double[] PROPOSAL = {0.75, 0.25};

    @Test
    void stationaryReceiverRetainsFullEmitterIntegral() {
        assertEquals(7.0, enumerate(new double[] {4, 3}, new double[] {4, 3},
                new double[] {1, 1}, 2, false), 1e-11);
    }

    @Test
    void changedReceiverReweightsHistoricalEmitterBeforeOcclusion() {
        double[] oldTarget = {1, 8};
        double[] currentTarget = {9, 2};
        double[] visibility = {1, 0};

        // Exact integral of the newly shaded, visible receiver: 9 + 2*0.
        assertEquals(9.0, enumerate(oldTarget, currentTarget, visibility, 2, false), 1e-11);
        // Reusing the old target instead of evaluating the previous light at
        // the *current* receiver would silently undercount this scene.
        assertEquals(5.0, enumerate(oldTarget, currentTarget, visibility, 2, true), 1e-11);
    }

    @Test
    void visibilityIsAppliedOnlyToTheSelectedCurrentFrameLight() {
        double[] oldTarget = {1, 8};
        double[] currentTarget = {9, 2};
        // Every light can be occluded even though the reservoir still has
        // positive *unshadowed* target weights.
        assertEquals(0.0, enumerate(oldTarget, currentTarget,
                new double[] {0, 0}, 2, false), 1e-11);
        // Reversing the visibility mask must preserve the integral of the
        // surviving emitter, not the obsolete previous-frame target.
        assertEquals(2.0, enumerate(oldTarget, currentTarget,
                new double[] {0, 1}, 2, false), 1e-11);
        assertEquals(11.0, enumerate(oldTarget, currentTarget,
                new double[] {1, 1}, 2, false), 1e-11);
    }

    @Test
    void limitingHistorySampleCountKeepsEstimatorNormalized() {
        double[] oldTarget = {1, 8};
        double[] currentTarget = {9, 2};
        double[] visibility = {1, 0};
        assertEquals(9.0, enumerate(oldTarget, currentTarget, visibility, 1, false), 1e-11);
        assertEquals(9.0, enumerate(oldTarget, currentTarget, visibility, 2, false), 1e-11);
        assertTrue(Math.abs(enumerate(oldTarget, currentTarget, visibility, 1, true) - 9.0)
                > 1.0);
    }

    /**
     * For each previous-frame proposal pair, sample previous reservoir survivor
     * exactly by its normalized old target / proposal PDF. Reweight its survivor
     * at the current receiver by current target * Wprev * capped Mprev. Form the
     * new reservoir from that representative plus two fresh current proposals.
     * The final survivor contributes Wnew * current target * visibility.
     */
    private static double enumerate(double[] oldTarget, double[] currentTarget,
                                    double[] visibility, double effectivePreviousM,
                                    boolean reusePreviousTargetIncorrectly) {
        double expectation = 0.0;
        double totalProbability = 0.0;
        for (int oldA = 0; oldA < 2; oldA++) {
            for (int oldB = 0; oldB < 2; oldB++) {
                double[] oldWeights = {
                        oldTarget[oldA] / PROPOSAL[oldA],
                        oldTarget[oldB] / PROPOSAL[oldB]
                };
                double oldWeightSum = oldWeights[0] + oldWeights[1];
                int[] oldCandidates = {oldA, oldB};
                for (int freshA = 0; freshA < 2; freshA++) {
                    for (int freshB = 0; freshB < 2; freshB++) {
                        double proposalProbability = PROPOSAL[oldA] * PROPOSAL[oldB]
                                * PROPOSAL[freshA] * PROPOSAL[freshB];
                        for (int previousChoice = 0; previousChoice < 2; previousChoice++) {
                            int oldSurvivor = oldCandidates[previousChoice];
                            double prevSelectedProbability = oldWeights[previousChoice] / oldWeightSum;
                            double previousW = oldWeightSum / (2.0 * oldTarget[oldSurvivor]);
                            double targetForReuse = reusePreviousTargetIncorrectly
                                    ? oldTarget[oldSurvivor] : currentTarget[oldSurvivor];
                            int[] candidateLights = {oldSurvivor, freshA, freshB};
                            double[] weights = {
                                    targetForReuse * previousW * effectivePreviousM,
                                    currentTarget[freshA] / PROPOSAL[freshA],
                                    currentTarget[freshB] / PROPOSAL[freshB]
                            };
                            double mergedSum = weights[0] + weights[1] + weights[2];
                            double mergedM = effectivePreviousM + 2.0;
                            double stateProbability = proposalProbability * prevSelectedProbability;
                            totalProbability += stateProbability;
                            for (int survivor = 0; survivor < 3; survivor++) {
                                int light = candidateLights[survivor];
                                double selectionProbability = weights[survivor] / mergedSum;
                                double selectedW = mergedSum / (mergedM * currentTarget[light]);
                                expectation += stateProbability * selectionProbability
                                        * selectedW * currentTarget[light] * visibility[light];
                            }
                        }
                    }
                }
            }
        }
        assertEquals(1.0, totalProbability, 1e-12);
        return expectation;
    }
}
