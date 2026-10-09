package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact finite-state two-frame oracle for the regularized temporal RIS
 * selection target. Full proposal support and an independent positive
 * selection target are distinct from a nonzero physical light contribution.
 * Not a validation of multi-frame Vulkan rendering, MIS or image variance.
 */
final class RtRestirPositiveSupportOracleTest {
    private static final double[] OLD_LOCAL = {0.70, 0.20, 0.10};
    private static final double[] OLD_GLOBAL = {0.20, 0.45, 0.35};
    private static final double[] NEW_LOCAL = {0.15, 0.45, 0.40};
    private static final double[] NEW_GLOBAL = {0.50, 0.20, 0.30};
    private static final double[] CURRENT_PHYSICAL = {1.5, 4.5, 0.2};
    private static final double[] VISIBILITY = {1.0, 0.25, 0.75};
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void regularizedTargetRestoresSupportChangedExpectation() {
        double[] oldPhysical = {2.5, 0.0, 4.0};
        double directReference = direct(CURRENT_PHYSICAL, VISIBILITY);
        assertEquals(2.775, directReference, 1e-12);
        for (double floor : new double[] {1e-6, 1e-3, 0.01, 0.1, 1.0}) {
            for (int historyM : new int[] {1, 2, 4}) {
                assertEquals(directReference, enumerate(oldPhysical, CURRENT_PHYSICAL,
                        VISIBILITY, floor, historyM), 1e-10);
            }
        }
    }

    @Test
    void completeLossOfOldPhysicalContributionDoesNotSuppressNewLighting() {
        double[] oldPhysical = {0.0, 0.0, 0.0};
        double[] current = {0.0, 4.5, 0.2};
        assertEquals(direct(current, VISIBILITY),
                enumerate(oldPhysical, current, VISIBILITY, 0.001, 2), 1e-11);
    }

    @Test
    void physicalVisibilityAndZeroPhysicalRadianceAreNotFloored() {
        double[] oldPhysical = {0.0, 0.0, 4.0};
        double[] newPhysical = {1.5, 0.0, 0.2};
        assertEquals(0.0, enumerate(oldPhysical, newPhysical,
                new double[] {0.0, 0.0, 0.0}, 0.001, 2), 1e-11);
        assertEquals(direct(newPhysical, VISIBILITY),
                enumerate(oldPhysical, newPhysical, VISIBILITY, 0.001, 2), 1e-11);
    }

    @Test
    void supportFixStillHasLargeVarianceForTinySamplingFloor() {
        double[] oldPhysical = {2.5, 0.0, 4.0};
        double lowFloorVariance = moments(oldPhysical, CURRENT_PHYSICAL,
                VISIBILITY, 0.001, 2).variance();
        double higherFloorVariance = moments(oldPhysical, CURRENT_PHYSICAL,
                VISIBILITY, 0.1, 2).variance();
        assertTrue(lowFloorVariance > 900.0);
        assertTrue(higherFloorVariance < 20.0);
        assertTrue(lowFloorVariance > higherFloorVariance * 20.0);
    }

    @Test
    void shaderOnlyFloorsOptionalTemporalSelectionNotRenderedContribution() throws IOException {
        String light = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        String indirect = Files.readString(ROOT.resolve(
                "shaders/pipelines/world/indirect.rgen.slang"));
        assertTrue(light.contains("RESTIR_TEMPORAL_TARGET_FLOOR = 1.0e-3"));
        assertTrue(light.contains("return max(physicalTarget, RESTIR_TEMPORAL_TARGET_FLOOR);"));
        assertTrue(light.contains("bool temporalFullSupport)"));
        assertTrue(light.contains("temporalFullSupport ? restirSelectionTarget(phat) : phat"));
        assertTrue(light.contains("reservoirOffer(r, sp, lightNormal, le, area, selectionTarget"));
        assertTrue(light.contains("return contrib * vis * s.W;"));
        assertTrue(indirect.contains("float prevSelectionTarget = restirSelectionTarget(prevTarget);"));
        assertTrue(indirect.contains("prev.samplePosArea.w, prevSelectionTarget, effectiveM,"));
        assertTrue(indirect.contains("true, 0.0, risSampler, false);"));
        assertTrue(indirect.contains("pc.restirHistoryWriteAddr != 0);"));
    }

    private static double direct(double[] physical, double[] vis) {
        double sum = 0.0;
        for (int i = 0; i < physical.length; i++) sum += physical[i] * vis[i];
        return sum;
    }

    private static double[] mixture(double[] local, double[] global) {
        double[] mix = new double[local.length];
        for (int i = 0; i < mix.length; i++) mix[i] = (local[i] + global[i]) / 2.0;
        return mix;
    }

    private static double[] floored(double[] physical, double floor) {
        double[] target = new double[physical.length];
        for (int i = 0; i < target.length; i++) target[i] = Math.max(physical[i], floor);
        return target;
    }

    private record Moments(double mean, double variance) {}

    private static double enumerate(double[] oldPhysical, double[] currentPhysical,
                                    double[] visibility, double floor, int historyM) {
        return moments(oldPhysical, currentPhysical, visibility, floor, historyM).mean();
    }

    /** Enumerate all 3^4 proposals and both historical reservoir survivors. */
    private static Moments moments(double[] oldPhysical, double[] currentPhysical,
                                   double[] visibility, double floor, int historyM) {
        double[] oldTarget = floored(oldPhysical, floor);
        double[] nowTarget = floored(currentPhysical, floor);
        double[] oldQ = mixture(OLD_LOCAL, OLD_GLOBAL);
        double[] nowQ = mixture(NEW_LOCAL, NEW_GLOBAL);
        double mean = 0.0, secondMoment = 0.0, probability = 0.0;

        for (int oldL = 0; oldL < 3; oldL++) {
            for (int oldG = 0; oldG < 3; oldG++) {
                double weightL = oldTarget[oldL] / oldQ[oldL];
                double weightG = oldTarget[oldG] / oldQ[oldG];
                double oldSum = weightL + weightG;
                for (int nowL = 0; nowL < 3; nowL++) {
                    for (int nowG = 0; nowG < 3; nowG++) {
                        double drawProbability = OLD_LOCAL[oldL] * OLD_GLOBAL[oldG]
                                * NEW_LOCAL[nowL] * NEW_GLOBAL[nowG];
                        for (int selectedOld = 0; selectedOld < 2; selectedOld++) {
                            int oldSample = selectedOld == 0 ? oldL : oldG;
                            double selectedProbability = (selectedOld == 0 ? weightL : weightG) / oldSum;
                            probability += drawProbability * selectedProbability;
                            double oldReservoirW = oldSum / (2.0 * oldTarget[oldSample]);
                            int[] candidate = {nowL, nowG, oldSample};
                            double[] weights = {
                                    nowTarget[nowL] / nowQ[nowL],
                                    nowTarget[nowG] / nowQ[nowG],
                                    nowTarget[oldSample] * oldReservoirW * historyM
                            };
                            double totalWeight = weights[0] + weights[1] + weights[2];
                            double M = 2.0 + historyM;
                            for (int selected = 0; selected < 3; selected++) {
                                int light = candidate[selected];
                                double pSelected = weights[selected] / totalWeight;
                                double normalizedW = totalWeight / (M * nowTarget[light]);
                                double output = normalizedW
                                        * currentPhysical[light] * visibility[light];
                                double stateWeight = drawProbability * selectedProbability * pSelected;
                                mean += stateWeight * output;
                                secondMoment += stateWeight * output * output;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(1.0, probability, 1e-12);
        return new Moments(mean, secondMoment - mean * mean);
    }
}
