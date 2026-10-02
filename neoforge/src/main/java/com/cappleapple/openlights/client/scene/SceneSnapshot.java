package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Objects;

/** Immutable CPU geometry. Read the vertex array only when revision changes, then reuse the GPU buffer. */
public final class SceneSnapshot {
    private final long revision;
    private final Vec3 origin;
    private final float[] opaqueTriangles;
    private final List<Medium> media;

    public SceneSnapshot(long revision, Vec3 origin, float[] opaqueTriangles, List<Medium> media) {
        this.revision = revision;
        this.origin = Objects.requireNonNull(origin);
        this.opaqueTriangles = opaqueTriangles.clone();
        this.media = List.copyOf(media);
    }

    public long revision() { return revision; }
    public Vec3 origin() { return origin; }
    public float[] opaqueTriangles() { return opaqueTriangles.clone(); }
    public List<Medium> media() { return media; }
}
