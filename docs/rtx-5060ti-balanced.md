# RTX 5060 Ti 16 GB — balanced rendering

The optional RTX presets are a manual four-way selector in Video Settings: **Custom / Quality / Balanced / Max FPS**. They are tuned as starting points for an RTX 5060 Ti 16 GB, not automatic GPU detection or guaranteed frame-rate targets.

| Setting | Quality | Balanced | Max FPS |
| --- | --- | --- | --- |
| Path samples per pixel | 1 | 1 | 1 |
| Max path bounces | 3 | 2 | 1 |
| RIS emitter candidates | 8 | 4 | 2 |
| DLSS Ray Reconstruction | Quality (NGX quality 2) | Balanced (NGX quality 1) | Performance (NGX quality 0) |
| Particles, entity glow, water waves | On | On | Off |
| Post sharpen | Off | Off | Off |
| Experimental temporal ReSTIR | **Not enabled** | **Not enabled** | **Not enabled** |
| Neural Rendering and Frame Generation | **Not changed** | **Not changed** | **Not changed** |

## How to apply

1. Open **Options → Video Settings**, choose **RTX Preset → Balanced** to start, or **Quality** when image fidelity matters more than rendered FPS.
2. Reopen Video Settings after switching preset to refresh the individual quality-slider positions.
3. Test movement, foliage, reflective materials, transparent water, and high-emitter-count areas at your actual monitor resolution. Compare **rendered FPS** and frame time (not generated/display FPS).
4. If the base framerate is too low, first switch **DLSS Quality** to **Performance**. If that is insufficient, choose **Max FPS** or lower render distance. Do not compensate for consistently low base FPS with Multi Frame Generation alone.
5. To return to custom settings, select **Custom**. Within the same session the prior values are restored. After restarting with a saved preset, Custom restores the renderer defaults instead of a forgotten pre-preset snapshot.

The selector applies the preset when changed in Video Settings. If a preset-controlled option (SPP, bounces, RIS candidates, DLSS-RR quality, particles, water waves or sharpness) is edited manually, the configuration becomes **Custom** and the edited value is kept. Reopen Video Settings to refresh the displayed preset selection and individual sliders. The existing save path persists these values to `config/caustica.toml`. Editing only `performance.quality=true`, `performance.balanced=true` or `performance.enabled=true` in TOML does **not** synthesize the other values; use Video Settings or set each numeric option explicitly. Only one choice can be selected through the UI.

## Display resolution and VRAM

- **1080p:** begin with DLSS-RR Balanced; check fine details and anti-aliasing during camera motion.
- **1440p:** begin with Balanced, then try Performance if base FPS or latency is unsatisfactory.
- **4K:** expect a substantial tracing burden; prefer DLSS-RR Performance or Ultra Performance and lower view distance where necessary. Quality is scene-dependent.
- The **16 GB** framebuffer improves headroom for textures and acceleration structures, but does not make ray tracing compute-free. Avoid claiming a fixed FPS from GPU VRAM alone.

This standalone profile change does **not** include the experimental ReSTIR shader/payload/history changes. No ReSTIR setting is automatically enabled by any preset. GPU memory consumption still varies with internal render resolution, draw distance, world content, textures and acceleration structures.

## Validation checklist

Benchmark base rendered FPS and GPU frame time, GPU/CPU utilization, 1% lows, VRAM usage, latency with/without Reflex, and optional Frame Generation separately. Check for ghosting, temporal flicker, visual material mismatches, and shader errors. Neither CI compilation nor a green test suite is evidence of verified RTX 5060 Ti runtime performance.

## RTX 5060 Ti hardware acceptance before stable release

Record your display resolution, render distance, NVIDIA driver version, Caustica JAR commit/hash, active DLSS-RR mode and whether FG/NR are independently enabled. Use the **same world, viewpoint, weather/time and movement route** for all comparisons.

1. **Preset selector and image quality:** switch to Quality, reopen Video Settings, confirm 1 SPP / 3 bounces / 8 RIS / DLSS-RR Quality. Repeat for Balanced (1 / 2 / 4 / Balanced) and Max FPS (1 / 1 / 2 / Performance). Check water, particles, glow and foliage; visually compare reflections, shadows, temporal noise and flicker.
2. **Custom restoration:** record custom values for all preset-controlled settings. Switch Custom → Quality → Balanced → Max FPS → Custom **without restarting** and verify the original values return.
3. **Manual overrides:** apply Balanced, reopen settings, manually change bounces or DLSS-RR quality, then reopen again. Confirm the selector displays **Custom**, the new value persists, and other previous settings are not silently restored. Repeat for RIS candidates and particles/water toggles.
4. **TOML and restart:** close Video Settings and the game normally, inspect `config/caustica.toml`, then relaunch. Verify the selected preset and numeric settings persist. Selecting Custom after a *restart* restores renderer defaults rather than the prior session's transient snapshot; verify this documented behavior.
5. **Performance and stability:** record average **rendered** FPS, 1% low, median/95th percentile GPU frame time, CPU frame time and peak VRAM for each preset at 1080p or 1440p (plus your actual target resolution). Run at least one demanding scene with many lights, water and entities. Evaluate Frame Generation separately; never treat generated FPS as base render throughput.
6. **Safety checks:** inspect game logs for NGX/Vulkan errors, unexpected shader warnings or crashes and look for ghosting, shimmering, light leaks and unresponsive controls after changing modes.

Complete the code review, automated builds and safe PR integration before the final hardware session when those checks suffice. **Do not mark the application hardware-validated or publish a production JAR until the complete release candidate passes these on-device checks.** If a profile fails, fix the issue on a new scoped PR and repeat the impacted acceptance checks. Report measured results rather than assuming a fixed FPS advantage from 16 GB of VRAM.
