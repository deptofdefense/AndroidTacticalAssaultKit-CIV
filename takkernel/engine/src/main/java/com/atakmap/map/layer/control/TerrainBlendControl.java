package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

public interface TerrainBlendControl extends MapControl {
    double getBlendFactor();
    boolean getEnabled();
    void setEnabled(boolean enabled);
    void setBlendFactor(double blendFactor);
}
