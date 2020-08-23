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
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.util.Collections2;

public class GLBatchGeometryCollection extends GLBatchGeometry {
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
        this.collectionEntityType = collectionEntityType;
        this.renderPass = renderPass;
    }

    @Override
    public synchronized void init(long featureId, String name) {
        super.init(featureId, name);
        for(GLBatchGeometry child : points)
            child.init(featureId, name);
        for(GLBatchGeometry child : lines)
            child.init(featureId, name);
        for(GLBatchGeometry child : polys)
            child.init(featureId, name);
        for(GLBatchGeometry child : collections)
            child.init(featureId, name);
    }
    
    @Override
    public synchronized void draw(GLMapView view) {
        for(GLBatchGeometry child : collections)
            child.draw(view);
        for(GLBatchGeometry child : polys)
            child.draw(view);
        for(GLBatchGeometry child : lines)
            child.draw(view);
        for(GLBatchGeometry child : points)
            child.draw(view);
    }

    @Override
    public synchronized void release() {
        for(GLBatchGeometry child : collections)
            child.release();
        for(GLBatchGeometry child : polys)
            child.release();
        for(GLBatchGeometry child : lines)
            child.release();
        for(GLBatchGeometry child : points)
            child.release();
    }

    @Override
    public final void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!MathUtils.hasBits(renderPass, this.renderPass))
            return;
        for(GLBatchGeometry child : collections)
            child.batch(view, batch, renderPass);
        for(GLBatchGeometry child : polys)
            child.batch(view, batch, renderPass);
        for(GLBatchGeometry child : lines)
            child.batch(view, batch, renderPass);
        for(GLBatchGeometry child : points)
            child.batch(view, batch, renderPass);
    }

    @Override
    public final int getRenderPass() {
        return this.renderPass;
    }

    @Override
    public synchronized void setStyle(Style style) {
        for(GLBatchGeometry child : collections)
            child.setStyle(style);
        for(GLBatchGeometry child : polys)
            child.setStyle(style);
        for(GLBatchGeometry child : lines)
            child.setStyle(style);
        for(GLBatchGeometry child : points)
            child.setStyle(style);
        this.style = style;
    }

    @Override
    public void setAltitudeMode(Feature.AltitudeMode altitudeMode) {
        for(GLBatchGeometry child : collections)
            child.setAltitudeMode(altitudeMode);
        for(GLBatchGeometry child : polys)
            child.setAltitudeMode(altitudeMode);
        for(GLBatchGeometry child : lines)
            child.setAltitudeMode(altitudeMode);
        for(GLBatchGeometry child : points)
            child.setAltitudeMode(altitudeMode);
        this.altitudeMode = altitudeMode;
    }

    @Override
    public void setExtrude(double value) {
        for(GLBatchGeometry child : collections)
            child.setExtrude(value);
        for(GLBatchGeometry child : polys)
            child.setExtrude(value);
        for(GLBatchGeometry child : lines)
            child.setExtrude(value);
        for(GLBatchGeometry child : points)
            child.setExtrude(value);
        this.extrude = value;
    }

    public void setGeometry(GeometryCollection geometry) {
        this.setGeometry(geometry, -1);
    }

    @Override
    public synchronized void setGeometry(Geometry g, int lod) {
        GeometryCollection geometry = (GeometryCollection)g;
        this.lod = lod;

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
        }
        
        while(pointsIter.hasNext()) {
            pointsIter.next();
            pointsIter.remove();
        }
        while(linesIter.hasNext()) {
            linesIter.next();
            linesIter.remove();
        }
        while(polysIter.hasNext()) {
            polysIter.next();
            polysIter.remove();
        }
        while(collectionsIter.hasNext()) {
            collectionsIter.next();
            collectionsIter.remove();
        }
    }

    @Override
    public synchronized void setGeometry(final ByteBuffer blob, final int type, int lod) {
        this.lod = lod;

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
            child.setGeometryImpl(blob, type);
            if(this.style != null)
                child.setStyle(this.style);
        }
        
        while(pointsIter.hasNext()) {
            pointsIter.next();
            pointsIter.remove();
        }
        while(linesIter.hasNext()) {
            linesIter.next();
            linesIter.remove();
        }
        while(polysIter.hasNext()) {
            polysIter.next();
            polysIter.remove();
        }
        while(collectionsIter.hasNext()) {
            collectionsIter.next();
            collectionsIter.remove();
        }
    }
    
    @Override
    protected final void setGeometryImpl(ByteBuffer blob, int type) {
        throw new IllegalStateException();
    }
    
    @Override
    protected final void setGeometryImpl(Geometry geom) {
        throw new IllegalStateException();
    }
}
