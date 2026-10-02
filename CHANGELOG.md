# Changelog

## 1.8.1 - 2026-10-02

### Changed

- Global illumination is disabled by default in new client configurations. Existing saved settings are preserved.
- Analytic lights use emitted influence bounds for selection and skip unrelated receiver and fog calculations.

### Added

- Forge 1.20.1 backport of the NeoForge client-only server fallback, per-source direct-light and GI caches, block exposure and source-cache memory controls.

### Fixed

- Lighting jobs repeatedly restarting during updates, slow dense-source edits, and removed emitters being restored by older scans.
- Washed-out textures and abrupt color or direction transitions between overlapping lights.
- Inconsistent texture bindings after cached hand-lighting uploads.
- Forge client-only connections rejecting servers without the beam-profile channel; local API beam particles no longer depend on absent-server registry entries.


## 1.6.0 - 2026-09-29

### Added

- Separate Minecraft 1.21.1 / NeoForge build with the lighting framework, native light items and blocks, beam profiles, colored hands and video settings.

### Fixed

- NeoForge: dark skylight patches and visible cache boundaries on terrain near the bottom of the world.
- NeoForge: blurred lighting-settings labels under the 1.21.1 menu background.

## 1.5.0 - 2026-09-29

### Added

- Open Lights block-lighting style with cached radial falloff, directional surface shading and direct voxel shadows. This is the default; Minecraft style preserves the previous propagated appearance.
- Economy, Balanced, Fast and Rapid update presets in Video Settings, controlling source discovery, snapshot capture, result application and texture uploads.

### Fixed

- First-person hands and held items retaining vanilla's uncolored lightmap while their surroundings use colored cached lighting.

## 1.4.1 - 2026-09-29

### Changed

- Aggregate light propagation and texture construction now run on a background worker. Finished lighting is applied in bounded batches, and outdated calculations are discarded when their inputs change.
- Cached light textures upload under separate count and time budgets. Growing the atlas retains the previous lighting until its replacement is ready.

### Added

- Snapshot capture and lighting upload budget controls in Video Settings → Open Lights, with snapshot memory and section-application limits in the client config.

## 1.4.0 - 2026-09-29

### Added

- Aggregate block lighting: nearby emitters increase lighting reach with diminishing returns while retaining their peak brightness. Extended light follows opaque barriers and texture-colored translucent paths, and can contribute to cached GI.
- Aggregate enable, maximum reach, and work-budget controls in Video Settings → Open Lights, plus client-config strength and memory limits.

## 1.3.3 - 2026-09-29

### Fixed

- Vanilla and modded block lighting losing detail outside the nearby cache, creating a lit region around the player and darkening distant lava. Lit sections now retain block-resolution colored lighting across loaded render distance.
- Repeated nearby updates delaying distant block-light cache work.
- The `maxRange` description confusing per-source reach with camera viewing distance.

## 1.3.2 - 2026-09-28

### Fixed

- Point, spot, and area lights fading out at 32 blocks from the camera. Direct lighting, shadows, and volumetrics now follow render distance while preserving each source's configured reach and falloff.
- Shadow banding on distant lit surfaces caused by camera-depth precision loss.

### Added

- Optional lighting viewing-distance control under vanilla Video Settings → Open Lights, with render distance as the default.

## 1.3.1 - 2026-09-28

### Fixed

- Cached world lighting switching back to vanilla during movement, teleporting, resource reloads, and render-distance changes. Replacement now starts on the first world frame and uses a coarse skylight estimate while missing samples fill in.

## 1.3.0 - 2026-09-28

### Added

- Block-light colors from emitting texture pixels and optional emissive masks, with resource-pack reload support and a separate color-propagation budget.
- Texture-derived glass, ice, and water filtering, including colored GI source visibility.
- A distant GI cache that follows render distance while sharing the existing tracing budget.

### Fixed

- Invalid lightmap reads caused by using a 1×1 replacement for Minecraft's 16×16 lightmap coordinates.
- Distant daytime terrain darkening when sky lighting blended with probes buried in solid blocks.
- Stale propagated colors after changing emitters or filters without changing their native light levels.
- Texture-binding state becoming stale when lighting textures are deleted during resource reload or target resizing.
- Corrupted lighting uploads caused by pixel offsets left by animated texture uploads.

## 1.2.1 - 2026-09-28

### Fixed

- Stop world-light sampling, GI retracing, and scene scanning after unchanged caches settle. Block, chunk, light, and tracing-setting changes refresh cached results under the existing work limits.
- Preserve cached lighting while changed probes wait for refresh, avoiding unnecessary warmup fallback.

### Added

- Optional `periodicCacheRefresh` safety sweeps for integrations that bypass ordinary Minecraft update notifications.

## 1.2.0 - 2026-09-28

### Added

- Cached single-bounce diffuse lighting from API lights and vanilla/modded block-light fields, with probe, source, distance, refresh, and CPU work limits.
- Experimental cached world-lightmap replacement with nearby and render-distance grids, warmup fallback, and automatic additive mode for active shader packs.
- Client controls for scene scanning, medium count, bloom, and disabling volumetric lighting.

### Changed

- Default world lighting to cached replacement when no shader pack is active and the cache is ready.

### Fixed

- Keep warmed scene and lighting caches across server time corrections.

## 1.1.1 - 2026-09-20

### Changed

- License Open Lights under CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers. The optical-material adapter retains its MIT license.
- Build Open Lights independently with its own Gradle wrapper and repository.

## 1.1.0 - 2026-09-18

### Added

- Datapack beam profiles and synchronized server defaults for handheld flashlights, placed spot lights, and integrations.
- Independent inner and outer beam color, intensity, angle, range, edge softness, and distance falloff.
- Per-beam fog strength and bounded dust particles that follow beam colors, with rate, size, lifetime, and drift controls.
- Live profile updates and removal through `/reload`.

### Fixed

- Striped self-shadowing on shallow-angle block surfaces.

## 1.0.0 - 2026-09-18

### Added

- Point, spot, and area lights, block-shape shadows, and volumetric beams for Forge 1.20.1.
- A handheld flashlight, placeable light blocks, and crafting recipes.
- Colored transmission through stained glass, panes, water, and ice; tinted glass blocks light.
- A public client API for persistent light handles and per-frame light collection.
- Rendering alongside Embeddium and Oculus shader packs.
