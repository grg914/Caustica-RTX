# Experimental ReSTIR temporal selection target: full-support prototype

This patch is stacked on [the statistical audit](restir-temporal-statistical-audit.md). It is **not a stable renderer or production-release authorization**.

## Problem

When the previous frame's unshadowed target for a light is zero, its sample has zero RIS selection probability. If the same light becomes important at the current receiver, the old reservoir cannot represent its contribution; accounting for the historical sample count can produce a negative bias. The exact three-emitter oracle detected 2.337375 versus a current-frame reference of 2.775 for capped historical M=2.

## Prototype correction

For the **optional temporal-history first receiver only**, use a strictly positive selection target:

`selectionTarget = max(physicalUnshadowedTarget, 1e-3)`

Use that target for the initial reservoir's candidate weights and for the historical sample's re-evaluation at the current receiver. Normalize reservoir weights using this same selection target, but **keep the actual RGB BRDF contribution and traced visibility unchanged** in `shadeReservoir`. The ordinary non-temporal RIS path retains its original target. Temporal reuse and spatial reuse remain default-OFF and disabled respectively.

A strictly positive target, together with a proposal PDF positive over the physical integrand's support, is a necessary condition for unbiased RIS integration. See [Wyman et al., *A Gentle Introduction to ReSTIR*](https://intro-to-restir.cwyman.org/presentations/2023ReSTIR_Course_Notes.pdf), Section 3, and [Lin et al., *Generalized Resampled Importance Sampling*](https://cwyman.org/papers/sig22_foundationsOfReSTIR.pdf). This **does not prove** that Caustica's real multi-frame estimator is unbiased: correlated reuse, changing emitter geometry/support, light-PDF evaluation, finite-precision/culling, reprojected receivers and visibility may still introduce bias.

## Automated acceptance

The independent finite-state test `RtRestirPositiveSupportOracleTest` enumerates two old and two new stratified local/global emitter proposals, their selected survivors, different proposal PDFs, and the real RGB-luminance proxy after visibility. It verifies exactly:

- The previous *physical* target may be zero while the current physical target is positive.
- Temporal-history M=1, 2 or 4 and selection floors between `1e-6` and `1` preserve the current integral of 2.775 in the enumerated example.
- Zero physical radiance or zero visibility still produces zero output; the floor is not added to the displayed light.
- Old-frame physical radiance may be entirely zero without making the new frame lose its current contribution.
- The floor applies only to the history-writing base receiver and history reweighting, never to ordinary RIS, glow or HDR outputs.

## Outstanding risks

In the finite-state three-emitter oracle with a formerly zero-target emitter and two historical candidates, the fixed `1e-3` floor restores the mean of 2.775 but yields an estimator variance of approximately **980.74**. A larger illustrative `0.1` floor reduces that variance to approximately **12.92** while retaining the same mean. The fresh-only RIS comparison has variance approximately **3.94**. These numbers describe a mathematical toy model, **not measured GPU FPS or real-image noise**.

The absolute floor `1e-3` is a provisional numerical choice. A small floor can produce **very high variance** when a previously insignificant light becomes important. It may increase histories of zero-contribution samples and affect path time. Do not characterize this as a complete fix for flicker, ghosting, light additions/removals or biased multi-frame reuse. A real RTX/Vulkan acceptance run, frame-variance/1% lows, denoiser tests and a stronger multi-frame oracle remain required. Do not fuse this experimental PR into stable `main` without demonstrating appropriate behavior and preserving all upstream ReSTIR dependencies.
