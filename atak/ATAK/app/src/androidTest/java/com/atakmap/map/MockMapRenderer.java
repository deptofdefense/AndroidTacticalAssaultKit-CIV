
package com.atakmap.map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer2;
import com.atakmap.math.PointD;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class MockMapRenderer implements MapRenderer, MapRenderer2 {
    RenderContext ctx;

    public MockMapRenderer(RenderContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void registerControl(Layer2 layer, MapControl ctrl) {
    }

    @Override
    public void unregisterControl(Layer2 layer, MapControl ctrl) {
    }

    @Override
    public <T extends MapControl> boolean visitControl(Layer2 layer,
            Visitor<T> visitor, Class<T> ctrlClazz) {
        return false;
    }

    @Override
    public boolean visitControls(Layer2 layer,
            Visitor<Iterator<MapControl>> visitor) {
        return false;
    }

    @Override
    public void visitControls(
            Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>> visitor) {

    }

    @Override
    public boolean isRenderThread() {
        return ctx.isRenderThread();
    }

    @Override
    public void queueEvent(Runnable r) {
        ctx.queueEvent(r);
    }

    @Override
    public void requestRefresh() {
        ctx.requestRefresh();
    }

    @Override
    public void setFrameRate(float rate) {
        ctx.setFrameRate(rate);
    }

    @Override
    public float getFrameRate() {
        return ctx.getFrameRate();
    }

    @Override
    public void setContinuousRenderEnabled(boolean enabled) {
        ctx.setContinuousRenderEnabled(enabled);
    }

    @Override
    public boolean isContinuousRenderEnabled() {
        return ctx.isContinuousRenderEnabled();
    }

    @Override
    public void addOnControlsChangedListener(OnControlsChangedListener l) {

    }

    @Override
    public void removeOnControlsChangedListener(OnControlsChangedListener l) {

    }

    @Override
    public RenderContext getRenderContext() {
        return this.ctx;
    }

    @Override
    public boolean lookAt(GeoPoint from, GeoPoint at, boolean animate) {
        return false;
    }

    @Override
    public boolean lookAt(GeoPoint at, double resolution, double azimuth,
            double tilt, boolean animate) {
        return false;
    }

    @Override
    public boolean lookFrom(GeoPoint from, double azimuth, double elevation,
            boolean animate) {
        return false;
    }

    @Override
    public boolean isAnimating() {
        return false;
    }

    @Override
    public MapSceneModel getMapSceneModel(boolean instant,
            DisplayOrigin origin) {
        return null;
    }

    @Override
    public DisplayMode getDisplayMode() {
        return null;
    }

    @Override
    public void setDisplayMode(DisplayMode mode) {

    }

    @Override
    public void setFocusPointOffset(float x, float y) {

    }

    @Override
    public float getFocusPointOffsetX() {
        return 0;
    }

    @Override
    public float getFocusPointOffsetY() {
        return 0;
    }

    @Override
    public DisplayOrigin getDisplayOrigin() {
        return null;
    }

    @Override
    public void addOnCameraChangedListener(OnCameraChangedListener l) {

    }

    @Override
    public void removeOnCameraChangedListener(OnCameraChangedListener l) {

    }

    @Override
    public boolean forward(GeoPoint lla, PointD xyz, DisplayOrigin origin) {
        return false;
    }

    @Override
    public InverseResult inverse(PointD xyz, GeoPoint lla, InverseMode mode,
            int hints, DisplayOrigin origin) {
        return null;
    }

    @Override
    public void setElevationExaggerationFactor(double factor) {

    }

    @Override
    public double getElevationExaggerationFactor() {
        return 0;
    }
}
