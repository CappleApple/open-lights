# Open Lights Flashlight

Requires Open Lights 2.x and the Open Lights Flashlight addon on the client and server. OL Global Illumination is optional.

Craft `openlights:flashlight` from three iron ingots, one glass pane, and one redstone dust. It appears in Tools & Utilities. Hold it in either hand and right-click to toggle the beam. The item keeps its pre-2.0 registry ID so existing inventories can retain it when the addon is installed.

## RF/FE charging

New flashlights start empty. The addon does not include a charger. Charge them in a charger that accepts the loader's standard item energy capability. The item accepts energy and does not supply energy to other items or machines. The tooltip and item bar show stored charge.

By default, an enabled flashlight consumes one RF/FE per server tick while held in either hand. Stored flashlights do not drain. It turns off when it cannot fund another full tick, preserving any leftover charge below that cost. With enabled flashlights in both hands, the main hand supplies the beam and the offhand battery waits. Charging does not automatically turn it on. Battery charge and the toggle persist with the item stack and synchronize through normal inventory updates.

Charging is implemented through Forge Energy on Forge and the NeoForge item energy capability on NeoForge. No separate RF API dependency is required. Compatibility with a particular charging mod depends on that mod supporting the corresponding item capability.

## Server configuration

Start the world once to generate `<world>/serverconfig/openlights_flashlight-server.toml`:

```toml
[battery]
requireEnergy = true
capacity = 100000
maxReceive = 1000
energyPerTick = 1
```

`maxReceive` limits each charging operation. `energyPerTick` applies only while enabled and held. Set `requireEnergy = false` for the previous unlimited-power behavior; setting `energyPerTick = 0` also removes consumption. Existing items without stored charge start empty when energy is required.

Beam colors, range, cone angles, fog, and dust still use the library's [beam profiles](beam-profiles.md), including `openlights:flashlight`. Rendering limits remain in `config/openlights-client.toml`.
