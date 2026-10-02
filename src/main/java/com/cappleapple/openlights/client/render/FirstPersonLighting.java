package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import java.util.Arrays;

/** Environment-colored lightmap confined to the first-person hand/item pass. */
final class FirstPersonLighting {
    private static DynamicTexture palette,texture;
    private static boolean active;
    private static int[] previous;
    private static long signature=Long.MIN_VALUE;
    static void palette(DynamicTexture nativeTexture){palette=nativeTexture;}
    static int texture(){return active&&texture!=null?texture.getId():0;}
    static void begin(WorldLightCache cache,boolean enabled) {
        active=false;if(!enabled||palette==null||palette.getPixels()==null)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var position=mc.gameRenderer.getMainCamera().getPosition();
        int value=cache.aggregates().sample(BlockPos.containing(position));
        boolean analytic=cache.aggregates().analytic();
        int nativeValue=cache.blocks().sample(position);
        if(!analytic&&(nativeValue>>>24)>(value>>>24))value=nativeValue;
        float level=(value>>>24)/255f,r=(value>>16&255)/255f,g=(value>>8&255)/255f,b=(value&255)/255f;
        var nativePixels=palette.getPixels();
        long next=value*31L+(analytic?1:0);next=next*31+Double.doubleToLongBits(ClientConfig.INTENSITY_MULTIPLIER.get());
        for(int sky=0;sky<16;sky++){next=next*31+nativePixels.getPixelRGBA(0,sky);next=next*31+nativePixels.getPixelRGBA(Math.min(15,Math.round(level*15)),sky);}
        if(texture!=null&&next==signature){active=true;RenderSystem.setShaderTexture(2,texture.getId());return;}
        int[] pixels=new int[256];
        for(int sky=0;sky<16;sky++)for(int block=0;block<16;block++) {
            int ambient=nativePixels.getPixelRGBA(0,sky),full=nativePixels.getPixelRGBA(Math.min(15,Math.round(level*15)),sky);
            float amount;
            if(analytic){float direct=level*level*2*ClientConfig.INTENSITY_MULTIPLIER.get().floatValue();amount=direct/(1+direct*.65f);}
            else amount=Math.max(Math.max((full&255)-(ambient&255),(full>>8&255)-(ambient>>8&255)),(full>>16&255)-(ambient>>16&255))/255f;
            int red=Math.min(255,Math.round((ambient&255)+255*amount*r));
            int green=Math.min(255,Math.round((ambient>>8&255)+255*amount*g));
            int blue=Math.min(255,Math.round((ambient>>16&255)+255*amount*b));
            pixels[sky*16+block]=0xff000000|(blue<<16)|(green<<8)|red;
        }
        try(var ignored=new GlState();var unpack=new TextureUploadState()) {
            if(texture==null)texture=new DynamicTexture(16,16,false);
            if(!Arrays.equals(previous,pixels)) {
                for(int i=0;i<256;i++)texture.getPixels().setPixelRGBA(i%16,i/16,pixels[i]);
                texture.upload();previous=pixels;
            }
        }
        signature=next;active=true;RenderSystem.setShaderTexture(2,texture.getId());
    }
    static void end(){active=false;}
    static void close(){active=false;if(texture!=null)texture.close();texture=null;previous=null;signature=Long.MIN_VALUE;}
}
