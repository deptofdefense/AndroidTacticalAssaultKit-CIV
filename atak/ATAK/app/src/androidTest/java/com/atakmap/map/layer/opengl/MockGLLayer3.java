
package com.atakmap.map.layer.opengl;

import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;

public class MockGLLayer3 implements GLLayer3 {
    Layer subject;
    int renderPass;

    public MockGLLayer3(Layer subject, int renderPass) {
        this.subject = subject;
        this.renderPass = renderPass;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    @Override
    public void draw(GLMapView view) {

    }

    @Override
    public void draw(GLMapView view, int renderPass) {

    }

    @Override
    public void release() {

    }

    @Override
    public int getRenderPass() {
        return this.renderPass;
    }
}
