# Open Lights for NeoForge

Point, spot and rectangular area lights, with cached colored world lighting and diffuse global illumination. Targets Minecraft 1.21.1, NeoForge 21.1.248 or later in the 21.1 series, and Java 21.

Install on the client for automatic client-only lighting on servers without Open Lights. No server installation, world conversion or extra setting is needed. Cached colored world lighting, GI, aggregation, both lighting styles and visual settings remain available. Server gameplay light levels stay unchanged.

Install on both sides to also use Open Lights flashlights, placed lights and synchronized beam profiles. These require server support and are hidden from creative tabs in client-only mode. The lighting screen title identifies client-only connections. Support is detected again for each connection, including when switching servers. The separate TaCZ Open Lights integration remains Forge-only.

GI is disabled by default in new configurations. Cached direct lighting remains enabled. Set `[globalIllumination].enabled = true` to enable indirect bounce lighting; existing saved settings are preserved.

## Lights and world lighting

- `openlights:flashlight`: hold in either hand and right-click to toggle.
- `openlights:point_light`, `openlights:spot_light`, `openlights:area_light`: place, then right-click with an empty hand to toggle. Sneak-right-click cycles reach through 8, 16, 24, 32 and 48 blocks. Dye changes color; client range limits still apply.
- With server support, all four have crafting recipes. Flashlights appear in Tools & Utilities; placed lights appear in Functional Blocks.
- Cached world lighting follows loaded render distance. Emitting textures supply colors; translucent textures filter light. Nearby emitters increase reach with diminishing returns.
- Open Lights block style uses radial falloff, directional shading and voxel occlusion. Minecraft style preserves propagated shading. Hands and held items receive cached environment colors.
- Point, spot and area lights include block-shape shadows, colored transmission and volumetric scattering. Cached GI includes light from these sources and emissive blocks.

Minecraft still computes gameplay light levels. No chunks are loaded for lighting. The renderer selects at most eight analytic lights, four shadow lights and 32 transmission regions per frame. Entity shadow casting is not supported. Coarse distant GI and voxel interpolation have the limitations described in the [lighting reference](docs/lighting.md).

## Settings and profiles

Open **Options > Video Settings > Open Lights** in the vanilla video menu. Replacement video menus may require editing the client TOML directly. Choose block style, viewing distance, aggregation, block exposure, source-cache memory and update budgets. Open Lights style reuses cached source/cluster calculations and uploads only changed combined bricks; removed sources also retire their cached GI contribution. Economy, Balanced, Fast and Rapid adjust discovery, snapshot capture, result adoption and GPU uploads. Higher budgets can cause longer client frames; the worker already runs without an artificial time limit.

Client configuration: `config/openlights-client.toml`. Server beam defaults: `<world>/serverconfig/openlights-server.toml`. [Beam profiles](docs/beam-profiles.md) describe datapack overrides and the included 1.21.1 example.

The [client API](docs/api.md) supports persistent handles and per-frame light submissions on the NeoForge event bus. The event package differs from the Forge build.

See [Sodium and Iris verification](docs/renderer-compatibility.md) for tested versions and caveats. An active Iris shader pack uses additive lighting/GI and bypasses Open Lights-style world replacement and aggregation.

The optional [Distant Horizons adapter](../docs/distant-horizons.md) preserves LOD terrain's native lighting and uses its depth for analytic receivers and volumetric clipping. Normal chunks retain the selected Open Lights style. No additional setting or API JAR is required. See [1.8.3 verification](../docs/verification-1.8.3.md) for validation status and scope.

## Build and verification

Run from this directory with Java 21:

```powershell
.\gradlew.bat test build
```

Runtime JARs are written to `build/libs/`. The Forge 1.20.1 build remains in the parent directory.

The [verification checklist](docs/verification.md) records runtime coverage and test limits. Development visual tests use a separate helper and disposable world:

```powershell
.\gradlew.bat runQa -PqaMode=style
```

Other modes: `dense`, `sourceGi`, `contrast`, `progress`, `visibility`, `full`, `gi`, `visual`, `range`, `aggregate`, `extra`, `daylight`. The helper mutes sound, hides its window and prevents mouse capture. Test classes are excluded from the runtime JAR.

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](../LICENSE). See [third-party notices](../THIRD_PARTY_NOTICES.md) for retained MIT code.
