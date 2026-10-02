package com.cappleapple.openlights.client.scene;

/** Bounded irradiance blend. Weak contributions fade in without changing the single-source response. */
final class LightBlend {
    private float weight,peak,red,green,blue,x,y,z;

    void add(double value,int color,double dx,double dy,double dz) {
        double contribution=value;
        weight+=contribution;peak=Math.max(peak,(float)value);
        red+=(color>>16&255)*contribution;green+=(color>>8&255)*contribution;blue+=(color&255)*contribution;
        x+=dx*contribution;y+=dy*contribution;z+=dz*contribution;
    }
    int value() {
        if(weight==0)return 0;
        int alpha=channel(Math.sqrt(peak)*255);
        return (alpha<<24)|(channel(red/weight)<<16)|(channel(green/weight)<<8)|channel(blue/weight);
    }
    int direction() {
        if(weight==0)return 0x808080;
        // The moment's length encodes cancellation between opposing lights. Normalizing
        // it would restore a sharp directional flip at the midpoint of their overlap.
        return (channel((x/weight*.5+.5)*255)<<16)|(channel((y/weight*.5+.5)*255)<<8)|channel((z/weight*.5+.5)*255);
    }
    private static int channel(double value){return Math.max(0,Math.min(255,(int)Math.round(value)));}
}
