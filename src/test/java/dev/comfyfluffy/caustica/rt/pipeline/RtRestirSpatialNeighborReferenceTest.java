package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CPU-only specification for a future spatial ReSTIR neighbor filter.
 * Does not exercise the shader, GPU synchronization, or establish unbiasedness.
 * Keeping this independent of rendering state makes rejection rules reviewable
 * before adding any spatial reservoir reads to the production shader.
 */
final class RtRestirSpatialNeighborReferenceTest {
    private static final double POSITION_THRESHOLD = 0.25;
    private static final double NORMAL_DOT_THRESHOLD = 0.95;
    private static final double ROUGHNESS_THRESHOLD = 0.15;

    record Surface(double x, double y, double z, double nx, double ny, double nz,
                   double roughness, int materialId, boolean valid) {}

    static boolean compatible(Surface center, Surface neighbor) {
        if (center == null || neighbor == null || !center.valid() || !neighbor.valid()) return false;
        double[] values = {center.x(), center.y(), center.z(), center.nx(), center.ny(),
                center.nz(), center.roughness(), neighbor.x(), neighbor.y(), neighbor.z(),
                neighbor.nx(), neighbor.ny(), neighbor.nz(), neighbor.roughness()};
        for (double value : values) if (!Double.isFinite(value)) return false;
        // Dot-product thresholds only describe angles for unit-length normals.
        // Reject corrupt, zero-length and scaled normals rather than admitting
        // incompatible surfaces through an arbitrarily large dot product.
        double centerLengthSq = center.nx() * center.nx() + center.ny() * center.ny()
                + center.nz() * center.nz();
        double neighborLengthSq = neighbor.nx() * neighbor.nx() + neighbor.ny() * neighbor.ny()
                + neighbor.nz() * neighbor.nz();
        if (!Double.isFinite(centerLengthSq) || !Double.isFinite(neighborLengthSq)
                || Math.abs(centerLengthSq - 1.0) > 1e-3
                || Math.abs(neighborLengthSq - 1.0) > 1e-3) return false;
        if (center.materialId() != neighbor.materialId()) return false;
        double dx = center.x() - neighbor.x(), dy = center.y() - neighbor.y();
        double dz = center.z() - neighbor.z();
        double separationSq = dx * dx + dy * dy + dz * dz;
        double dot = center.nx() * neighbor.nx() + center.ny() * neighbor.ny()
                + center.nz() * neighbor.nz();
        return Double.isFinite(separationSq) && separationSq < POSITION_THRESHOLD * POSITION_THRESHOLD
                && dot > NORMAL_DOT_THRESHOLD
                && Math.abs(center.roughness() - neighbor.roughness()) < ROUGHNESS_THRESHOLD;
    }

    private static final Surface CENTER =
            new Surface(0, 0, 0, 0, 1, 0, 0.4, 7, true);

    @Test
    void identicalReceiverPassesButDisoccludedNeighborDoesNot() {
        assertTrue(compatible(CENTER, CENTER));
        assertFalse(compatible(CENTER, new Surface(0, 0.3, 0, 0, 1, 0, 0.4, 7, true)));
        assertFalse(compatible(CENTER, new Surface(0.25, 0, 0, 0, 1, 0, 0.4, 7, true)));
    }

    @Test
    void surfaceAndMaterialMismatchRejectSpatialReuse() {
        assertFalse(compatible(CENTER, new Surface(0, 0, 0, 0, -1, 0, 0.4, 7, true)));
        assertFalse(compatible(CENTER, new Surface(0, 0, 0, 0, 1, 0, 0.6, 7, true)));
        assertFalse(compatible(CENTER, new Surface(0, 0, 0, 0, 1, 0, 0.4, 8, true)));
        assertFalse(compatible(CENTER, new Surface(0, 0, 0, 0, 1, 0, 0.4, 7, false)));
    }

    @Test
    void nonUnitAndDegenerateNormalsCannotPassAngularGate() {
        assertFalse(compatible(CENTER,
                new Surface(0, 0, 0, 0, 0, 0, 0.4, 7, true)));
        assertFalse(compatible(CENTER,
                new Surface(0, 0, 0, 0, 2, 0, 0.4, 7, true)));
        assertFalse(compatible(
                new Surface(0, 0, 0, 0, 10, 0, 0.4, 7, true), CENTER));
    }

    @Test
    void invalidNumbersAndMissingReceiversFailClosed() {
        assertFalse(compatible(null, CENTER));
        assertFalse(compatible(CENTER, null));
        assertFalse(compatible(CENTER,
                new Surface(Double.NaN, 0, 0, 0, 1, 0, 0.4, 7, true)));
        assertFalse(compatible(CENTER,
                new Surface(0, 0, 0, Double.POSITIVE_INFINITY, 1, 0, 0.4, 7, true)));
    }

    @Test
    void finiteBoundaryInsideThresholdPasses() {
        Surface neighbor = new Surface(0.1, 0, 0, 0, 1, 0, 0.45, 7, true);
        assertTrue(compatible(CENTER, neighbor));
        assertEquals(0.25, POSITION_THRESHOLD, 0.0);
    }
}
