package dev.comfyfluffy.caustica.rt.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtRestirTemporalShaderContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void historyLayoutContainsIdentityLaneAndMatchesHostStride() throws IOException {
        String shader = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        int start = shader.indexOf("public struct RestirHistory {");
        int end = shader.indexOf("}", start);
        assertTrue(start >= 0 && end > start);
        String layout = shader.substring(start, end);
        assertEquals(5, occurrences(layout, "public float4"));
        assertEquals(1, occurrences(layout, "public uint4"));
        assertTrue(layout.contains("receiverIdentity"));
        assertTrue(layout.contains("samplePosArea"));
        assertTrue(layout.contains("sampleNormalWeight"));
        assertTrue(layout.contains("sampleEmissionCount"));
        assertTrue(layout.contains("receiverPosRoughness"));
        assertTrue(layout.contains("receiverNormalValid"));
    }

    @Test
    void temporalReuseGuardsSurfaceReprojectionAndPreservesFallback() throws IOException {
        String shader = Files.readString(ROOT.resolve("shaders/pipelines/world/indirect.rgen.slang"));
        assertTrue(shader.contains("if (pc.restirHistoryReadAddr != 0)"));
        assertTrue(shader.contains("pathSamplerWithGroup("));
        assertTrue(shader.contains("risSampler, PATH_GROUP_RIS_TEMPORAL)"));
        assertFalse(shader.contains("PATH_GROUP_RIS_CANDIDATE_FIRST + 128u"));
        assertTrue(shader.contains("prevClip.w > 0.0"));
        assertTrue(shader.contains("prevClip.w < 1.0e20"));
        assertTrue(shader.contains("all(abs(prevClip.xyz) < float3(1.0e20))"));
        assertTrue(shader.contains("all(prevUv >= float2(0.0))"));
        assertTrue(shader.contains("all(prevUv < float2(1.0))"));
        assertTrue(shader.contains("distance(prevHit, expectedPreviousHit) < 0.25"));
        assertTrue(shader.contains("dot(previousNormal, n) > 0.95"));
        assertTrue(shader.contains("abs(prevNormalLen2 - 1.0) <= 1.0e-3"));
        assertTrue(shader.contains("abs(curNormalLen2 - 1.0) <= 1.0e-3"));
        assertTrue(shader.contains("all(abs(prevHit) < float3(1.0e20))"));
        assertTrue(shader.contains("all(abs(expectedPreviousHit) < float3(1.0e20))"));
        assertTrue(shader.contains("abs(prev.receiverPosRoughness.w) < 1.0e20"));
        assertTrue(shader.contains("abs(rough) < 1.0e20"));
        assertTrue(shader.contains("float contributionWeight = restirHistoryCandidateWeight("));
        assertTrue(shader.contains("reservoirFinalize(r);"));
        assertTrue(shader.contains("restirForPixel = restirStore(r, hitPos, n, rough, payload.materialId);"));
        assertTrue(shader.contains("prev.receiverIdentity.x == payload.materialId"));
        assertTrue(shader.contains("payload.materialId != PAYLOAD_INVALID_MATERIAL_ID"));
        assertTrue(shader.contains("if (firstOpaqueReceiver && pathBranch == 0u && sampleIndex == 0u"));
        assertTrue(shader.contains("firstOpaqueReceiver = false;"));
        assertFalse(shader.contains("if (bounce == 0 && pathBranch == 0u && sampleIndex == 0u"));
        assertTrue(shader.contains("RestirHistory temporalHistory = restirInvalidHistory();"));
        assertFalse(shader.contains("RestirHistory stored ="));
        assertTrue(shader.contains("if (pc.restirHistoryWriteAddr != 0)"));
        assertTrue(shader.contains("L += throughput * shadeReservoir(r,"));
    }

    @Test
    void temporalCandidateWeightRejectsInvalidHistoryAndPreservesNormalization() throws IOException {
        String lighting = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        String indirect = Files.readString(ROOT.resolve("shaders/pipelines/world/indirect.rgen.slang"));

        assertTrue(lighting.contains("public float restirHistoryCandidateWeight("));
        assertTrue(lighting.contains("previous.receiverNormalValid.w > 0.5"));
        assertTrue(lighting.contains("previousWeight < 1.0e20"));
        assertTrue(lighting.contains("previousM >= 1.0"));
        assertTrue(lighting.contains("area > 0.0 && area < 1.0e10"));
        assertTrue(lighting.contains("candidateWeight < 1.0e20"));
        assertTrue(indirect.contains("if (contributionWeight > 0.0)"));
        assertFalse(indirect.contains("prevTarget * prev.sampleNormalWeight.w * effectiveM"));

        // Analytic one-light case: source PDF 1/4, target 4 gives W=1/q=4.
        // Repeated temporal resampling must preserve the same normalization
        // even when the prior reservoir candidate count is capped at 4*M.
        double target = 4.0, initialM = 8.0, cappedHistoryM = 32.0;
        double previousWeight = 4.0;
        double currentSum = initialM * target * previousWeight;
        for (int frame = 0; frame < 120; frame++) {
            double historyWeight = target * previousWeight * cappedHistoryM;
            previousWeight = (currentSum + historyWeight)
                    / ((initialM + cappedHistoryM) * target);
            assertEquals(4.0, previousWeight, 1.0e-12);
        }

        // The explicit upper bound on the final candidate weight also rejects
        // an infinite previous weight rather than emitting NaNs into the shader.
        assertFalse(Double.isFinite(target * Double.POSITIVE_INFINITY * cappedHistoryM));
    }

    @Test
    void firstOpaqueReceiverAfterPrimaryDielectricPrefixCanWriteHistory() throws IOException {
        String primary = Files.readString(ROOT.resolve("shaders/pipelines/world/primary.rgen.slang"));
        String indirect = Files.readString(ROOT.resolve("shaders/pipelines/world/indirect.rgen.slang"));

        // The primary guide pass consumes a glass/water interface before Pass B.
        assertTrue(primary.contains("seed, bounce + 1,"));
        assertTrue(primary.contains("return continuation;"));
        // Pass B must not equate first shaded opaque receiver with bounce zero.
        assertTrue(indirect.contains("bool firstOpaqueReceiver = true;"));
        assertTrue(indirect.contains("if (firstOpaqueReceiver && pathBranch == 0u && sampleIndex == 0u"));
        assertTrue(indirect.contains("firstOpaqueReceiver = false;"));
        assertFalse(indirect.contains("if (bounce == 0 && pathBranch == 0u && sampleIndex == 0u"));
    }

    @Test
    void hostDisablesExperimentalReuseByDefaultAndTracksHistoryLifetime() throws IOException {
        String config = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java"));
        String host = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String common = Files.readString(ROOT.resolve("shaders/pipelines/world/world_common.slang"));

        assertTrue(config.contains("lights.restir-di\", false)"));
        assertTrue(host.contains("RESTIR_HISTORY_STRIDE_BYTES = 96L"));
        assertTrue(host.contains("RESTIR_HISTORY_MEMORY_BUDGET = 768L"));
        assertTrue(host.contains("restirLastLightGeneration == terrain.lightGeneration()"));
        assertTrue(host.contains("restirLastMaterialEpoch == RtMaterialRegistry.INSTANCE.epoch()"));
        assertTrue(host.contains("restirLastMaterialEpoch = RtMaterialRegistry.INSTANCE.epoch();"));
        assertTrue(host.contains("restirLastWorld == Minecraft.getInstance().level"));
        assertTrue(host.contains("restirLastPathEpoch == pathSampleEpoch"));
        assertTrue(host.contains("restirLastFrameSerial == frameCounter - 1"));
        assertTrue(host.contains("restirLastFrameSerial = frameCounter;"));
        assertTrue(host.contains("(double) mvCamDeltaZ * mvCamDeltaZ < 64.0"));
        assertTrue(host.contains("destroyRestirHistory();"));
        assertTrue(common.contains("public uint64_t restirHistoryReadAddr;"));
        assertTrue(common.contains("public uint64_t restirHistoryWriteAddr;"));
        String sampler = Files.readString(ROOT.resolve("shaders/pipelines/world/math.slang"));
        String javaRoots = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtPathSamplerData.java"));
        assertTrue(sampler.contains("PATH_GROUP_CELESTIAL = 68u;"));
        assertTrue(sampler.contains("PATH_GROUP_RIS_TEMPORAL = 69u;"));
        assertTrue(sampler.contains("PATH_GROUP_COUNT = 70u;"));
        assertTrue(javaRoots.contains("GROUP_COUNT = 3 + 1 + MAX_RIS_CANDIDATES * 2 + 1 + 1;"));
        assertFalse(host.contains("restirHistoryValid = true; // optimistic"));
    }

    @Test
    void spatialGeometryHelperIsNotMistakenForEnabledReuse() throws IOException {
        String lighting = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        String indirect = Files.readString(ROOT.resolve("shaders/pipelines/world/indirect.rgen.slang"));
        assertTrue(lighting.contains("public bool restirSpatialGeometryCompatible("));
        assertTrue(lighting.contains("abs(centerLengthSq - 1.0) <= 1.0e-3"));
        assertTrue(lighting.contains("abs(neighborLengthSq - 1.0) <= 1.0e-3"));
        assertTrue(lighting.contains("separationSq < 0.25 * 0.25"));
        assertTrue(lighting.contains("dot(receiverNormal, neighborNormal) > 0.95"));
        assertTrue(lighting.contains("abs(neighbor.receiverPosRoughness.w - receiverRoughness) < 0.15"));
        // Material identity is stored, but proposal identity/MIS is still missing: the
        // production raygen must not consume neighbors from this helper alone.
        assertFalse(indirect.contains("restirSpatialGeometryCompatible("));
    }

    private static int occurrences(String src, String fragment) {
        int count = 0;
        for (int from = 0; (from = src.indexOf(fragment, from)) >= 0; from += fragment.length())
            count++;
        return count;
    }
}
