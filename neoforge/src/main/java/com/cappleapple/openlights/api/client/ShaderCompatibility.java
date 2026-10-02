package com.cappleapple.openlights.api.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/** Optional access to the public Iris/Oculus API without a required shader-loader dependency. */
public final class ShaderCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Access UNAVAILABLE = new Access(null, null, null);
    private static volatile Access access;

    private ShaderCompatibility() {}

    /** Replacement of the vanilla lightmap is unsafe while a shader pack owns material lighting. */
    public static boolean isShaderPackInUse() {
        isRenderingShadowPass(); // Resolve the optional API once.
        Access current = access;
        if (current == null || current == UNAVAILABLE) {
            // If a known shader loader exists but its API failed, keep the safe additive path.
            return net.neoforged.fml.ModList.get().isLoaded("oculus")
                    || net.neoforged.fml.ModList.get().isLoaded("iris")
                    || net.neoforged.fml.ModList.get().isLoaded("optifine");
        }
        try { return Boolean.TRUE.equals(current.shaderPack.invoke(current.instance)); }
        catch (ReflectiveOperationException | RuntimeException exception) { return true; }
    }

    /**
     * True only while Iris/Oculus is drawing its shadow view. The public API is
     * resolved once on first client use; its current pass state is queried each
     * call. Returns false without resolving shader classes on dedicated servers
     * or when the optional API is unavailable.
     */
    public static boolean isRenderingShadowPass() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        Access current = access;
        if (current == null) {
            synchronized (ShaderCompatibility.class) {
                current = access;
                if (current == null) access = current = resolve();
            }
        }
        if (current == UNAVAILABLE) return false;
        try {
            return Boolean.TRUE.equals(current.shadowPass.invoke(current.instance));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            access = UNAVAILABLE;
            LOGGER.warn("Open Lights could not query the optional Iris public shadow-pass API; disabling that adapter", exception);
            return false;
        }
    }

    private static Access resolve() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                    ShaderCompatibility.class.getClassLoader());
            Method shadowPass = api.getMethod("isRenderingShadowPass");
            if (shadowPass.getReturnType() != boolean.class) return UNAVAILABLE;
            Object instance = api.getMethod("getInstance").invoke(null);
            return instance == null ? UNAVAILABLE : new Access(instance, shadowPass, api.getMethod("isShaderPackInUse"));
        } catch (ClassNotFoundException exception) {
            return UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("Open Lights could not resolve the optional Iris public shadow-pass API", exception);
            return UNAVAILABLE;
        }
    }

    private record Access(Object instance, Method shadowPass, Method shaderPack) {}
}
