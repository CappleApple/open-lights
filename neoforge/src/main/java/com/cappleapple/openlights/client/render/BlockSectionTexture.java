package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.client.scene.BlockLightSections;
import com.cappleapple.openlights.config.ClientConfig;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.util.HashMap;
import java.util.Map;

/** Budgeted native/aggregate atlases. Resize into a staging atlas while retaining the visible one. */
final class BlockSectionTexture implements AutoCloseable {
    private Atlas active,staging;
    private long generation=Long.MIN_VALUE;
    private int uploaded;
    private boolean pending;
    int uploaded(){return uploaded;}
    boolean pending(){return pending;}
    void bind(GlProgram shader,BlockLightSections cache,Vec3 origin) {
        if(generation!=cache.generation()){close();generation=cache.generation();}
        int side=1;while(side*side*side<cache.slots())side*=2;
        int layers=1;while(layers*side*side<cache.slots())layers*=2;
        Budget budget=new Budget();
        try(var ignored=new TextureUploadState()) {
            if(active==null)active=new Atlas(side,layers);
            if(active.side!=side||active.layers!=layers) {
                if(staging==null||staging.side!=side||staging.layers!=layers){if(staging!=null)staging.close();staging=new Atlas(side,layers);}
                if(staging.upload(cache,budget)) {
                    staging.pages(cache);active.close();active=staging;staging=null;
                }
            }
            if(staging==null){pending=!active.upload(cache,budget);active.pages(cache);}
            else pending=true;
        }
        uploaded=budget.used;
        active.bind(shader,origin);
    }
    private static final class Budget {
        final long deadline=System.nanoTime()+(long)(ClientConfig.LIGHT_UPLOAD_MILLIS.get()*1_000_000);
        final int maximum=ClientConfig.LIGHT_UPLOAD_SECTIONS.get();
        int used;
        boolean available(){return used<maximum&&(used==0||System.nanoTime()<deadline);}
    }
    private static final class Atlas implements AutoCloseable {
        final int side,layers,nativeTexture,aggregateTexture,directionTexture,table;
        final Map<Integer,Long> nativeRevision=new HashMap<>(),aggregateRevision=new HashMap<>(),identities=new HashMap<>();
        int width,height,minX,minY,minZ,tableWidth,tableHeight;
        long completeRevision=Long.MIN_VALUE,tableRevision=Long.MIN_VALUE,uploadRevision,tableUploadRevision=-1;
        Atlas(int side,int layers) {
            this.side=side;this.layers=layers;
            int maximum=GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE);
            if(side*18>maximum||layers*18>maximum)throw new IllegalStateException("Block light atlas exceeds GPU texture dimensions");
            nativeTexture=texture(8,side,layers);aggregateTexture=texture(10,side,layers);directionTexture=texture(11,side,layers);table=GL11.glGenTextures();
        }
        private static int texture(int unit,int side,int layers) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGenTextures();GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL11.GL_RGBA8,side*18,side*18,layers*18,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
            parameters(GL11.GL_LINEAR);return texture;
        }
        boolean upload(BlockLightSections cache,Budget budget) {
            if(completeRevision==cache.revision())return true;
            boolean complete=true;
            for(var tile:cache.tiles()) {
                if(tile.data==null)continue;
                boolean fresh=identities.getOrDefault(tile.slot,-1L)!=tile.identity;
                boolean nativeDirty=fresh||nativeRevision.getOrDefault(tile.slot,Long.MIN_VALUE)!=tile.revision;
                boolean aggregateDirty=fresh||aggregateRevision.getOrDefault(tile.slot,Long.MIN_VALUE)!=tile.aggregateRevision;
                if(!nativeDirty&&!aggregateDirty)continue;
                if(!budget.available()){complete=false;continue;}
                if(nativeDirty){upload(8,nativeTexture,tile.slot,tile.data);nativeRevision.put(tile.slot,tile.revision);}
                if(aggregateDirty){upload(10,aggregateTexture,tile.slot,tile.aggregateData==null?BlockLightSections.EMPTY_BRICK:tile.aggregateData);upload(11,directionTexture,tile.slot,tile.directionData==null?BlockLightSections.EMPTY_BRICK:tile.directionData);aggregateRevision.put(tile.slot,tile.aggregateRevision);}
                identities.put(tile.slot,tile.identity);uploadRevision++;budget.used++;
            }
            if(complete)completeRevision=cache.revision();
            return complete;
        }
        private void upload(int unit,int texture,int slot,byte[] data) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            var bytes=MemoryUtil.memAlloc(data.length);
            try {
                bytes.put(data).flip();
                GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,(slot%side)*18,(slot/side%side)*18,(slot/(side*side))*18,18,18,18,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            }finally{MemoryUtil.memFree(bytes);}
        }
        void pages(BlockLightSections cache) {
            if(tableRevision==cache.revision()&&tableUploadRevision==uploadRevision)return;
            GL13.glActiveTexture(GL13.GL_TEXTURE9);GL11.glBindTexture(GL12.GL_TEXTURE_3D,table);
            width=cache.width;height=cache.height;minX=cache.minX;minY=cache.minY;minZ=cache.minZ;
            int w=Math.max(1,width),h=Math.max(1,height);
            if(w!=tableWidth||h!=tableHeight){
                tableWidth=w;tableHeight=h;
                GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_R32I,w,h,w,0,GL30.GL_RED_INTEGER,GL11.GL_INT,(java.nio.ByteBuffer)null);
                parameters(GL11.GL_NEAREST);
            }
            boolean[] initialized=new boolean[cache.slots()];
            for(var tile:cache.tiles())if(identities.getOrDefault(tile.slot,-1L)==tile.identity)initialized[tile.slot]=true;
            int[] pages=cache.pages.length==0?new int[]{0}:cache.pages.clone();
            for(int i=0;i<pages.length;i++)if(pages[i]>0&&!initialized[pages[i]-1])pages[i]=0;
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,0,0,0,w,h,w,GL30.GL_RED_INTEGER,GL11.GL_INT,pages);
            tableRevision=cache.revision();tableUploadRevision=uploadRevision;
        }
        void bind(GlProgram shader,Vec3 origin) {
            GL13.glActiveTexture(GL13.GL_TEXTURE8);GL11.glBindTexture(GL12.GL_TEXTURE_3D,nativeTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE9);GL11.glBindTexture(GL12.GL_TEXTURE_3D,table);
            GL13.glActiveTexture(GL13.GL_TEXTURE10);GL11.glBindTexture(GL12.GL_TEXTURE_3D,aggregateTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE11);GL11.glBindTexture(GL12.GL_TEXTURE_3D,directionTexture);
            shader.integer("BlockSectionAtlas",8);shader.integer("BlockSectionTable",9);shader.integer("AggregateSectionAtlas",10);
            shader.integer("BlockDirectionAtlas",11);
            shader.scalar("BlockSectionSide",side);shader.scalar("BlockSectionLayers",layers);
            shader.vec3("BlockSectionMinimum",(float)(minX*16.0-origin.x),(float)(minY*16.0-origin.y),(float)(minZ*16.0-origin.z));
            shader.vec2("BlockSectionSize",width,height);
        }
        public void close(){
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(nativeTexture);
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(aggregateTexture);
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(directionTexture);
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(table);
        }
    }
    private static void parameters(int filter) {
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,filter);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,filter);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_R,GL12.GL_CLAMP_TO_EDGE);
    }
    public void close(){if(active!=null)active.close();if(staging!=null)staging.close();active=staging=null;generation=Long.MIN_VALUE;uploaded=0;pending=false;}
}
