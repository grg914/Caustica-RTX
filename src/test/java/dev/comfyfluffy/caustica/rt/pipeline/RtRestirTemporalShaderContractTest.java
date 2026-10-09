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
    void historyLayoutHasFiveFixedFloat4Lanes() throws IOException {
        String shader = Files.readString(ROOT.resolve("shaders/pipelines/world/lighting.slang"));
        int start = shader.indexOf("public struct RestirHistory {");
        int end = shader.indexOf("}", start);
        assertTrue(start >= 0 && end > start);
        String layout = shader.substring(start, end);
        assertEquals(5, occurrences(layout, "public float4"));
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
        assertTrue(shader.contains("distance(prevHit, expectedPreviousHit) < 0.25"));
        assertTrue(shader.contains("dot(previousNormal, n) > 0.95"));
        assertTrue(shader.contains("prevTarget * prev.sampleNormalWeight.w * effectiveM"));
        assertTrue(shader.contains("reservoirFinalize(r);"));
        assertTrue(shader.contains("restirForPixel = restirStore(r, hitPos, n, rough);"));
        assertTrue(shader.contains("RestirHistory temporalHistory = restirInvalidHistory();"));
        assertFalse(shader.contains("RestirHistory stored ="));
        assertTrue(shader.contains("if (pc.restirHistoryWriteAddr != 0)"));
        assertTrue(shader.contains("L += throughput * shadeReservoir(r,"));
    }

    @Test
    void hostDisablesExperimentalReuseByDefaultAndTracksHistoryLifetime() throws IOException {
        String config = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java"));
        String host = Files.readString(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));
        String common = Files.readString(ROOT.resolve("shaders/pipelines/world/world_common.slang"));

        assertTrue(config.contains("lights.restir-di\", false)"));
        assertTrue(host.contains("RESTIR_HISTORY_STRIDE_BYTES = 80L"));
        assertTrue(host.contains("RESTIR_HISTORY_MEMORY_BUDGET = 768L"));
        assertTrue(host.contains("restirLastLightGeneration == terrain.lightGeneration()"));
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

    private static int occurrences(String src, String fragment) {
        int count = 0;
        for (int from = 0; (from = src.indexOf(fragment, from)) >= 0; from += fragment.length())
            count++;
        return count;
    }
}
