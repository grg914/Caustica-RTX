# Caustica RTX — DLSS 5 Native Readiness (2026-10-12)

Status: EXPERIMENTAL / NOT A FUNCTIONAL DLSS 5 RELEASE.

## Integration scope

Minecraft Java 26.2 → Vulkan → Caustica hardware path tracing → DLSS Ray Reconstruction → SDR display tonemapping → optional NVIDIA DLSS 5 3D-Guided Neural Rendering → Minecraft UI / FG / MFG / present.

The Java backend and native C ABI already exist. An NVIDIA-authorized DLSS-NR NGX SDK providing `nvsdk_ngx_helpers_dlssnr_vk.h` and a compatible runtime is **required** to compile and execute the actual neural rendering feature. The public SDK often lacks this header. The normal public CI build must continue to expose safe unavailable stubs, and RR/FG/Reflex must still work.

Never redistribute restricted NVIDIA SDK headers, libraries, feature DLLs, leaked runtimes, or model weights in this repository.

## Changes in this preparation branch

- Avoid caching the NR probe before Vulkan/NGX initialization.
- Force NVIDIA auto masking until a valid per-material Vulkan control-mask image is actually bound; hide the currently ineffective manual-mask UI option.
- Record successful first *native* NR evaluation distinctly from mere class/ABI discovery.
- Split Vulkan GPU timestamps into display and neuralRendering stages, and expose `frame.dlssNr` in frame diagnostics. Rendering/debug paths remain mutually exclusive.
- Do not change path tracing, RR, DLSS quality, texture quality, MFG multiplier, or any stable Manager installation flow.

## Acceptance gates (all must pass before merging as a production feature)

1. Authorized matching NGX SDK/runtime, license and NVIDIA application/project identity checked.
2. Compiled native shim contains the real NGX DLSSNR create and evaluate path (not stubs).
3. Actual RTX 50 / Vulkan driver capability probe reports NR supported.
4. Feature creation/evaluation succeeds in logs; `first native evaluation succeeded` is logged after successful return.
5. SDR image, render-resolution depth and motion-guide orientation/scaling are verified; no model evaluation in HDR/debug mode.
6. Changes between NR intensity 0 and 1 produce a repeatable material/lighting image difference without temporal ghosting.
7. No unmanaged NVIDIA SDK files shipped; unsupported GPUs and missing runtimes fail closed without killing RR/FG.
8. Benchmark GPU stage `neuralRendering`, average FPS *excluding* MFG, 1% low, frametime, VRAM and input latency with NR OFF vs ON in 1080p/1440p/4K.
9. Full Windows GPU, Gradle, CI, Java/native ABI and Minecraft world-load tests; validate resize, world switch, feature toggle, resource reload, and restart.
10. Existing RTX/ReSTIR draft branches remain isolated; do not merge into production V4.6 without review.

Read `docs/denoising.md` in NVIDIA's `nvpro-samples/vk_gltf_renderer` for the reference ordering and beta-SDK limitations.

## Diagnostics

Enable Caustica `frame-stats.enabled=true` in a test instance. The Minecraft profile produces `rt-frame-stats/frame.csv` with CPU-stage timings, and Caustica logs the non-blocking Vulkan GPU stages in periodic 120-frame aggregates. When NR is disabled, the `neuralRendering` timestamp segment is near zero (its label does not assert that NR executed). Only a successful NGX evaluation log and visible output change demonstrate real runtime execution.
