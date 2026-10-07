package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/** Client render-thread access to Distant Horizons' public render events and depth texture. */
public final class DistantHorizonsCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized, installed, disabled, frameOpen;
    private static Frame frame;

    private DistantHorizonsCompatibility() {}

    /** Starts before either renderer draws terrain, preventing reuse of another frame's depth. */
    public static void beginFrame() {
        frame = null;
        frameOpen = true;
        if (initialized || disabled) return;
        initialized = true;
        installed = ModList.get().isLoaded("distanthorizons");
        if (!installed) return;
        try {
            Adapter.register();
        } catch (LinkageError | RuntimeException exception) {
            disable(exception);
        }
    }

    /** Returns null unless DH completed a terrain pass during this world frame. */
    public static Frame snapshot() { return frame; }

    /** Native ambient must be lit before DH blends it with its already-lit terrain. */
    public static boolean preserveNativeSky() { return installed; }

    public static void clearFrame() {
        frame = null;
        frameOpen = false;
    }

    public record Frame(int depthTexture, Matrix4f inverseProjection, Matrix4f inverseView,
                        boolean reversedDepth, boolean zeroToOneDepth) {}

    private static void disable(Throwable exception) {
        frame = null;
        if (!disabled) {
            disabled = true;
            LOGGER.warn("Open Lights could not access Distant Horizons' public depth API; disabling its LOD depth adapter", exception);
        }
    }

    // Keeping every DH type in this nested class lets the normal renderer load without DH installed.
    private static final class Adapter {
        private static boolean depthConventionApi;

        static void register() {
            int major = com.seibel.distanthorizons.api.DhApi.getApiMajorVersion();
            int minor = com.seibel.distanthorizons.api.DhApi.getApiMinorVersion();
            if (major < 3) throw new IllegalStateException("Distant Horizons API 3.0.0 or newer is required");
            depthConventionApi = major > 7 || (major == 7 && minor >= 2);
            var result = com.seibel.distanthorizons.api.methods.events.DhApiEventRegister.on(
                    com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent.class,
                    new Capture());
            if (!result.success) throw new IllegalStateException(result.message);
        }

        private static final class Capture extends com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent {
            @Override
            public void beforeCleanup(com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam<
                    com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam> event) {
                if (!frameOpen || disabled || event == null || event.value == null
                        || ShaderCompatibility.isRenderingShadowPass()) return;
                var parameter = event.value;
                if (parameter.renderPass == com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass.TRANSPARENT) return;
                try {
                    var proxy = com.seibel.distanthorizons.api.DhApi.Delayed.renderProxy;
                    if (proxy == null) return;
                    // The original getter remains available on both old and current DH APIs.
                    var depth = proxy.getDhDepthTextureId();
                    if (!depth.success || depth.payload == null || depth.payload <= 0 || !GL11.glIsTexture(depth.payload)) return;
                    boolean reversed = false, zeroToOne = false;
                    if (depthConventionApi) {
                        reversed = proxy.getDepthDirection() == com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection.REVERSE_Z;
                        zeroToOne = proxy.getDepthRange() == com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange.ZERO_TO_POS_ONE;
                    }
                    Matrix4f projection = matrix(parameter.dhProjectionMatrix);
                    Matrix4f view = matrix(parameter.dhModelViewMatrix);
                    if (!projection.isFinite() || !view.isFinite()
                            || Math.abs(projection.determinant()) < 1.0e-12f || Math.abs(view.determinant()) < 1.0e-12f) return;
                    projection.invert();
                    view.invert();
                    if (!projection.isFinite() || !view.isFinite()) return;
                    // DH's vertex model offset already includes minimum world Y and subtracts the camera.
                    // worldYOffset is used for height clipping, so adding it here would shift all receivers.
                    frame = new Frame(depth.payload, projection, view, reversed, zeroToOne);
                } catch (LinkageError | RuntimeException exception) {
                    disable(exception);
                }
            }
        }

        private static Matrix4f matrix(com.seibel.distanthorizons.api.objects.math.DhApiMat4f source) {
            // DH arrays contain rows; JOML's array setter consumes columns.
            return new Matrix4f().set(source.getValuesAsArray()).transpose();
        }
    }
}
