# NeoForge verification

The [Open Lights-style Sodium/Iris checks](renderer-compatibility.md) record the later renderer-specific runs, shader-toggle behavior and an unresolved transient terrain observation. A passing startup or cache assertion is not a blanket visual-compatibility claim.

## 1.8.1 GI default

Verified 2026-10-02: 95 tests, build, runtime JAR isolation and dedicated-server startup/restart pass. A fresh packaged client confirmed GI disabled before any fixture overrides, then rendered 320 connected sources with Sodium 0.8.13 + Iris 1.8.14-beta.1 (shaders disabled). Placement/removal settled in approximately 252 ms; idle cache, explicit GI enable and exposure/cache controls pass. Dense-room and menu captures were inspected. Tests ran hidden, muted and with mouse grabs canceled. This is a default-setting change; existing saved configuration values are retained.

## 1.8.0 source caching and dense emitters

Verified 2026-09-30 on Minecraft 1.21.1, NeoForge 21.1.248, Java 21 and an RTX 5070 Ti. Packaged clients used Sodium 0.8.13 + Iris 1.8.14-beta.1, shaders disabled, 1280×800, six-chunk render distance and Balanced budgets. Clients were hidden, muted and prevented from capturing the mouse. The user's modpack and worlds were not modified.

95 unit tests, build and runtime JAR isolation passed. New checks cover unchanged-channel reuse with zero material reads, removal without tracing, local occlusion invalidation including a newly opaque wall, cluster edits, memory overflow without lost light, weak overlaps preserving peak intensity, immutable snapshot copies, GI subtraction before the age gate, unclamped sums, movement, overflow storage and invalid GI inputs.

Two packaged fixtures compare 1.7.3 with 1.8.0. One has 328 separated end rods; the other has 320 adjacent end rods arranged in continuous wall rows, forming 108 local clusters. A single source is removed and replaced. Timings start after the client receives the block state and end when the radial field and its pending texture uploads are finished. They exclude network transit and do not wait for every native light-engine/mesh update. These are individual fixture runs, not latency percentiles or full-modpack benchmarks.

| Fixture | 1.7.3 removal / placement | 1.8.0 removal / placement | Estimated retained source channels |
| --- | --- | --- | --- |
| 328 separated emitters | 1250 / 1319 ms | 52 / 53 ms; later repetition 102 / 154 ms | 30.6 MiB |
| 320 connected emitters | 3378 / 3463 ms | 352 / 252 ms | 18.0 MiB |

Separated removal retraced zero channels and reused 327; placement traced one and reused 327. The connected-row edit changed cluster partitioning: removal traced nine and reused 100, placement traced eight and reused 100. Only changed combined bricks were uploaded. Both reached 100 idle ticks with no world/color/aggregate work or light uploads. Cold source discovery/material capture still took about 4.5–4.8 seconds; this change primarily improves warm edits.

Idle renderer GPU means were about 0.065 ms with GI disabled in both versions. With GI enabled, the connected fixture measured 0.0756 ms in both versions across 600 idle frames; the separated 1.8.0 fixture measured 0.0783 ms. These time Open Lights' rendering passes, not total Minecraft GPU/frame time. No additional per-source GPU textures, draws or shader loops were added.

The two-source GI fixture removed one emitting block with a 200-tick dirty-probe age and one-probe work limit. Cached energy fell from 5.573604 to 2.786802 within one or two ticks, with zero probe retraces; the other source remained. Before/after and immediate-removal captures were inspected. Exact independent removal applies while contributions have separate columns; overflow groups and changed aggregate clusters use the documented fallback behavior.

The packaged style, full renderer, contrast and continuous-update suites passed. Representative radial shadows, purple hand/item, API/GI and warm-white/red-blue texture captures were inspected. Brighter overlap retained texture contrast and smooth color blending. The connected and separated fixtures were also visually inspected with GI enabled. Menu checks changed exposure from 2× to 3× and source-cache capacity from 128 to 256 MiB; the final compact labels fit and the captured menu was inspected. Dedicated startup/restart passed with clean shutdowns.

Full Stoneblock gameplay and active shader packs were not retested. Different occluders, source reach, aggregation, cache overflow, initial discovery and running jobs can increase edit latency. Existing distant-room terrain observations remain outside this change. GI surface rays still run under client-thread budgets; radial tracing and brick baking run on the worker.

Local evidence: parent `.research/neoforge-dense-qa.py`, `.research/neoforge-1.8.0-server-qa.py`, `.research/neoforge-packaged-1.8.0-*.log`, and `run-1.8.0-*-compat/screenshots`. Development modes: `dense`, `sourceGi`, `style`, `full`, `contrast`, `progress`; the connected fixture adds `-Dopenlights.denseBars=true`.

Runtime artifact: `openlights-neoforge-1.21.1-1.8.0.jar`.

SHA-256: `74689153dde57add2cd636432553fcd85e6e55fa2fbc6ab8145373288fc8df43`.

## 1.7.3 visibility culling

Verified 2026-09-30 with the packaged runtime, Minecraft 1.21.1, NeoForge 21.1.248, Java 21 and an NVIDIA RTX 5070 Ti. The disposable clients used Sodium 0.8.13 + Iris 1.8.14-beta.1 with shaders disabled, hidden windows, muted audio and mouse capture disabled. The user's modpack installation and worlds were not modified.

The 83-test suite, build and runtime JAR isolation passed. New unit checks cover arbitrary cone orientations, all four area emitter offsets, profile layers wider than the spot definition, layer-specific reach, the shader's narrow-angle clamp, and off-screen point sources.

The packaged visibility fixture submitted twelve away-facing spot/area/profile lights plus an off-screen point source. Only the point consumed a light slot while looking forward, and its warm light remained visible on the room. Turning around restored the beams. The paired captures of an opaque wall with a bright red beam behind it and the same beam disabled were pixel-for-pixel identical. Moving the camera inside the beam volume restored its visible red illumination. All five captures were inspected, and an image check verified the identical wall pair and the warm/red receiver colors.

This does not test whole-light wall occlusion culling: the hidden beam remained selected, while the shader rejected its surface/fog contributions. Shadow geometry and cached world/GI coverage remain conservative; see [visibility culling](lighting.md#visibility-culling).

The packaged style, full renderer, contrast and continuous-update suites also passed. Representative purple hand/item, finite-wall shadow, point/spot/area, media, grazing-shadow, cached GI and red/blue overlap captures were inspected. The progress suite maintained publication through continuous edits and chunk-boundary crossings, removed its source contribution, then reached 100 idle ticks with one-section adoption/upload limits. These regressions use the same Sodium/Iris pair.

The eight-light API performance fixture measured stationary renderer CPU mean/p95 of 0.084/0.130 ms and GPU mean/p95 of 0.377/0.397 ms. Moving lights measured 0.138/0.241 ms CPU and 0.408/0.417 ms GPU. Each sample used 180 frames at 1280×800, half-resolution lighting, 256-pixel shadows, four shadow slots and 12 volumetric steps. These measure the renderer rather than total frame time; the fixture keeps all eight lights eligible and is not a worst-case culling benchmark.

Dedicated fresh startup and restart reached `Done` and shut down cleanly. Full Stoneblock gameplay and active shader packs were not tested for this version. The existing large-room distant-wall observation remains outside this change.

`VisibilityVisualHarness` runs with `-PqaMode=visibility`. Ignored local evidence: parent `.research/neoforge-visibility-qa.py`, `.research/visibility-image-check.py`, `.research/neoforge-1.7.3-server-qa.py` and `.research/neoforge-packaged-1.7.3-*.log`; captures in `run-1.7.3-visibility-compat/screenshots`.

Runtime artifact: `openlights-neoforge-1.21.1-1.7.3.jar`.

SHA-256: `e0ce6871fe62b141de2080a917e7588a542a21a3d4dd4359171b87e4570435ac`.

## 1.7.2 texture contrast and light blending

Verified 2026-09-30 with the packaged runtime, Minecraft 1.21.1, NeoForge 21.1.248, Java 21 and an NVIDIA RTX 5070 Ti. Clients ran hidden and muted with mouse capture disabled. Sodium 0.8.13 and Iris 1.8.14-beta.1 were copied from the user's Stoneblock mod directory into disposable instances; shaders were disabled. The user's installation and worlds were not modified.

| Check | Result |
| --- | --- |
| Unit tests, build and runtime JAR isolation | 77 tests passed; no QA classes in the runtime JAR |
| Same textured room with 1.7.1 and 1.7.2 | Warm/white and red/blue captures inspected; improved texture contrast and smooth overlapping colors |
| Single-source response, bounded overlap, opposing directions and source order | Unit and analytic-field integration assertions passed |
| Open Lights style | Radial falloff, finite-wall shadows, purple glass, hand/item tint, presets and idle passed; representative captures inspected |
| Continuous edits and chunk-boundary crossings | Cold startup, publication, removal, one-section adoption/upload budgets and 100 idle ticks passed |
| Full renderer suite | Point/spot/area API lights, native content, media, shadows, profiles, cached GI, intensity changes and removal passed; representative captures inspected |
| Dedicated server | Fresh startup and restart reached `Done` and shut down cleanly |

The paired room uses a checker texture plus diorite and deepslate surfaces. Two emitters sit in front of the wall so their unobstructed light overlaps. Measured from the unedited 1280×800 captures, warm/white checker contrast `(P90 - P10) / (P90 + P10)` increased from 0.134 to 0.571 in pixels x690–709, y290–339. Across the red/blue overlap, the largest adjacent-column change in `R / (R + B)` decreased from 0.0239 to 0.0031 after averaging rows 260–329 and applying a five-pixel moving average over columns 550–729. These are fixture-specific image measurements, not general photometric guarantees. The midpoint retained the same encoded intensity while changing from a single winner to a mixed color.

The eight-light API fixture measured stationary renderer CPU mean/p95 of 0.097/0.153 ms and GPU mean/p95 of 0.391/0.406 ms; moving lights measured 0.161/0.373 ms CPU and 0.426/0.431 ms GPU. Each sample used 180 frames, half-resolution lighting, 256-pixel shadows, four shadow slots and 12 volumetric steps. These timings cover the renderer, not total frame time or dense cached-block rebuilds. Blending adds worker-side contribution tracing and temporary accumulators; it adds no texture samples or render targets. Both paired room runs reached 100 idle ticks without further lighting calculations.

`ContrastVisualHarness` runs with `-PqaMode=contrast`. Local packaged launchers/logs are in the ignored parent `.research/neoforge-contrast-qa.py`, `.research/neoforge-1.7.2-server-qa.py` and `.research/neoforge-packaged-1.7.2-*.log`. Paired captures are in `run-1.7.1-contrast-compat/screenshots` and `run-1.7.2-contrast-compat/screenshots`; regression captures are in `run-1.7.2-style-compat/screenshots` and `run-1.7.2-full-compat/screenshots`.

Full Stoneblock gameplay and active shader packs were not tested for 1.7.2. The existing large-room distant-wall visibility observation remains outside this fix.

Runtime artifact: `openlights-neoforge-1.21.1-1.7.2.jar`.

SHA-256: `59269551cd9b8cc4cf3e3b1fa133d712f16e87af9d1a94e6f67803c1e0603975`.

## 1.7.1 calculation progress

Verified 2026-09-29 using the packaged runtime, Minecraft 1.21.1, NeoForge 21.1.248, Java 21 and an NVIDIA RTX 5070 Ti. Test clients ran hidden and muted with mouse capture disabled. The user's Stoneblock installation and worlds were not modified.

| Check | Result |
| --- | --- |
| Unit tests, build and runtime JAR isolation | 72 tests passed; no QA classes in the runtime JAR |
| Continuous updates, cold start, source removal and idle | Passed in the isolated vanilla-renderer fixture; captures inspected |
| Continuous updates plus crossing a chunk boundary every tick | Passed with Sodium 0.8.13 + Iris 1.8.14-beta.1, shaders disabled; one-section adoption/upload limits held |
| Same stress harness with the old 1.7.0 runtime | Reproduced ordinary-update cancellation; expected assertion failure |
| Open Lights style with that Sodium/Iris pair | Radial falloff, finite-wall shadows, purple filtering, colored hand/item bindings, update presets and idle assertions passed; representative captures inspected |
| Minecraft-style aggregation regression | Single/cluster reach, disable/restore, opaque wall, red glass, GI, source removal during calculation and budget assertions passed; representative captures inspected |
| Dedicated server | Fresh startup and restart reached `Done` and shut down cleanly |

The renderer-pair stress run published lighting through 356 update ticks, removed the source contribution while updates continued, then reached 100 idle ticks. Its discard counter stayed unchanged during ordinary updates. The large style fixture retained the previously documented distant-wall visibility issue; this change does not claim to fix terrain visibility. Full Stoneblock gameplay and active shader packs were not tested for 1.7.1.

`UpdateProgressHarness` runs with `-PqaMode=progress`. Local packaged launchers and logs are under the ignored parent `.research/neoforge-progress-qa.py`, `.research/neoforge-1.7.1-server-qa.py`, and `.research/neoforge-packaged-1.7.1-*.log`. Captures are under `run-1.7.1-progress-compat/screenshots`, `run-1.7.1-style-compat/screenshots`, and `run-1.7.1-aggregate/screenshots`.

Runtime artifact: `openlights-neoforge-1.21.1-1.7.1.jar`.

SHA-256: `79fc2073eba8d9c353affdac3a5538727e71822086492f582d15b4fe14bf2f6f`.

## 1.7.0 client-only mode

Verified 2026-09-29. The network/lifecycle regression run passed using the packaged 1.7.0 client with an empty NeoForge 21.1.248 server, an unmodified Minecraft 1.21.1 server, and a NeoForge server with Open Lights. These are disposable localhost servers, not the user's modpack or world.

The 67-test unit suite and build passed. Runtime assertions and inspected in-game captures passed for both block-light styles, colored glass transmission, GI and aggregation, hidden unsupported creative items, rejected stale creative item/drop submissions, resource reload, dimension changes, the client-only settings title, full-server content restoration and returning to client-only mode without stale profiles. Ordinary creative item submission also passed on the vanilla server. All test clients ran hidden and muted with mouse capture disabled; all test servers shut down cleanly.

The reload/reconnect test checks every facing state of all three light-block models. A GPU regression creates and uploads managed textures inside the lighting state guard, then verifies both restored bindings and subsequent managed texture binds. Inspected captures confirm restored light-block textures and intact settings text after fixing both regressions.

The regular `full` renderer suite and the active Iris/Sodium/Complementary `gi` suite also passed on the final 1.7.0 sources. Representative point/spot/area, native content, colored GI and shader-fallback captures were inspected. The broader matrix and performance samples below remain explicitly labeled as the 1.6.0 port baseline.

Client-only QA uses `ClientOnlyHarness` (`-PqaMode=clientOnly`). It expects an operator named `Dev` on a disposable server without Open Lights at port 25587 and one with Open Lights at port 25586. It creates a test room, switches dimensions and connects to both servers. The packaged vanilla run overrides the absent-server port to 25588. Do not point the fixture at an existing world.

Runtime artifact: `openlights-neoforge-1.21.1-1.7.0.jar`.

SHA-256: `43a97d279bc8fc51b138380faf7c86a8570ee75e072442b91a3ad32323577c64`.

## 1.6.0 port baseline

Verified 2026-09-29: Open Lights 1.6.0, Minecraft 1.21.1, NeoForge 21.1.248, Java 21 on Windows with an NVIDIA RTX 5070 Ti. The feature checks below passed. This covers Open Lights; the separate TaCZ integration remains Forge 1.20.1.

## Feature matrix

| Feature | Evidence | Result |
| --- | --- | --- |
| Worker math, cancellation, stale results, budgets, colors, profile codec/parser, API invariants | JUnit suite | 67 passed, no failures or skips |
| Registration, mixin targets, client and server class isolation | Real development and packaged startup/world join; dedicated server | Passed |
| Open Lights/Minecraft styles, finite-wall shadows, colored hands/items | Style suite and inspected framebuffer captures, repeated with packaged JAR | Passed |
| Noon skylight and low-altitude cache boundaries | Native/cached daylight comparisons; exposed-sky interpolation assertions | Passed after excluding below-build-height void samples |
| Texture emitter colors, translucent filtering, same-brightness edits, emissive masks, pack reload | Visual suite with actual texture overrides and inspected captures | Passed |
| Replacement ownership through movement, teleport, resize, reload and incomplete caches | 3,616 checked frames, including 1,708 incomplete-cache frames; first and settled captures | Passed |
| Distant point/spot/area lights, source-centered shadows, optional view cap | Range suite around 60 blocks from the camera, including crossing the former 32-block boundary | Passed |
| Vanilla Video Settings entry, style, distance, aggregation and saved budget controls | Actual menu navigation plus range/style/aggregate button assertions and captures | Passed |
| Single versus 27-source clusters, 30-block reach, bounded peak | Aggregate field assertions and inspected single/cluster captures | Passed |
| Aggregation disable/restore, wall/glass filtering, removal during calculation | Aggregate suite | Passed |
| Worker execution, stale-job cancellation, bounded adoption/upload | Aggregate suite, including two-section adoption and one-section upload caps | Passed |
| Idle caching | 100 unchanged ticks with no world/GI/scene/shadow work; packaged style run also observed 135 idle ticks | Passed |
| Persistent API create/update/close, frame-local submissions and light selection | Full and supplemental suites | Passed |
| Point/spot/area shadow views, slot reuse, resolution changes and grazing surfaces | Full suite assertions and paired captures | Passed |
| Native flashlight toggles, both hands, third person, component serialization | Full suite and native assertions; separate-server toggle synchronization | Passed |
| All placed lights: six facing directions, toggle, dye, range cycle, save/load, drops and recipes | Full suite and native server assertions | Passed |
| Layered profiles, defaults, login, reload, removal/fallback and beam dust | Full suite; separate-server login and reload payloads | Passed |
| Glass, tinted glass, water, ice and block-shape occlusion | Full/visual captures and material tests | Passed |
| API/block GI, intensity changes, source removal, clock rollback, disabled mode | Full cached-GI stages | Passed |
| Render scale, volumetrics, bloom, light/shadow/media budgets and periodic refresh | Supplemental runtime control sweep and inspected captures | Passed |
| GPU unpack state and texture ownership | GPU round trip with poisoned unpack offsets and buffer binding | Passed |
| Dimension changes, resource reload, logout and stale handles | Supplemental lifecycle assertions and captures | Passed |
| Active shader-pack fallback | Iris 1.8.12, Sodium 0.6.13, Complementary Reimagined 5.8.1; asserted active pack, additive mode, GI and idle state | Passed |
| Sodium without an active shader pack | Packaged style suite and inspected captures | Passed |
| Packaged multiplayer | Separate localhost client/server: login, item toggles, placed-light NBT updates, rendered lights, profile reload | Passed |
| Dedicated server persistence | Fresh start, clean shutdown, restart, retained changed color/disabled state, second clean shutdown | Passed |
| Distribution contents | JAR inspection: NeoForge metadata, current resources, no QA classes or old Forge metadata | Passed |

## Performance sample

Measured the Open Lights renderer at 1280×800, half-resolution lighting, 256-pixel shadow maps, 12 volumetric steps, eight lights (three point, three spot, two area), and four shadowed-light slots. Each sample contains 180 frames.

| Workload | CPU mean / p95 | GPU mean / p95 |
| --- | --- | --- |
| Stationary lights | 0.159 / 0.238 ms | 0.387 / 0.401 ms |
| Moving lights | 0.227 / 0.416 ms | 0.421 / 0.423 ms |

These measure the Open Lights renderer, not total game frame time. The separately measured world-cache CPU cost averaged approximately 0.006 ms per frame in this fixture. GI averaged 0.016 ms per tick stationary and 0.023 ms moving. Different worlds, hardware and settings can cost more.

In the packaged enclosed-room fixture, switching to Open Lights style settled in 128 ticks with Balanced and 25 ticks with Rapid. This includes source discovery, snapshot capture, worker execution and publication. It demonstrates faster updates, not an instant-update guarantee.

## Scope and reproduction

QA uses disposable worlds, muted master audio, a hidden window and a test-only mixin preventing mouse capture. Framebuffer screenshots were opened and inspected; success logs alone were not treated as visual evidence. The helper is a separate source set and is excluded from the runtime JAR.

From this directory with Java 21:

```powershell
.\gradlew.bat test build qaJar
.\gradlew.bat runQa -PqaMode=style
```

Run each mode separately: `style`, `contrast`, `visibility`, `full`, `visual`, `range`, `aggregate`, `extra`, `daylight`, `progress`. `gi` runs the focused GI/shader-fallback stages. Do not build while a development client is using the same class outputs. Local optional shader QA uses `-PqaShaders`, JARs in `.research/shader-mods`, and a configured active pack under `run-shaders`.

Packaged checks loaded the built runtime JAR from isolated `mods` directories with source class/resource outputs removed from the launcher classpath. The client helper was also a separate JAR. Dedicated servers loaded only the runtime mod. The NeoForge development launch runtime supplied Minecraft/loader libraries; an external launcher installation was not tested. The user's existing Forge Test instance and worlds were not modified.

The Video Settings button targets the vanilla menu. Sodium's replacement menu has no dedicated adapter; use `config/openlights-client.toml` there. The Sodium test opened the lighting screen directly and does not verify an entry in Sodium's menu. Other shader packs, GPUs, replacement menus and arbitrary modpacks are not covered by these results. Complementary emitted optional-uniform warnings for newer Minecraft biome/End features, but the tested shader pipeline remained active. Existing renderer approximations are listed in [lighting limitations](lighting.md#limitations-and-diagnostics).

Runtime artifact: `openlights-neoforge-1.21.1-1.6.0.jar`.

SHA-256: `4c229e0581dd34b93601bc50f2b41fdcdd61b13f417e86b2d27624df76877277`.
