# Changelog

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
