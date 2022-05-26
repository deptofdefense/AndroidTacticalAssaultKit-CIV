package com.atakmap.map.layer.model.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.opengl.GLMapRenderable2;

public interface GLSceneSpi {
    public GLMapRenderable2 create(MapRenderer ctx, ModelInfo info, String cacheDir);
    public int getPriority();
}
