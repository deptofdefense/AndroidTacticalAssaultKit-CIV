package com.atakmap.map.layer.opengl;

import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapRenderable;

public interface GLLayer extends GLMapRenderable {

    public Layer getSubject();
}
