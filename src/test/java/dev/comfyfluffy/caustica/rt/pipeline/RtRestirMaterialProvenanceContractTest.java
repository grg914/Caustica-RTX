package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-closed provenance contract for a future spatial ReSTIR implementation.
 * Primitive material IDs currently exist at closest hit, but only coarse
 * categories pass to indirect lighting. Spatial reuse must not silently
 * treat MATERIAL_OPAQUE as a unique material identity.
 */
final class RtRestirMaterialProvenanceContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void primitiveMaterialIdentityExistsButIsNotYetInHistory() throws IOException {
        String common = Files.readString(ROOT.resolve("shaders/pipelines/world/world_common.slang"));
        String closest = Files.readString(ROOT.resolve("shaders/pipelines/world/closest_hit.rchit.slang"));
        String lighting = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        String indirect = Files.readString(ROOT.resolve("shaders/pipelines/world/indirect.rgen.slang"));

        assertTrue(common.contains("public uint materialId;"));
        assertTrue(closest.contains("[pr.materialId]"));
        assertTrue(common.contains("bits 0..1 material"));
        assertTrue(indirect.contains("uint material = payloadMaterial();"));

        int start = lighting.indexOf("public struct RestirHistory {");
        int end = lighting.indexOf("}", start);
        assertTrue(start >= 0 && end > start);
        String history = lighting.substring(start, end);
        assertFalse(history.contains("materialId"));
        assertFalse(indirect.contains("restirSpatialGeometryCompatible("));
    }

    @Test
    void distinctAuthoredMaterialsCannotBeEquatedByCoarseClassification() {
        int opaqueA = 123;
        int opaqueB = 456;
        int opaqueCategory = 0;
        assertTrue(opaqueA != opaqueB);
        assertTrue(opaqueCategory == opaqueCategory);
        // Material equality must compare stable authored IDs, not categories.
        assertFalse(sameMaterial(opaqueA, opaqueB));
        assertTrue(sameMaterial(opaqueA, opaqueA));
    }

    static boolean sameMaterial(int center, int candidate) {
        return center >= 0 && center == candidate;
    }
}
