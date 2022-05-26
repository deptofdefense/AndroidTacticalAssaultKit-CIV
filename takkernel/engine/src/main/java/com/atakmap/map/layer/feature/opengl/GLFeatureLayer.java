package com.atakmap.map.layer.feature.opengl;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.FeatureLayer2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.service.FeatureHitTestControl;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLRenderBatch;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLFeatureLayer extends GLAsynchronousLayer<Collection<Feature>> implements FeatureDataStore2.OnDataStoreContentChangedListener, FeatureHitTestControl {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // FeatureLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if(layer instanceof FeatureLayer)
                return new GLFeatureLayer(surface, (FeatureLayer)layer);
            else if(layer instanceof FeatureLayer2)
                return new GLFeatureLayer(surface, (FeatureLayer2)layer);
            else if(layer instanceof FeatureLayer3)
                return new GLFeatureLayer(surface, (FeatureLayer3)layer);
            return null;
        }
    };

    private final static Comparator<Feature> FID_COMPARATOR = new Comparator<Feature>() {
        @Override
        public int compare(Feature lhs, Feature rhs) {
            final long fid0 = lhs.getId();
            final long fid1 = rhs.getId();
            
            if(fid0 < fid1)
                return -1;
            else if(fid0 > fid1)
                return 1;
            else
                return 0;
        }
    };
    
    private final static String TAG = "GLFeatureLayer";

    private FeatureDataStore2 dataStore;

    private Map<Long, GLFeature> features;

    private GLRenderBatch batch;
    private Collection<GLMapRenderable> renderList;
    
    public GLFeatureLayer(MapRenderer surface, FeatureLayer subject) {
        this(surface, subject, Adapters.adapt(subject.getDataStore()));
    }

    public GLFeatureLayer(MapRenderer surface, FeatureLayer2 subject) {
        this(surface, subject, Adapters.adapt(subject.getDataStore()));
    }

    public GLFeatureLayer(MapRenderer surface, FeatureLayer3 subject) {
        this(surface, subject, subject.getDataStore());
    }

    private GLFeatureLayer(MapRenderer surface, Layer2 subject, FeatureDataStore2 dataStore) {
        super(surface, subject);

        this.dataStore = dataStore;
        
        this.features = new TreeMap<Long, GLFeature>();
        
        this.batch = null;
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public synchronized void start() {
        this.dataStore.addOnDataStoreContentChangedListener(this);        
        this.renderContext.registerControl((Layer2)this.subject, this);

        super.start();
    }
    
    @Override
    public synchronized void stop() {
        this.renderContext.unregisterControl((Layer2)this.subject, this);
        this.dataStore.removeOnDataStoreContentChangedListener(this);
        
        super.stop();
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected String getBackgroundThreadName() {
        return "Feature [" + this.subject.getName() + "] GL worker@" + Integer.toString(this.hashCode(), 16) + "-worker";
    }
    
    @Override
    protected void initImpl(GLMapView view) {
        this.batch = new GLRenderBatch(5000);
        this.renderList = Collections.<GLMapRenderable>singleton(new GLBatchRenderer());
                
        super.initImpl(view);
    }

    @Override
    protected void releaseImpl() {
        for(GLFeature feature : this.features.values())
            feature.release();
        this.features.clear();
        this.batch = null;
        super.releaseImpl();
    }
    
    @Override
    protected Collection<? extends GLMapRenderable> getRenderList() {
        return this.renderList;
    }

    @Override
    protected void resetPendingData(Collection<Feature> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(Collection<Feature> pendingData) {
        pendingData.clear();
    }

    @Override
    protected Collection<Feature> createPendingData() {
        return new LinkedList<Feature>();
    }

    @Override
    protected boolean updateRenderableReleaseLists(Collection<Feature> pendingData) {
        Map<Long, GLFeature> swap = new TreeMap<Long, GLFeature>();
        
        Iterator<Feature> iter = pendingData.iterator();
        Feature feature;
        GLFeature glfeature;
        while(iter.hasNext()) {
            feature = iter.next();
            glfeature = this.features.remove(Long.valueOf(feature.getId()));
            if(glfeature == null) {
                glfeature = new GLFeature(this.renderContext, feature);
            } else if(!Feature.isSame(glfeature.getSubject(), feature)) {
                glfeature.update(feature);
            }
            swap.put(Long.valueOf(feature.getId()), glfeature);
            iter.remove();
        }
        
        if(this.features.size() > 0) {
            final Collection<GLFeature> releaseList = this.features.values();
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    for(GLFeature f : releaseList)
                        f.release();
                }
            });
        }

        this.features = swap;
        
        return true;
    }

    @Override
    protected void query(ViewState state, Collection<Feature> retval) {
        if(state.crossesIDL) {
            Set<Feature> result = new TreeSet<Feature>(FID_COMPARATOR);

            // west of IDL
            this.queryImpl(state.northBound,
                           state.westBound,
                           state.southBound,
                           180d,
                           state.drawMapResolution,
                           result);
            
            // east of IDL
            this.queryImpl(state.northBound,
                           -180d,
                           state.southBound,
                           state.eastBound,
                           state.drawMapResolution,
                           result);
            
            retval.addAll(result);
        } else {
            this.queryImpl(state.northBound,
                           state.westBound,
                           state.southBound,
                           state.eastBound,
                           state.drawMapResolution,
                           retval);
        }
    }

    private void queryImpl(double northBound,
                           double westBound,
                           double southBound,
                           double eastBound,
                           double drawMapResolution,
                           Collection<Feature> retval) {

        FeatureCursor result = null;
        try {
            FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
            params.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(westBound, southBound, 0d, eastBound, northBound, 0d));
            params.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
            params.featureSetFilter.maxResolution = drawMapResolution;
            params.visibleOnly = true;
            params.ignoredFeatureProperties = FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES;

            if (this.checkQueryThreadAbort())
                return;

            //long s = System.currentTimeMillis();
            result = this.dataStore.queryFeatures(params);
            while (result.moveToNext()) {
                if (this.checkQueryThreadAbort())
                    break;
                retval.add(result.get());
            }
            //long e = System.currentTimeMillis();
            //System.out.println(retval.size() + " results in " + (e-s) + "ms");
        } catch(DataStoreException e) {
            Log.w(TAG, "[" + getSubject().getName() + "] query failed.", e);
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**************************************************************************/
    // Feature Data Store On Data Store Content Changed Listener

    @Override
    public void onDataStoreContentChanged(FeatureDataStore2 dataStore) {
        this.invalidate();
    }

    // XXX - next 4 -- for now, just invoke content changed. in the future we
    //       should update individual features for more interactive editing
    @Override
    public void onFeatureInserted(FeatureDataStore2 dataStore, long fid, FeatureDefinition2 def, long version) {
        this.invalidate();
    }
    @Override
    public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid, int modificationMask, String name, Geometry geom, Style style, AttributeSet attribs, int attribsUpdateType) {
        this.invalidate();
    }
    @Override
    public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid) {
        this.invalidate();
    }
    @Override
    public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore, long fid, boolean visible) {
        this.invalidate();
    }

    private class GLBatchRenderer implements GLMapRenderable {

        @Override
        public void draw(GLMapView view) {
            boolean inBatch = false;
            for(GLFeature feature : GLFeatureLayer.this.features.values()) {
                if(feature.isBatchable(view)) {
                    if(!inBatch) {
                        GLFeatureLayer.this.batch.begin();
                        inBatch = true;
                    }
                    feature.batch(view, GLFeatureLayer.this.batch);
                } else {
                    if(inBatch)
                        GLFeatureLayer.this.batch.end();
                    feature.draw(view);
                }
            }
            if(inBatch)
                GLFeatureLayer.this.batch.end();
        }

        @Override
        public void release() {}
    }

    @Override
    public synchronized void hitTest(Collection<Long> fids, float screenX, float screenY, GeoPoint point, double resolution,
            float radius, int limit) {
        
        Map<Long, GLFeature> reverse = ((NavigableMap<Long, GLFeature>)this.features).descendingMap();
        
        long s = System.currentTimeMillis();
        GLFeature f;
        for(Map.Entry<Long, GLFeature> entry : reverse.entrySet()) {
            f = entry.getValue();
            if(hitTest(f.getSubject().getGeometry(), point, radius*resolution, null)) {
                fids.add(Long.valueOf(f.getSubject().getId()));
                if(fids.size() == limit)
                    break;
            }
        }
        long e = System.currentTimeMillis();
        
        Log.d(TAG, "HIT TEST [" + this.subject.getName() + "] on " + this.features.size() + " in " + (e-s) + "ms");
    }
    
    public static boolean hitTest(Geometry g, GeoPoint point, double radius) {
        return hitTest(g, point, radius, null);
    }

    public static boolean hitTest(Geometry g, GeoPoint point, double radius, GeoPoint touchPoint) {
        if(g instanceof Point) {
            Point p = (Point)g;
            if(touchPoint != null) touchPoint.set(p.getY(), p.getX());
            return (GeoCalculations.distanceTo(point, new GeoPoint(p.getY(), p.getX())) <= radius);
        } else if(g instanceof LineString){
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;
            return testOrthoHit((LineString)g, point, radius, touchPoint);
        } else if(g instanceof Polygon) {
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;
            
            Polygon p = (Polygon)g;
            if(testOrthoHit(p.getExteriorRing(), point, radius, touchPoint))
                return true;
            for(LineString inner : p.getInteriorRings())
                if(testOrthoHit(inner, point, radius, touchPoint))
                    return true;
            return false;
        } else if(g instanceof GeometryCollection) {
            if(!mbrIntersects(g.getEnvelope(), point, radius))
                return false;

            for(Geometry child : ((GeometryCollection)g).getGeometries())
                if(hitTest(child, point, radius, touchPoint))
                    return true;
            return false;
        } else {
            throw new IllegalStateException();
        }
    }

    // XXX - next 3 modified from EditablePolyline, review for optimization
    
    private static boolean mbrIntersects(Envelope mbb, GeoPoint point, double radiusMeters) {
        final double x = point.getLongitude();
        final double y = point.getLatitude();

        if(Rectangle.contains(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY, x, y))
            return true;
        
        // XXX - check distance from minimum bounding box is with the radius
        final double fromX;
        if(x < mbb.minX) {
            fromX = mbb.minX;
        } else if(x > mbb.maxX){
            fromX = mbb.maxX;
        } else {
            fromX = x;
        }
        
        final double fromY;
        if(y < mbb.minY) {
            fromY = mbb.minY;
        } else if(y > mbb.maxY){
            fromY = mbb.maxY;
        } else {
            fromY = y;
        }

        return (GeoCalculations.distanceTo(new GeoPoint(fromY, fromX), new GeoPoint(y, x)) < radiusMeters);
    }

    private static boolean testOrthoHit(LineString linestring, GeoPoint point, double radius, GeoPoint touchPoint) {

        boolean res = mbrIntersects(linestring.getEnvelope(), point, radius);
        if (!res) {
            //Log.d(TAG, "hit not contained in any geobounds");
            return false;
        }

        final int numPoints = linestring.getNumPoints();
        
        final double px = point.getLongitude();
        final double py = point.getLatitude();

        int detected_partition = -1;
        Envelope minibounds = new Envelope(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        double x0;
        double y0;
        double x1;
        double y1;
        for (int i = 0; i < numPoints-1; ++i) {
            x0 = linestring.getX(i);
            y0 = linestring.getY(i);
            x1 = linestring.getX(i+1);
            y1 = linestring.getY(i+1);
            
            // construct the minimum bounding box for the segment
            minibounds.minX = Math.min(x0, x1);
            minibounds.minY = Math.min(y0, y1);
            minibounds.maxX = Math.max(x0, x1);
            minibounds.maxY = Math.max(y0, y1);
            
            if (mbrIntersects(minibounds, point, radius)) {
                Log.d(TAG, "hit maybe contained in geobounds: " + i);
                Point isect = (touchPoint!=null) ? new Point(0, 0) : null;
                if(dist(x0, y0, x1, y1, px, py, isect) < radius) {
                    if(touchPoint != null && isect != null) 
                         touchPoint.set(isect.getY(), isect.getX());
                    return true;
                }
            }
        }
        //Log.d(TAG, "hit not contained in any sub geobounds");
        return false;
    }

    private static double dist(double x1, double y1, double x2, double y2, double x3,double y3, Point linePt) { // x3,y3 is the point
        double px = x2-x1;
        double py = y2-y1;
    
        double something = px*px + py*py;
    
        double u =  ((x3 - x1) * px + (y3 - y1) * py) / something;
    
        if(u > 1)
            u = 1;
        else if(u < 0)
            u = 0;
    
        double x = x1 + u * px;
        double y = y1 + u * py;
        
        if(linePt != null) {
            linePt.set(x, y);
        }

        return GeoCalculations.distanceTo(new GeoPoint(y, x), new GeoPoint(y3, x3));
    }

} // GLFeatureLayer
