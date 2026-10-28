# Migrating to Open Lights 2.0

Open Lights 1.8.x bundled the renderer, world lighting, GI, flashlight, and placed lights. Version 2.0 builds three separately installable mods.

| Existing feature | Install in 2.0 |
| --- | --- |
| TaCZ or another API integration | Open Lights plus that integration |
| Handheld flashlight | Open Lights + Open Lights Flashlight |
| Colored world lighting, aggregation, indirect bounce, placed lights | Open Lights + OL Global Illumination |
| All previous features | All three mods |

Replace the old Open Lights JAR. Do not keep 1.8.x alongside 2.0. Match Minecraft and loader versions for all three JARs.

## Worlds and items

The flashlight retains `openlights:flashlight`. The GI addon retains `openlights:point_light`, `openlights:spot_light`, `openlights:area_light`, and the `openlights:light_source` block-entity type. Install the corresponding addons before opening a world containing those items or blocks. Removing a content addon leaves its registrations unavailable; back up existing worlds before changing installed content.

Flashlights now require RF/FE by default. Items without battery data start empty. Set `[battery].requireEnergy = false` in `openlights_flashlight-server.toml` to retain unlimited power.

## Settings

Analytic-light settings remain in `config/openlights-client.toml`. Cached world-lighting and GI keys move to `config/ol_global_illumination-client.toml`; the old file is not automatically migrated. Copy the relevant world-lighting, `[aggregateLight]`, and `[globalIllumination]` settings after the addon generates its config. Shared beam defaults and datapack paths remain unchanged.

Installing OL Global Illumination explicitly opts into world lighting. Its default is cached Open Lights-style world lighting with indirect bounce enabled. Install only the library and flashlight addon when that processing is unwanted.

Prior 1.8.x verification reports remain historical evidence for those builds. The [2.0 validation record](verification-2.0.0.md) records checks for the new package boundaries.
