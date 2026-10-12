package dev.comfyfluffy.caustica.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RtQualityProfileTest {
    @Test void ultraRetainsAllAvailableVisualEffects() {
        RtQualityProfile p = RtQualityProfile.forSelection(4);
        assertEquals(1, p.spp());
        assertEquals(4, p.maxBounces());
        assertEquals(8, p.risCandidates());
        assertEquals(2, p.dlssQuality());
        assertTrue(p.particles());
        assertTrue(p.glow());
        assertTrue(p.waterWaves());
        assertEquals(0.0f, p.sharpen());
    }
    @Test void previouslyPublishedPresetValuesDoNotChange() {
        for (int selection = 1; selection <= 4; selection++) {
            assertEquals(1, RtQualityProfile.forSelection(selection).spp());
        }
        assertEquals(3, RtQualityProfile.forSelection(1).maxBounces());
        assertEquals(8, RtQualityProfile.forSelection(1).risCandidates());
        assertEquals(2, RtQualityProfile.forSelection(1).dlssQuality());
        assertEquals(2, RtQualityProfile.forSelection(2).maxBounces());
        assertEquals(4, RtQualityProfile.forSelection(2).risCandidates());
        assertEquals(1, RtQualityProfile.forSelection(2).dlssQuality());
        assertEquals(1, RtQualityProfile.forSelection(3).maxBounces());
        assertEquals(2, RtQualityProfile.forSelection(3).risCandidates());
        assertEquals(0, RtQualityProfile.forSelection(3).dlssQuality());
        assertFalse(RtQualityProfile.forSelection(3).particles());
        assertFalse(RtQualityProfile.forSelection(3).glow());
        assertFalse(RtQualityProfile.forSelection(3).waterWaves());
    }
    @Test void invalidSelectionsNeverActivateAProfile() {
        assertNull(RtQualityProfile.forSelection(0));
        assertThrows(IllegalArgumentException.class, () -> RtQualityProfile.forSelection(-1));
        assertThrows(IllegalArgumentException.class, () -> RtQualityProfile.forSelection(5));
    }
}
