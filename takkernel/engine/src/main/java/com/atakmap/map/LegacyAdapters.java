package com.atakmap.map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.TransmuteIterator;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.engine.map.ILayer;
import gov.tak.platform.marshal.MarshalManager;

import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class LegacyAdapters {
    final static Map<WeakReference<MapRenderer>, RenderContext> legacyContextAdapters = new HashMap<>();
    final static Map<WeakReference<MapRenderer2>, gov.tak.api.engine.map.MapRenderer> legacyRendererAdapters = new HashMap<>();


    private LegacyAdapters() {}

    public static gov.tak.api.engine.map.MapRenderer adapt(com.atakmap.map.MapRenderer2 legacy) {
        if(legacy == null)
            return null;
        else if(legacy instanceof gov.tak.api.engine.map.MapRenderer)
            return (gov.tak.api.engine.map.MapRenderer)legacy;
        synchronized(legacyRendererAdapters) {
            Iterator<Map.Entry<WeakReference<MapRenderer2>, gov.tak.api.engine.map.MapRenderer>> iter = legacyRendererAdapters.entrySet().iterator();
            while(iter.hasNext()) {
                final Map.Entry<WeakReference<MapRenderer2>, gov.tak.api.engine.map.MapRenderer> entry = iter.next();
                final MapRenderer2 r = entry.getKey().get();
                if(r == null) {
                    iter.remove();
                    continue;
                } else if(r == legacy) {
                    return entry.getValue();
                }
            }

            final gov.tak.api.engine.map.MapRenderer adapted;
            if(legacy instanceof MapRenderer3)
                adapted = new MapRenderer3Adapter((MapRenderer3)legacy);
            else
                adapted = new MapRenderer2Adapter(legacy);
            legacyRendererAdapters.put(new WeakReference<>(legacy), adapted);
            return adapted;
        }
    }

    public static GLMapView adapt(gov.tak.api.engine.map.MapRenderer renderer) {
        synchronized(legacyRendererAdapters) {
            for(Map.Entry<WeakReference<MapRenderer2>, gov.tak.api.engine.map.MapRenderer> entry : legacyRendererAdapters.entrySet()) {
                if(entry.getValue() != renderer)
                    continue;
                final MapRenderer2 legacy = entry.getKey().get();
                if (legacy instanceof GLMapView)
                    return (GLMapView)legacy;
                break;
            }
        }
        return null;
    }

    public static gov.tak.api.engine.map.RenderContext getRenderContext2(MapRenderer renderer) {
        return getRenderContext(renderer);
    }
    /**
     * Use {@link #getRenderContext2(MapRenderer)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public static RenderContext getRenderContext(MapRenderer renderer) {
        if(renderer instanceof MapRenderer2)
            return ((MapRenderer2)renderer).getRenderContext();
        synchronized(legacyContextAdapters) {
            Iterator<Map.Entry<WeakReference<MapRenderer>, RenderContext>> iter = legacyContextAdapters.entrySet().iterator();
            while(iter.hasNext()) {
                final Map.Entry<WeakReference<MapRenderer>, RenderContext> entry = iter.next();
                final MapRenderer r = entry.getKey().get();
                if(r == null) {
                    iter.remove();
                    continue;
                } else if(r == renderer) {
                    return entry.getValue();
                }
            }

            final  RenderContext adapted = new MapRendererContext(renderer);
            legacyContextAdapters.put(new WeakReference<>(renderer), adapted);
            return adapted;
        }
    }

    final static class MapRendererContext implements RenderContext {
        final MapRenderer impl;
        final RenderSurface surface;

        MapRendererContext(MapRenderer impl) {
            this.impl = impl;
            if(impl instanceof GLMapView)
                surface = ((GLMapView)impl).getRenderSurface();
            else
                surface = new RenderSurface() {
                    @Override
                    public double getDpi() { return 96d; }
                    @Override
                    public int getWidth() { return 1; }
                    @Override
                    public int getHeight() { return 1; }
                    @Override
                    public void addOnSizeChangedListener(OnSizeChangedListener l) {}
                    @Override
                    public void removeOnSizeChangedListener(OnSizeChangedListener l) {}
                };
        }

        @Override
        public boolean isRenderThread() { return this.impl.isRenderThread(); }
        @Override
        public void queueEvent(Runnable r) { this.impl.queueEvent(r); }
        @Override
        public void requestRefresh() { this.impl.requestRefresh(); }
        @Override
        public void setFrameRate(float rate) { this.impl.setFrameRate(rate); }
        @Override
        public float getFrameRate() { return this.impl.getFrameRate(); }
        @Override
        public void setContinuousRenderEnabled(boolean enabled) { this.impl.setContinuousRenderEnabled(enabled); }
        @Override
        public boolean isContinuousRenderEnabled() { return this.impl.isContinuousRenderEnabled(); }
        @Override
        public boolean supportsChildContext() { return false; }
        @Override
        public RenderContext createChildContext() { return null; }
        @Override
        public void destroyChildContext(gov.tak.api.engine.map.RenderContext child) {}
        @Override
        public boolean isAttached() { return this.isRenderThread(); }
        @Override
        public boolean attach() { return isAttached(); }
        @Override
        public boolean detach() { return false; }
        @Override
        public boolean isMainContext() { return true; }
        @Override
        public RenderSurface getRenderSurface() { return surface; }
    }

    static class MapRenderer2Adapter implements gov.tak.api.engine.map.MapRenderer<RenderContext> {

        final MapRenderer2 _impl;
        final Map<gov.tak.api.engine.map.MapRenderer.OnCameraChangedListener, MapRenderer2.OnCameraChangedListener> _onCameraChangedForwarders = new IdentityHashMap<>();
        final Map<gov.tak.api.engine.map.MapRenderer.OnControlsChangedListener, MapRendererBase.OnControlsChangedListener> _onControlsChangedForwarders = new IdentityHashMap<>();

        MapRenderer2Adapter(MapRenderer2 impl) {
            _impl = impl;
        }

        @Override
        public boolean lookAt(gov.tak.api.engine.map.coords.GeoPoint from, gov.tak.api.engine.map.coords.GeoPoint at, CameraCollision collision, boolean animate) {
            return _impl.lookAt(
                    MarshalManager.marshal(from, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    MarshalManager.marshal(at, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    animate);
        }

        @Override
        public boolean lookAt(gov.tak.api.engine.map.coords.GeoPoint at, double resolution, double azimuth, double tilt, CameraCollision collision, boolean animate) {
            return _impl.lookAt(
                    MarshalManager.marshal(at, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    resolution,
                    azimuth,
                    tilt,
                    animate);
        }

        @Override
        public boolean lookFrom(gov.tak.api.engine.map.coords.GeoPoint from, double azimuth, double elevation, CameraCollision collision, boolean animate) {
            return _impl.lookFrom(
                    MarshalManager.marshal(from, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    azimuth,
                    elevation,
                    animate);
        }

        @Override
        public <T> T getControl(Class<T> controlType) {
            if(!MapControl.class.isAssignableFrom(controlType))
                return null;
            final Object[] control = new Object[1];
            _impl.visitControls(null, new com.atakmap.util.Visitor<Iterator<MapControl>>() {
                @Override
                public void visit(Iterator<MapControl> it) {
                    while(it.hasNext()) {
                        final MapControl ctrl = it.next();
                        if (!controlType.isAssignableFrom(ctrl.getClass()))
                            continue;
                        control[0] = ctrl;
                        break;
                    }
                }
            });
            return (T)control[0];
        }

        @Override
        final public boolean isAnimating() {
            return _impl.isAnimating();
        }

        @Override
        final public gov.tak.api.engine.map.MapSceneModel getMapSceneModel(boolean instant, DisplayOrigin origin) {
            return MarshalManager.marshal(_impl.getMapSceneModel(instant, origin), com.atakmap.map.MapSceneModel.class, gov.tak.api.engine.map.MapSceneModel.class);
        }

        @Override
        final public DisplayMode getDisplayMode() {
            return _impl.getDisplayMode();
        }

        @Override
        final public void setDisplayMode(DisplayMode mode) {
            _impl.setDisplayMode(mode);
        }

        @Override
        final public void setFocusPointOffset(float x, float y) {
            _impl.setFocusPointOffset(x, y);
        }

        @Override
        final public float getFocusPointOffsetX() {
            return _impl.getFocusPointOffsetX();
        }

        @Override
        final public float getFocusPointOffsetY() {
            return _impl.getFocusPointOffsetY();
        }

        @Override
        final public DisplayOrigin getDisplayOrigin() {
            return _impl.getDisplayOrigin();
        }

        @Override
        final public void addOnCameraChangedListener(gov.tak.api.engine.map.MapRenderer.OnCameraChangedListener l) {
            final MapRenderer2.OnCameraChangedListener forwarder;
            synchronized(_onCameraChangedForwarders) {
                if(_onCameraChangedForwarders.containsKey(l))   return;
                forwarder = new CameraChangedEventForwarder(this, l);
                _onCameraChangedForwarders.put(l, forwarder);
            }
            _impl.addOnCameraChangedListener(forwarder);
        }

        @Override
        final public void removeOnCameraChangedListener(gov.tak.api.engine.map.MapRenderer.OnCameraChangedListener l) {
            final MapRenderer2.OnCameraChangedListener forwarder;
            synchronized(_onCameraChangedForwarders) {
                forwarder = _onCameraChangedForwarders.remove(l);
                if(forwarder == null)   return;
            }
            _impl.removeOnCameraChangedListener(forwarder);
        }

        @Override
        final public boolean forward(gov.tak.api.engine.map.coords.GeoPoint lla, gov.tak.api.engine.math.PointD xyz, DisplayOrigin origin) {
            com.atakmap.math.PointD result = new com.atakmap.math.PointD();
            if(!_impl.forward(
                    MarshalManager.marshal(lla, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    result,
                    origin)) {
                return false;
            }
            xyz.x = result.x;
            xyz.y = result.y;
            xyz.z = result.z;
            return true;
        }

        @Override
        final public InverseResult inverse(gov.tak.api.engine.math.PointD xyz, gov.tak.api.engine.map.coords.GeoPoint lla, InverseMode mode, int modeHints, DisplayOrigin origin) {
            com.atakmap.coremap.maps.coords.GeoPoint geo = com.atakmap.coremap.maps.coords.GeoPoint.createMutable();
            final InverseResult result = _impl.inverse(
                    new com.atakmap.math.PointD(xyz.x, xyz.y, xyz.z),
                    geo,
                    mode,
                    modeHints,
                    origin);
            if(result != InverseResult.None) {
                lla.set(
                        geo.getLatitude(),
                        geo.getLongitude(),
                        geo.getAltitude(),
                        MarshalManager.marshal(geo.getAltitudeReference(), GeoPoint.AltitudeReference.class, gov.tak.api.engine.map.coords.GeoPoint.AltitudeReference.class),
                        geo.getCE(),
                        geo.getLE());
            }
            return result;
        }

        @Override
        final public void setElevationExaggerationFactor(double factor) {
            _impl.setElevationExaggerationFactor(factor);
        }

        @Override
        final public double getElevationExaggerationFactor() {
            return _impl.getElevationExaggerationFactor();
        }

        @Override
        final public void registerControl(gov.tak.api.engine.map.ILayer layer, Object ctrl) {
            if(ctrl instanceof MapControl) {
                final com.atakmap.map.layer.Layer adapted = com.atakmap.map.layer.LegacyAdapters.adapt(layer);
                if(adapted instanceof com.atakmap.map.layer.Layer2)
                    _impl.registerControl((com.atakmap.map.layer.Layer2)adapted, (MapControl) ctrl);
            }
        }

        @Override
        final public void unregisterControl(gov.tak.api.engine.map.ILayer layer, Object ctrl) {
            if(ctrl instanceof MapControl) {
                final com.atakmap.map.layer.Layer adapted = com.atakmap.map.layer.LegacyAdapters.adapt(layer);
                if(adapted instanceof com.atakmap.map.layer.Layer2)
                    _impl.unregisterControl((com.atakmap.map.layer.Layer2)adapted, (MapControl) ctrl);
            }
        }

        @Override
        final public <T> boolean visitControl(gov.tak.api.engine.map.ILayer layer, final gov.tak.api.util.Visitor<T> visitor, final Class<T> ctrlClazz) {
            if(!MapControl.class.isAssignableFrom(ctrlClazz))
                return false;
            final boolean[] retval = new boolean[] {false};
            final com.atakmap.map.layer.Layer adapted = com.atakmap.map.layer.LegacyAdapters.adapt(layer);
            if(!(adapted instanceof com.atakmap.map.layer.Layer2))  return false;
            _impl.visitControls((com.atakmap.map.layer.Layer2)adapted, new com.atakmap.util.Visitor<Iterator<MapControl>>() {
                @Override
                public void visit(Iterator<MapControl> it) {
                    while(it.hasNext()) {
                        final Object ctrl = it.next();
                        if(!ctrlClazz.isAssignableFrom(ctrl.getClass())) continue;
                        visitor.visit((T)ctrl);
                        retval[0] = true;
                        break;
                    }
                }
            });
            return retval[0];
        }

        @Override
        final public boolean visitControls(gov.tak.api.engine.map.ILayer layer, final gov.tak.api.util.Visitor<Iterator<Object>> visitor) {
            final com.atakmap.map.layer.Layer adapted = com.atakmap.map.layer.LegacyAdapters.adapt(layer);
            if(!(adapted instanceof com.atakmap.map.layer.Layer2))  return false;
            return _impl.visitControls((com.atakmap.map.layer.Layer2)adapted, new com.atakmap.util.Visitor<Iterator<MapControl>>() {
                @Override
                public void visit(Iterator<MapControl> object) {
                    visitor.visit((Iterator)object);
                }
            });
        }

        @Override
        final public void visitControls(final gov.tak.api.util.Visitor<Iterator<Map.Entry<gov.tak.api.engine.map.ILayer, Collection<Object>>>> visitor) {
            _impl.visitControls(new com.atakmap.util.Visitor<Iterator<Map.Entry<com.atakmap.map.layer.Layer2, Collection<MapControl>>>>() {
                @Override
                public void visit(Iterator<Map.Entry<com.atakmap.map.layer.Layer2, Collection<MapControl>>> object) {
                    visitor.visit(new TransmuteIterator<Map.Entry<com.atakmap.map.layer.Layer2, Collection<MapControl>>, Map.Entry<gov.tak.api.engine.map.ILayer, Collection<Object>>>(object) {
                        @Override
                        protected Map.Entry<ILayer, Collection<Object>> transmute(Map.Entry<Layer2, Collection<MapControl>> arg) {
                            return new AbstractMap.SimpleImmutableEntry<ILayer, Collection<Object>>(com.atakmap.map.layer.LegacyAdapters.adapt2(arg.getKey()), (Collection)arg.getValue());
                        }
                    });
                }
            });
        }

        @Override
        final public void addOnControlsChangedListener(gov.tak.api.engine.map.MapRenderer.OnControlsChangedListener l) {
            final MapRendererBase.OnControlsChangedListener forwarder;
            synchronized(_onControlsChangedForwarders) {
                if(_onControlsChangedForwarders.containsKey(l)) return;
                forwarder = new ControlsChangedEventForwarder(this, l);
                _onControlsChangedForwarders.put(l, forwarder);
            }
            _impl.addOnControlsChangedListener(forwarder);
        }

        @Override
        final public void removeOnControlsChangedListener(gov.tak.api.engine.map.MapRenderer.OnControlsChangedListener l) {
            final MapRendererBase.OnControlsChangedListener forwarder;
            synchronized(_onControlsChangedForwarders) {
                forwarder = _onControlsChangedForwarders.remove(l);
                if(forwarder == null) return;
            }
            _impl.removeOnControlsChangedListener(forwarder);
        }

        @Override
        final public RenderContext getRenderContext() {
            return _impl.getRenderContext();
        }
    }

    final static class MapRenderer3Adapter extends MapRenderer2Adapter {
        final MapRenderer3 _impl;

        MapRenderer3Adapter(MapRenderer3 impl) {
            super(impl);

            _impl = impl;
        }

        @Override
        public boolean lookAt(gov.tak.api.engine.map.coords.GeoPoint from, gov.tak.api.engine.map.coords.GeoPoint at, CameraCollision collision, boolean animate) {
            return _impl.lookAt(
                    MarshalManager.marshal(from, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    MarshalManager.marshal(at, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    collision,
                    animate);
        }

        @Override
        public boolean lookAt(gov.tak.api.engine.map.coords.GeoPoint at, double resolution, double azimuth, double tilt, CameraCollision collision, boolean animate) {
            return _impl.lookAt(
                    MarshalManager.marshal(at, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    resolution,
                    azimuth,
                    tilt,
                    collision,
                    animate);
        }

        @Override
        public boolean lookFrom(gov.tak.api.engine.map.coords.GeoPoint from, double azimuth, double elevation, CameraCollision collision, boolean animate) {
            return _impl.lookFrom(
                    MarshalManager.marshal(from, gov.tak.api.engine.map.coords.GeoPoint.class, com.atakmap.coremap.maps.coords.GeoPoint.class),
                    azimuth,
                    elevation,
                    collision,
                    animate);
        }

        @Override
        public <T> T getControl(Class<T> controlType) {
            return _impl.getControl(controlType);
        }
    }

    final static class CameraChangedEventForwarder implements MapRenderer2.OnCameraChangedListener {
        final WeakReference<gov.tak.api.engine.map.MapRenderer> _target;
        final gov.tak.api.engine.map.MapRenderer.OnCameraChangedListener _cb;

        CameraChangedEventForwarder(gov.tak.api.engine.map.MapRenderer target, gov.tak.api.engine.map.MapRenderer.OnCameraChangedListener cb) {
            _target = new WeakReference<>(target);
            _cb = cb;
        }

        @Override
        public void onCameraChanged(MapRenderer2 renderer) {
            final gov.tak.api.engine.map.MapRenderer target = _target.get();
            if(target != null)  _cb.onCameraChanged(target);
        }
    }

    final static class ControlsChangedEventForwarder implements MapRendererBase.OnControlsChangedListener {
        final WeakReference<gov.tak.api.engine.map.MapRenderer> _target;
        final gov.tak.api.engine.map.MapRenderer.OnControlsChangedListener _cb;

        ControlsChangedEventForwarder(gov.tak.api.engine.map.MapRenderer target, gov.tak.api.engine.map.MapRenderer.OnControlsChangedListener cb) {
            _target = new WeakReference<>(target);
            _cb = cb;
        }

        @Override
        public void onControlRegistered(com.atakmap.map.layer.Layer2 layer, MapControl ctrl) {
            final gov.tak.api.engine.map.MapRenderer target = _target.get();
            if(target != null)  _cb.onControlRegistered(com.atakmap.map.layer.LegacyAdapters.adapt2(layer), ctrl);
        }

        @Override
        public void onControlUnregistered(com.atakmap.map.layer.Layer2 layer, MapControl ctrl) {
            final gov.tak.api.engine.map.MapRenderer target = _target.get();
            if(target != null)  _cb.onControlUnregistered(com.atakmap.map.layer.LegacyAdapters.adapt2(layer), ctrl);
        }
    }
}
