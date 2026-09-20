# Client lighting API

Rendering API classes are in `com.cappleapple.openlights.api.client`, including `LightDefinition`, `LightKey`, `LightHandle`, `OpenLightsApi`, `CollectLightsEvent`, `BeamLights`, and `ShaderCompatibility`. Renderer and scene-cache classes are implementation details.

Lights affect client rendering only. The API does not send definitions over the network. A provider that needs multiplayer lights must reconstruct them from synchronized game state or synchronize its own state.

## Definitions

`LightDefinition` is a sealed interface with three immutable records. Its vectors use Minecraft's immutable `Vec3`, preserving double-precision world positions.

All shapes have these properties:

| Property | Contract |
| --- | --- |
| `position` | Absolute world position with finite coordinates. |
| `color` | Nonnegative, finite RGB multipliers; `(1, 1, 1)` is white. |
| `intensity` | Finite, nonnegative intensity before the client multiplier. |
| `range` | Finite, positive distance in blocks, subject to renderer limits. |
| `shadows` | Requests shadows, subject to the client's shadow-light budget. |
| `volumetricStrength` | Finite, nonnegative contribution to visible light shafts. |

Constructors use this argument order:

```java
new LightDefinition.Point(position, color, intensity, range, shadows, volumetricStrength);

new LightDefinition.Spot(position, forward, up, color, intensity, range,
        outerConeAngleDegrees, innerConeAngleDegrees, shadows, volumetricStrength);

new LightDefinition.Area(position, forward, up, color, intensity, range,
        width, height, spreadAngleDegrees, shadows, volumetricStrength);
```

`forward` must have a finite squared length of at least `1e-12` and is normalized during construction. `up` is projected perpendicular to `forward` and normalized. A parallel or zero preferred up vector receives a perpendicular fallback.

Cone and spread angles are **full angles in degrees**, greater than zero and less than 180. A spot's inner angle may be zero and cannot exceed its outer angle. Area width and height must be finite, positive block distances.

Null required values throw `NullPointerException`. Invalid numeric values throw `IllegalArgumentException` before reaching the renderer. Renderer budgets do not make a valid definition invalid; a submitted light is not guaranteed a visible slot or a shadow slot.

## Persistent handles

Create one handle for a continuing light, then update it when its properties change:

```java
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightHandle;
import com.cappleapple.openlights.api.client.OpenLightsApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ExampleLamp {
    private LightHandle handle;

    public void update(Vec3 position) {
        var definition = new LightDefinition.Point(
                position, new Vec3(1.0, 0.8, 0.6), 1.0f, 16.0f, true, 0.25f);
        if (handle == null || !handle.isValid()) {
            handle = OpenLightsApi.create(new ResourceLocation("example", "lamp"), definition);
        } else {
            handle.update(definition);
        }
    }

    public void remove() {
        if (handle != null) handle.close();
        handle = null;
    }
}
```

The owner resource location identifies the provider; it is not a registry lookup. Replace `example:lamp` with an identifier in your mod's namespace. Each `create` call allocates a unique UUID, available through `handle.key()`.

`create`, `update`, `close`, `isValid`, and `snapshot` are synchronized and may be called from any thread. Consumers must still obey Minecraft's thread rules when reading world or entity state.

- `close()` is idempotent and removes that handle's light.
- `update()` on a closed or invalidated handle throws `IllegalStateException`.
- World unload and resource reload invalidate existing handles. Check `isValid()` before reusing a retained handle.
- `OpenLightsApi.snapshot()` returns an immutable map. Updating a handle cannot mutate an earlier snapshot.
- `OpenLightsApi.clear()` invalidates every handle and is intended for renderer lifecycle management. Providers should close their own handles.

## Profile-driven beams

`BeamLights.spot(position, forward, up, profile)` creates one spot light with independent additive inner/outer layers. Both layers share one shadow view and one renderer slot. Profile types and synchronized lookups are in `com.cappleapple.openlights.beam`. Read a synchronized profile with `BeamProfiles.clientProfile(id)`; see the [datapack and server-default contract](beam-profiles.md).

`LightDefinition.Spot.beamProfile()` is nullable. The original ten-argument constructor remains available and keeps the original single-cone behavior, with no profile dust. Use the `BeamLights` adapter to keep profile and shadow-cone bounds consistent. Rendering still applies the client's maximum range.

## Frame-local lights

Subscribe to `CollectLightsEvent` on the Forge event bus from client-only code. It supplies `partialTick()` for interpolation and accepts definitions through `add(LightKey, LightDefinition)`.

```java
@SubscribeEvent
public static void collect(CollectLightsEvent event) {
    LightKey key = new LightKey(new ResourceLocation("example", "entity_light"), entity.getUUID());
    Vec3 position = entity.getPosition(event.partialTick()).add(0, 1, 0);
    event.add(key, new LightDefinition.Point(
            position, new Vec3(1, 1, 1), 1, 16, true, 0.25f));
}
```

This method fragment assumes the provider already has its relevant client entity. Stable keys let the renderer reuse cached shadow data. Repeated additions with the same key replace that key's definition. Different provider owner IDs separate otherwise identical UUIDs.

The renderer inserts persistent snapshots before posting the event. An event subscriber can therefore replace a persistent definition with the same key for that frame. A frame-local light disappears when the provider stops submitting it.

The event runs on the render thread. Do not retain it, mutate it asynchronously, create GPU resources in a subscriber, or perform an unbounded world scan. `lights()` exposes a read-only view of the current collection.

## Rendering behavior

The renderer selects nearby visible lights within client limits. Point, spot, and area lights require six, one, and four shadow views respectively. Shadow geometry comes from cached block shapes, so this API currently cannot request shadows from entity models.

Sources and receivers are restricted to the cached 32-block radius around the camera, with a smooth boundary fade. Requested range is also capped by the client configuration.

The optional public helper `ShaderCompatibility.isRenderingShadowPass()` identifies Iris/Oculus shadow views. It returns false when that API is absent or on a dedicated server; the client resolves only public API methods once.

Light definitions contain no loader-specific rendering objects. Resource packs may replace Open Lights shader resources, but shaders and internal rendering classes are not part of the public Java compatibility contract.
