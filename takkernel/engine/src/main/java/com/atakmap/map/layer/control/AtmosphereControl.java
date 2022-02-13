package com.atakmap.map.layer.control;

import com.atakmap.map.MapControl;

public interface AtmosphereControl extends MapControl {
    boolean isAtmosphereEnabled();
    void setAtmosphereEnabled(boolean enabled);
}
