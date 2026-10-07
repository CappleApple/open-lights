# 1.8.3 verification

Validated on 2026-10-06 using packaged JARs in disposable instances. Clients ran hidden and muted with mouse capture disabled. The reported Test instance initially used the final 1.8.2 Forge artifact; its night/DH settings informed the new fixture.

## Artifacts and builds

| Build | Runtime | Tests |
| --- | --- | --- |
| Forge 1.20.1 / Java 17 | Forge 47.4.23; compilation targets 47.4.10 | 96 passed |
| NeoForge 1.21.1 / Java 21 | NeoForge 21.1.248 | 96 passed |

Both builds passed `verifyModJar`. Neither runtime JAR contains QA or DH API classes. Both declare version 1.8.3. The sky-palette test checks native sky coordinates and RGBA preservation across every block-light column. Fresh dedicated-server startup, restart and clean shutdown passed on both final artifacts without DH or a separate DH API installed; copied server JAR hashes match the artifacts below.

```text
openlights-1.20.1-1.8.3.jar
489df3751c52edb3f46c48df585c31b398cd4f78f828eb24172dd3a5beb3006d

openlights-neoforge-1.21.1-1.8.3.jar
1ba8bc25831e6b98ad2351845014f48e2b138abb9d6ccbbd12d3e88fff40035a
```

## Ambient transition regression

The earlier [1.8.2 checks](verification-1.8.2.md) covered LOD depth, beam clipping, overlap ownership and lifecycle behavior. They did not compare contiguous native/LOD terrain through DH's fade band. DH blends already-lit LOD colors into the neutral native scene before Open Lights' final cached-sky multiplication; that multiplication also darkened the blended LOD contribution.

The new fixture uses Distant Horizons 3.3.3, contiguous grass, sand, snow, dirt, water and foliage, gamma 1, native and LOD depth ownership, and DH's actual fade distances. DH brightness/saturation remain 1; vanilla fog is disabled and DH fog retains its default distance and height settings. It compares cached lighting against a disabled control, first using archived exact 1.8.2 artifacts and then the final 1.8.3 artifacts.

The Forge 1.8.2 baseline reproduced a visible dark band under `DOUBLE_PASS`, with actual fade bounds of 41.381–52.417 blocks, 19,975 native fade pixels and 20,375 LOD pixels. Cached/disabled grass luminance ratios were 0.634 before the fade, 0.286 inside it and 0.212 beyond it; LOD terrain remained at 0.998. Mean absolute RGB error in the fade band was 0.105704 on a normalized 0–1 scale. Both controls were inspected.

The final Forge artifact passes the strict night comparison with both live-verified `DOUBLE_PASS` and `NONE`. Native sand/grass/dirt before the fade match the disabled control exactly. With no fade, all native sand/grass/dirt groups match exactly. Under `DOUBLE_PASS`, fade/after-fade mean absolute RGB differences are 0.00103–0.00143 for grass and 0.00105–0.00140 for sand. The cached scene-to-final difference is zero in these source-free captures, and the original and copied lightmap both use linear filtering. Final cached/control captures were inspected and the dark band is gone.

Forge's expanded 40-capture suite also passes: all three fade modes by day/night, GI off/on, DH fog enabled/disabled, additive controls, DH disable/restore and a 1600×500 view at 15 native chunks. GI-on captures populated 125 nearby probes and up to 729 far probes. The wide view retained 348 native fade-band pixels and 402 LOD pixels at actual fade distances of approximately 151–191 blocks; the strict wide-band material comparison covers grass, while the normal view covers all six material groups overall. The largest eligible mean absolute RGB difference is 0.00527345 on grass LOD in the wide daytime view; the largest native-only difference is 0.00298030, below the 0.02 tolerance. The source-free scene-to-final difference remains exactly zero. Wide day/night captures were inspected.

NeoForge passes the same 40 logical controls, retained as a 30-capture run and a 10-capture continuation. The initial wide-view setup changed the local render distance without broadcasting it to the integrated server, so the server still sent four chunks and the fixture correctly failed its native-fade coverage assertion. The corrected QA helper broadcasts the setting and asserts configured, effective, requested and server render distance, camera height and rotation. The final wide view has all four distances at 15 chunks, 348 native fade pixels and 342/369 LOD pixels by night/day. The maximum native RGB difference is 0.00237587 and the maximum LOD difference is 0.00600429, below 0.02; all 330 eligible cached scene-to-final measurements are exactly zero. Six GI-on captures each populate 125 nearby probes. Native and wide day/night cached/control images were inspected; the dark band is gone.

## Retained rendering and lifecycle checks

All rows below passed against the final artifact hashes above. The default-renderer rows include separate depth/beam and colored-light style clients.

| Loader | Distant Horizons | Renderer / shader pack | Result |
| --- | --- | --- | --- |
| Forge 1.20.1 | 3.3.3 | Default renderer; depth, LOD occlusion and style | Passed |
| NeoForge 1.21.1 | 3.3.3 | Default renderer; depth, LOD occlusion and style | Passed |
| Forge 1.20.1 | 3.3.3 | Embeddium 0.3.31 + Oculus 1.8.0; shaders off | Passed |
| NeoForge 1.21.1 | 3.3.3 | Sodium 0.8.13 + Iris 1.8.14-beta.1; shaders off | Passed |
| Forge 1.20.1 | 3.3.3 | Embeddium/Oculus; Complementary Reimagined r5.9.3 active | Passed |
| NeoForge 1.21.1 | 3.3.3 | Sodium/Iris; Complementary Reimagined r5.9.3 active | Passed |
| Forge 1.20.1 | 2.4.5-b | Default renderer | Passed |
| NeoForge 1.21.1 | 2.4.5-b | Default renderer | Passed |
| Forge 1.20.1 | Absent | Default renderer; full suite | Passed |
| NeoForge 1.21.1 | Absent | Default renderer; style suite | Passed |

DH captures pass far-plane receiver reconstruction, native opaque-depth priority, API lighting, additive mode, reload, resize and DH disable/restore. Every measured native-depth ownership violation count is zero. The nearby LOD-only wall retains 74,834 receiver pixels on Forge and 75,021 on NeoForge; enabling a beam entirely behind it changes mean RGB by 0.000451 and 0.000691 respectively, below the 0.005 tolerance. Inspected beam and resized captures show no earlier thin-LOD blue gaps or near-wall shading bands.

Active Complementary and legacy DH runs use forward depth; current DH with shaders off uses reverse depth. Active packs correctly disable cached replacement and retain additive lighting. DH-installed style checks pass radial falloff, finite-wall shadows, tinted glass, colored item/empty-hand bindings, Minecraft style and settings. Representative block-light and hand captures were inspected on both loaders.

Without DH or its API on the launch classpath, Forge's full suite passes API lights, media, profiles, emission, hand lightmap restoration, cached GI/source removal, reload and idle-cache checks. NeoForge's style suite passes. Fifteen final-artifact client executions exit successfully; the additional NeoForge 30-capture prefix retains its QA setup failure separately rather than being counted as a successful client. Copied runtime hashes match every final artifact. Raw logs, screenshots, exit records and the split-proof audit remain under ignored `.research/`, with the aggregate record in `dh-1.8.3-evidence.json`.

## Scope

With DH installed, cached mode preserves native per-vertex skylight, ambient occlusion and fog before DH fading. Cached block lighting remains a separate addition; API/GI use bounded reflectance recovered from the ambient-lit scene. That estimate remains approximate on translucent/fogged or mixed native/LOD pixels. DH's material averages, geometry, face shading and fog can naturally differ from normal chunks. Shader packs retain additive Open Lights rendering.

The correction adds a 16×16 RGBA8 sky palette, updated only when its sixteen source colors change. It restores linear filtering after every upload and uses native lightmap coordinates for reflectance normalization. Unchanged palette checks allocate nothing. Existing full-resolution receiver-buffer costs from 1.8.2 remain; these checks do not establish whole-pipeline performance.
