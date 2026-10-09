package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive two-frame oracle for an RIS estimator with one local and one
 * global light proposal per frame. A mixed proposal PDF is required for
 * deterministic local/global stratification, even when the proposal mixture
 * and the receiver target change between frames.
 *
 * This tests the estimator equations, not Vulkan execution, temporal image
 * stability, or the correctness of a spatial ReSTIR implementation.
 */
final class RtRestirTemporalProposalSupportOracleTest {
    private static final double[] PREVIOUS_LOCAL = {0.70, 0.20, 0.10};
    private static final double[] PREVIOUS_GLOBAL = {0.20, 0.45, 0.35};
    private static final double[] CURRENT_LOCAL = {0.15, 0.45, 0.40};
    private static final double[] CURRENT_GLOBAL = {0.50, 0.20, 0.30};
    private static final double[] CURRENT_TARGET = {1.5, 4.5, 0.2};
    private static final double[] VISIBILITY = {1.0, 0.25, 0.75};
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void differingStratifiedProposalPdfsRemainNormalizedWithFullSupport() {
        double[] oldTarget = {2.5, 0.7, 4.0};
        double reference = directIntegral(CURRENT_TARGET, VISIBILITY);
        assertEquals(2.775, reference, 1e-12);

        for (int cappedHistoryM : new int[] {1, 2, 4}) {
            assertEquals(reference, enumerate(oldTarget, CURRENT_TARGET, VISIBILITY,
                    cappedHistoryM, false, false), 1e-11);
        }
    }

    @Test
    void usingStaleReceiverTargetProducesDetectableError() {
        double[] oldTarget = {2.5, 0.7, 4.0};
        double wrong = enumerate(oldTarget, CURRENT_TARGET, VISIBILITY,
                2, true, false);
        assertEquals(4.225, wrong, 1e-11);
        assertTrue(Math.abs(wrong - directIntegral(CURRENT_TARGET, VISIBILITY)) > 1.0);
    }

    @Test
    void newlyContributingEmitterExposesTemporalSupportBias() {
        // Emitter 1 has zero old target but a nonzero current target.
        // The old reservoir cannot select it, even though its proposal PDF
        // is positive. Reusing that reservoir counts missing historical
        // samples in M; the estimator is biased low despite matching grid
        // cells and receiver geometry.
        double[] oldTarget = {2.5, 0.0, 4.0};
        double reference = directIntegral(CURRENT_TARGET, VISIBILITY);
        double reused = enumerate(oldTarget, CURRENT_TARGET, VISIBILITY,
                2, false, false);
        assertEquals(2.337375, reused, 1e-11);
        assertTrue(reused < reference - 0.4);

        // If temporal reuse is rejected and only fresh RIS proposals are
        // used, the expectation returns to the current-frame integral.
        assertEquals(reference, enumerate(oldTarget, CURRENT_TARGET, VISIBILITY,
                2, false, true), 1e-11);
    }

    @Test
    void invalidHistoryFallsBackWithoutInventingLighting() {
        double[] zeroOldTarget = {0.0, 0.0, 0.0};
        assertEquals(directIntegral(CURRENT_TARGET, VISIBILITY),
                enumerate(zeroOldTarget, CURRENT_TARGET, VISIBILITY, 2, false, false),
                1e-11);
        assertEquals(0.0,
                enumerate(new double[] {2.5, 0.7, 4.0}, CURRENT_TARGET,
                        new double[] {0.0, 0.0, 0.0}, 2, false, false), 1e-11);
    }

    @Test
    void shaderRetainsFiniteHistoryAndProposalCompatibilityGates() throws IOException {
        String indirect = Files.readString(ROOT.resolve(
                "shaders/pipelines/world/indirect.rgen.slang"));
        String lighting = Files.readString(ROOT.resolve(
                "shaders/pipelines/world/lighting.slang"));
        String host = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        assertTrue(indirect.contains("prev.receiverIdentity.x == payload.materialId"));
        assertTrue(indirect.contains("all(prev.receiverIdentity.yzw == proposalCellKey)"));
        assertTrue(indirect.contains("distance(prevHit, expectedPreviousHit) < 0.25"));
        assertTrue(indirect.contains("dot(previousNormal, n) > 0.95"));
        assertTrue(indirect.contains("restirHistoryCandidateWeight("));
        assertTrue(lighting.contains("target > 0.0 && target < 1.0e20"));
        assertTrue(lighting.contains("effectiveM = min(previousM, maxEffectiveM);"));
        assertTrue(host.contains("restirLastWorld == Minecraft.getInstance().level"));
        assertTrue(host.contains("restirLastPathEpoch == pathSampleEpoch"));
        assertTrue(host.contains(
                "restirLastRisCandidates == CausticaConfig.Rt.Lights.RIS_CANDIDATES.value()"));
        assertFalse(indirect.contains("restirSpatialGeometryCompatible("));
    }

    private static double directIntegral(double[] target, double[] visibility) {
        double result = 0.0;
        for (int i = 0; i < target.length; i++) result += target[i] * visibility[i];
        return result;
    }

    private static double[] mixture(double[] local, double[] global) {
        double[] result = new double[local.length];
        for (int i = 0; i < local.length; i++) result[i] = 0.5 * (local[i] + global[i]);
        return result;
    }

    /**
     * Enumerates every old/new light proposal (3^4 states), every previous
     * reservoir survivor, and every final reservoir survivor. Old and new
     * local/global PDFs differ; their average is the balance-heuristic PDF
     * used by the stratified RIS proposal. The one history offer has mass
     * pCurrent * WPrevious * cappedM, exactly as the shader does.
     */
    private static double enumerate(double[] oldTarget, double[] currentTarget,
                                    double[] visibility, double cappedHistoryM,
                                    boolean stalePreviousTarget, boolean rejectHistory) {
        double[] oldMixture = mixture(PREVIOUS_LOCAL, PREVIOUS_GLOBAL);
        double[] nowMixture = mixture(CURRENT_LOCAL, CURRENT_GLOBAL);
        double expectedRadiance = 0.0;
        double totalProbability = 0.0;

        for (int oldLocal = 0; oldLocal < 3; oldLocal++) {
            for (int oldGlobal = 0; oldGlobal < 3; oldGlobal++) {
                int[] previousLights = {oldLocal, oldGlobal};
                double[] previousWeights = {
                        oldTarget[oldLocal] / oldMixture[oldLocal],
                        oldTarget[oldGlobal] / oldMixture[oldGlobal]
                };
                double previousSum = previousWeights[0] + previousWeights[1];
                for (int newLocal = 0; newLocal < 3; newLocal++) {
                    for (int newGlobal = 0; newGlobal < 3; newGlobal++) {
                        double stateProbability = PREVIOUS_LOCAL[oldLocal]
                                * PREVIOUS_GLOBAL[oldGlobal]
                                * CURRENT_LOCAL[newLocal] * CURRENT_GLOBAL[newGlobal];
                        int choices = previousSum > 0.0 ? 2 : 1;
                        for (int choice = 0; choice < choices; choice++) {
                            double chosenProbability = previousSum > 0.0
                                    ? previousWeights[choice] / previousSum : 1.0;
                            if (chosenProbability == 0.0) continue;
                            int previousLight = previousLights[choice];

                            int[] candidates = {newLocal, newGlobal, previousLight};
                            double[] weights = {
                                    currentTarget[newLocal] / nowMixture[newLocal],
                                    currentTarget[newGlobal] / nowMixture[newGlobal],
                                    0.0
                            };
                            double count = 2.0;
                            if (!rejectHistory && previousSum > 0.0) {
                                double previousW = previousSum
                                        / (2.0 * oldTarget[previousLight]);
                                double targetForHistory = stalePreviousTarget
                                        ? oldTarget[previousLight]
                                        : currentTarget[previousLight];
                                double historyWeight = targetForHistory
                                        * previousW * cappedHistoryM;
                                if (historyWeight > 0.0 && Double.isFinite(historyWeight)) {
                                    weights[2] = historyWeight;
                                    count += cappedHistoryM;
                                }
                            }

                            double sum = weights[0] + weights[1] + weights[2];
                            double probability = stateProbability * chosenProbability;
                            totalProbability += probability;
                            if (sum == 0.0) continue;

                            for (int survivor = 0; survivor < 3; survivor++) {
                                if (weights[survivor] <= 0.0) continue;
                                int light = candidates[survivor];
                                double selectedProbability = weights[survivor] / sum;
                                double finalW = sum / (count * currentTarget[light]);
                                expectedRadiance += probability * selectedProbability
                                        * finalW * currentTarget[light] * visibility[light];
                            }
                        }
                    }
                }
            }
        }
        assertEquals(1.0, totalProbability, 1e-12);
        return expectedRadiance;
    }
}
