package com.atakmap.map.opengl;

import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.util.Releasable;

public interface GLMapBatchable2 extends Releasable {

    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass);
    
    @Override
    public void release();
    
    public int getRenderPass();
}
