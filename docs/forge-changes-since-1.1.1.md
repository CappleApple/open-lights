# Forge changes since 1.1.1

Changes included in Open Lights 1.8.1 for Minecraft 1.20.1 / Forge.

## Added

- Cached colored world lighting across loaded render distance, replacing vanilla world shading while leaving gameplay light levels unchanged.
- Optional single-bounce global illumination from API lights and emitting blocks, with nearby and distant probe caches.
- Block-light colors derived from emitting textures and emissive masks. Translucent textures color transmitted light; resource-pack reloads refresh those colors.
- Open Lights block-lighting style with radial falloff, directional surface shading and voxel shadows. Minecraft style remains selectable.
- Aggregate emitters: nearby matching sources increase lighting reach with diminishing returns without multiplying peak brightness.
- Automatic client-only lighting on servers without Open Lights. Native flashlights, placed lights and synchronized beam profiles remain available when the server also has the mod.
- Open Lights controls in vanilla Video Settings, including lighting style, viewing distance, aggregation, exposure, source-cache memory and update budgets.
- Economy, Balanced, Fast and Rapid lighting-update presets, plus separate snapshot capture, section application and texture-upload limits.
- Optional periodic cache refresh for integrations that bypass ordinary world-update notifications.

## Changed

- GI defaults off in new client configurations; cached direct lighting stays enabled. Existing saved GI settings are preserved.
- Point, spot and area lights follow render distance instead of a fixed 32-block camera cutoff. Each source retains its configured reach and falloff; an optional viewing-distance cap is available.
- Aggregate lighting calculations and texture construction run on a background worker using immutable world snapshots. Capture and result application remain budgeted on the client thread.
- Unchanged source or cluster calculations are reused. Edits recompose affected cached regions and upload only changed combined lighting bricks.
- GI retains per-source contributions, allowing removal to subtract cached bounce light without waiting for probe retracing.
- Settled caches stop rescanning, retracing and uploading unchanged lighting by default.
- Analytic light selection uses emitted influence bounds; shading skips unrelated receivers and fog intervals.
- An active shader pack selects additive lighting instead of replacing the pack's world shading.

## Fixed

- Uncolored first-person hands and held items in colored environments.
- Washed-out surface textures, abrupt overlap transitions and weak lights dimming stronger lights.
- Repeated calculation cancellation during movement, chunk loading and ordinary edits; active calculations now finish before queued follow-up work.
- Slow placement/removal in dense emitter arrangements, stale material snapshots and older scans restoring removed emitters.
- A lit square around the player, distant block-light detail loss and nearby updates starving distant cache work.
- Vanilla lighting reappearing during movement, teleporting, reloads and cache rebuilds.
- Dark daytime terrain near the bottom of the world and buried probes incorrectly reducing distant skylight.
- Distant shadow banding and texture-binding/upload corruption affecting cached lighting, hands and later rendering.
- Unsupported-server channel rejection, stale creative Open Lights item submissions and missing placed-light models after returning to a supported server.
- Client-only API beam particles depending on registry entries unavailable on servers without Open Lights.
