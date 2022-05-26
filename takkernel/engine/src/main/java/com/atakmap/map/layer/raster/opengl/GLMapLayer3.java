package com.atakmap.map.layer.raster.opengl;

import com.atakmap.map.MapControl;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.opengl.GLMapRenderable;

public interface GLMapLayer3 extends GLMapRenderable {
    public String getLayerUri();
    public DatasetDescriptor getInfo();
    public <T extends MapControl> T getControl(Class<T> clazz);
}
