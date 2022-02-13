package com.atakmap.map;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.LegacyAdapters;
import com.atakmap.util.Collections2;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.engine.map.IGlobe;
import gov.tak.api.engine.map.ILayer;

/**
 * <B>WARNING: VIEW STATE INFORMATION AND LISTENERS WILL BE REFACTORED OUT OF THIS CLASS</B>
 */
@DontObfuscate
public final class Globe implements IGlobe, Disposable {
    static {
        EngineLibrary.initialize();
    }

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        public void run(Pointer pointer, Object opaque) {
            if(pointer.raw != 0L) {
                final Pointer callbackForwarder = (Pointer)opaque;
                unregisterNativeCallbackForwarder(pointer.raw, callbackForwarder);
            }
            destruct(pointer);
        }
    };

    final static Interop<Layer> Layer_interop = Interop.findInterop(Layer.class);

    @DontObfuscate
    public interface OnLayersChangedListener {
        void onLayerAdded(Globe mapView, Layer layer);
        void onLayerRemoved(Globe mapView, Layer layer);

        /**
         * Notifies the user when the position of the layer has been explicitly
         * changed. This callback will <B>NOT</B> be invoked when a layer's
         * position changes due to the addition or removal of other layers.
         *
         * @param mapView       The map view
         * @param layer         The layer
         * @param oldPosition   The layer's old position
         * @param newPosition   The layer's new position
         */
        void onLayerPositionChanged(Globe mapView, Layer layer, int oldPosition, int newPosition);
    }

    @DontObfuscate
    interface OnElevationExaggerationFactorChangedListener {
        void onTerrainExaggerationFactorChanged(Globe mapView, double factor);
    }

    @DontObfuscate
    interface OnContinuousScrollEnabledChangedListener {
        void onContinuousScrollEnabledChanged(Globe mapView, boolean enabled);
    }

    /**
     * View bounds resize listener
     *
     */
    @DontObfuscate
    interface OnMapViewResizedListener {
        /**
         * The MapView's bounds changed
         *
         * @param view
         */
        void onMapViewResized(Globe view);
    }

    /**
     */
    @DontObfuscate
    static interface OnMapMovedListener {
        /**
         * @param view
         * @param animate       smooth transition requested
         */
        void onMapMoved (Globe view, boolean animate);
    }

    /**
     * Callback interface that notifies on map projection changes.
     *
     * @author Developer
     */
    @DontObfuscate
    static interface OnMapProjectionChangedListener {
        /**
         * Invoked when the map projection changes
         *
         * @param view  The map whose projection changed
         */
        void onMapProjectionChanged(Globe view);
    } // OnMapProjectionChangedListener

    /**
     * Callback interface that notifies on map projection changes.
     *
     * @author Developer
     */
    @DontObfuscate
    static interface OnFocusPointChangedListener {
        /**
         * Invoked when the map projection changes
         *
         * @param view  The map whose projection changed
         */
        void onFocusPointChanged(Globe view, float focusx, float focusy);
    } // OnMapProjectionChangedListener

    public Globe() {
        this(1920, 1080, 96d, 2.5352504279048383E-9d, 0.01d);
    }

    /**
     * Creates a new <code>Globe</code>
     *
     * @param width         The initial width, in pixels
     * @param height        The initial height, in pixels
     * @param displayDpi    The DPI of the display the globe will be rendered on
     * @param minScale      The minimum scale the globe is allowed to render at
     * @param maxScale      The maximum scale the globe is allowed to render at
     */
    Globe(int width, int height, double displayDpi, double minScale, double maxScale) {
        this(create(width, height, displayDpi, minScale, maxScale), null);

        setSize(this.pointer.raw, width, height);
        setFocusPointOffset(this.pointer.raw, 0, 0);
    }

    Globe(Pointer ptr, Object ownerRef) {


        if(ptr.raw == 0L)
            throw new OutOfMemoryError();

        this.pointer = ptr;

        setProjection(this.pointer.raw, 4326);
        setMaxMapTilt(this.pointer.raw, 85d);

        this.callbackForwarder = new NativeCallbackForwarder(this);
        this.callbackForwarder.pointer = registerNativeCallbackForwarder(this.pointer.raw, this.callbackForwarder);

        this.cleaner = NativePeerManager.register(this, ptr, rwlock, callbackForwarder.pointer, CLEANER);

        this.layerToPointer = new IdentityHashMap<>();
        this.pointerToLayer = new HashMap<>();
    }

    /**************************************************************************/
    // LIFECYCLE

    public final void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    /**************************************************************************/
    // LAYERS

    /**
     * Adds the specified {@link com.atakmap.map.layer.Layer Layer} to the map.
     *
     * @param layer The layer to be added
     */
    public void addLayer(Layer layer) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized(this.layerToPointer) {
                // don't allow double add
                if(this.layerToPointer.containsKey(layer))
                    return;

                long clayerPtr;
                if(Layer_interop.hasPointer(layer)) {
                    clayerPtr = Layer_interop.getPointer(layer);
                    pointerToLayer.put(clayerPtr, layer);
                } else {
                    Pointer clayer = Layer_interop.wrap(layer);
                    layerToPointer.put(layer, clayer);
                    clayerPtr = clayer.raw;
                    pointerToLayer.put(clayerPtr, layer);
                }

                addLayer(this.pointer.raw, clayerPtr);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Adds the specified {@link com.atakmap.map.layer.Layer Layer} to the map
     * at the specified index in the layer stack.
     *
     * @param position  The index
     * @param layer     The layer
     *
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link Globe#getNumLayers()}.
     */
    public synchronized void addLayer(int position, Layer layer) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized(this.layerToPointer) {
                // don't allow double add
                if(this.layerToPointer.containsKey(layer))
                    return;

                long clayerPtr;
                if(Layer_interop.hasPointer(layer)) {
                    clayerPtr = Layer_interop.getPointer(layer);
                    pointerToLayer.put(clayerPtr, layer);
                } else {
                    Pointer clayer = Layer_interop.wrap(layer);
                    layerToPointer.put(layer, clayer);
                    clayerPtr = clayer.raw;
                    pointerToLayer.put(clayerPtr, layer);
                }

                addLayer(this.pointer.raw, position, clayerPtr);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Removes the specified {@link com.atakmap.map.layer.Layer Layer}.
     *
     * @param layer The layer to remove.
     */
    public synchronized void removeLayer(Layer layer) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized(this.layerToPointer) {
                Pointer clayer = this.layerToPointer.remove(layer);
                long clayerPtr;
                if(clayer != null) {
                    clayerPtr = clayer.raw;
                } else {
                    if(Layer_interop.hasPointer(layer)) {
                        clayerPtr = Layer_interop.getPointer(layer);
                        pointerToLayer.put(clayerPtr, layer);
                    } else {
                        return;
                    }
                }
                this.pointerToLayer.remove(clayerPtr);
                removeLayer(this.pointer.raw, clayerPtr);
                if(clayer != null)
                    Layer_interop.destruct(clayer);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Removes all {@link com.atakmap.map.layer.Layer Layer} objects from the
     * map.
     */
    public synchronized void removeAllLayers() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized(this.layerToPointer) {
                removeAllLayers(this.pointer.raw);
                this.pointerToLayer.clear();
                for(Pointer clayer : this.layerToPointer.values())
                    Layer_interop.destruct(clayer);
                this.layerToPointer.clear();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the position of the specified
     * {@link com.atakmap.map.layer.Layer Layer} in the layer stack.
     *
     * @param layer     The layer
     * @param position  The new position for the layer.
     *
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link Globe#getNumLayers()}.
     */
    public void setLayerPosition(Layer layer, int position) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized(this.layerToPointer) {
                Pointer clayer = this.layerToPointer.get(layer);
                long clayerPtr;
                if (clayer != null) {
                    clayerPtr = clayer.raw;
                } else {
                    if (Layer_interop.hasPointer(layer)) {
                        clayerPtr = Layer_interop.getPointer(layer);
                        pointerToLayer.put(clayerPtr, layer);
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
                setLayerPosition(this.pointer.raw, clayerPtr, position);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the number of {@link com.atakmap.map.layer.Layer Layer} objects
     * in the layer stack.
     *
     * @return  The number of layers.
     */
    public int getNumLayers() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getNumLayers(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the number of {@link com.atakmap.map.layer.Layer Layer} at the
     * specified index in the layer stack.
     *
     * @param position  The index
     *
     * @return  The layer at the specified index
     *
     * @throws IndexOutOfBoundsException    If <code>position</code> is less
     *                                      than <code>0</code> or greater than
     *                                      or equal to
     *                                      {@link Globe#getNumLayers()}.
     */
    public Layer getLayer(int position) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final long ptr = getLayer(this.pointer.raw, position);
            synchronized(this.layerToPointer) {
                return getLayerNoSync(ptr);
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns a {@link java.util.List List} of all of the
     * {@link com.atakmap.map.layer.Layer Layer} objects in the layer stack.
     *
     * @return  A list of the current layers
     */
    public List<Layer> getLayers() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            long[] layerPtrs = getLayers(this.pointer.raw);
            ArrayList<Layer> layers = new ArrayList<>(layerPtrs.length);
            synchronized(this.layerToPointer) {
                for (long ptr : layerPtrs) {
                    Layer mlayer = getLayerNoSync(ptr);
                    if(mlayer != null)
                        layers.add(mlayer);
                }
            }
            return layers;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    private Layer getLayerNoSync(long ptr) {
        // if wrapper, unwrap
        if (Layer_interop.hasObject(ptr))
            return Layer_interop.getObject(ptr);
            // if cached, use cached
        else if (this.pointerToLayer.containsKey(ptr))
            return this.pointerToLayer.get(ptr);
        // XXX - wrap and cache
        return null;
    }

    /**
     * Adds the specified {@link OnLayersChangedListener}.
     *
     * @param l The listener to add
     */
    public synchronized void addOnLayersChangedListener(OnLayersChangedListener l) {
        this.callbackForwarder.layersChangedListeners.add(l);
    }

    /**
     * Remove the specified {@link OnLayersChangedListener}.
     *
     * @param l The listener to remove
     */
    public synchronized void removeOnLayersChangedListener(OnLayersChangedListener l) {
        this.callbackForwarder.layersChangedListeners.remove(l);
    }

    /**
     * Get the minimum latitude possible at the center of the view
     *
     * @return
     */
    double getMinLatitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinLatitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Get the maximum latitude possible at the center of the view
     *
     * @return
     */
    double getMaxLatitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxLatitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Get the minimum longitude possible at the center of the view
     *
     * @return
     */
    double getMinLongitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinLongitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Get the maximum longitude possible at the center of the view
     *
     * @return
     */
    double getMaxLongitude() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxLongitude(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    void addOnMapViewResizedListener(OnMapViewResizedListener l) {
        this.callbackForwarder._onMapViewResized.add(l);
    }

    void removeOnMapViewResizedListener(OnMapViewResizedListener l) {
        this.callbackForwarder._onMapViewResized.remove(l);
    }

    void addOnMapMovedListener(OnMapMovedListener l) {
        this.callbackForwarder._onMapMoved.add(l);
    }

    void removeOnMapMovedListener(OnMapMovedListener l) {
        this.callbackForwarder._onMapMoved.remove(l);
    }

    void addOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        this.callbackForwarder._onMapProjectionChanged.add(l);
    }

    void removeOnMapProjectionChangedListener(OnMapProjectionChangedListener l) {
        this.callbackForwarder._onMapProjectionChanged.remove(l);
    }

    void addOnFocusPointChangedListener(OnFocusPointChangedListener l) {
        this.callbackForwarder._onFocusChanged.add(l);
    }

    void removeOnFocusPointChangedListener(OnFocusPointChangedListener l) {
        this.callbackForwarder._onFocusChanged.remove(l);
    }

    /**************************************************************************/

    /**
     * Returns the minimum allowed scale for the map.
     *
     * @return  The minimum allowed scale for the map.
     */
    double getMinMapScale() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMinMapScale(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the maximum allowed scale for the map.
     *
     * @return  The maximum allowed scale for the map.
     */
    double getMaxMapScale() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getMaxMapScale(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    synchronized void addOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.callbackForwarder.elevationExaggerationFactorListeners.add(l);
    }

    synchronized void removeOnElevationExaggerationFactorChangedListener(OnElevationExaggerationFactorChangedListener l) {
        this.callbackForwarder.elevationExaggerationFactorListeners.remove(l);
    }

    void setContinuousScrollEnabled(boolean enabled) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setContinuousScrollEnabled(this.pointer.raw, enabled);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    synchronized boolean isContinuousScrollEnabled() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return isContinuousScrollEnabled(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    synchronized void addOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.callbackForwarder.continuousScrollEnabledListeners.add(l);
    }

    synchronized void removeOnContinuousScrollEnabledChangedListener(OnContinuousScrollEnabledChangedListener l) {
        this.callbackForwarder.continuousScrollEnabledListeners.remove(l);
    }

    // IGlobe

    private Set<ILayer> refs = Collections2.newIdentityHashSet();
    private Map<IGlobe.OnLayersChangedListener, Globe.OnLayersChangedListener> layersChangedForwarder = new IdentityHashMap<>();

    @Override
    public void getLayers(List<ILayer> layers) {
        for(Layer l : getLayers())
            layers.add(LegacyAdapters.adapt2(l));
    }

    @Override
    public ILayer getLayerAt(int position) {
        return LegacyAdapters.adapt2(getLayer(position));
    }

    @Override
    public void setLayerPosition(ILayer layer, int position) {
        setLayerPosition(LegacyAdapters.adapt(layer), position);
    }

    @Override
    public void removeLayer(ILayer layer) {
        removeLayer(LegacyAdapters.adapt(layer));
        synchronized(refs) {
            refs.remove(layer);
        }
    }

    @Override
    public void addLayer(int position, ILayer layer) {
        synchronized (refs) {
            refs.add(layer);
        }
        addLayer(position, LegacyAdapters.adapt(layer));
    }

    @Override
    public void addLayer(ILayer layer) {
        synchronized (refs) {
            refs.add(layer);
        }

        addLayer(LegacyAdapters.adapt(layer));
    }

    @Override
    public void addOnLayersChangedListener(IGlobe.OnLayersChangedListener l) {
        Globe.OnLayersChangedListener forwarder;
        synchronized(layersChangedForwarder) {
            if(layersChangedForwarder.containsKey(l))   return;
            forwarder = new LayersChangedForwarder(l);
            layersChangedForwarder.put(l, forwarder);
        }
        addOnLayersChangedListener(forwarder);
    }

    @Override
    public void removeOnLayersChangedListener(IGlobe.OnLayersChangedListener l) {
        Globe.OnLayersChangedListener forwarder;
        synchronized(layersChangedForwarder) {
            forwarder = layersChangedForwarder.remove(l);
            if(forwarder == null)   return;
        }
        addOnLayersChangedListener(forwarder);
    }

    /**************************************************************************/

    private NativeCallbackForwarder callbackForwarder;

    Pointer pointer;
    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;

    /**************************************************************************/

    final static class NativeCallbackForwarder implements
            OnMapMovedListener,
            OnLayersChangedListener,
            OnMapProjectionChangedListener,
            OnMapViewResizedListener,
            OnElevationExaggerationFactorChangedListener,
            OnContinuousScrollEnabledChangedListener,
            OnFocusPointChangedListener {

        final WeakReference<Globe> ownerRef;
        Pointer pointer;

        final ConcurrentLinkedQueue<OnMapMovedListener> _onMapMoved = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<OnMapProjectionChangedListener> _onMapProjectionChanged = new ConcurrentLinkedQueue<>();
        final Set<OnElevationExaggerationFactorChangedListener> elevationExaggerationFactorListeners = Collections2.newIdentityHashSet();
        final Set<OnContinuousScrollEnabledChangedListener> continuousScrollEnabledListeners = Collections2.newIdentityHashSet();
        final ConcurrentLinkedQueue<OnMapViewResizedListener> _onMapViewResized = new ConcurrentLinkedQueue<>();
        final Set<OnLayersChangedListener> layersChangedListeners = Collections2.newIdentityHashSet();
        final ConcurrentLinkedQueue<OnFocusPointChangedListener> _onFocusChanged = new ConcurrentLinkedQueue<>();

        private NativeCallbackForwarder(Globe owner) {
            this.ownerRef = new WeakReference<>(owner);
        }


        @Override
        public void onMapMoved(Globe ignored, boolean animate) {
            final Globe view = this.ownerRef.get();
            for(OnMapMovedListener l : _onMapMoved)
                l.onMapMoved(view, animate);
        }

        @Override
        public void onLayerAdded(Globe ignored, Layer layer) {
            final Globe view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerAdded(view, layer);
            }
        }

        @Override
        public void onLayerRemoved(Globe ignored, Layer layer) {
            final Globe view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerRemoved(view, layer);
            }
        }

        @Override
        public void onLayerPositionChanged(Globe ignored, Layer layer, int oldPosition, int newPosition) {
            final Globe view = this.ownerRef.get();
            synchronized(view) {
                for(OnLayersChangedListener l : layersChangedListeners)
                    l.onLayerPositionChanged(view, layer, oldPosition, newPosition);
            }
        }

        @Override
        public void onTerrainExaggerationFactorChanged(Globe ignored, double factor) {
            final Globe view = this.ownerRef.get();
            synchronized(view) {
                for(OnElevationExaggerationFactorChangedListener l : elevationExaggerationFactorListeners)
                    l.onTerrainExaggerationFactorChanged(view, factor);
            }
        }

        @Override
        public void onContinuousScrollEnabledChanged(Globe ignored, boolean enabled) {
            final Globe view = this.ownerRef.get();
            synchronized(view) {
                for(OnContinuousScrollEnabledChangedListener l : continuousScrollEnabledListeners)
                    l.onContinuousScrollEnabledChanged(view, enabled);
            }
        }

        @Override
        public void onMapViewResized(Globe ignored) {
            final Globe view = this.ownerRef.get();
            for (OnMapViewResizedListener l : _onMapViewResized) {
                l.onMapViewResized(view);
            }
        }

        @Override
        public void onMapProjectionChanged(Globe ignored) {
            final Globe view = this.ownerRef.get();
            for (OnMapProjectionChangedListener l : _onMapProjectionChanged)
                l.onMapProjectionChanged(view);
        }

        @Override
        public void onFocusPointChanged(Globe ignored, float x, float y) {
            final Globe view = this.ownerRef.get();
            for (OnFocusPointChangedListener l : _onFocusChanged)
                l.onFocusPointChanged(view, x, y);
        }
    }

    final static class LayersChangedForwarder implements Globe.OnLayersChangedListener {

        final IGlobe.OnLayersChangedListener _cb;
        LayersChangedForwarder(IGlobe.OnLayersChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onLayerAdded(Globe mapView, Layer layer) {
            _cb.onLayerAdded(mapView, LegacyAdapters.adapt2(layer));
        }

        @Override
        public void onLayerRemoved(Globe mapView, Layer layer) {
            _cb.onLayerRemoved(mapView, LegacyAdapters.adapt2(layer));
        }

        @Override
        public void onLayerPositionChanged(Globe mapView, Layer layer, int oldPosition, int newPosition) {
            _cb.onLayerPositionChanged(mapView, LegacyAdapters.adapt2(layer), oldPosition, newPosition);
        }
    }

    /**************************************************************************/

    private Map<Layer, Pointer> layerToPointer;
    private Map<Long, Layer> pointerToLayer;

    /*************************************************************************/

    // The following will be refactored out with map view state at a later time
    static boolean isHae(GeoPoint p) {
        return (p.getAltitudeReference() == GeoPoint.AltitudeReference.HAE);
    }

    static void panTo(Globe globe, GeoPoint location, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panTo(globe.pointer.raw, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panZoomTo(Globe globe, GeoPoint location, double scale, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panZoomTo(globe.pointer.raw, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), scale, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panZoomRotateTo(Globe globe, GeoPoint location, double scale, double rotation, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panZoomRotateTo(globe.pointer.raw, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), scale, rotation, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panTo(Globe globe, GeoPoint location, float viewx, float viewy, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panTo(globe.pointer.raw, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), viewx, viewy, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panByAtScale(Globe globe, float x, float y, double scale, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panByAtScale(globe.pointer.raw, x, y, scale, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panBy(Globe globe, float x, float y, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panBy(globe.pointer.raw, x, y, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void panByScaleRotate(Globe globe, float x, float y, double scale, double rotate, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            panByScaleRotate(globe.pointer.raw, x, y, scale, rotate, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void zoomTo(Globe globe, double scale, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            zoomTo(globe.pointer.raw, scale, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void zoomBy(Globe globe, double scale, float x, float y, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            zoomBy(globe.pointer.raw, scale, x, y, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void rotateTo(Globe globe, double rotation, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            rotateTo(globe.pointer.raw, rotation, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void rotateBy(Globe globe, double theta, float xpos, float ypos, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            rotateBy(globe.pointer.raw, theta, xpos, ypos, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void tiltTo(Globe globe, double tilt, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            tiltTo(globe.pointer.raw, tilt, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void tiltBy(Globe globe, double tilt, float xpos, float ypos, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            tiltBy(globe.pointer.raw, tilt, xpos, ypos, animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void tiltBy(Globe globe, double tilt, GeoPoint location, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            tiltBy(globe.pointer.raw, tilt, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void rotateBy(Globe globe, double theta, GeoPoint location, boolean animate) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            rotateBy(globe.pointer.raw, theta, location.getLatitude(), location.getLongitude(), location.getAltitude(), isHae(location), animate);
        } finally {
            globe.rwlock.releaseRead();
        }
    }
    static void setFocusPointOffset(Globe globe, float offsetX, float offsetY) {
        globe.rwlock.acquireRead();
        try {
            if(globe.pointer.raw == 0L)
                throw new IllegalStateException();
            setFocusPointOffset(globe.pointer.raw, offsetX, offsetY);
        } finally {
            globe.rwlock.releaseRead();
        }
    }

    /*************************************************************************/
    // Interop implementation
    static long getPointer(Globe object) {
        if(object != null)
            return object.pointer.raw;
        else
            return 0L;
    }
    static boolean hasPointer(Globe object) {
        return true;
    }
    static Globe create(Pointer pointer, Object ownerReference) {
        return new Globe(pointer, ownerReference);
    }


    /*************************************************************************/
    // native implementation

    // lifecycle
    static native Pointer create(int width, int height, double displayDpi, double minMapScale, double maxMapScale);
    static native void destruct(Pointer pointer);

    // layer management
    static native void addLayer(long pointer, long clayer);
    static native void addLayer(long pointer, int position, long clayer);
    static native void setLayerPosition(long pointer, long clayer, int position);
    static native void removeLayer(long pointer, long clayer);
    static native void removeAllLayers(long pointer);
    static native long getLayer(long pointer, int position);
    static native long[] getLayers(long pointer);
    static native int getNumLayers(long pointer);

    // callbacks
    static native Pointer registerNativeCallbackForwarder(long pointer, Globe.NativeCallbackForwarder l);
    static native void unregisterNativeCallbackForwarder(long pointer, Pointer cl);

    // view state !!! will be refactored out !!!!
    public static native double getFullEquitorialExtentPixels(double dpi);
    static native double getDisplayDpi(long pointer);

    //static native boolean updateView (long pointer, double latitude, double longitude, double scale, double rotation, double tilt, boolean animate);
    //static native int getProjection(long pointer);
    static native int setProjection(long pointer, int srid);
    static native double getMaxMapScale(long pointer);
    static native double getMinMapScale(long pointer);
    //static native double getMapScaleImpl(long pointer);
    //static native double getMapResolutionImpl(long raw);
    //static native double getMapResolutionImpl(long raw, double mapScale);
    //static native double mapResolutionAsMapScale(long raw, double resolution);
    //static native double setElevationExaggerationFactor(long raw, double factor);
    //static native double getElevationExaggerationFactor(long raw);
    //static native boolean forward(long pointer, double latitude, double longitude, double alt, boolean altIsHae, PointD result);

    static native double getMinLatitude(long pointer);
    static native double getMaxLatitude(long pointer);
    static native double getMinLongitude(long pointer);
    static native double getMaxLongitude(long pointer);

    //static native boolean isAnimating(long pointer);
    //static native double getMapRotation(long pointer);
    //static native double getMapTilt(long pointer);
    //static native GeoPoint getPoint(long pointer);
    static native double setMaxMapTilt(long pointer, double maxTilt);
    static native void setContinuousScrollEnabled(long pointer, boolean v);
    static native boolean isContinuousScrollEnabled(long pointer);

    static native void setFocusPointOffset(long pointer, float x, float y);
    static native void setSize(long pointer, int width, int height);
    static native int getWidth(long pointer);
    static native int getHeight(long pointer);
    static native Pointer createSceneModel(long pointer);

    public static native double getMapResolution(double dpi, double scale);
    public static native double getMapScale(double dpi, double resolution);

    // controller methods !!! will be refactored out !!!

    static native void panTo(long ptr, double lat, double lng, double alt, boolean hae, boolean animate);
    static native void panZoomTo(long ptr, double lat, double lng, double alt, boolean hae, double scale, boolean animate);
    static native void panZoomRotateTo(long ptr, double lat, double lng, double alt, boolean hae, double scale, double rotation, boolean animate);
    static native void panTo(long ptr, double lat, double lng, double alt, boolean hae, float viewx, float viewy, boolean animate);
    static native void panByAtScale(long ptr, float x, float y, double scale, boolean animate);
    static native void panBy(long ptr, float x, float y, boolean animate);
    static native void panByScaleRotate(long ptr, float x, float y, double scale, double rotate, boolean animate);
    static native void zoomTo(long ptr, double scale, boolean animate);
    static native void zoomBy(long ptr, double scale, float x, float y, boolean animate);
    static native void rotateTo(long ptr, double rotation, boolean animate);
    static native void rotateBy(long ptr, double theta, float xpos, float ypos, boolean animate);
    static native void tiltTo(long ptr, double tilt, boolean animate);
    static native void tiltTo(long ptr, double tilt, double rotation, boolean animate);
    static native void tiltBy(long ptr, double tilt, float xpos, float ypos, boolean animate);
    static native void tiltBy(long ptr, double tilt, double latitude, double longitude, double alt, boolean hae, boolean animate);
    static native void rotateBy(long ptr, double theta, double latitude, double longitude, double alt, boolean hae, boolean animate);
    static native void updateBy(long ptr, double scale, double rotation, double tilt, float xpos, float ypos, boolean animate);
    static native void updateBy(long ptr, double scale, double rotation, double tilt, double lat, double lng, double alt, boolean hae, boolean animate);

    static native float getFocusPointX(long ptr);
    static native float getFocusPointY(long ptr);
}
