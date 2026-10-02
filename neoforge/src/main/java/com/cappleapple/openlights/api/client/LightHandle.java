package com.cappleapple.openlights.api.client;

/**
 * A persistent client light. Update with immutable definitions; close when its
 * owner disappears. World unload and resource reload invalidate handles. Closing is idempotent;
 * updating an invalid handle throws IllegalStateException.
 */
public interface LightHandle extends AutoCloseable {
    LightKey key();
    void update(LightDefinition definition);
    boolean isValid();
    @Override void close();
}
