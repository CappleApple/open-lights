# Open Lights

Rendering library for point, spot, and rectangular area lights in Minecraft. Other mods submit lights through the client API; Open Lights supplies shadows, colored transmission, volumetric beams, and beam profiles.

Open Lights 2.0 adds no flashlight, placed light blocks, automatic block lighting, or global illumination by itself. With no submitted lights, it skips scene scans and lighting render passes.

Download all three mods for either loader from the [2.0.0 release](https://github.com/CappleApple/open-lights/releases/tag/v2.0.0).

## Choose what to install

| Mod | ID | Provides |
| --- | --- | --- |
| Open Lights | `openlights` | Shared renderer, light types, profiles, and client API. Required by either addon. |
| [Open Lights Flashlight](docs/flashlight.md) | `openlights_flashlight` | Handheld flashlight with RF/FE charging. Does not require GI. |
| [OL Global Illumination](docs/lighting.md) | `ol_global_illumination` | Cached colored world lighting, aggregation, indirect bounce lighting, placed lights, and world-lighting settings. Does not require the flashlight addon. |

For flashlights only, install **Open Lights + Open Lights Flashlight**. Both addons can also be installed together. They are separate JARs; neither addon bundles or automatically installs the other. Flashlights require RF/FE by default and start empty. Use an item charger from another mod, or disable the energy requirement in the flashlight server config.

## Supported builds

| Minecraft | Loader | Java | Source/build directory |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.10 or later in 47.x | 17 | Repository root |
| 1.21.1 | NeoForge 21.1.248 or later in 21.1.x | 21 | [`neoforge/`](neoforge/README.md) |

The flashlight requires its addon and the library on both client and server. GI world rendering can run on the client; its placed blocks require server support. The library can serve client-side API integrations on servers without Open Lights. Minecraft still owns gameplay light levels.

[TACZ Open Lights](https://github.com/CappleApple/tacz-open-lights) remains a separate Forge integration and uses the library API. It does not require OL Global Illumination or the handheld flashlight addon. Open Lights 2.x requires an updated integration build that accepts 2.x; older TaCZ Open Lights 1.1.2 declares an Open Lights version below 2.0.

## Rendering limits

The analytic renderer selects at most eight lights and four lights with shadows per frame. Point, spot, and area lights use six, one, and four shadow views respectively. Shadows use cached block shapes; entity shadow casting is not supported. Clear glass transmits light, stained glass colors it, and tinted glass blocks it.

An active shader pack uses additive compositing. The optional [Distant Horizons adapter](docs/distant-horizons.md) supplies LOD depth for receivers and beam clipping. Previous compatibility reports describe the 1.8.x monolithic releases; see the [2.0 validation record](docs/verification-2.0.0.md) for split-build coverage.

## Configuration

- `config/openlights-client.toml`: analytic rendering quality, light/shadow limits, beam dust, scene scanning, transmission, and bloom.
- `<world>/serverconfig/openlights-server.toml`: shared beam appearance defaults. [Datapacks](docs/beam-profiles.md) override individual profiles.
- `config/ol_global_illumination-client.toml`: GI addon world-lighting, aggregation, and bounce settings.
- `<world>/serverconfig/openlights_flashlight-server.toml`: flashlight battery policy. See [flashlight usage and configuration](docs/flashlight.md).

World-lighting settings moved out of the library config in 2.0. Review [migration notes](docs/migration-2.0.md) before updating an existing world.

## For developers

Use immutable `LightDefinition` values with persistent `LightHandle` instances, or submit lights during `CollectLightsEvent`. Public API packages and beam profile IDs remain unchanged. Positions use world coordinates with double precision. See [API contracts and examples](docs/api.md).

The `main` source set builds the library. `globalIllumination` and `flashlight` compile against it independently. Addon implementations are absent from the library JAR. `verifySplitJars` checks metadata, duplicate classes/packages, native-content isolation, and library bytecode references to addon classes.

## Building

From the repository root with Java 17:

```powershell
.\gradlew.bat test build
```

For NeoForge, run the same command from `neoforge/` with Java 21. Each build writes three runtime JARs and their source JARs to its `build/libs/` directory. Forge names are:

```text
openlights-1.20.1-2.0.0.jar
ol-global-illumination-forge-1.20.1-2.0.0.jar
open-lights-flashlight-forge-1.20.1-2.0.0.jar
```

QA helpers are separate artifacts and must not be distributed with the runtime mods.

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](LICENSE), copyright 2026 CappleApple. See [third-party notices](THIRD_PARTY_NOTICES.md) for retained MIT code.
