# 1.8.1 backport validation

Validation uses packaged release JARs in disposable instances. Clients run hidden, with master volume zero and a QA mixin that cancels mouse grabs. QA helpers are separate from release artifacts. User instances are not modified.

## Completed checks

- Forge 1.20.1 builds independently with Java 17 and passes 95 tests. The parent TaCZ integration passes 13 tests.
- NeoForge 1.21.1 builds with Java 21 and passes 95 tests.
- Both runtime JARs exclude QA code. Forge includes its production Mixin refmap.
- Fresh Forge client configuration starts with GI disabled; direct cached lighting remains enabled. Explicit GI-on tracing and cached source removal pass.
- Packaged Forge fixtures cover both styles, aggregation, source colors and emissive masks, translucent filtering, colored hands/items, overlapping-color contrast, ongoing edits/movement, influence culling, exposure/cache controls and update presets.
- The native/API suite covers point/spot/area lights, toggles, dyes/range changes, profile synchronization/reload/removal, beam dust, occluder updates, shadow-slot reuse/resizing, water/ice, GI disable/removal, idle caches and clock rollback.
- Distant point/spot/area lighting, optional viewing caps, camera boundary crossings and navigation through Options > Video Settings pass.
- Dedicated Forge and NeoForge servers start and restart cleanly.

## Dense-source observations

A closed room with 320 connected end rods forms 108 aggregate channels. Balanced placement/removal settled in approximately 254 ms, reusing 100 channels. Initial warmup was 4.71 seconds. At 1280×800 on an RTX 5070 Ti, the renderer averaged approximately 0.063 ms GPU with GI off and 0.069 ms with GI on. These are fixture measurements, not guarantees for other worlds or hardware.

Removing one of two sources removed exactly half its cached bounce energy in one tick with zero probe retraces, despite a 200-tick refresh-age limit. This does not promise constant-cost edits: cluster regrouping, changed occluders, field-map publication and cache-budget overflow can require more work.

## Visual review

Actual game captures were inspected for the dense room/settings, warm-white and red/blue checker textures, purple hand/held-item tint, native/API lights, grazing shadows, distant-light cap and low-altitude daylight. Checker contrast remains visible and overlap blends through mixed colors.

Local logs and captures are under the parent repository's ignored `.research/forge-1.8.1-*` directories. The helper source is in the parent `src/productionQa`; it is built with `-PproductionQa productionQaJar`.

## Compatibility and limits

Forge-without-mod and vanilla-server joins, reload/dimension changes, supported-server transitions and return joins pass. Connection fixtures wait one second for Forge disconnect cleanup before reconnecting. Embeddium 0.3.31 + Oculus 1.8.0 pass both styles with shaders disabled; Complementary Reimagined r5.9.3 passes active-pack additive/GI fallback. NeoForge 1.8.1 passes a fresh-config/dense fixture with Sodium 0.8.13 + Iris 1.8.14-beta.1, shaders disabled; its actual game captures were inspected. Explicit saved-GI preservation passes: a client TOML containing `[globalIllumination].enabled=true` is read as enabled despite the new default. A second dense fixture with 328 separate sources settles removal/placement in approximately 52–53 ms, reusing 327 channels; removal retraces zero channels. Client-only API beam dust passes on a server without Open Lights; local SpriteSet-based particles spawn without registry lookups. One initial isolated cold-server login timed out without a mod exception. Restarting it and two additional fresh-server joins passed; the timeout cause was not established. No full-modpack compatibility claim is made. The isolated fixtures do not cover arbitrary resource packs, custom renderers, OptiFine or Distant Horizons.

## Runtime artifacts

| Artifact | SHA-256 |
| --- | --- |
| `openlights-1.20.1-1.8.1.jar` | `a423caf758aa031f509e7e568a944172e158ac0183385901a418f50e5d356fe7` |
| `openlights-neoforge-1.21.1-1.8.1.jar` | `c3211ab51d52057b6650fc1ebfb562bd88cc9a23f9d6a1a04ee05c6883294c15` |

The final Forge artifact was repeated through the full native/API/GI suite, saved-GI configuration and client-only beam-dust checks. The release rebuild has byte-identical archive entries to the packaged Forge client artifact; only ZIP metadata differs. The NeoForge hash matches its packaged client. No development class directory is used by those launches.
