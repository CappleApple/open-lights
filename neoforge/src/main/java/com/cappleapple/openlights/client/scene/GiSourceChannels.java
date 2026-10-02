package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Sparse source columns on the CPU and one cached, unclamped sum per probe. */
final class GiSourceChannels {
    static final Object OVERFLOW=new Object();
    final Map<Object,float[]> columns=new HashMap<>();
    private float[] sum;
    private final ProbeGrid grid;
    private final long budget;
    private int x,y,z;
    GiSourceChannels(ProbeGrid grid,long budget){this.grid=grid;this.budget=budget;sum=new float[grid.count()*3];x=grid.minimumX();y=grid.minimumY();z=grid.minimumZ();}
    boolean retain(Set<?> live) {
        boolean changed=false;var iterator=columns.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();if(live.contains(entry.getKey()))continue;
            var values=entry.getValue();
            for(int i=0;i<sum.length;i++){sum[i]=Math.max(0,sum[i]-values[i]);int pixel=i/3*4+i%3;float value=Math.min(16,sum[i]);if(grid.data[pixel]!=value){grid.data[pixel]=value;changed=true;}}
            iterator.remove();
        }
        return changed;
    }
    void move() {
        int nx=grid.minimumX(),ny=grid.minimumY(),nz=grid.minimumZ();
        if(nx==x&&ny==y&&nz==z)return;
        int dx=(nx-x)/grid.spacing,dy=(ny-y)/grid.spacing,dz=(nz-z)/grid.spacing,size=grid.size;
        sum=new float[grid.count()*3];
        columns.replaceAll((source,previous)->{
            var next=new float[previous.length];
            for(int i=0;i<grid.count();i++) {
                int ox=i%size+dx,oy=i/size%size+dy,oz=i/(size*size)+dz;
                if(ox<0||oy<0||oz<0||ox>=size||oy>=size||oz>=size)continue;
                int old=ox+size*(oy+size*oz);
                for(int c=0;c<3;c++){next[i*3+c]=previous[old*3+c];sum[i*3+c]+=next[i*3+c];}
            }
            return next;
        });x=nx;y=ny;z=nz;
    }
    Vec3 replace(int index,Map<Object,Vec3> values) {
        for(var column:columns.values())Arrays.fill(column,index*3,index*3+3,0);
        Vec3 overflow=Vec3.ZERO,total=Vec3.ZERO;
        for(var entry:values.entrySet()) {
            Vec3 incoming=entry.getValue();Vec3 value=new Vec3(clean(incoming.x),clean(incoming.y),clean(incoming.z));if(value.lengthSqr()==0)continue;
            float[] column=columns.get(entry.getKey());
            if(column==null) {
                if((columns.size()+3L)*grid.count()*12>budget){overflow=overflow.add(value);total=total.add(value);continue;}
                column=new float[grid.count()*3];columns.put(entry.getKey(),column);
            }
            put(column,index,value);total=total.add(value);
        }
        if(overflow.lengthSqr()>0){var column=columns.computeIfAbsent(OVERFLOW,key->new float[grid.count()*3]);put(column,index,overflow);}
        put(sum,index,total);return total;
    }
    boolean clear(){return retain(Set.of());}
    long bytes(){return (columns.size()+1L)*grid.count()*12;}
    int count(){return columns.size();}
    private static double clean(double value){return Double.isFinite(value)?Math.max(0,Math.min(1e6,value)):0;}
    private static void put(float[] data,int index,Vec3 value){data[index*3]=(float)value.x;data[index*3+1]=(float)value.y;data[index*3+2]=(float)value.z;}
}
