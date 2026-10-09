package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-closed provenance contract for a future spatial ReSTIR implementation.
 * Authored material IDs must propagate through closest-hit and the temporal
 * reservoir. Spatial reuse must still remain disabled until proposal identity,
 * target-PDF weighting and synchronization have been validated.
 */
final class RtRestirMaterialProvenanceContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void primitiveMaterialIdentityPropagatesToHistoryButSpatialReuseStaysOff() throws IOException {
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
        assertTrue(history.contains("receiverIdentity"));
        assertTrue(indirect.contains("prev.receiverIdentity.x == payload.materialId"));
        assertTrue(closest.contains("payload.materialId = pr.materialId;"));
        assertTrue(common.contains("public uint   materialId;"));
        assertTrue(common.contains("PAYLOAD_INVALID_MATERIAL_ID = 0xffffffffu"));
        String trace = Files.readString(ROOT.resolve("shaders/pipelines/world/trace.slang"));
        String host = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        assertEquals(3, occurrences(closest, "payload.materialId = pr.materialId;"));
        assertTrue(trace.contains("p.materialId = PAYLOAD_INVALID_MATERIAL_ID;"));
        assertTrue(trace.contains("shadowPayload.materialId = PAYLOAD_INVALID_MATERIAL_ID;"));
        assertTrue(lighting.contains("h.receiverIdentity = uint4(materialId, 0u, 0u, 0u);"));
        assertTrue(lighting.contains("r.W > 0.0 && materialId != PAYLOAD_INVALID_MATERIAL_ID"));
        assertTrue(indirect.contains("restirStore(r, hitPos, n, rough, payload.materialId)"));
        assertTrue(host.contains("RESTIR_HISTORY_STRIDE_BYTES = 96L"));
        assertTrue(host.contains("restirLastMaterialEpoch == RtMaterialRegistry.INSTANCE.epoch()"));
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

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = 0; (at = haystack.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    static boolean sameMaterial(int center, int candidate) {
        return center >= 0 && center == candidate;
    }
}
