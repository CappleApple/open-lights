# Open Lights

Point, spot, and rectangular area lighting for Minecraft Forge 1.20.1. Open Lights includes a handheld flashlight, placeable light sources, and a client API for other mods.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or later in the 47.x series
- Java 17

Install the mod on clients and the server. Rendering runs on clients; item and block states are synchronized by the server.

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

- Point, spot, and area lights use six, one, and four shadow views respectively.
- Shadows follow cached block shapes. Entity models do not currently cast these shadows.
- Clear glass transmits light; stained glass colors transmitted light. Tinted glass blocks light. Water and ice have separate transmission properties.
- Volumetric lighting renders at a configurable resolution and sample count.
- Scene geometry is cached and refreshed incrementally. Stationary lights reuse shadow maps until their definition or cached geometry changes.

The renderer currently processes at most eight lights, four lights with shadows, and 32 nearby transparent-medium regions per frame. Nearby lights receive priority. Sources and lit surfaces fade out at the edge of a 32-block camera-centered scene cache. Shadow updates may lag block changes while the scene cache processes its work budget.

Tested with Embeddium 0.3.31 and Oculus 1.8.0 using Complementary Reimagined r5.9.3. No manual Open Lights compatibility switch is needed. Other shader packs, OptiFine, and Distant Horizons are untested. Added lighting is composited after the shader pack; it does not participate in the pack's exposure, TAA, material lighting, or internal bloom. The integration uses Forge hooks and the public Iris/Oculus shadow-pass API.

## Beam profiles

Server defaults and datapacks control handheld and placed spot-light beams. Inner and outer layers have independent colors, brightness, angles, ranges, edge softness, and falloff. Fog strength and dust are separate settings. [TACZ Open Lights](https://github.com/CappleApple/tacz-open-lights) applies the same profiles by attachment ID.

See the [profile guide and example datapack](docs/beam-profiles.md). The client retains its quality and performance limits.

## Configuration

Client settings are stored in `config/openlights-client.toml`.

| Key | Default | Range |
| --- | --- | --- |
| `enabled` | `true` | Boolean |
| `beamDust` | `true` | Boolean; local particle override |
| `maxLights` | `8` | 1–8 |
| `maxShadowLights` | `4` | 0–4 |
| `shadowResolution` | `256` | 64–2048 pixels per face |
| `volumetricSteps` | `12` | 4–24 |
| `renderScale` | `0.5` | 0.25–1.0 |
| `maxRange` | `24.0` | 1–32 blocks |
| `intensityMultiplier` | `1.0` | 0–8 |
| `mediumUpdateTicks` | `10` | 1–200 ticks |

## For developers

Use immutable light definitions with persistent handles or submit lights during `CollectLightsEvent`. Positions use world coordinates with double precision. See the [API contract](docs/api.md) for construction, ownership, and lifecycle rules.

## Building

From the repository root, using Java 17:

```powershell
.\gradlew.bat test build
```

The standalone mod JAR is written to `build/libs/`.

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](LICENSE), copyright 2026 CappleApple. The optical-material adapter retains its MIT license; see [third-party notices](THIRD_PARTY_NOTICES.md).
