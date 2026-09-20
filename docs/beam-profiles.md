# Beam profiles

Open Lights 1.1.0 reads beam appearance from server configuration and datapacks. The server sends the resolved profiles to clients when they join and after `/reload`.

Profiles apply to TaCZ attachment flashlights, the `openlights:flashlight` item, and placed `openlights:spot_light` blocks. Point and area lights do not use these profiles.

## Change the defaults

Start the world once to generate `<world>/serverconfig/openlights-server.toml`. For a single-player world, this is `saves/<world>/serverconfig/openlights-server.toml`.

Edit the file, then run `/reload`. The following is a complete default configuration:

```toml
[defaults]
fogDensity = 0.3
shadows = true

[defaults.inner]
color = "#FFF5E0"
intensity = 1.5
angleDegrees = 30.0
range = 24.0
edgeSoftness = 0.2
falloff = 2.0

[defaults.outer]
color = "#FFF5E0"
intensity = 0.7
angleDegrees = 46.0
range = 24.0
edgeSoftness = 0.2
falloff = 2.0

[defaults.dust]
rate = 6.0
size = 0.025
lifetimeTicks = 30
speed = 0.02
```

Modpack authors can copy this file to `defaultconfigs/openlights-server.toml` to seed new worlds. Existing worlds keep their own server configuration.

Datapack fields override these defaults individually. A missing profile uses the defaults; a partial profile inherits every omitted field, including fields inside `inner`, `outer`, and `dust`.

## Override one light with a datapack

Copy the [example datapack directory](../examples/beam-profiles) into `<world>/datapacks/`, then run `/reload`. The copied directory must contain `pack.mcmeta` directly inside it.

Profile files use this path:

```text
data/<namespace>/openlights/beam_profiles/<path>.json
```

The resulting profile ID is `<namespace>:<path>`.

| Light | Profile file |
| --- | --- |
| TaCZ LoPro flashlight | `data/tacz/openlights/beam_profiles/laser_lopro.json` |
| TaCZ PEQ-15 flashlight | `data/tacz/openlights/beam_profiles/laser_peq15.json` |
| Open Lights handheld flashlight | `data/openlights/openlights/beam_profiles/flashlight.json` |
| Open Lights placed spot light | `data/openlights/openlights/beam_profiles/spot_light.json` |

For other TaCZ attachments, use the attachment's actual resource ID. A profile changes an existing flashlight's appearance; it does not add an emitter to an ordinary laser or an unsupported attachment.

This minimal profile gives LoPro a warm center and blue spill, with additional visible fog and dust:

```json
{
  "inner": {
    "color": "#FFF0CC",
    "intensity": 1.8,
    "angleDegrees": 20.0
  },
  "outer": {
    "color": "#88AAFF",
    "intensity": 0.4,
    "angleDegrees": 50.0
  },
  "fogDensity": 0.5,
  "dust": {
    "rate": 12.0
  }
}
```

The server resolves the entire set before replacing the active snapshot. Each connected client receives the complete snapshot in one update. Resource-pack reloads do not reload beam profiles.

Invalid profiles are reported in the server log and use the server defaults. An invalid value in a recognized field invalidates that whole profile; unknown keys are ignored. Profiles are limited to 256 entries, IDs to 256 characters, and individual entries to 16,384 characters of serialized JSON.

## Beam settings

`inner` and `outer` are independently colored, additive light layers. Both illuminate the center of the beam. The default center intensity is therefore `1.5 + 0.7 = 2.2` before distance attenuation and surface shading.

Angles are **full cone angles**, not half angles. The inner angle must be less than or equal to the outer angle. Each layer has its own range; the inner layer may reach farther than the outer layer.

| Field in each layer | Meaning | Accepted values |
| --- | --- | --- |
| `color` | RGB color | Six-digit `"#RRGGBB"` string |
| `intensity` | Layer brightness; zero disables the layer | 0–16 |
| `angleDegrees` | Full cone angle | Greater than 0, up to 175 degrees |
| `range` | Maximum layer reach | Greater than 0, up to 32 blocks |
| `edgeSoftness` | Fraction of the angular edge that fades; zero gives a hard edge | 0–1 |
| `falloff` | Exponent of the range fade; higher values dim the beam sooner | 0.1–8 |

The server configuration accepts a minimum angle and range of `0.1`; datapacks accept any positive value within the limits above. If the configured default inner angle exceeds the outer angle, Open Lights logs a warning and clamps the inner angle. An inverted angle pair in a datapack invalidates that profile.

| Top-level field | Meaning | Accepted values |
| --- | --- | --- |
| `fogDensity` | Strength of visible scattering inside the beam; zero disables it | 0–8 |
| `shadows` | Allow block-shape shadows for this beam | Boolean |

Placed spot lights also multiply the profile colors by their dyed color and brightness by their stored intensity. Their selected block range caps both layers. Undyed spot lights retain the profile colors.

Client rendering settings still apply. In `config/openlights-client.toml`, `maxRange` caps the rendered reach and defaults to 24 blocks, `intensityMultiplier` scales brightness, and `maxShadowLights` limits how many lights receive shadows. The client does not replace the server's profile definitions.

## Dust settings

Dust is a visual particle effect inside illuminated portions of the beam. It does not create additional lights or send a network packet per particle.

| Field in `dust` | Meaning | Default | Accepted values |
| --- | --- | --- | --- |
| `rate` | Requested particles per second per beam; zero disables dust | 6 | 0–80 |
| `size` | Particle width in blocks | 0.025 | 0.01–0.2 |
| `lifetimeTicks` | Maximum lifetime; 20 ticks equal one second | 30 | Integer, 1–200 |
| `speed` | Maximum initial drift per axis, in blocks per tick | 0.02 | 0–1 |

Dust samples both layer colors; there is no separate `dust.color` setting. It fades with the beam illumination and disappears outside the beam. Opaque block shapes prevent spawning behind walls. The client caps dust at eight spawn attempts per tick and 256 active particles across all beams, so the requested rate is a ceiling rather than a guarantee.

Set `beamDust = false` in `config/openlights-client.toml` to disable the effect locally. Minecraft's Decreased particle setting reduces the requested rate to one quarter; Minimal disables beam dust.
