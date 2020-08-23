
package com.atakmap.map.layer;

public class MockLayer implements Layer {
    private String name;
    private boolean visible;

    public MockLayer(String name, boolean initVis) {
        this.name = name;
        this.visible = initVis;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public void addOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
    }

    @Override
    public void removeOnLayerVisibleChangedListener(
            OnLayerVisibleChangedListener l) {
    }

    @Override
    public String getName() {
        return this.name;
    }
}
