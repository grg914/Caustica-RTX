# Temporal ReSTIR: proposal-PDF and support audit

**Scope:** experimental stacked PRs #10 → #15–#24. This does not authorize spatial reuse, a main-branch merge or an RTX production release.

The shader's `risInitial` stratifies candidates between a local grid-cell proposal and a global emitter proposal. Each candidate uses the balance-heuristic mixture PDF `q = alpha*qCell + (1-alpha)*qGlobal`, where `alpha` is the fraction of local candidates. The temporal offer weights a selected history sample using `pCurrent * WPrevious * cappedM`, while the new frame samples from the new proposal PDF. A material/proposal-cell match is a necessary *compatibility check*, not a proof of statistical unbiasedness.

## Deterministic finite-state oracle

`RtRestirTemporalProposalSupportOracleTest` enumerates all combinations of two old proposals, two current proposals, the old reservoir survivor and the current reservoir survivor over three emitters. Old and new local/global PDFs differ, and the direct-light visibility mask is evaluated only for the final survivor.

| Case | Direct reference | Temporal estimator (capped M=2) | Interpretation |
| --- | ---: | ---: | --- |
| Positive old target for all current contributors | 2.775 | 2.775 | Matches the exact integral for this controlled two-frame case |
| Incorrectly evaluate old target instead of current target | 2.775 | 4.225 | Regression that the current shader must not introduce |
| Emitter contributes zero in previous frame, positive in current frame | 2.775 | 2.337375 | **Known support-change bias**, even when proposal PDFs retain full support |
| Reject temporal history in that support-change case | 2.775 | 2.775 | Fresh-RIS reference avoids historical missing contribution |

The support-change example is a mathematical counterexample to the statement that proposal-cell, material and candidate-count checks alone make temporal reuse unbiased. A previously zero-weight emitter cannot survive the old reservoir, so assigning that history an effective sample count can underweight newly appearing contributions. Decreasing the history cap limits but does not eliminate this bias.

## Acceptance implications

- Retain current geometry, material, light-generation, proposal-cell and candidate-count rejection gates, and default-OFF experimental ReSTIR.
- Do not label the temporal estimator universally unbiased. A remedy needs additional mathematics for changing support, resampling distributions, history confidence or carefully bounded bias, **not** only stricter spatial proximity checks.
- The CPU oracle proves only its enumerated three-emitter mathematical cases. It cannot establish correct Vulkan execution, shader/compiler ABI, noise, ghosting, disocclusion or performance.
- Before experimental promotion: investigate temporal support changes; establish a reference estimator with known assumptions; test moving camera, material swaps, emissive animation, light insertion/removal, thin occluders, different RIS candidate counts and history epochs; compare rendered luminance bias/variance and ghosting against fresh RIS with DLSS-RR and FG separately; validate on the final RTX 5060 Ti build.
- Do not enable spatial reuse until proposal-family compatibility, target PDF/MIS weighting, visibility and GPU read/write synchronization are implemented and proven separately.
