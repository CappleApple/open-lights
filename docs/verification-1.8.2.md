# 1.8.2 verification

Checked on 2026-10-06 using packaged Open Lights JARs in disposable instances. Visual clients ran hidden and muted with mouse capture disabled. User modpacks and worlds were not modified.

Coverage note: these fixtures did not compare contiguous normal/LOD terrain through DH's fade band and missed cached ambient shading darkening that transition. See [1.8.3 verification](verification-1.8.3.md) for the correction and targeted regression status.

## Builds and isolation

| Build | Runtime used for client/server checks | Tests |
| --- | --- | --- |
| Forge 1.20.1 / Java 17 | Forge 47.4.23; compilation targets 47.4.10 | 95 passed |
| NeoForge 1.21.1 / Java 21 | NeoForge 21.1.248 | 95 passed |

Both builds passed runtime-JAR checks. The Forge JAR contains its refmap and reobfuscated mixins. Neither runtime JAR contains QA code or DH API classes. Fresh dedicated-server startup, restart and clean shutdown passed on both final artifacts without Distant Horizons or a separate DH API installed.

Final runtime SHA-256:

```text
openlights-1.20.1-1.8.2.jar
8b92b80722e33dfafa0eb89321256384a19ac624a1e1d5d2b605af1ea429f1c2

openlights-neoforge-1.21.1-1.8.2.jar
8c55786903492d5f7cc1d84fa3b155a0415cf99837da9deda127b0ce6c305576
```

## Client fixture

The DH fixture renders a colored skyline 384 blocks away with Minecraft's render distance set to four chunks. Terrain fog and distant generation are disabled to keep the measured skyline stable; active shader-pack atmosphere is retained. GPU readback checks LOD receiver ownership and true distance beyond the approximately 256-block native projection far plane. Every valid captured normal-chunk depth pixel must retain normal-chunk ownership, including coplanar LOD overlaps.

Captures compare native and cached LOD shading by day and night with API lights disabled, then exercise API lights, additive mode, resource reload, framebuffer resize and DH disable/restore. A separate 48-block LOD-only wall fixture drops its normal client chunks and checks a selected beam behind the wall against a disabled-beam control.

## Packaged clients

All rows below passed against the final artifact hashes above, including the thin-LOD foreground-fog correction.

| Loader | Distant Horizons | Graphics mods / shader pack | Result |
| --- | --- | --- | --- |
| Forge 1.20.1 | 3.3.3 | Default renderer | Passed, including the LOD-only occluder |
| NeoForge 1.21.1 | 3.3.3 | Default renderer | Passed, including the LOD-only occluder |
| Forge 1.20.1 | 3.3.3 | Embeddium 0.3.31 + Oculus 1.8.0; shaders disabled | Passed |
| NeoForge 1.21.1 | 3.3.3 | Sodium 0.8.13 + Iris 1.8.14-beta.1; shaders disabled | Passed |
| Forge 1.20.1 | 3.3.3 | Embeddium 0.3.31 + Oculus 1.8.0; Complementary Reimagined r5.9.3 active | Passed |
| NeoForge 1.21.1 | 3.3.3 | Sodium 0.8.13 + Iris 1.8.14-beta.1; Complementary Reimagined r5.9.3 active | Passed |
| Forge 1.20.1 | 2.4.5-b | Default renderer | Passed |
| NeoForge 1.21.1 | 2.4.5-b | Default renderer | Passed |
| Forge 1.20.1 | Absent | Default renderer; full suite | Passed |
| NeoForge 1.21.1 | Absent | Default renderer; style suite | Passed |

The final default-renderer beam captures each reconstructed approximately 197,400 LOD pixels beyond Minecraft's far plane. No normal-depth pixel switched to LOD ownership. Day/night images were inspected for coplanar overlap bands. Reload, resize and DH disable/restore passed. The LOD-only wall retained 74,937 receiver pixels on Forge and 74,834 on NeoForge; enabling the selected beam behind it changed mean RGB by less than 0.0006 on a normalized 0–1 scale, below the fixture's 0.005 tolerance.

GPU readback also found thin LOD pixels missing from all four reduced-resolution receiver samples, with colored pure-fog neighbors. Final beam/resized captures recover their foreground fog without the earlier blue gaps. Nearby surfaces and the LOD-only occluder retain strict clipping. This checks the fallback's actual rendering path rather than relying only on a depth assertion.

Active Complementary runs used DH's forward-depth path on both loaders, with Open Lights cached replacement disabled. Shader-disabled DH 3.3.3 runs used reverse depth. Both conventions passed the lifecycle and ownership checks, and representative active-pack beam/night captures were inspected.

DH 2.4.5-b used the legacy forward-depth fallback on both loaders and passed the same shading, ownership and lifecycle checks. Initial depth assertions alone did not detect nearby overlap bands or thin-LOD foreground-fog gaps; final coverage includes inspected captures and controlled DH enable/disable comparisons.

Without DH or its API on the launch classpath, Forge's full suite passed API lights, native blocks/handhelds, shadow reassignment/resizing, media, profile reload/fallback and cached GI lifecycle. NeoForge's style suite passed radial falloff, finite-wall shadows, colored item/hand bindings, settings/presets and idle checks. Representative no-DH captures were inspected. All ten packaged clients exited successfully.

## Scope

These are isolated rendering and lifecycle checks. They do not establish compatibility with arbitrary modpacks, every shader pack or OptiFine. LOD base lighting remains DH-owned; Open Lights' block-light and GI caches retain their loaded-chunk coverage. See [Distant Horizons behavior and limits](distant-horizons.md).

The receiver buffers now use 20 bytes per framebuffer pixel, previously four, and add one full-resolution metadata pass even without DH. This adds 15.625 MiB at the fixture's 1280×800 size and 10 MiB after resizing to 1024×640. DH reconstruction is skipped without a valid capture. Existing renderer GPU timings exclude the metadata pass; these checks do not establish its total performance cost.
