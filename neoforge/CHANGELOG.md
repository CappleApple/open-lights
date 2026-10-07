# Changelog

## 1.8.3 - 2026-10-06

### Fixed

- Nighttime terrain darkening at the transition from normal chunks to Distant Horizons terrain when cached lighting shaded DH's fade contribution twice. Normal chunks now retain native sky shading before the fade and receive cached block lighting separately.

## 1.8.2 - 2026-10-06

### Fixed

- Analytic lights and volumetric beams ignoring Distant Horizons terrain depth. LOD terrain retains its native shading while normal chunks continue using the selected Open Lights style.
- Stale terrain depth after framebuffer resizing.

## 1.8.1 - 2026-10-02

### Changed

- Global illumination is disabled by default in new client configurations. Existing saved settings are preserved.

## 1.8.0 - 2026-09-30

### Added

- Bounded source/cluster caches for Open Lights-style block lighting. Unchanged lights reuse their calculations, and edits rebuild only affected combined bricks.
- Per-source cached GI contributions, allowing removed sources to lose their bounce light without waiting for probe retracing.
- Block exposure and source-cache memory controls in Video Settings → Open Lights.

### Changed

- Small block edits update emitters immediately and patch nearby immutable material data instead of discarding whole section snapshots.
- Default Open Lights-style block exposure is 2×.

### Fixed

- Weak overlapping lights dimming stronger light. Intensity now preserves the strongest contribution while colors and directions continue blending.
- In-progress section scans restoring an emitter after it was removed.

## 1.7.3 - 2026-09-30

### Changed

- Point, spot and area light selection now uses their emitted influence bounds. Away-facing beams no longer consume light or shadow slots when their influence is outside the view.
- Analytic shading rejects receivers outside light bounds before sampling shadows or transmission. Fog evaluation clips each light to the visible ray before opaque depth and skips unrelated lights and empty intervals.
- Frames without analytic lights or active GI skip the lighting shader's scene/depth sampling.

## 1.7.2 - 2026-09-30

### Fixed

- Open Lights-style block lighting washing out surface textures. Direct light now modulates texture color, with its brightness response applied before reflectance.
- Abrupt color, intensity and shading transitions where cached lights overlap. Contributions now blend without increasing brightness beyond the contributing lights' peak.
- Cached API lights and GI giving dark texture pixels an artificial minimum reflectance.

## 1.7.1 - 2026-09-29

### Fixed

- Lighting calculations repeatedly restarting during block updates, chunk loading, source discovery, or movement. Ordinary changes now queue a follow-up calculation while the current snapshot finishes.
- Completed lighting being discarded before collection or before its sections finished applying.
- Open Lights style selecting an empty light field before the initial source scan found emitters.
- Material snapshots changed during capture being reused by later calculations, and continuously updated sections delaying source discovery or removal.

## 1.7.0 - 2026-09-29

### Added

- Automatic client-only lighting when the connected server does not have Open Lights. Colored world lighting, GI, aggregation and lighting settings remain available without changing the server world.
- A client-only indicator in the lighting settings title.

### Changed

- Flashlights and placeable lights appear in creative tabs only with server support. Stale Open Lights items from saved creative hotbars are blocked from creative inventory/drop submissions on unsupported servers.
- Beam-profile synchronization is optional and sent only over a negotiated channel. Full server support resumes automatically when joining a server with Open Lights.

### Fixed

- Missing placed-light models after reloading resources on a server without Open Lights and then joining one with it.
- Texture binding state left inconsistent after cached hand-lighting uploads, which could corrupt later texture uploads and menu text.
