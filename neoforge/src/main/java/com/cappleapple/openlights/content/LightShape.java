package com.cappleapple.openlights.content;
import net.minecraft.util.StringRepresentable;
public enum LightShape implements StringRepresentable {
    POINT, SPOT, AREA;
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
