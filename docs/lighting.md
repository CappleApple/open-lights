# Cached lighting and performance

All settings below are client settings in `config/openlights-client.toml`. They do not change server light levels, spawning, crops, or block emission. The implementation targets Minecraft 1.20.1, Forge 47.4.10, and Java 17.

## Rendering modes

`lightingMode = "CACHED"` is the default. It replaces the world lightmap binding with a neutral 16×16 texture during world rendering, then applies cached block/sky lighting in Open Lights' full-resolution composite. Vanilla and modded block lights use one-block-resolution section caches across loaded render distance. Lit sections store colored light in 18³ bricks, including a border for interpolation; unlit sections use only a lookup entry. Blocks do not consume analytic light slots. Sky lighting uses a fixed 33³ nearby grid and a second grid across render distance with wider spacing. The original palette supplies sky color, brightness, darkness, night vision, and block-light brightness; emitting textures supply block-light color. Sky interpolation excludes buried solid probes so they do not darken exposed distant terrain.

`lightingMode = "ADDITIVE"` preserves vanilla surface lighting and adds analytic lights and GI. Active Oculus shader packs automatically use this mode. If a known shader loader's API cannot be queried, replacement stays disabled. This does not establish support for other renderers or shader packs.

Replacement starts before geometry on the first world frame. Cache warmup, movement, teleporting, resource reloads, and render-distance changes do not switch world shading back to vanilla. Newly exposed distant cells receive a coarse skylight estimate from 125 samples. The near/far grids supply provisional block lighting while section bricks fill under the shared sample and color budgets. Section bricks then supply the same block-light resolution throughout the loaded view. Existing overlapping bricks remain usable during movement and dirty refreshes. New chunks and resource reloads need warmup; many lit sections take longer to fill. A renderer error restores the ordinary lightmap for subsequent rendering; resource reload retries initialization. First-person hands and items use the cached environment's block-light color through a separate lightmap; the original binding is restored afterward for the UI. World unload and resource reload discard cached lighting and invalidate API handles.

Minecraft's CPU light engine remains responsible for gameplay light. Open Lights replaces base-light rendering and adds a separate visual propagation cache for aggregate sources. It does not discover arbitrary third-party GPU lights or change the existing eight-light analytic limit. Providers continue to submit point, spot, and area lights through the [client API](api.md).

## Aggregate block light

`blockLightStyle = "OPEN_LIGHTS"` is the default. Emitting blocks become cached point-light approximations with round distance falloff, direction-dependent surface shading, and direct occlusion. The falloff uses the point renderer's squared range edge and distance denominator; surface shading uses its wrapped diffuse term, and the composite uses the same soft knee. Adjacent same-color emitters are grouped into bounded local clusters, increasing their reach and slowing falloff without raising peak power. Cluster positions are emission-weighted centers. Block textures retain their own emission brightness.

The worker traces voxel paths through immutable material snapshots. Opaque cells stop direct light; translucent textures color it. Cached directions supply surface shading without per-pixel tracing or consuming the eight API light slots. Overlapping sources blend their filtered colors and directions using irradiance weights. Cell intensity retains the strongest irradiance, so adding weak overlapping sources cannot dim an existing light. Filtered colors and directions still blend smoothly, and overlap does not raise the contributing peak. Opposing directions retain their weighted moment instead of switching abruptly between normalized directions.

Cached direct light multiplies the surface texture rather than adding a flat color over it. The soft knee applies to irradiance before texture reflectance; combined sky/direct illumination is bounded to preserve contrast in bright texels. Emitting block textures retain their emission floor. Cached API lights and GI also use the neutral-lit scene color as their reflectance proxy without an artificial brightness floor. Additive/shader-pack mode keeps its existing proxy because its scene already contains base lighting; volumetric light keeps its additive composition.

This is a cached approximation: shadows have block resolution, partial geometry is represented by opacity and shared-face masks, and grouped emitters use one center rather than the area renderer's four shadow samples. It does not reproduce every detail of the API renderer's geometry shadow maps or volumetric beams.

`blockLightStyle = "MINECRAFT"` selects the previous propagated appearance. It retains the native lightmap brightness response, colored block light and aggregate reach. Both styles are available in Video Settings → Open Lights. First-person color applies in both; additive/shader-pack mode retains its original hand rendering.

Aggregation is enabled by default in cached mode. A local 5×5×5 neighborhood supplies emission power; cube-root growth increases reach with a configurable cap. A 3×3×3 glowstone cluster has roughly 2.8 times the single-source reach after integer quantization. Peak power remains capped. Sources along a long chain or a large lava lake cannot pool unlimited power. Disabling aggregation leaves single-source lighting active in either style.

In Minecraft style, extended light propagates along the six block directions, using opacity, shared-face occlusion, and texture-derived transmission colors. It can travel around a barrier through an open path. Open Lights style requires a direct voxel path instead; cached GI can supply bounce light. Translucent filtering averages textures rather than projecting their pixels. API point, spot, and area lights are not aggregated.

Cold-load emitters are discovered as native block-light sections fill. Ordinary small block updates also update the emitter list immediately, so placement/removal need not wait for another section scan. A scan in progress cannot resurrect a removed source. Small geometry edits clone and patch only the affected snapshot cells and their neighbors; unchanged optical data retains its original snapshot. Workers keep immutable copies of their starting geometry. A single background worker calculates propagated or radial lighting and builds the finished color/direction texture bricks. It reads immutable material snapshots captured on the client thread under count and time budgets; it never accesses the live world, texture models, or OpenGL. Snapshots are reused until affected blocks change. Native light sampling, texture-color discovery, and GI rays still run under their existing client-thread budgets.

Source and geometry changes queue a follow-up calculation without interrupting the current snapshot. Camera movement keeps the running job's capture bounds stable and queues work for the new view. Relevant settings changes cancel incompatible calculations; world unload and resource reload discard pending results. The client never waits for the worker. Existing lighting can briefly reflect an earlier snapshot while the next calculation catches up, including after source removal. Snapshots changed during capture are excluded from reuse by later jobs.

Completed fields remain usable while a replacement builds. Finished brick arrays are adopted under a section-count budget and uploaded under separate per-frame count and time budgets, without rescanning world blocks. Adoption finishes before the next calculation starts, so continuous updates cannot abandon a partly applied field. Cold starts retain provisional cached lighting until sources are found or the initial section queue is exhausted. Atlas growth fills a replacement before switching away from the visible atlas. Worker failures clear the aggregate contribution and retain native cached lighting.

Minecraft style rebuilds its aggregate field globally. Open Lights style retains sparse irradiance/direction channels per emitter or bounded aggregate cluster. Unchanged channels reuse their traced voxels; changed occlusion retraces channels whose coverage intersects the dirty section. Recomposition visits the union of changed channels using a section-local source index, and only affected bricks and their interpolation neighbors are baked. Unchanged arrays retain their identities and are not uploaded. The final field maps are copied when publishing a generation, so edit cost still grows with total stored cells even when no rays need tracing. Removing a member can change its cluster and require tracing the remaining cluster. Multiple edits during a job are combined into one pending rebuild rather than repeatedly restarting it. Completed fields perform no propagation while their inputs are unchanged. No chunks are forced to load. Cached GI uses the extended field when block-light bounce is enabled. Shader-pack/additive mode does not use aggregation.

Cold calculations check occlusion for each contributing emitter; cached channels retain those results under a memory budget. Dense overlap increases channel memory and local recomposition work. Disabling the source cache or exceeding its byte budget falls back to a full radial rebuild without dropping lights solely because the channel cache is full. An overflowing cold build can do partial cached work before falling back. It uses the existing color/direction atlases and adds no per-pixel light samples or additional render targets.

All keys below are under `[aggregateLight]`:

| Key | Default | Behavior |
| --- | --- | --- |
| `enabled` | `true` | Enable aggregation in cached mode |
| `maximumMultiplier` | `3.0` | 1–4 maximum reach multiplier before quantization |
| `strength` | `1.0` | 0–2 growth strength; zero preserves ordinary light |
| `cellsPerTick` | `8192` | 128–65536 material cells captured on the client thread per tick |
| `budgetMillis` | `1.0` | 0.1–8 ms soft snapshot-capture budget; does not throttle the worker |
| `maximumCells` | `1048576` | 65536–4194304 propagated cells across falloff tiers, or stored radial-light cells in Open Lights style |
| `snapshotSections` | `2048` | 64–8192 reusable material sections and newly requested sections per job; a section contains 4096 cells |
| `sourceCacheMiB` | `128` | 0–512 MiB estimated sparse radial-channel storage; zero disables reuse, overflow falls back to a full rebuild |
| `applySectionsPerTick` | `16` | 1–256 finished texture bricks adopted per client tick |

Video Settings → Open Lights exposes block exposure (0.75–4× presets), source-cache memory (0/32/64/128/256/512 MiB), the toggle, range cap, snapshot-capture presets (0.35 ms/2048 cells, 1 ms/8192 cells, and 2 ms/16384 cells), and upload-time presets (0.25, 0.5, 1, and 2 ms). These budgets change update speed, not settled light quality. Source-list copying, result publication, atlas allocation, and page-table updates also have costs outside these timers; an individual capture or upload can exceed its soft time limit.

Reaching `maximumCells` or the snapshot-request limit truncates the worker field. Minecraft style retains ordinary native lighting when its extension is truncated; Open Lights style can omit direct light beyond the cap. Each snapshot holds about 20 KiB of raw cell data. A job retains its starting snapshots plus newly requested sections. Temporary maps, queued paths, completed fields, and texture bricks consume additional memory. The renderer stores native, worker-color and worker-direction RGBA8 atlases; atlas growth temporarily retains both sizes. Initial warmup and large lava regions can therefore cost substantially more than an idle scene.

### Update speed presets

Video Settings → Open Lights → Lighting update speed cycles through these presets. Manual edits display as Custom.

| Preset | World/color cells per tick | Snapshot cells / soft ms per tick | Applied sections per tick | Uploaded sections / soft ms per frame |
| --- | --- | --- | --- | --- |
| Economy | 1024 each | 2048 / 0.35 | 4 | 2 / 0.25 |
| Balanced | 4096 each | 8192 / 1 | 16 | 8 / 0.5 |
| Fast | 16384 each | 32768 / 3 | 64 | 32 / 2 |
| Rapid | 32768 each | 65536 / 8 | 256 | 128 / 8 |

Balanced matches the defaults. The worker already runs without a per-tick time throttle. Higher presets feed snapshots and discover sources sooner, then apply/upload more results at once. They can cause larger client-thread frame spikes; they do not guarantee immediate convergence or increase the memory caps. GI trace budgets remain separate.

## Diffuse GI

GI is disabled by default. Enable `[globalIllumination].enabled` to add indirect bounce lighting; cached direct world lighting remains enabled independently. Saved configurations retain their explicit value.

The nearby GI grid is 32 blocks wide. A second 9³ grid covers render distance plus a guard band, blending into the near grid. Both share the same probe-count and time budgets. Each probe traces six axis-aligned rays to collision surfaces. Hits receive light from selected API sources and, optionally, the colored native block-light field. Surface texture averages approximate diffuse reflectance. API visibility walks a bounded segment through block shapes and texture-colored optical media. Results are cached as RGB irradiance and sampled in the lighting shader; GI performs no per-pixel ray tracing.

Sources are selected independently of the view frustum, so a light behind the camera can contribute. Point and spot attenuation and profile layers follow the direct renderer. Area lights use their center as a cheaper approximation. Only one diffuse bounce is estimated. Skylight contributes to replacement base lighting, not GI bounce tracing.

The cache retains exact overlapping probes as the camera moves. New cells start empty and fill under the work budget. Updates use current world state on the client render thread, at most once per game tick, without loading chunks. Changed or removed API sources, affected blocks, chunk changes, propagated block-light updates, and tracing settings mark GI probes dirty. `refreshTicks` limits how soon dirty probes can be retraced; the work budget can delay them further. Cached values remain usable while dirty probes wait. GI also keeps CPU source columns and an unclamped summed result. Removed radial sources or clusters are subtracted from the displayed probes before the refresh-age gate; remaining contributions do not need to retrace. Cluster changes retire the old cluster column, then fill the new one under normal budgets. Each near/far grid has a 32 MiB column budget; excess contributions share an overflow column that is conservatively cleared on source changes and refilled. The budget includes the summed array and reserves overflow storage, but recentering temporarily keeps both copies. Minecraft-style and uncached radial block GI share one block-field column, so independent block-source removal requires the radial source cache. API definitions are separate columns; changing a definition retires its old column and refills under the probe budget. Only the final near/far textures reach the GPU.

By default, settled caches perform no world-light samples, GI traces, or scene-sector scans. Native light-engine section notifications refresh only affected world-grid cells, including touching-cell inputs. Relevant GI geometry/light notifications conservatively dirty the nearby GI volume because a change can affect a surface ray or its visibility to a source. Sky-light updates refresh the world field without retracing GI. Palette changes such as time of day are applied by the composite.

`periodicCacheRefresh = true` restores periodic safety sweeps for integrations that bypass ordinary block, chunk, or light-update notifications. World-grid samples then have a ten-tick minimum age, block-light sections are queued every 200 ticks, GI uses `refreshTicks`, and scene sectors use `mediumUpdateTicks`. With this fallback off, such integrations must issue the normal Minecraft notifications to avoid stale results.

Drawing still has a per-frame cost: direct lighting, volumetric effects, sampling cached textures, and compositing use the current camera and scene depth. Event-driven caches do not cache the final screen image or remove Minecraft's gameplay light engine.

## Visibility culling

Analytic light selection checks emitted influence against render-distance coverage and the current frustum before assigning the eight light slots and four shadow slots. Points use their range bounds; spots use spherical-sector bounds for their beam angle and reach. Profile layers contribute their own angles and capped ranges. Area bounds include all four sampled emitter offsets. A source outside the screen remains eligible when it can illuminate a visible surface or fog ray.

The lighting shader rejects surface positions outside each light's bounds before shadow or transmission sampling. Volumetrics intersect those bounds with the camera ray, stop at opaque depth, and skip unrelated lights and gaps between influence intervals. Without analytic lights or active GI, the shader returns before fetching scene/depth textures. These checks use the existing depth buffer and add no visibility pass, GPU readback, or extra texture samples.

This is conservative influence culling, not whole-light wall occlusion culling. An in-frustum influence volume hidden behind a wall can still occupy an analytic/shadow slot; the receiver and shadow tests prevent its contribution, and fog beyond opaque depth is skipped. Off-screen geometry and media may still be needed to cast shadows or filter light onto visible surfaces. They are not culled by the camera frustum alone.

Cached block lighting has no per-emitter draw calls: its composite samples the visible receiver. World/GI caches remain populated across render distance, including behind the camera, so turning does not require a new lighting calculation and off-screen sources can supply visible direct or bounced light. This optimization does not alter their worker, discovery, adoption or upload budgets.

## Texture colors

Block-light color is reconstructed along increasing native light levels back to emitting blocks. Each emitting model's face textures are sampled once and cached. An optional `assets/<namespace>/textures/<sprite-path>_e.png` supplies emissive pixels; a resource pack can change the suffix through `assets/minecraft/optifine/emissive.properties` and `suffix.emissive`. Without a mask, bright saturated pixels estimate the glowing part, avoiding dark housings. This is a heuristic: Minecraft's emission value does not identify glowing texels. Model tint indices are applied, and lava uses its fluid texture. Custom renderers with no usable model texture fall back to a particle texture or neutral color.

Glass, panes, ice, and other translucent render-layer blocks filter the cached color using their actual textures. Water combines its fluid texture with biome tint. Tinted glass still blocks light. Native propagation chooses the available paths; colors mix where paths meet. A glass filter can therefore be bypassed when light can travel around it. Filtering uses a texture average per block, not a projection of individual texels.

Color propagation stores at most 131,072 resolved cells and has its own per-tick work limit. Edited blocks invalidate dependent colors up to the native 15-block light reach, even when emission strength stays the same. Resource reload clears texture summaries, propagation results, and GPU caches. Animated textures average up to four frames instead of changing light every animation frame. `ADDITIVE` and shader-pack fallback retain their existing base lighting; texture-derived base colors apply in `CACHED` mode, while GI and analytic transmission also use the texture summaries.

## Settings

These keys are at the TOML root:

| Key | Default | Allowed values and cost |
| --- | --- | --- |
| `lightingMode` | `"CACHED"` | `CACHED` or `ADDITIVE` |
| `blockLightExposure` | `2.0` | 0.25–4 brightness multiplier for Open Lights-style cached block light and its GI; does not increase reach or change gameplay light |
| `blockLightStyle` | `"OPEN_LIGHTS"` | `OPEN_LIGHTS` for cached radial shading and direct shadows; `MINECRAFT` for propagated lightmap-style shading |
| `lightRenderDistanceChunks` | `0` | 0 follows Minecraft render distance; 1–64 optionally reduces analytic-light viewing distance. Also available under vanilla Video Settings → Open Lights. Source reach and world/GI coverage are unchanged. |
| `periodicCacheRefresh` | `false` | Opt-in periodic world/GI/scene safety rescans; default updates only changed inputs and new cells |
| `worldGridSize` | `33` | 9–65 samples per axis for the distant grid; storage and warmup grow cubically |
| `worldSamplesPerTick` | `4096` | 128–32768 total samples: half for block-light sections, one quarter each for the nearby and distant grids. Sections can use the whole budget when both grids are clean. |
| `colorSamplesPerTick` | `4096` | 128–32768 new color-propagation cells per tick, shared by world probes and GI |
| `sceneBlockBudget` | `8192` | 512–32768 occupied positions per tick, rounded down to 512-block sectors |
| `lightUploadsPerFrame` | `8` | 1–128 cached light sections uploaded per frame, shared by active and growing atlases |
| `lightUploadBudgetMillis` | `0.5` | 0.1–8 ms soft section-upload budget per frame; at least one pending section can progress |
| `maxMedia` | `32` | 0–32 analytic-light medium regions; zero skips those transmission calculations, while cached block colors and GI visibility still filter textures |
| `bloomStrength` | `0.08` | 0–2; zero skips bloom neighbor taps |
| `volumetricSteps` | `12` | 0–24; zero skips volumetric ray marching |

The other analytic-light controls remain in the [README](../README.md#configuration). `renderScale` controls the shared direct/GI/volumetric buffer; replacement base lighting is composited at full resolution.

These keys belong to `[globalIllumination]`:

| Key | Default | Allowed values and behavior |
| --- | --- | --- |
| `enabled` | `false` | Disable to skip GI traces and contribution |
| `blockLight` | `true` | Include the cached visual block-light field in bounce estimates |
| `probeSpacing` | `8` | 2–8 blocks; probe count is `(floor(32 / spacing) + 1)³` |
| `probesPerTick` | `4` | 1–64 completed probes per tick |
| `refreshTicks` | `20` | 1–200 minimum ticks before retracing a dirty probe; clean probes do not expire by default |
| `sourceLimit` | `8` | 1–32 nearest submitted lights whose influence reaches the render-distance volume plus the GI surface-ray margin |
| `strength` | `0.35` | 0–2; scales the cached result without changing tracing work |
| `traceDistance` | `8.0` | 2–16 blocks from probe to bounce surface |
| `budgetMillis` | `0.5` | 0.1–8 ms soft limit, checked between probes; one probe can overrun it |

At defaults, GI contains 125 nearby probes and 729 distant probes. A complete initial sweep needs at least 214 ticks at four probes per tick; near and far work alternate, and an idle grid yields its share. Time and color-propagation limits can extend warmup. Each probe has at most six surface rays plus six visibility segments per selected source. Lower `sourceLimit` limits the worst-case work of a single probe.

Both replacement grids contain 35,937 samples at defaults and share the 4,096-sample budget. Filling their detailed samples therefore takes at least 18 ticks; unresolved colors can extend this. Replacement stays active during that process. A sample checks its cell and six touching cells, skipping unloaded chunks. The 125-sample skylight bootstrap runs only when the distant grid is created or moves, separately from the detailed-sample budget; it never marks detailed work complete. Each grid has a corresponding RGB tint texture. Storage grows cubically with `worldGridSize`; recentering temporarily copies overlapping arrays. Distant GI keeps 729 probes as render distance increases, trading spatial resolution for bounded storage.

## Example performance profiles

These are starting configurations, not hardware guarantees or an automatic preset selector. Keep unlisted keys at their defaults. Put root keys before `[globalIllumination]`.

Minimal extra work, preserving vanilla base shading:

```toml
lightingMode = "ADDITIVE"
maxShadowLights = 1
shadowResolution = 128
volumetricSteps = 0
renderScale = 0.25
maxMedia = 8
bloomStrength = 0.0
sceneBlockBudget = 4096

[globalIllumination]
enabled = true
probeSpacing = 8
probesPerTick = 2
sourceLimit = 4
refreshTicks = 40
budgetMillis = 0.25
```

Cached replacement with denser GI and quicker refreshes:

```toml
lightingMode = "CACHED"
worldGridSize = 49
worldSamplesPerTick = 8192
maxShadowLights = 4
shadowResolution = 512
volumetricSteps = 8
renderScale = 0.5

[globalIllumination]
enabled = true
probeSpacing = 4
probesPerTick = 16
sourceLimit = 8
refreshTicks = 10
budgetMillis = 1.0
```

For the lowest cost, disable GI, use `ADDITIVE`, set `maxShadowLights = 0`, and set `volumetricSteps = 0`. Lowering the source or shadow budgets changes which lights contribute, not just image smoothness.

## Limitations and diagnostics

- Cached replacement is experimental. Sky and GI still use coarse distant probes. Block-light sections retain one-block resolution after filling, but interpolation and touching-cell sampling can spread light across thin walls. Warmup uses the older grid estimates until individual bricks are ready.
- Each lit section uses 23,328 bytes of cached RGBA data on the CPU and GPU, with additional GPU capacity reserved for growth. Unlit sections need only a page-table entry. Increasing render distance or loading many lit sections increases memory and initial sampling work. Unchanged completed sections do not resample.
- GI uses six directions, collision shapes, texture averages, and a single bounce. Thin details can be missed and interpolated probes can leak. Source-visibility segments account for optical media; the six surface rays still treat collision surfaces as possible bounce receivers.
- The final composite has scene color and opaque depth, not material/albedo buffers. Fog, translucent surfaces, particles, emissive textures, and custom fullbright rendering can differ from vanilla. Sky rendering is preserved. This is not a drop-in replacement for a shader pack's material pipeline.
- Analytic lights render across the view distance by default, retaining their configured reach and falloff. Geometry caches cover selected source volumes plus a nearby 32-block region, rather than scanning a whole sphere across render distance. Distant shadow geometry fills under `sceneBlockBudget`; newly selected distant lights can temporarily have incomplete shadows. More separated sources increase scan, mesh, and upload work. The shared 32-region medium limit still applies; material along camera rays outside the cached regions is not represented.
- There is no universal FPS or sub-millisecond guarantee. Resolution, ray intersections, render distance, geometry, driver, and competing GPU work affect cost.

`OpenLightRenderer.cacheStatistics()` exposes replacement status, world/far-GI population and capacity, near-GI population, last-tick world/GI/color work counts and scene-sector scan count, world-cache CPU time for the latest frame, and GI CPU time for the latest update tick. Aggregate fields report cell count, last-tick work, pending work, and whether the last rebuild hit its cell cap. `AggregateLightCache` also exposes source-channel count, traced/reused channels, recomposed cells, estimated channel bytes and last worker time; `IndirectLightCache` exposes GI column count and bytes. `statistics()` retains renderer CPU/GPU timing; its CPU timer excludes the earlier world-cache update. These are internal diagnostics, not a stable integration API.
