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

The draft material-aware ReSTIR history stores 96 bytes per rendering pixel in each of two buffers and retains the existing aggregate 768 MiB allocation cap. Its memory usage depends on the **internal render resolution**, not simply the output resolution. It remains opt-in and is **not enabled by this preset** until Vulkan/RTX correctness, frame time, and visual parity are validated.

## Validation checklist

Benchmark base rendered FPS and GPU frame time, GPU/CPU utilization, 1% lows, VRAM usage, latency with/without Reflex, and optional Frame Generation separately. Check for ghosting, temporal flicker, visual material mismatches, and shader errors. Neither CI compilation nor a green test suite is evidence of verified RTX 5060 Ti runtime performance.
