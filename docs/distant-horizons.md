# Distant Horizons

Open Lights has an optional client adapter for Distant Horizons on Forge 1.20.1 and NeoForge 1.21.1. Install both mods on the client; no integration setting or additional API JAR is needed.

See [1.8.3 verification](verification-1.8.3.md) for validation status and tested DH, renderer and shader-pack versions.

## Rendering behavior

Normal Minecraft chunks retain Open Lights' selected lighting mode and block-light style. Distant Horizons terrain keeps its native lighting, colors and fog. Cached replacement does not apply the normal-chunk light cache to LOD terrain: that cache only contains loaded Minecraft chunks.

With DH installed and no active shader pack, `CACHED` mode uses a sky-only copy of Minecraft's native lightmap for normal chunks. Each sky-light row keeps its zero-block-light color across all block-light columns. Native per-vertex skylight, ambient occlusion and fog therefore apply before DH fades between normal and LOD terrain. The final composite leaves that ambient scene intact and adds cached block lighting separately, avoiding a second sky-light multiplication of DH's already-lit contribution. The branch follows DH installation even when its terrain rendering is disabled; it does not change DH brightness, shading, fog or fade settings.

On normal-chunk receivers, cached block lighting, analytic lights and GI use a bounded reflectance estimate from the ambient-lit scene and cached sky palette. This is an approximation, not access to the original surface material: translucent layers, fog and mixed normal/LOD pixels can still affect added lighting. LOD materials, face shading and fog can also differ naturally from normal chunks. Without DH, cached mode retains its neutral lightmap and applies cached sky and block lighting in the final composite.

Open Lights combines normal-chunk and Distant Horizons depth before evaluating analytic lights, GI and volumetric scattering. Normal opaque geometry owns pixels it draws after DH's background color pass; LOD depth fills pixels without a normal receiver. This keeps overlapping LOD meshes from skipping normal-chunk shading. The visible receiver stops a beam's fog. LOD receivers retain their real distance even beyond Minecraft's far plane. Light selection, source reach, shadow geometry and GI coverage still follow Open Lights' existing limits; the adapter does not load chunks or extend those caches to the entire LOD distance.

Depth-aware upsampling and bloom keep surface lighting on its receiver. When a thin LOD pixel has no matching reduced-resolution sample, pure foreground fog can interpolate across the boundary only after both rays cover every selected volumetric influence. This prevents gaps without borrowing surface lighting or fog behind a nearby wall. Resource reloads, framebuffer resizing and toggling Distant Horizons discard old captures. An active shader pack retains the existing additive Open Lights path; the adapter only uses depth from a completed non-shadow DH pass.

## Optional API and failure behavior

The adapter uses the public `DhApiBeforeRenderCleanupEvent` and render proxy. Each world frame starts with no LOD capture. A completed terrain pass supplies its projection, view matrix and borrowed depth texture. Open Lights reconstructs physical distance through DH's projection instead of interpreting LOD depth through Minecraft's projection, and queries depth direction and range independently on API 7.2 or later.

The build uses the DH API as a compile-only dependency. DH classes resolve only when the mod is installed. APIs before 3.0 are unsupported; unavailable API data leaves normal-chunk lighting active. An adapter error logs one warning and disables LOD depth integration for that client session.

DH's OptiFine cleanup path clears its depth before Open Lights' final composite. That path is not covered by this adapter. Shader-pack compatibility also depends on the pack exposing a DH terrain pass through the public API.

Public references: [render parameters](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/methods/events/sharedParameterObjects/DhApiRenderParam.html), [DH 3.3.3 depth conventions](https://gitlab.com/distant-horizons-team/distant-horizons/-/blob/3.3.3/common/src/main/java/com/seibel/distanthorizons/common/render/openGl/GlDhRenderApiDefinition.java).
