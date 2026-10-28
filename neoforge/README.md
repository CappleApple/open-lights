# Open Lights for NeoForge

The Minecraft 1.21.1 build targets NeoForge 21.1.248 or later in 21.1.x and Java 21.

Open Lights is a rendering library. Install **Open Lights + Open Lights Flashlight** for the handheld flashlight with RF/FE support. Add **OL Global Illumination** separately for colored world lighting, aggregation, indirect bounce lighting, and placed light blocks. The flashlight works without GI.

See the [main README](../README.md), [flashlight guide](../docs/flashlight.md), and [2.0 migration notes](../docs/migration-2.0.md) for installation and configuration. The client API uses the NeoForge event bus; see [API examples](docs/api.md).

## Build

Run from this directory with Java 21:

```powershell
.\gradlew.bat test build
```

Runtime artifacts in `build/libs/`:

```text
openlights-neoforge-1.21.1-2.0.0.jar
ol-global-illumination-neoforge-1.21.1-2.0.0.jar
open-lights-flashlight-neoforge-1.21.1-2.0.0.jar
```

Ordinary development runs load only the library. Add `-PwithFlashlight`, `-PwithGI`, or both to include addons. The existing `runQa` helper loads both addons and is excluded from runtime artifacts; its old scenarios require review against the split. See the [2.0 validation record](../docs/verification-2.0.0.md) for checks actually run.

## License

[CC BY-NC-SA 4.0 with Additional Permission for Minecraft Modpacks and Servers](../LICENSE). See [third-party notices](../THIRD_PARTY_NOTICES.md).
