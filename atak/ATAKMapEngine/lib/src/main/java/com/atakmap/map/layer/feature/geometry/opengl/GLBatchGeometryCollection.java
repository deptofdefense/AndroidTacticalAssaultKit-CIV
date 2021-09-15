package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.util.Collections2;

public class GLBatchGeometryCollection extends GLBatchGeometry {
    // next four collections are only _mutated_ on the GL thread while holding
    // the lock on `this`. They may be  _accessed_ on the GL thread without
    // synchronization; they may be accessed on other threads synchronized on
    // `this`
    Collection<GLBatchGeometry> points;
    Collection<GLBatchGeometry> lines;
    Collection<GLBatchGeometry> polys;
    Collection<GLBatchGeometry> collections;

    private Style style;
    private Feature.AltitudeMode altitudeMode;
    private double extrude;

    private int collectionEntityType;
    private int renderPass;

    public GLBatchGeometryCollection(GLMapSurface surface) {
        this(surface.getGLMapView());
    }
    
    public GLBatchGeometryCollection(MapRenderer surface) {
        this(surface, 10, 0, GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE);
    }

    GLBatchGeometryCollection(MapRenderer surface, int zOrder, int collectionEntityType, int renderPass) {
        super(surface, zOrder);

        points = new LinkedList<GLBatchGeometry>();
        lines = new LinkedList<GLBatchGeometry>();
        polys = new LinkedList<GLBatchGeometry>();
        collections = new LinkedList<GLBatchGeometry>();
        
        this.style = null;

        // provide a non nullary default altitude mode for the batch rendering in case setAltitude is
        // not called prior to the rendering
        this.altitudeMode = Feature.AltitudeMode.ClampToGround;

        this.extrude = 0d;


        this.collectionEntityType = collectionEntityType;
        this.renderPass = renderPass;
    }

    @Override
    public synchronized void init(long featureId, String name) {
        super.init(featureId, name);
        for(GLBatchGeometry child : getChildren())
            child.init(featureId, name);
    }
    
    @Override
    public void draw(GLMapView view) {
        for(GLBatchGeometry child : getChildren())
            child.draw(view);
    }

    @Override
    public void release() {
        for(GLBatchGeometry child : getChildren())
            child.release();
    }

    @Override
    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.renderPass))
            return;
        for (GLBatchGeometry child : getChildren())
            child.batch(view, batch, renderPass);
    }

    @Override
    public final int getRenderPass() {
        return this.renderPass;
    }

    @Override
    public synchronized void setStyle(Style style) {
        for(GLBatchGeometry child : getChildren())
            child.setStyle(style);
        this.style = style;
    }

    @Override
    public synchronized void setAltitudeMode(Feature.AltitudeMode altitudeMode) {
        for(GLBatchGeometry child : getChildren())
            child.setAltitudeMode(altitudeMode);
        this.altitudeMode = altitudeMode;
    }

    @Override
    public synchronized void setExtrude(double value) {
        for (GLBatchGeometry child : getChildren())
            child.setExtrude(value);
        this.extrude = value;
    }

    @Override
    public void setLollipopsVisible(boolean v) {
        super.setLollipopsVisible(v);
        for (GLBatchGeometry child : getChildren())
            child.setLollipopsVisible(v);
    }

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        super.setClampToGroundAtNadir(v);
        for (GLBatchGeometry child : getChildren())
            child.setClampToGroundAtNadir(v);
    }

    public void setGeometry(GeometryCollection geometry) {
        this.setGeometry(geometry, -1);
    }

    @Override
    public synchronized void setGeometry(Geometry g, int lod) {
        this.lod = lod;

        GeometryCollection geometry = (GeometryCollection)g;

        Iterator<GLBatchGeometry> pointsIter = points.iterator();
        Iterator<GLBatchGeometry> linesIter = lines.iterator();
        Iterator<GLBatchGeometry> polysIter = polys.iterator();
        Iterator<GLBatchGeometry> collectionsIter = collections.iterator();

        int allocs = 0;
        int reuse = 0;

        Collection<Geometry> c = geometry.getGeometries();
        int idx = 1;
        for(Geometry child : c) {
            final int codedEntityType;
            if(child instanceof Point) {
                codedEntityType = 1;
            } else if(child instanceof LineString) {
                codedEntityType = 2;
            } else if(child instanceof Polygon) {
                codedEntityType = 3;
            } else {
                Log.e("GLBatchGeometryCollection", "Invalid collectionentity encountered: " + child.getClass());
                return;

            }
            
            if(this.collectionEntityType != 0 && codedEntityType != this.collectionEntityType) {
                Log.e("GLBatchGeometryCollection", "Invalid collectionentity encountered, expected " + this.collectionEntityType + ", decoded " + codedEntityType);
                return;
            }

            GLBatchGeometry glchild;
            if(child instanceof Point) {
                if(pointsIter.hasNext()) {
                    glchild = pointsIter.next();
                    reuse++;
                } else {
                    glchild = new GLBatchPoint(this.renderContext);
                    points.add(glchild);
                    pointsIter = Collections2.EMPTY_ITERATOR;
                    allocs++;
                }
            } else if(child instanceof LineString) {
                if(linesIter.hasNext()) {
                    glchild = linesIter.next();
                    reuse++;
                } else {
                    glchild = new GLBatchLineString(this.renderContext);
                    lines.add(glchild);
                    linesIter = Collections2.EMPTY_ITERATOR;
                    allocs++;
                }
            } else if(child instanceof Polygon) {
                if(polysIter.hasNext()) {
                    glchild = polysIter.next();
                    reuse++;
                } else {
                    glchild = new GLBatchPolygon(this.renderContext);
                    polys.add(glchild);
                    polysIter = Collections2.EMPTY_ITERATOR;
                    allocs++;
                }
            } else {
                throw new IllegalStateException();
            }

            glchild.setGeometry(child, lod);
            glchild.init(this.featureId, this.name);
            glchild.subid = idx++;
            if(this.style != null)
                glchild.setStyle(this.style);
            glchild.setExtrude(this.extrude);
            glchild.setAltitudeMode(this.altitudeMode);
            glchild.setLollipopsVisible(getLollipopsVisible());
            glchild.setClampToGroundAtNadir(getClampToGroundAtNadir());
        }
        
        while(pointsIter.hasNext()) {
            final GLBatchGeometry geom = pointsIter.next();
            geom.release();
            pointsIter.remove();
        }
        while(linesIter.hasNext()) {
            final GLBatchGeometry geom = linesIter.next();
            geom.release();
            linesIter.remove();
        }
        while(polysIter.hasNext()) {
            final GLBatchGeometry geom = polysIter.next();
            geom.release();
            polysIter.remove();
        }
        while(collectionsIter.hasNext()) {
            final GLBatchGeometry geom = collectionsIter.next();
            geom.release();
            collectionsIter.remove();
        }
    }

    @Override
    protected synchronized void setGeometryImpl(final ByteBuffer blob, final int type) {
        Iterator<GLBatchGeometry> pointsIter = points.iterator();
        Iterator<GLBatchGeometry> linesIter = lines.iterator();
        Iterator<GLBatchGeometry> polysIter = polys.iterator();
        Iterator<GLBatchGeometry> collectionsIter = collections.iterator();

        final int numPoints = blob.getInt();
        GLBatchGeometry child;
        for(int i = 0; i < numPoints; i++) {
            final int entity = blob.get()&0xFF;
            if(entity != 0x69) {
                Log.e("GLBatchGeometryCollection", "Bad coding: fid=" + this.featureId + " name=" + name);
                //throw new IllegalArgumentException("Bad coding: fid=" + this.featureId + " name=" + name + " blob="+ toHexString(blob.array()));
                return;
            }
            final int codedEntityType = blob.getInt();
            if(this.collectionEntityType != 0 && codedEntityType != this.collectionEntityType) {
                Log.e("GLBatchGeometryCollection", "Invalid collectionentity encountered, expected " + this.collectionEntityType + ", decoded " + codedEntityType);
                return;
            }
            switch(codedEntityType) {
                case 1 :
                    if(pointsIter.hasNext()) {
                        child = pointsIter.next();
                    } else {
                        child = new GLBatchPoint(this.renderContext);
                        points.add(child);
                        pointsIter = Collections2.EMPTY_ITERATOR;
                    }
                    break;
                case 2 :
                    if(linesIter.hasNext()) {
                        child = linesIter.next();
                    } else {
                        child = new GLBatchLineString(this.renderContext);
                        lines.add(child);
                        linesIter = Collections2.EMPTY_ITERATOR;
                    }
                    break;
                case 3 :
                    if(polysIter.hasNext()) {
                        child = polysIter.next();
                    } else {
                        child = new GLBatchPolygon(this.renderContext);
                        polys.add(child);
                        polysIter = Collections2.EMPTY_ITERATOR;
                    }
                    break;
                default :
                    Log.e("GLBatchGeometryCollection", "Invalid collectionentity encountered: " + codedEntityType);
                    return;
            }

            child.init(this.featureId, this.name);
            child.subid = i;
            child.setGeometry(blob, type, lod);
            if(this.style != null)
                child.setStyle(this.style);
            child.setExtrude(this.extrude);
            child.setAltitudeMode(this.altitudeMode);
            child.setLollipopsVisible(getLollipopsVisible());
            child.setClampToGroundAtNadir(getClampToGroundAtNadir());
        }
        
        while(pointsIter.hasNext()) {
            final GLBatchGeometry geom = pointsIter.next();
            geom.release();
            pointsIter.remove();
        }
        while(linesIter.hasNext()) {
            final GLBatchGeometry geom = linesIter.next();
            geom.release();
            linesIter.remove();
        }
        while(polysIter.hasNext()) {
            final GLBatchGeometry geom = polysIter.next();
            geom.release();
            polysIter.remove();
        }
        while(collectionsIter.hasNext()) {
            final GLBatchGeometry geom = collectionsIter.next();
            geom.release();
            collectionsIter.remove();
        }
    }

    @Override
    protected void setGeometryImpl(Geometry geom) {
        throw new IllegalStateException();
    }

    /**
     * Iterator for every piece of geometry that belongs to this collection
     * Used to cut down on duplicate code
     * @return Children iterator
     */
    private Iterable<GLBatchGeometry> getChildren() {
        final Collection<?>[] lists = {
                collections, points, lines, polys
        };
        return new Iterable<GLBatchGeometry>() {
            @Override
            public Iterator<GLBatchGeometry> iterator() {
                return new Iterator<GLBatchGeometry>() {

                    Iterator<?> iter;
                    int l = 0;

                    @Override
                    public boolean hasNext() {
                        while (l < lists.length && (iter == null || !iter.hasNext()))
                            iter = lists[l++].iterator();
                        return iter != null && iter.hasNext();
                    }

                    @Override
                    public GLBatchGeometry next() {
                        return (GLBatchGeometry) iter.next();
                    }
                };
            }
        };
    }
}
