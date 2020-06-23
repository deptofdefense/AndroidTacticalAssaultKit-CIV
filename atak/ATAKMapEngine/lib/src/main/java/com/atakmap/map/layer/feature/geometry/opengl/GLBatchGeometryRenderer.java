package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import android.graphics.Color;
import android.opengl.GLES30;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLTextureAtlas;
import com.atakmap.util.ConfigOptions;

public class GLBatchGeometryRenderer implements GLMapRenderable, GLMapRenderable2 {
    
    private static Comparator<GLBatchPoint> POINT_BATCH_COMPARATOR = new Comparator<GLBatchPoint>() {
        @Override
        public int compare(GLBatchPoint lhs, GLBatchPoint rhs) {
            long retval = lhs.textureId-rhs.textureId;
            if(retval != 0)
                return (int)retval;
            retval = lhs.color-rhs.color;
            if(retval != 0)
                return (int)retval;
            retval = (lhs.featureId-rhs.featureId);
            if(retval != 0)
                return (retval>0) ? 1 : -1;
            retval = (lhs.subid-rhs.subid);
            return (int)retval;
        }
    };
    
    private static Comparator<GLBatchGeometry> FID_COMPARATOR = new Comparator<GLBatchGeometry>() {
        @Override
        public int compare(GLBatchGeometry lhs, GLBatchGeometry rhs) {
            long retval = (lhs.featureId-rhs.featureId);
            if(retval != 0L)
                return (retval>0L) ? 1 : -1;
            retval = (lhs.subid-rhs.subid);
            return (int)retval;
        }
    };
    
    private final static int PRE_FORWARD_LINES_POINT_RATIO_THRESHOLD = 3;

    private final static int MAX_BUFFERED_2D_POINTS = 20000;
    private final static int MAX_BUFFERED_3D_POINTS = (MAX_BUFFERED_2D_POINTS*2)/3;
    private final static int MAX_VERTS_PER_DRAW_ARRAYS = 5000;

    private final static int POINT_BATCHING_THRESHOLD = 500;

    private final static Map<MapRenderer, RenderBuffers> renderBuffers = new IdentityHashMap<MapRenderer, RenderBuffers>();

    /*************************************************************************/
    
    private LinkedList<GLBatchPolygon> polys = new LinkedList<GLBatchPolygon>();
    private LinkedList<GLBatchLineString> lines = new LinkedList<GLBatchLineString>();
    private ArrayList<GLBatchPoint> batchPoints2 = new ArrayList<GLBatchPoint>();
    private LinkedList<GLBatchPoint> labels = new LinkedList<GLBatchPoint>();
    private LinkedList<GLBatchPoint> loadingPoints = new LinkedList<GLBatchPoint>();

    private SortedSet<GLBatchGeometry> sortedPolys = new TreeSet<GLBatchGeometry>(FID_COMPARATOR);
    private SortedSet<GLBatchGeometry> sortedLines = new TreeSet<GLBatchGeometry>(FID_COMPARATOR);

    private SortInfo sortInfo = new SortInfo();

    private final MapRenderer renderCtx;
    private RenderBuffers buffers;
    private FloatBuffer pointsBuffer;
    private long pointsBufferPtr;
    private FloatBuffer pointsVertsTexCoordsBuffer;
    private IntBuffer textureAtlasIndicesBuffer;
    
    private BatchPipelineState state;
    
    private GLRenderBatch2 batch;

    private TextureProgram textureProgram2d;
    private TextureProgram textureProgram3d;
    
    private VectorProgram vectorProgram2d;
    private VectorProgram vectorProgram3d;
        
    public GLBatchGeometryRenderer() {
        this(null);
    }

    public GLBatchGeometryRenderer(MapRenderer renderCtx) {
        this.renderCtx = renderCtx;

        this.state = new BatchPipelineState();
        this.batch = null;
        
        this.buffers = null;
    }
    
    /** @deprecated */
    public long hitTest(Point loc, double thresholdMeters) {
        ArrayList<Long> fid = new ArrayList<Long>();
        this.hitTest2(fid, loc, thresholdMeters, 1);
        if(fid.isEmpty())
            return FeatureDataStore.FEATURE_ID_NONE;
        return fid.get(0).longValue();
    }
    
    public void hitTest2(Collection<Long> fids, Point loc, double thresholdMeters, int limit) {
        final double locx = loc.getX();
        final double locy = loc.getY();
        final double rlat = Math.toRadians(locy);
        final double metersDegLat = 111132.92 - 559.82 * Math.cos(2* rlat) + 1.175*Math.cos(4*rlat);
        final double metersDegLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3*rlat);

        final double ra = thresholdMeters/metersDegLat;
        final double ro = thresholdMeters/metersDegLng;
        
        final Envelope hitBox = new Envelope(locx-ro, locy-ra, Double.NaN, locx+ro, locy+ra, Double.NaN);
            
        Iterator<GLBatchPoint> pointIter;
        
        pointIter = this.labels.descendingIterator();
        while(pointIter.hasNext()) {
            GLBatchPoint item = pointIter.next();
            if(Rectangle.contains(hitBox.minX, hitBox.minY, hitBox.maxX, hitBox.maxY, item.longitude, item.latitude)) {
                fids.add(Long.valueOf(item.featureId));
                if(fids.size() == limit)
                    return;
            }
        }
        
        for(int i = this.batchPoints2.size()-1; i >= 0; i--) {
            GLBatchPoint item = this.batchPoints2.get(i);
            if(Rectangle.contains(hitBox.minX, hitBox.minY, hitBox.maxX, hitBox.maxY, item.longitude, item.latitude)) {
                fids.add(Long.valueOf(item.featureId));
                if(fids.size() == limit)
                    return;
            }
        }

        Iterator<? extends GLBatchLineString> lineIter;
        
        lineIter = this.lines.descendingIterator();
        do {
            long fid = hitTestLineStrings(lineIter, loc, thresholdMeters, hitBox);
            if(fid != FeatureDataStore.FEATURE_ID_NONE) {
                fids.add(Long.valueOf(fid));
                if(fids.size() == limit)
                    return;
            }
        } while(lineIter.hasNext());
        
        lineIter = this.polys.descendingIterator();
        do {
            long fid = hitTestLineStrings(lineIter, loc, thresholdMeters, hitBox);
            if(fid != FeatureDataStore.FEATURE_ID_NONE) {
                fids.add(Long.valueOf(fid));
                if(fids.size() == limit)
                    return;
            }
        } while(lineIter.hasNext());
    }

    public void setBatch(Collection<GLBatchGeometry> geoms) {
        sortedPolys.clear();
        sortedLines.clear();

        polys.clear();
        lines.clear();
        
        loadingPoints.clear();
        batchPoints2.clear();
        labels.clear();

        this.fillBatchLists(geoms);
        
        // 
        if(sortInfo.order == SortInfo.FID)
            Collections.sort(this.batchPoints2, FID_COMPARATOR);
        else if(sortInfo.order == SortInfo.DEPTH)
            Collections.sort(this.batchPoints2, new DepthComparator(sortInfo.centerLat, sortInfo.measureFromLat, sortInfo.measureFromLng));

        Iterator<GLBatchGeometry> iter;
        
        iter = this.sortedLines.iterator();
        while(iter.hasNext()) {
            this.lines.add((GLBatchLineString)iter.next());
            iter.remove();
        }
        
        iter = this.sortedPolys.iterator();
        while(iter.hasNext()) {
            this.polys.add((GLBatchPolygon)iter.next());
            iter.remove();
        }
    }
    
    private void fillBatchLists(Collection<GLBatchGeometry> geoms) {
        for(GLBatchGeometry g : geoms) {
            switch(g.zOrder) {
                case 0: {
                    GLBatchPoint point = (GLBatchPoint)g;
                    if(point.textureKey != 0L) {
                        batchPoints2.add(point);
                    } else if(point.iconUri != null) {
                        loadingPoints.add(point);
                    } else if(point.name != null){
                        labels.add(point);
                    }
                    break;
                }
                case 2: {
                    if(((GLBatchPolygon)g).fillColorA > 0.0f) {
                        sortedPolys.add(g);
                        break;
                    } else if(!((GLBatchPolygon)g).drawStroke) {
                        break;
                    }
                    // if the polygon isn't filled, treat it just like a line
                }
                case 1: {
                    //if(((GLBatchLineString)g).strokeColorA > 0.0f)
                        sortedLines.add(g);
                    break;
                }
                case 10 :
                case 11 :
                case 12 :
                case 13 :
                    this.fillBatchLists(((GLBatchGeometryCollection)g).points);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).lines);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).polys);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).collections);
                    break;
                default :
                    throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public  void draw(GLMapView view, int renderPass) {
        if(this.buffers == null) {
            synchronized(renderBuffers) {
                buffers = renderBuffers.get(renderCtx);
                if(buffers == null) {
                    buffers = new RenderBuffers(MAX_BUFFERED_2D_POINTS);
                    if(renderCtx != null)
                        renderBuffers.put(renderCtx, buffers);
                }
                buffers.references++;
            }
         
            this.pointsBuffer = buffers.pointsBuffer;
            this.pointsBufferPtr = buffers.pointsBufferPtr;
            this.pointsVertsTexCoordsBuffer = buffers.pointsVertsTexCoordsBuffer;
            this.textureAtlasIndicesBuffer = buffers.textureAtlasIndicesBuffer;            
        }

        // reset the state to the defaults
        this.state.color = 0xFFFFFFFF;
        this.state.lineWidth = 1.0f;
        this.state.texId = 0;
        
        int[] i = new int[1];
        GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_ACTIVE_TEXTURE, i, 0);
        this.state.textureUnit = i[0];
        
        if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            this.renderSurface(view);
        if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            this.renderSprites(view);
    }

    private void renderSurface(GLMapView view) {
        // polygons
        if(this.polys.size() > 0) {
            if(this.batch == null)
                this.batch = new GLRenderBatch2();

            GLES20FixedPipeline.glPushMatrix();
            
            // XXX - batch currently only supports 2D vertices

            final boolean hardwareTransforms = (view.drawMapResolution > view.hardwareTransformResolutionThreshold/4d);
            
            final int vertType;
            if(!hardwareTransforms) {
                // XXX - force all polygons projected as pixels as stroking does
                //       not work properly. since vertices are in projected
                //       coordinate space units, width also needs to be
                //       specified as such. attempts to compute some nominal
                //       scale factor produces reasonable results at lower map
                //       resolutions but cause width to converge to zero (32-bit
                //       precision?) at higher resolutions
                vertType = GLGeometry.VERTICES_PIXEL;
            } else {
                vertType = GLGeometry.VERTICES_PROJECTED;
                
                GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
            }

            int hints = GLRenderBatch2.HINT_UNTEXTURED;
            if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
                hints |= GLRenderBatch2.HINT_TWO_DIMENSION;
            
            this.batch.begin(hints);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            
            GLBatchPolygon poly;
            for(GLBatchGeometry g : this.polys) {
                poly = (GLBatchPolygon)g;
                poly.batch(view, this.batch, GLMapView.RENDER_PASS_SURFACE, vertType);
            }
            this.batch.end();
            GLES20FixedPipeline.glPopMatrix();
        }

        // lines
        if(this.lines.size() > 0)
            this.batchDrawLines(view);
    }
    
    private void renderSprites(GLMapView view) {
        sortInfo.order = (view.drawTilt > 0d || view.drawSrid == 4978) ? SortInfo.DEPTH : SortInfo.FID;
        sortInfo.centerLat = view.drawLat;
        sortInfo.centerLng = view.drawLng;
        
        view.scratch.geo.set(view.drawLat, view.drawLng);
        GeoPoint bottomCenter = GeoCalculations.midPoint(view.lowerLeft, view.lowerRight);
        GeoPoint measureFrom = DistanceCalculations.computeDestinationPoint(view.scratch.geo, view.scratch.geo.bearingTo(bottomCenter), view.scratch.geo.distanceTo(bottomCenter)*1.5d);
        
        sortInfo.measureFromLat = measureFrom.getLatitude();
        sortInfo.measureFromLng = measureFrom.getLongitude();
        sortInfo.measureFromHae = measureFrom.getAltitude();

        // points
        
        // if the relative scaling has changed we need to reset the default text
        // and clear the texture atlas
        if(GLBatchPoint.iconAtlasDensity != AtakMapView.DENSITY) {
            GLBatchPoint.ICON_ATLAS.release();
            GLBatchPoint.ICON_ATLAS = new GLTextureAtlas(1024, (int)Math.ceil(32*AtakMapView.DENSITY));
            GLBatchPoint.iconLoaders.clear();
            GLBatchPoint.iconAtlasDensity = AtakMapView.DENSITY;
        }

        // check all points with loading icons and move those whose icon has
        // loaded into the batchable list
        final Iterator<GLBatchPoint> iter = this.loadingPoints.iterator();        
        while(iter.hasNext()) {
            GLBatchPoint point = iter.next();
            GLBatchPoint.getOrFetchIcon(view.getSurface(), point);
            if(point.textureKey != 0L) {
                this.batchPoints2.add(point);
                iter.remove();
            }
        }

        // render all labels
        if(!this.labels.isEmpty()) {
            if(this.batch == null)
                this.batch = new GLRenderBatch2();

            int hints = 0;
            // XXX - when do we want to do 2D for labels???
            /*
            if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
                hints |= GLRenderBatch2.HINT_TWO_DIMENSION;
            */
            this.batch.begin(hints);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            
            for(GLBatchGeometry g : this.labels)
                g.batch(view, this.batch, GLMapView.RENDER_PASS_SPRITES);
            this.batch.end();
        }

        // render points with icons
        final int numBatchPoints = this.batchPoints2.size();
        if(forceGLRB == -1)
            forceGLRB = ConfigOptions.getOption("glbatchgeometryrenderer.force-points-render-batch", 0);
        if(forcePointsDraw == -1)
            forcePointsDraw = ConfigOptions.getOption("glbatchgeometryrenderer.force-points-draw", 0);
        if((forceGLRB == 0 && forcePointsDraw == 0) && (numBatchPoints > POINT_BATCHING_THRESHOLD ||
                (numBatchPoints > 1 &&
                        (!GLMapSurface.SETTING_displayLabels ||
                         view.drawMapScale < GLBatchPoint.defaultLabelRenderScale)))) {

            // batch if there are many points on the screen or if we have more
            // than one point and labels are not going to be drawn
            this.batchDrawPoints(view);
        } else if(forcePointsDraw != 0) {
            for(GLBatchGeometry point : this.batchPoints2)
                point.draw(view);
        } else if(numBatchPoints > 0) {
            if(this.batch == null)
                this.batch = new GLRenderBatch2();

            int hints = 0;
            // XXX - when do we want to do 2D for points???
            /*
            if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
                hints |= GLRenderBatch2.HINT_TWO_DIMENSION;
            */
            this.batch.begin(hints);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            
            for(GLBatchGeometry point : this.batchPoints2)
                point.batch(view, this.batch, GLMapView.RENDER_PASS_SPRITES);
            this.batch.end();
        }
    }
    
    int forceGLRB = -1;
    int forcePointsDraw = -1;

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE;
    }

    private void batchDrawLines(GLMapView view) {
        final boolean hardwareTransforms = (view.drawMapResolution > view.hardwareTransformResolutionThreshold/4d);
        if(hardwareTransforms) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
            batchDrawLinesImpl(view, GLGeometry.VERTICES_PROJECTED);
            GLES20FixedPipeline.glPopMatrix();
        } else {
            batchDrawLinesImpl(view, GLGeometry.VERTICES_PIXEL);
        }
    }
    
    private void batchDrawLinesImpl(GLMapView view, int vertexType) {
        VectorProgram vectorProgram;
        final int maxBufferedPoints;
        if(this.vectorProgram3d == null)
            this.vectorProgram3d = new VectorProgram(3);
        else
            GLES20FixedPipeline.glUseProgram(this.vectorProgram3d.programHandle);
        vectorProgram = this.vectorProgram3d;
        maxBufferedPoints = MAX_BUFFERED_3D_POINTS;

        
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(vectorProgram.uProjectionHandle, 1, false,
                view.scratch.matrixF, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(vectorProgram.uModelViewHandle, 1, false, view.scratch.matrixF,
                0);
        
        // sync the current color with the shader
        GLES20FixedPipeline.glUniform4f(vectorProgram.uColorHandle,
                                        Color.red(this.state.color) / 255f,
                                        Color.green(this.state.color) / 255f,
                                        Color.blue(this.state.color) / 255f,
                                        Color.alpha(this.state.color) / 255f);
        
        // sync the current line width
        GLES20FixedPipeline.glLineWidth(this.state.lineWidth);
        
        this.pointsBuffer.clear();
        final FloatBuffer linesBuffer = this.pointsBuffer;
        final long linesBufferPtr = this.pointsBufferPtr;

        GLBatchLineString.RenderState[] defaultrs = new GLBatchLineString.RenderState[] {GLBatchLineString.DEFAULT_RS};

        GLBatchLineString line;
        for(GLBatchGeometry g : this.lines) {
            line = (GLBatchLineString)g;
            if(line.numRenderPoints < 2)
                continue;

            GLBatchLineString.RenderState[] renderStates = line.renderStates;
            if(renderStates == null)
                renderStates = defaultrs;
            for(GLBatchLineString.RenderState rs : renderStates) {
                if (rs.strokeColor != this.state.color) {
                    if (linesBuffer.position() > 0) {
                        linesBuffer.flip();
                        renderLinesBuffers(vectorProgram,
                                linesBuffer,
                                GLES20FixedPipeline.GL_LINES,
                                linesBuffer.remaining() / vectorProgram.vertSize);
                        linesBuffer.clear();
                    }

                    GLES20FixedPipeline.glUniform4f(vectorProgram.uColorHandle,
                            rs.strokeColorR,
                            rs.strokeColorG,
                            rs.strokeColorB,
                            rs.strokeColorA);

                    this.state.color = rs.strokeColor;
                }

                if (rs.strokeWidth != this.state.lineWidth) {
                    if (linesBuffer.position() > 0) {
                        linesBuffer.flip();
                        renderLinesBuffers(vectorProgram,
                                linesBuffer,
                                GLES20FixedPipeline.GL_LINES,
                                linesBuffer.remaining() / vectorProgram.vertSize);
                        linesBuffer.clear();
                    }

                    GLES20FixedPipeline.glLineWidth(rs.strokeWidth);
                    this.state.lineWidth = rs.strokeWidth;
                }

                line.projectVertices(view, vertexType);
                if (((line.numRenderPoints - 1) * 2) > maxBufferedPoints) {
                    // the line has more points than can be buffered -- render it
                    // immediately as a strip and don't touch the buffer.
                    // technically, this will violate Z-order, but since we have the
                    // same state (color, line-width) with everything currently
                    // batched we shouldn't be able to distinguish Z anyway...
                    renderLinesBuffers(vectorProgram,
                            line.vertices,
                            GLES20FixedPipeline.GL_LINE_STRIP,
                            line.numRenderPoints);
                } else {
                    int remainingSegments = line.numRenderPoints - 1;
                    int numSegsToExpand;
                    int off = 0;
                    while (remainingSegments > 0) {
                        numSegsToExpand = Math.min(linesBuffer.remaining() / (2 * vectorProgram.vertSize), remainingSegments);
                        expandLineStringToLines(vectorProgram.vertSize,
                                line.verticesPtr,
                                off,
                                linesBufferPtr,
                                linesBuffer.position(),
                                numSegsToExpand + 1);

                        linesBuffer.position(linesBuffer.position() + (numSegsToExpand * (2 * vectorProgram.vertSize)));
                        off += numSegsToExpand * vectorProgram.vertSize;
                        remainingSegments -= numSegsToExpand;
                        if (linesBuffer.remaining() < (2 * vectorProgram.vertSize)) {
                            linesBuffer.flip();
                            renderLinesBuffers(vectorProgram,
                                    linesBuffer,
                                    GLES20FixedPipeline.GL_LINES,
                                    linesBuffer.remaining() / vectorProgram.vertSize);
                            linesBuffer.clear();
                        }
                    }
                }
            }
        }

        if(linesBuffer.position() > 0) {
            linesBuffer.flip();
            renderLinesBuffers(vectorProgram,
                               linesBuffer,
                               GLES20FixedPipeline.GL_LINES,
                               linesBuffer.remaining() / vectorProgram.vertSize);
            linesBuffer.clear();
        }

        // sync the current color with the pipeline
        GLES20FixedPipeline.glColor4f(Color.red(this.state.color) / 255f,
                                      Color.green(this.state.color) / 255f,
                                      Color.blue(this.state.color) / 255f,
                                      Color.alpha(this.state.color) / 255f);
    }
    
    private static void renderLinesBuffers(VectorProgram vectorProgram, FloatBuffer buf, int mode, int numPoints) {
        GLES20FixedPipeline.glVertexAttribPointer(vectorProgram.aVertexCoordsHandle,
                                                  vectorProgram.vertSize,
                                                  GLES20FixedPipeline.GL_FLOAT,
                                                  false,
                                                  0,
                                                  buf);

        GLES20FixedPipeline.glEnableVertexAttribArray(vectorProgram.aVertexCoordsHandle);        
        GLES30.glDrawArrays(mode, 0, numPoints);
        GLES20FixedPipeline.glDisableVertexAttribArray(vectorProgram.aVertexCoordsHandle);
    }
    
    private void batchDrawPoints(GLMapView view) {
        this.state.color = 0xFFFFFFFF;
        this.state.texId = 0;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadIdentity();
        
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        final TextureProgram textureProgram;
        
        if(view.drawTilt > 0d || view.drawSrid == 4978) {
            if(this.textureProgram3d == null)
                this.textureProgram3d = new TextureProgram(3);
            else
                GLES20FixedPipeline.glUseProgram(this.textureProgram3d.programHandle);
            textureProgram = this.textureProgram3d;
        } else {
            if(this.textureProgram2d == null)
                this.textureProgram2d = new TextureProgram(2);
            else
                GLES20FixedPipeline.glUseProgram(this.textureProgram2d.programHandle);
            textureProgram = this.textureProgram2d;
        }
        

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(textureProgram.uProjectionHandle, 1, false,
                view.scratch.matrixF, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(textureProgram.uModelViewHandle, 1, false, view.scratch.matrixF,
                0);
        
        // work with texture0
        GLES20FixedPipeline.glActiveTexture(this.state.textureUnit);
        GLES20FixedPipeline.glUniform1i(textureProgram.uTextureHandle, this.state.textureUnit
                - GLES20FixedPipeline.GL_TEXTURE0);
        
        // sync the current color with the shader
        GLES20FixedPipeline.glUniform4f(textureProgram.uColorHandle,
                                        Color.red(this.state.color) / 255f,
                                        Color.green(this.state.color) / 255f,
                                        Color.blue(this.state.color) / 255f,
                                        Color.alpha(this.state.color) / 255f);
        
        this.pointsBuffer.clear();
        this.textureAtlasIndicesBuffer.clear();

        int pointsBufferPos = 0;

        GLBatchPoint point;
        for(GLBatchGeometry geom : this.batchPoints2) {
            point = (GLBatchPoint)geom;
            
            if(point.iconUri == null)
                continue;
            
            if(point.textureKey == 0L) {
                GLBatchPoint.getOrFetchIcon(view.getSurface(), point);
                continue;
            }
            
            if(this.state.texId != point.textureId) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                pointsBufferPos = 0;
                this.pointsBuffer.clear();
                this.textureAtlasIndicesBuffer.clear();
                
                this.state.texId = point.textureId;
                GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                        this.state.texId);
            }
            if(this.state.texId == 0)
                continue;
            
            if(point.color != this.state.color) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                pointsBufferPos = 0;
                this.pointsBuffer.clear();
                this.textureAtlasIndicesBuffer.clear();
                
                GLES20FixedPipeline.glUniform4f(textureProgram.uColorHandle,
                        Color.red(point.color) / 255f,
                        Color.green(point.color) / 255f,
                        Color.blue(point.color) / 255f,
                        Color.alpha(point.color) / 255f);
                
                this.state.color = point.color;
            }

            if(textureProgram.vertSize == 2) {
                Unsafe.setFloats(this.pointsBufferPtr+pointsBufferPos,
                                 (float)view.idlHelper.wrapLongitude(point.longitude),
                                 (float)point.latitude);
                pointsBufferPos += 8;
            } else {
                double alt = 0d;
                if(view.drawTilt > 0d) {
                    final double el = point.validateLocalElevation(view);
                    // note: always NaN if source alt is NaN
                    double adjustedAlt = (el + view.elevationOffset) * view.elevationScaleFactor;

                    // move up half icon height
                    adjustedAlt += view.drawMapResolution * (point.iconHeight / 2d);

                    // move up ~5 pixels from surface
                    adjustedAlt += view.drawMapResolution * 10d * AtakMapView.DENSITY;

                    alt = adjustedAlt;
                }

                Unsafe.setFloats(this.pointsBufferPtr+pointsBufferPos,
                                 (float)view.idlHelper.wrapLongitude(point.longitude),
                                 (float)point.latitude,
                                 (float)alt);
                pointsBufferPos += 12;                
            }
            this.textureAtlasIndicesBuffer.put(point.textureIndex);

            if(((pointsBufferPos/4)+3) >= this.pointsBuffer.limit()) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                this.textureAtlasIndicesBuffer.clear();
                this.pointsBuffer.clear();
                pointsBufferPos = 0;
            }
        }
        
        if(pointsBufferPos > 0) {
            this.pointsBuffer.position(pointsBufferPos/4);
            this.renderPointsBuffers(view, textureProgram);
            this.textureAtlasIndicesBuffer.clear();
            this.pointsBuffer.clear();
            pointsBufferPos = 0;
        }
        
        GLES20FixedPipeline.glPopMatrix();
        
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

        if(this.state.texId != 0)
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
        
        // sync the current color with the pipeline
        GLES20FixedPipeline.glColor4f(Color.red(this.state.color) / 255f,
                                      Color.green(this.state.color) / 255f,
                                      Color.blue(this.state.color) / 255f,
                                      Color.alpha(this.state.color) / 255f);
    }

    private void renderPointsBuffers(GLMapView view, TextureProgram textureProgram) {
        this.textureAtlasIndicesBuffer.flip();
        
        if(this.textureAtlasIndicesBuffer.remaining() < 1)
            return;

        this.pointsBuffer.flip();
        view.forward(pointsBuffer, textureProgram.vertSize, pointsBuffer, textureProgram.vertSize);
        pointsBuffer.clear();

        this.pointsVertsTexCoordsBuffer.clear();

        fillVertexArrays(textureProgram.vertSize,
                         this.pointsBuffer,
                         this.textureAtlasIndicesBuffer,
                         GLBatchPoint.ICON_ATLAS.getImageWidth(0), // fixed size
                         GLBatchPoint.ICON_ATLAS.getTextureSize(),
                         this.pointsVertsTexCoordsBuffer,
                         this.textureAtlasIndicesBuffer.remaining());

        GLES20FixedPipeline.glVertexAttribPointer(textureProgram.aVertexCoordsHandle, textureProgram.vertSize,
                GLES20FixedPipeline.GL_FLOAT,
                false, (4*textureProgram.vertSize)+8, this.pointsVertsTexCoordsBuffer.position(0));
        GLES20FixedPipeline.glEnableVertexAttribArray(textureProgram.aVertexCoordsHandle);

        GLES20FixedPipeline.glVertexAttribPointer(textureProgram.aTextureCoordsHandle, 2,
                GLES20FixedPipeline.GL_FLOAT,
                false, (4*textureProgram.vertSize)+8, this.pointsVertsTexCoordsBuffer.position(textureProgram.vertSize));
        GLES20FixedPipeline.glEnableVertexAttribArray(textureProgram.aTextureCoordsHandle);
        
        int remaining = this.textureAtlasIndicesBuffer.remaining();
        final int iconsPerPass = MAX_VERTS_PER_DRAW_ARRAYS / 6;
        int off = 0;
        do {
            // XXX - note that we could use triangle strips here, but we would
            //       need a degenerate triangle for every icon except the last
            //       one, meaning that all icons except the last would require
            //       6 vertices
            GLES30.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLES,
                                off*6,
                                Math.min(remaining, iconsPerPass)*6);

            remaining -= iconsPerPass;
            off += iconsPerPass;
        } while(remaining > 0);
        
        GLES20FixedPipeline.glDisableVertexAttribArray(textureProgram.aVertexCoordsHandle);
        GLES20FixedPipeline.glDisableVertexAttribArray(textureProgram.aTextureCoordsHandle);
        
        this.pointsBuffer.position(this.pointsBuffer.limit());
        this.textureAtlasIndicesBuffer.position(this.textureAtlasIndicesBuffer.limit());
    }
    
    @Override
    public void release() {
        this.lines.clear();
        this.polys.clear();
        
        this.batchPoints2.clear();
        this.loadingPoints.clear();
        this.labels.clear();
        
        if(batch != null) {
            this.batch.release();
            this.batch.dispose();
            this.batch = null;
        }

        if(this.buffers != null) {
            this.buffers.references--;
            if(this.buffers.references < 1) {
                synchronized(renderBuffers) {
                    renderBuffers.remove(this.renderCtx);
                }
            }

            this.pointsBuffer = null;
            this.pointsVertsTexCoordsBuffer = null;
            this.textureAtlasIndicesBuffer = null;
            this.buffers = null;
        }
    }
    
    /**************************************************************************/
    
    private static long hitTestLineStrings(Iterator<? extends GLBatchLineString> iter, Point loc, double radius, Envelope hitBox) {
        GLBatchLineString item;
        while (iter.hasNext()) {
            item = iter.next();
            if(item.renderPoints == null)
                continue;            
            if(testOrthoHit(item.renderPoints, item.numRenderPoints, 3, item.mbb, loc, radius, hitBox))
                return item.featureId;
        }

        return FeatureDataStore.FEATURE_ID_NONE;
    }


    /**************************************************************************/
    
    private static native void fillVertexArrays(int vertSize,
                                                FloatBuffer translations,
                                                IntBuffer textureKeys,
                                                int iconSize,
                                                int textureSize,
                                                FloatBuffer vertsTexCoords,
                                                int count);
    
    /**
     * Expands a buffer containing a line strip into a buffer containing lines.
     * None of the properties of the specified buffers (e.g. position, limit)
     * are modified as a result of this method.
     * 
     * @param size              The vertex size, in number of elements
     * @param linestripPtr         The pointer to the base of the line strip buffer
     * @param linestripPosition The position of the linestrip buffer (should
     *                          always be <code>linestrip.position()</code>).
     * @param linesPtr             The pointer to the base of the destination
     *                          buffer for the lines
     * @param linesPosition     The position of the lines buffer (should always
     *                          be <code>lines.position()</code>).
     * @param count             The number of points in the line string to be
     *                          consumed.
     */
    private static native void expandLineStringToLines(int size, long linestripPtr,
            int linestripPosition, long linesPtr, int linesPosition, int count);
    
    /**************************************************************************/
    
    private static class BatchPipelineState {
        public int color;
        public float lineWidth;
        public int texId;
        public int textureUnit;
    }
    
    private static class VectorProgram {
        final int programHandle;
        final int uProjectionHandle;
        final int uModelViewHandle;
        final int aVertexCoordsHandle;
        final int uColorHandle;
        
        final int vertSize;
        
        VectorProgram(int vertSize) {
            this.vertSize = vertSize;

            String vertShaderSrc;
            switch(this.vertSize) {
                case 2 :
                    vertShaderSrc = GLES20FixedPipeline.VECTOR_2D_VERT_SHADER_SRC;
                    break;
                case 3 :
                    vertShaderSrc = GLES20FixedPipeline.VECTOR_3D_VERT_SHADER_SRC;
                    break;
                default :
                    throw new IllegalArgumentException();
            }
            final int vertShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_VERTEX_SHADER,
                    vertShaderSrc);
            final int fragShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_FRAGMENT_SHADER,
                    GLES20FixedPipeline.GENERIC_VECTOR_FRAG_SHADER_SRC);
            
            this.programHandle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            GLES20FixedPipeline.glUseProgram(this.programHandle);

            this.uProjectionHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uProjection");
            
            this.uModelViewHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uModelView");
            
            this.uColorHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uColor");
            
            this.aVertexCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aVertexCoords");
        }
    }
    
    private static class TextureProgram {
        final int programHandle;
        final int uProjectionHandle;
        final int uModelViewHandle;
        final int uTextureHandle;
        final int aTextureCoordsHandle;
        final int aVertexCoordsHandle;
        final int uColorHandle;
        
        final int vertSize;
        
        TextureProgram(int vertSize) {
            this.vertSize = vertSize;

            String vertShaderSrc;
            switch(this.vertSize) {
                case 2 :
                    vertShaderSrc = GLES20FixedPipeline.TEXTURE_2D_VERT_SHADER_SRC;
                    break;
                case 3 :
                    vertShaderSrc = GLES20FixedPipeline.TEXTURE_3D_VERT_SHADER_SRC;
                    break;
                default :
                    throw new IllegalArgumentException();
            }
            final int vertShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_VERTEX_SHADER,
                    vertShaderSrc);
            final int fragShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_FRAGMENT_SHADER,
                    GLES20FixedPipeline.MODULATED_TEXTURE_FRAG_SHADER_SRC);
            
            this.programHandle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            GLES20FixedPipeline.glUseProgram(this.programHandle);

            this.uProjectionHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uProjection");
            
            this.uModelViewHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uModelView");
            
            this.uTextureHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uTexture");
            
            this.uColorHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uColor");
            
            this.aVertexCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aVertexCoords");
            this.aTextureCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aTextureCoords");

        }
    }
    
    /**************************************************************************/
    // XXX - CONSOLIDATE INTO ONE LOCATION POST 3.3 !!!
    /**************************************************************************/

    // XXX - next 3 modified from EditablePolyline, review for optimization
    
    private static boolean mbrIntersects(Envelope mbb, Point point, double radiusMeters) {
        final double x = point.getX();
        final double y = point.getY();

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

        return (DistanceCalculations.calculateRange(new GeoPoint(fromY, fromX), new GeoPoint(y, x)) < radiusMeters);
    }
    
    private static boolean testOrthoHit(DoubleBuffer linestring, int numPoints,
            int size,
            Envelope mbr, Point loc, double radius, Envelope test) {
        final long linestringPtr = Unsafe.getBufferPointer(linestring);
        double x0;
        double y0;
        double x1;
        double y1;
        int idx;

        double lx = loc.getX();
        Envelope t2 = new Envelope(test.minX, test.minY, test.minZ,
                test.maxX, test.maxY, test.maxZ);

        // Account for unwrapped longitudes
        if (mbr.maxX > 180 && mbr.minX > t2.maxX) {
            t2.minX += 360;
            t2.maxX += 360;
            lx += 360;
        } else if (mbr.minX < -180 && mbr.maxX < t2.minX) {
            t2.minX -= 360;
            t2.minX -= 360;
            lx -= 360;
        }

        if (!Rectangle.intersects(mbr.minX, mbr.minY, mbr.maxX, mbr.maxY,
                                  t2.minX, t2.minY, t2.maxX, t2.maxY)) {

            //Log.d(TAG, "hit not contained in any geobounds");
            return false;
        }

        final double ly = loc.getY();
        for (int i = 0; i < numPoints-1; ++i) {
            idx = (i*(8*size));
            x0 = Unsafe.getDouble(linestringPtr+idx);
            y0 = Unsafe.getDouble(linestringPtr+idx+8);
            x1 = Unsafe.getDouble(linestringPtr+idx+(8*size));
            y1 = Unsafe.getDouble(linestringPtr+idx+(8*size)+8);
            
            if(isectTest(x0, y0, x1, y1, lx, ly, radius, t2)) {
                return true;
            }
        }
        //Log.d(TAG, "hit not contained in any sub geobounds");
        return false;
    }

    private static boolean isectTest(double x1, double y1, double x2, double y2, double x3,double y3, double radius, Envelope test) { // x3,y3 is the point
        if(!Rectangle.intersects(Math.min(x1, x2), Math.min(y1, y2),
                                 Math.max(x1, x2), Math.max(y1, y2),
                                 test.minX, test.minY,
                                 test.maxX, test.maxY)) {
            return false;
        }

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

        return DistanceCalculations.calculateRange(new GeoPoint(y, x), new GeoPoint(y3, x3))<radius;
    }
    
    protected final static class RenderBuffers {
        public int references;
        public final FloatBuffer pointsBuffer;
        public final long pointsBufferPtr;
        public final FloatBuffer pointsVertsTexCoordsBuffer;
        public final IntBuffer textureAtlasIndicesBuffer;
        
        public RenderBuffers(int maxBuffered2dPoints) {
            this.pointsBuffer = com.atakmap.lang.Unsafe.allocateDirect(maxBuffered2dPoints * 2 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            this.pointsBufferPtr = Unsafe.getBufferPointer(pointsBuffer);

            this.pointsVertsTexCoordsBuffer = Unsafe
                    .allocateDirect(pointsBuffer.capacity() * 2 * 4 * 6 * 2).order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            this.textureAtlasIndicesBuffer = Unsafe
                    .allocateDirect(pointsBuffer.capacity() * 4).order(ByteOrder.nativeOrder())
                    .asIntBuffer();            
        }
    }
    
    final static class SortInfo {
        final static int DEPTH = 0;
        final static int FID = 1;
        
        int order = FID;
        double centerLat = 0d;
        double centerLng = 0d;
        double measureFromLat = 0d;
        double measureFromLng = 0d;
        double measureFromHae = 0d;
    }
    
    static class DepthComparator implements Comparator<GLBatchPoint> {

        double measureFromLat;
        double measureFromLng;
        double measureFromHae;
        double metersPerDegreeLatSq;
        double metersPerDegreeLngSq;
        
        DepthComparator(double centerLat, double measureFromLat, double measureFromLng) {
            // XXX - this is a bit crude, but we don't have the camera
            //       information here...
            
            this.measureFromLat = measureFromLat;
            this.measureFromLng = measureFromLng;
            
            // approximate meters per degree given center
            final double rlat = Math.toRadians(centerLat);
            final double metersLat = 111132.92 - 559.82 * Math.cos(2* rlat) + 1.175*Math.cos(4*rlat);
            final double metersLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3*rlat);
            
            metersPerDegreeLatSq = metersLat*metersLat;
            metersPerDegreeLngSq = metersLng*metersLng;
        }
        
        @Override
        public int compare(GLBatchPoint a, GLBatchPoint b) {
            // check if the same object
            if(a.featureId == b.featureId)
                return 0;
            
            final double adlat = a.latitude - measureFromLat;
            final double adlng = a.longitude - measureFromLng;
            
            final double bdlat = b.latitude - measureFromLat;
            final double bdlng = b.longitude - measureFromLng;
            

            // compute distance-squared for comparison
            final double aDistSq = ((adlat*adlat)*metersPerDegreeLatSq) + ((adlng*adlng)*metersPerDegreeLngSq);
            final double bDistSq = ((bdlat*bdlat)*metersPerDegreeLatSq) + ((bdlng*bdlng)*metersPerDegreeLngSq);

            if(aDistSq > bDistSq)
                return -1;
            else if(aDistSq < bDistSq)
                return 1;
            else
                return FID_COMPARATOR.compare(a, b);
        }
    }
}
