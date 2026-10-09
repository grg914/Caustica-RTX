package dev.comfyfluffy.caustica.rt.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numerical boundary oracle for the temporal ReSTIR clip-to-pixel guard.
 * CPU-only: does not replace Slang/Vulkan validation.
 */
final class RtRestirTemporalProjectionReferenceTest {
    static int pixelIndex(double clipX, double clipY, double clipW, int width, int height) {
        if (width <= 0 || height <= 0 || !(clipW > 0 && clipW < 1e20)
                || !Double.isFinite(clipX) || !Double.isFinite(clipY)
                || Math.abs(clipX) >= 1e20 || Math.abs(clipY) >= 1e20) return -1;
        double u = clipX / clipW * 0.5 + 0.5;
        double v = clipY / clipW * 0.5 + 0.5;
        if (!(u >= 0 && u < 1 && v >= 0 && v < 1)) return -1;
        return (int) Math.floor(v * height) * width + (int) Math.floor(u * width);
    }

    @Test
    void mapsCenterAndTopLeftWithinFrame() {
        assertEquals(5, pixelIndex(0, 0, 1, 4, 4));
        assertEquals(0, pixelIndex(-1, -1, 1, 4, 4));
    }

    @Test
    void rejectsRightBottomBoundaryAndBehindCamera() {
        assertEquals(-1, pixelIndex(1, 0, 1, 4, 4));
        assertEquals(-1, pixelIndex(0, 1, 1, 4, 4));
        assertEquals(-1, pixelIndex(0, 0, 0, 4, 4));
        assertEquals(-1, pixelIndex(0, 0, -1, 4, 4));
    }

    @Test
    void rejectsNonfiniteAndOverflowedProjectedCoordinates() {
        assertEquals(-1, pixelIndex(Double.NaN, 0, 1, 4, 4));
        assertEquals(-1, pixelIndex(Double.POSITIVE_INFINITY, 0, 1, 4, 4));
        assertEquals(-1, pixelIndex(0, 0, Double.NaN, 4, 4));
        assertEquals(-1, pixelIndex(1, 0, 1e-300, 4, 4));
        assertEquals(-1, pixelIndex(0, 0, 1e20, 4, 4));
    }
}
