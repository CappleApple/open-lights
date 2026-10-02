# Sodium and Iris verification

## 1.7.3 visibility regression

Tested 2026-09-30 with the packaged runtime, Sodium 0.8.13 and Iris 1.8.14-beta.1, shaders disabled. Visibility, style, full renderer, contrast and continuous-update suites passed. Inspected captures confirmed off-screen sources illuminating visible receivers, restoration when turning toward culled beams, and a camera inside a beam. An opaque-wall/disabled-light capture pair was pixel-for-pixel identical. See [the 1.7.3 verification record](verification.md#173-visibility-culling) for scope, timing and evidence.

This verifies the isolated renderer pair, not full Stoneblock gameplay or active shader packs. Influence culling remains conservative for wall-hidden lights, shadow geometry and GI caches.

## 1.7.2 texture and overlap regression

Tested 2026-09-30 with the packaged 1.7.2 runtime, Sodium 0.8.13 and Iris 1.8.14-beta.1, shaders disabled. Paired in-game captures against 1.7.1 confirmed preserved texture contrast and smoother warm/white and red/blue transitions. Style, continuous-update and full renderer suites also passed; representative shadow, hand/item, API and GI captures were inspected. Clients ran hidden and muted without mouse capture.

See [the 1.7.2 verification record](verification.md#172-texture-contrast-and-light-blending) for image measurements, performance scope and evidence. This isolates the renderer pair; full Stoneblock gameplay and active shader packs were not tested. The existing distant-wall observation remains outside this change.

## 1.7.1 calculation regression

Tested 2026-09-29 with Sodium **0.8.13** and Iris **1.8.14-beta.1** for Minecraft 1.21.1, copied into disposable instances from the user's Stoneblock mod directory. Shaders were disabled and `blockLightStyle = "OPEN_LIGHTS"` remained selected.

The packaged 1.7.1 runtime passed continuous block updates, repeated chunk-boundary crossings, source removal and idle checks with one-section adoption/upload budgets. The same stress harness reproduced cancellation with the old 1.7.0 runtime. Separate style checks passed radial falloff, wall shadows, purple glass transmission, colored hand/item bindings, settings and idle assertions. In-game captures were inspected. See [the 1.7.1 verification record](verification.md#171-calculation-progress) for scope and evidence.

This tests the renderer pair in isolation, not the entire Stoneblock pack. Active shader packs were not retested for 1.7.1. The previously documented large-room distant-wall observation remained visible; it is not fixed by the scheduler changes.

## 1.7.0 renderer and lifecycle checks

Tested on 2026-09-29 with Open Lights 1.7.0, Minecraft 1.21.1, NeoForge 21.1.248, Java 21 and an NVIDIA RTX 5070 Ti. Tests used the packaged runtime JAR and kept `blockLightStyle = "OPEN_LIGHTS"` selected. No runtime changes were made for these tests.

| Client renderer | Observed behavior |
| --- | --- |
| Sodium 0.6.13, without Iris | Open Lights replacement, radial falloff, aggregation, wall shadows and colored hands/items passed. One transient missing-terrain observation remains noted below. |
| Sodium 0.6.13 + Iris 1.8.12, shaders disabled | Open Lights replacement and the same style checks passed. |
| Sodium 0.6.13 + Iris 1.8.12 + Complementary Reimagined 5.8.1 enabled | Additive lights/GI worked. Full Open Lights-style world replacement is **not active** with a shader pack. |

## Active shader packs

Iris owns world shading while a pack is enabled. Open Lights retains the selected style in configuration but switches to its additive path: point/spot/area lights and GI remain available, while world-light replacement, aggregate block lighting and Open Lights' first-person color replacement are bypassed.

The same client was tested with shaders enabled, disabled and re-enabled. Disabling shaders restored Open Lights-style replacement and populated aggregate caches without restarting; re-enabling restored the additive path. Keeping the style selected does not override shader-pack ownership.

This verifies one shader pack on the listed versions. It does not establish support for every pack, version or modpack.

## Test coverage and caveats

- Client-only connections to an empty NeoForge server; switching to a server with Open Lights and back.
- Resource reloads, dimension changes, server-content availability and profile reset.
- Radial falloff, finite-wall shadows, purple glass transmission, colored item/hand lightmap bindings, update presets and at least 100 idle ticks without cache work.
- Real in-game captures inspected in addition to runtime assertions. All clients ran hidden and muted, with mouse capture disabled.

The first Sodium-only multiplayer run showed missing terrain at a nearby chunk boundary after bulk fixture edits. Reconnecting cleared it. A repeat with the same runtime and two small-room control runs without Open Lights did not reproduce it. Its cause is unresolved; the passing lighting assertions are not evidence that this observation was fixed.

A separate large-room control without Open Lights also showed absent distant wall geometry at six-chunk render distance, before and after rebuilding terrain. That observation is distinct from the first run's nearby missing chunk and does not resolve its cause. The control asserted that all 27 source blocks were present.

Sodium's replacement Video Settings menu does not contain the Open Lights button. Edit `config/openlights-client.toml` for those settings. The tests of the Open Lights settings screen opened it directly and do not establish a Sodium menu integration.

Local screenshots and logs are retained under the ignored `.research/verification-1.7.0-openstyle/` directory. The runtime artifact remains the one identified in [verification.md](verification.md).
