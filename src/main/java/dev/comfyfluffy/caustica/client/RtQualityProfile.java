package dev.comfyfluffy.caustica.client;

/**
 * Immutable fidelity/cost presets. Does not alter textures, view distance, DLSS-NR,
 * Frame Generation, Reflex or experimental ReSTIR.
 * 0 = Custom, 1 = Quality, 2 = Balanced, 3 = Max FPS, 4 = Ultra Quality.
 */
public record RtQualityProfile(
        int spp, int maxBounces, int risCandidates,
        boolean particles, boolean glow, boolean waterWaves,
        float sharpen, int dlssQuality) {
    /** Custom returns null; invalid option IDs fail closed. */
    public static RtQualityProfile forSelection(int id) {
        return switch (id) {
            case 0 -> null;
            case 1 -> new RtQualityProfile(1, 3, 8, true, true, true, 0.0f, 2);
            case 2 -> new RtQualityProfile(1, 2, 4, true, true, true, 0.0f, 1);
            case 3 -> new RtQualityProfile(1, 1, 2, false, false, false, 0.0f, 0);
            case 4 -> new RtQualityProfile(1, 4, 8, true, true, true, 0.0f, 2);
            default -> throw new IllegalArgumentException("Unsupported RTX preset: " + id);
        };
    }
}
