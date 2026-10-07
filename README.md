# Open Lights

Point, spot, and rectangular area lighting for Minecraft Forge 1.20.1, with cached diffuse GI and experimental replacement of world lightmap shading. Open Lights includes a handheld flashlight, placeable light sources, and a client API for other mods.

The separate [NeoForge 1.21.1 build](neoforge/README.md) has its own sources, Java 21 build and verification checklist. See the [Forge changes since 1.1.1](docs/forge-changes-since-1.1.1.md) for the cumulative update notes.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or later in the 47.x series
- Java 17

Install on the client for automatic client-only lighting on servers without Open Lights. Colored world lighting, aggregation, optional GI, both lighting styles and video settings remain available. Install on both sides to also use flashlights, placeable lights and synchronized beam profiles. Native items are hidden and stale creative submissions are blocked on unsupported servers.

GI is disabled by default in new configurations. Cached direct lighting remains enabled. Set `[globalIllumination].enabled = true` to enable indirect bounce lighting; existing saved settings are preserved.

## Lights

| Item or block | Registry ID | Use |
| --- | --- | --- |
| Flashlight | `openlights:flashlight` | Hold in either hand; right-click to toggle. No battery is required. |
| Point Light | `openlights:point_light` | Emits in all directions. |
| Spot Light | `openlights:spot_light` | Emits a directional cone. |
| Area Light | `openlights:area_light` | Emits from a directional rectangular surface. |

The flashlight appears in Tools & Utilities. Placeable lights appear in Functional Blocks. All four have crafting recipes.

For a placed light, right-click with an empty hand to toggle it. Sneak-right-click with an empty hand to cycle its requested range through 8, 16, 24, 32, and 48 blocks. Apply a dye to change its color. Spot and area lights face the player when placed, including vertical placement. The client renderer's range limit still applies.

## Rendering and limits

- Block lighting defaults to the Open Lights appearance: cached radial falloff, directional surface shading and voxel shadows. Select Minecraft style in Video Settings for the previous propagated appearance.
- First-person hands and held items receive the cached environment's light color in either cached style.
- Nearby emissive blocks pool light to extend its reach with diminishing returns. A 3×3×3 glowstone cluster lights a larger area than one block without multiplying peak brightness. Aggregate lighting is cached, follows opaque barriers, and filters through translucent textures.
- Aggregate propagation and texture construction run on a background worker. The client captures material snapshots and uploads finished sections under separate budgets. Ordinary edits queue follow-up work without restarting the active calculation; unchanged source channels are reused, and only changed combined bricks upload.
- Point, spot, and area lights use six, one, and four shadow views respectively.
- Shadows follow cached block shapes. Entity models do not currently cast these shadows.
- Clear glass transmits light; stained glass colors transmitted light. Tinted glass blocks light. Water and ice have separate transmission properties.
- Volumetric lighting renders at a configurable resolution and sample count.
- Scene geometry and light probes refresh when their inputs change or the camera exposes new cells. Stationary lights reuse shadow maps until their definition or cached geometry changes.
- Diffuse GI uses cached six-ray probes near the camera and a coarse cache across render distance, updated under shared work and time limits. API lights and texture-colored block light can contribute.
- Emitting textures and optional emissive masks supply block-light colors. Translucent block textures filter light passing through them; resource-pack reloads refresh the caches.
- `lightingMode = "CACHED"` shades block light at one-block resolution across loaded render distance. Without DH, nearby and coarse distant caches supply skylight; with DH installed, native per-vertex skylight applies before terrain fading. Minecraft still computes gameplay light levels and the lightmap color palette. `ADDITIVE` keeps vanilla surface lighting.
- Cached world shading stays active during movement, reloads, and cache rebuilds. Without DH, a coarse skylight estimate covers missing samples while colored block lighting fills in.

The analytic renderer processes at most eight lights, four lights with shadows, and 32 transparent-medium regions per frame. Nearby lights receive priority. Point, spot, and area lights remain visible across Minecraft render distance; each source keeps its configured reach and falloff. Shadow geometry is cached around selected sources, alongside the nearby scene, under the existing scan budget. Cached block/sky lighting has separate budgets. Shadow and GI updates may lag moving lights or block changes while their caches process work.

Cached replacement is experimental: coarse distant GI can miss small bounce surfaces, and interpolation can leak illumination through thin walls. Transparency, fog, and custom fullbright materials are approximated by the final scene composite. Sky rendering is preserved; first-person hands retain their geometry and use cached environment colors. Minecraft still owns gameplay light propagation. See [lighting modes, performance settings, and limitations](docs/lighting.md).

Cached replacement was checked with the vanilla renderer. See [backport validation](docs/verification-1.8.1.md) for the existing packaged-client checks. Additive GI was checked with Embeddium 0.3.31 and Oculus 1.8.0 using Complementary Reimagined r5.9.3. A running shader pack automatically selects additive rendering, including GI; Open Lights does not replace the pack's material lighting. Other shader packs and OptiFine are untested. Added lighting is composited after the shader pack; it does not participate in the pack's exposure, TAA, material lighting, or internal bloom. The integration uses Forge hooks and the public Iris/Oculus API.

The optional [Distant Horizons adapter](docs/distant-horizons.md) preserves LOD terrain's native lighting and uses its depth for analytic receivers and volumetric clipping. Normal chunks retain the selected Open Lights style. No additional setting or API JAR is required. See [1.8.3 verification](docs/verification-1.8.3.md) for validation status and scope.

## Beam profiles

Server defaults and datapacks control handheld and placed spot-light beams. Inner and outer layers have independent colors, brightness, angles, ranges, edge softness, and falloff. Fog strength and dust are separate settings. [TACZ Open Lights](https://github.com/CappleApple/tacz-open-lights) applies the same profiles by attachment ID.

See the [profile guide and example datapack](docs/beam-profiles.md). The client retains its quality and performance limits.

## Configuration

Client settings are stored in `config/openlights-client.toml`.

In vanilla Video Settings, open **Open Lights** to choose the block-lighting style, viewing distance, aggregation, block exposure, source-cache memory and update budgets. **Lighting update speed** offers Economy, Balanced, Fast and Rapid presets; higher settings spend more client time preparing and applying worker results. Lighting distance follows Minecraft render distance by default; lower values limit analytic-light visibility without changing source reach, world lighting or GI. Menus supplied by other rendering mods may require editing the TOML settings instead.

| Key | Default | Range |
| --- | --- | --- |
| `enabled` | `true` | Boolean |
| `beamDust` | `true` | Boolean; local particle override |
| `maxLights` | `8` | 1–8 |
| `lightRenderDistanceChunks` | `0` | 0 follows Minecraft render distance; 1–64 sets a lower viewing limit in chunks |
| `maxShadowLights` | `4` | 0–4 |
| `shadowResolution` | `256` | 64–2048 pixels per face |
| `volumetricSteps` | `12` | 0–24; zero disables volumetrics |
| `renderScale` | `0.5` | 0.25–1.0 |
| `maxRange` | `24.0` | 1–32 blocks of reach from each analytic source; does not limit camera viewing distance or vanilla block lighting |
| `intensityMultiplier` | `1.0` | 0–8 |
| `periodicCacheRefresh` | `false` | Enable periodic safety rescans for mods that bypass ordinary update notifications |
| `mediumUpdateTicks` | `10` | 1–200 ticks between safety rescans when `periodicCacheRefresh` is enabled |

Replacement, GI, scene scanning, transmission, and bloom settings are covered in the [lighting configuration reference](docs/lighting.md), including example performance profiles.

## For developers

Use immutable light definitions with persistent handles or submit lights during `CollectLightsEvent`. Positions use world coordinates with double precision. See the [API docs](docs/api.md) for construction, ownership, and lifecycle rules.

## Building

From the repository root, using Java 17:

```powershell
.\gradlew.bat test build
```

The standalone mod JAR is written to `build/libs/`.

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](LICENSE), copyright 2026 CappleApple. The optical-material adapter retains its MIT license; see [third-party notices](THIRD_PARTY_NOTICES.md).
