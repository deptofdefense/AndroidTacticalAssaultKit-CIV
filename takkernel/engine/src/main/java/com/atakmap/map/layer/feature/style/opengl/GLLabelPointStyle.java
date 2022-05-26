package com.atakmap.map.layer.feature.style.opengl;

import java.nio.FloatBuffer;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.util.Pair;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLPoint;
import com.atakmap.map.layer.feature.geometry.opengl.GLPolygon;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLText;

/**
 * @deprecated
 */
@Deprecated
@DeprecatedApi(since = "4.1")
public abstract class GLLabelPointStyle extends GLStyle {

    public final static GLStyleSpi SPI = new GLStyleSpi() {

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLStyle create(Pair<Style, Geometry> object) {
            final Style s = object.first;
            final Geometry g = object.second;
            if(s == null || g == null)
                return null;

            if(!(s instanceof LabelPointStyle))
                return null;
            if(g instanceof com.atakmap.map.layer.feature.geometry.Point)
                return new Point((LabelPointStyle)s);
            else if(g instanceof com.atakmap.map.layer.feature.geometry.LineString)
                return new LineString((LabelPointStyle)s);
            else if(g instanceof com.atakmap.map.layer.feature.geometry.Polygon)
                return new Polygon((LabelPointStyle)s);
            else if(g instanceof GeometryCollection)
                return new GLGeometryCollectionStyle(s, this);
            return null;
        }
    };

    private final static int MAX_TEXT_WIDTH = Math.round(80 * GLRenderGlobals.getRelativeScaling());

    private final String text;
    private final float textSize;
    private final int alignX;
    private final int alignY;
    private final float textColorR;
    private final float textColorG;
    private final float textColorB;
    private final float textColorA;
    private final float bgColorR;
    private final float bgColorG;
    private final float bgColorB;
    private final float bgColorA;
    private final boolean drawBackground;
    private final LabelPointStyle.ScrollMode scrollMode;
    
    public GLLabelPointStyle(LabelPointStyle style) {
        super(style);
        
        this.text = GLText.localize(style.getText());
        this.textSize = style.getTextSize();
        this.alignX = style.getLabelAlignmentX();
        this.alignY = style.getLabelAlignmentY();
        this.textColorR = Color.red(style.getTextColor())/255f;
        this.textColorG = Color.green(style.getTextColor())/255f;
        this.textColorB = Color.blue(style.getTextColor())/255f;
        this.textColorA = Color.alpha(style.getTextColor())/255f;
        this.bgColorR = Color.red(style.getBackgroundColor())/255f;
        this.bgColorG = Color.green(style.getBackgroundColor())/255f;
        this.bgColorB = Color.blue(style.getBackgroundColor())/255f;
        this.bgColorA = Color.alpha(style.getBackgroundColor())/255f;
        this.drawBackground = (this.bgColorA > 0.0f);
        this.scrollMode = style.getScrollMode();
    }


    public void drawAt(GLMapView view, float x, float y, StyleRenderContext ctx) {
        final LabelRenderContext context = (LabelRenderContext)ctx;

        LabelPointStyle.ScrollMode marquee;
        switch(this.scrollMode) {
            case DEFAULT :
                if(GLMapSurface.SETTING_shortenLabels)
                    marquee = LabelPointStyle.ScrollMode.ON;
                else
                    marquee = LabelPointStyle.ScrollMode.OFF;
                break;
            case ON :
            case OFF :
                marquee = this.scrollMode;
                break;
            default :
                throw new IllegalStateException();
        }
                
        final boolean willMarquee = (marquee == LabelPointStyle.ScrollMode.ON && context.shouldMarquee);

        final float renderLabelWidth;
        if(willMarquee)
            renderLabelWidth = MAX_TEXT_WIDTH;
        else
            renderLabelWidth = context.labelWidth;

        final float renderLabelHeight = context.labelHeight;
        
        final float labelOffsetX;
        if(this.alignX < 0)
            labelOffsetX = -renderLabelWidth;
        else if(this.alignX == 0)
            labelOffsetX = -(renderLabelWidth/2);
        else if(this.alignX > 0)
            labelOffsetX = 0.0f;
        else
            throw new IllegalStateException();

        final float labelOffsetY;
        if(this.alignY < 0)
            labelOffsetY = 0.0f;
        else if(this.alignY == 0)
            labelOffsetY = -(renderLabelHeight/2);
        else if(this.alignY > 0)
            labelOffsetY = -renderLabelHeight;
        else
            throw new IllegalStateException();

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(x+labelOffsetX, y+labelOffsetY, 0);

        if(this.drawBackground) {
            GLES20FixedPipeline.glColor4f(this.bgColorR,
                                          this.bgColorG,
                                          this.bgColorB,
                                          this.bgColorA);
            context.background.draw(-4f,
                                    -context.text.getDescent(),
                                    renderLabelWidth + 8f,
                                    renderLabelHeight,
                                    true);
        }
        
        if(willMarquee) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(context.marqueeOffset, 0f, 0f);
            context.text.draw(this.text,
                              this.textColorR,
                              this.textColorG,
                              this.textColorB,
                              this.textColorA,
                              context.marqueeOffset,
                              -context.marqueeOffset + MAX_TEXT_WIDTH);

            GLES20FixedPipeline.glPopMatrix();
            
            final long deltaTime = view.animationDelta;
            float textEndX = context.marqueeOffset + context.labelWidth;
            if (context.marqueeTimer <= 0) {

                // return to neutral scroll and wait 3 seconds
                if (textEndX <= MAX_TEXT_WIDTH) {
                    context.marqueeTimer = 3000;
                    context.marqueeOffset = 0f;
                }
                else {
                    // animate at 10 pixels per second
                    context.marqueeOffset -= (deltaTime * 0.02f);
                    if (context.marqueeOffset + context.labelWidth <= MAX_TEXT_WIDTH) {
                        context.marqueeOffset = MAX_TEXT_WIDTH - context.labelWidth;
                        context.marqueeTimer = 2000;
                    }
                }
            } else {
                context.marqueeTimer -= deltaTime;
            }
            view.requestRefresh();
        } else {
            // XXX - handling of strings with newlines is implementation detail;
            //       user should have single interface for draw vs batch
            context.text.drawSplitString(this.text,
                                         this.textColorR,
                                         this.textColorG,
                                         this.textColorB,
                                         this.textColorA);
        }
        
        GLES20FixedPipeline.glPopMatrix();
    }

    public void batchAt(GLMapView view, GLRenderBatch batch, float xpos, float ypos,
            StyleRenderContext ctx) {
        
        final LabelRenderContext context = (LabelRenderContext)ctx;

        LabelPointStyle.ScrollMode marquee;
        switch(this.scrollMode) {
            case DEFAULT :
                if(GLMapSurface.SETTING_shortenLabels)
                    marquee = LabelPointStyle.ScrollMode.ON;
                else
                    marquee = LabelPointStyle.ScrollMode.OFF;
                break;
            case ON :
            case OFF :
                marquee = this.scrollMode;
                break;
            default :
                throw new IllegalStateException();
        }
                
        final boolean willMarquee = (marquee == LabelPointStyle.ScrollMode.ON && context.shouldMarquee);

        final float renderLabelWidth;
        if(willMarquee)
            renderLabelWidth = MAX_TEXT_WIDTH;
        else
            renderLabelWidth = context.labelWidth;

        final float renderLabelHeight = context.labelHeight;
        
        final float labelOffsetX;
        if(this.alignX < 0)
            labelOffsetX = -renderLabelWidth;
        else if(this.alignX == 0)
            labelOffsetX = -(renderLabelWidth/2);
        else if(this.alignX > 0)
            labelOffsetX = 0.0f;
        else
            throw new IllegalStateException();

        final float labelOffsetY;
        if(this.alignY < 0)
            labelOffsetY = 0.0f;
        else if(this.alignY == 0)
            labelOffsetY = -(renderLabelHeight/2);
        else if(this.alignY > 0)
            labelOffsetY = -renderLabelHeight;
        else
            throw new IllegalStateException();

        final float textRenderX = xpos + labelOffsetX;
        final float textRenderY = ypos + labelOffsetY;

        if (this.drawBackground) {
            context.background.batch(batch, textRenderX - 4f,
                    textRenderY - context.text.getDescent(), renderLabelWidth + 8f, context.labelHeight, this.bgColorR,
                    this.bgColorG, this.bgColorB, this.bgColorA);
        }

        float scissorX0 = 0.0f;
        float scissorX1 = Float.MAX_VALUE;
        if (willMarquee) {
            scissorX0 = -context.marqueeOffset;
            scissorX1 = -context.marqueeOffset + renderLabelWidth;
        }

        context.text.batch(batch,
                this.text,
                textRenderX + context.marqueeOffset,
                textRenderY,
                this.textColorR,
                this.textColorG,
                this.textColorB,
                this.textColorA,
                scissorX0, scissorX1);

        if (willMarquee) {
            final long deltaTime = view.animationDelta;

            float textEndX = context.marqueeOffset + context.labelWidth;
            if (context.marqueeTimer <= 0) {
                // return to neutral scroll and wait 3 seconds
                if (textEndX <= MAX_TEXT_WIDTH) {
                    context.marqueeTimer = 3000;
                    context.marqueeOffset = 0f;
                }
                else {
                    // animate at 10 pixels per second
                    context.marqueeOffset -= (deltaTime * 0.02f);
                    if (context.marqueeOffset + context.labelWidth <= MAX_TEXT_WIDTH) {
                        context.marqueeOffset = MAX_TEXT_WIDTH - context.labelWidth;
                        context.marqueeTimer = 2000;
                    }
                }
            } else {
                context.marqueeTimer -= deltaTime;
            }
            view.requestRefresh();
        }
        
    }

    @Override
    public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
        return true;
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geometry) {
        return new LabelRenderContext(view);
    }

    @Override
    public void releaseRenderContext(StyleRenderContext ctx) {}
    
    /**************************************************************************/

    private class LabelRenderContext extends StyleRenderContext {
        float labelWidth;
        float labelHeight;
        float marqueeOffset;
        long marqueeTimer;
        boolean shouldMarquee;
        GLNinePatch background;
        GLText text;
        
        public LabelRenderContext(GLMapView view) {
            this.marqueeOffset = 0f;
            this.marqueeTimer = 0;
            
            if(GLLabelPointStyle.this.textSize > 0)
                this.text = GLText.getInstance(null, new MapTextFormat(Typeface.DEFAULT, (int)Math.ceil(GLLabelPointStyle.this.textSize)));
            else
                this.text = GLText.getInstance(null, AtakMapView.getDefaultTextFormat());
            this.labelWidth = this.text.getStringWidth(GLLabelPointStyle.this.text);
            this.labelHeight = this.text.getStringHeight();
            this.background = GLRenderGlobals.get(view).getSmallNinePatch();
        }
    }

    /**************************************************************************/

    private static final class Point extends GLLabelPointStyle {

        Point(LabelPointStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            GLPoint p = (GLPoint)geometry;
            p.getVertex(view, GLGeometry.VERTICES_PIXEL, view.scratch.pointD);
            this.drawAt(view,
                          (float)view.scratch.pointD.x,
                          (float)view.scratch.pointD.y,
                          ctx);
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry,
                StyleRenderContext ctx) {
            GLPoint p = (GLPoint)geometry;
            p.getVertex(view, GLGeometry.VERTICES_PIXEL, view.scratch.pointD);
            this.batchAt(view,
                           batch,
                           (float)view.scratch.pointD.x,
                           (float)view.scratch.pointD.y,
                           ctx);
        }
    }
    
    private static class LineString extends GLLabelPointStyle {

        public LineString(LabelPointStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            GLLineString p = (GLLineString)geometry;
            FloatBuffer buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL);
            long pointer = Unsafe.getRelativeBufferPointer(buffer);
            for(int i = 0; i < p.getNumVertices(); i++) {
                this.drawAt(view,
                            Unsafe.getFloat(pointer),
                            Unsafe.getFloat(pointer+4),
                            ctx);
                pointer += 8;
            }
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry,
                StyleRenderContext ctx) {
            GLLineString p = (GLLineString)geometry;
            FloatBuffer buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL);
            long pointer = Unsafe.getRelativeBufferPointer(buffer);
            for(int i = 0; i < p.getNumVertices(); i++) {
                this.batchAt(view,
                             batch,
                            Unsafe.getFloat(pointer),
                             Unsafe.getFloat(pointer+4),
                             ctx);
                pointer += 8;
            }
        }
    }
    
    private static class Polygon extends GLLabelPointStyle {

        public Polygon(LabelPointStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            GLPolygon p = (GLPolygon)geometry;
            //
            // OGR style for labels on polygons is to label the centroid,
            // not the vertices.
            //
//            FloatBuffer buffer;
//            long pointer;
//            for(int i = 0; i < p.getNumInteriorRings()+1; i++) {
//                buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL, i);
//                pointer = Unsafe.getRelativeBufferPointer(buffer);
//                for(int j = 0; j < p.getNumVertices(i); j++) {
//                    this.drawAt(view,
//                                Unsafe.getFloat(pointer),
//                                Unsafe.getFloat(pointer+4),
//                                ctx);
//                    pointer += 8;
//                }
//            }
            PointF centroid
                = polygonCentroid (Unsafe.getRelativeBufferPointer
                                       (p.getVertices (view,
                                                       GLGeometry.VERTICES_PIXEL,
                                                       0)),
                                   p.getNumVertices (0));
            this.drawAt (view, centroid.x, centroid.y, ctx);
        }
        
        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry,
                StyleRenderContext ctx) {

            GLPolygon p = (GLPolygon)geometry;
            //
            // OGR style for labels on polygons is to label the centroid,
            // not the vertices.
            //
//            FloatBuffer buffer;
//            long pointer;
//            for(int i = 0; i < p.getNumInteriorRings()+1; i++) {
//                buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL, i);
//                pointer = Unsafe.getRelativeBufferPointer(buffer);
//                for(int j = 0; j < p.getNumVertices(i); j++) {
//                    this.batchAt(view,
//                                 batch,
//                                 Unsafe.getFloat(pointer),
//                                 Unsafe.getFloat(pointer+4),
//                                 ctx);
//                    pointer += 8;
//                }
//            }
            PointF centroid
                = polygonCentroid (Unsafe.getRelativeBufferPointer
                                       (p.getVertices (view,
                                                       GLGeometry.VERTICES_PIXEL,
                                                       0)),
                                   p.getNumVertices (0));
            this.batchAt (view, batch, centroid.x, centroid.y, ctx);
        }

    private static
    double
    polygonArea (long polygonPtr,
                 int vertexCount)
      {
        //
        // Begin with the wraparound pair (N-1, 0)
        //
        long iPtr = polygonPtr + 8 * (vertexCount - 1);
        long jPtr = polygonPtr;
        double area = 0.0;

        do
          {
            area += Unsafe.getFloat (iPtr) * Unsafe.getFloat (jPtr + 4);
            area -= Unsafe.getFloat (iPtr + 4) * Unsafe.getFloat (jPtr);
            iPtr = jPtr;
            jPtr += 8;
          }
        while (--vertexCount > 0);

        return Math.abs (area / 2.0);
      }

    /**
     * Finds the centroid of a polygon with integer verticies.
     * 
     * @param pg
     *            The polygon to find the centroid of.
     * @return The centroid of the polygon.
     */

    private static
    PointF
    polygonCentroid (long polygonPtr,
                     int vertexCount)
      {
        if(true) {
            if(vertexCount == 0)
                return new PointF(Float.NaN, Float.NaN);
            
            float x;
            float y;
            
            x = Unsafe.getFloat(polygonPtr);
            y = Unsafe.getFloat(polygonPtr + 4);
            polygonPtr += 8;
            
            float minX = x;
            float minY = y;
            float maxX = x;
            float maxY = y;
            
            for(int i = 1; i < vertexCount; i++) {
                x = Unsafe.getFloat(polygonPtr);
                y = Unsafe.getFloat(polygonPtr + 4);
                polygonPtr += 8;
                
                minX = Math.min(x, minX);
                minY = Math.min(y, minY);
                maxX = Math.max(x, maxX);
                maxY = Math.max(y, maxY);
            }
            
            return new PointF((minX+maxX)/2f, (minY+maxY)/2f);
        }

//        double A = polygonArea (polygonPtr, vertexCount);

        long iPtr = polygonPtr + 8 * (vertexCount - 1);
        long jPtr = polygonPtr;
        float ix = Unsafe.getFloat (iPtr);
        float iy = Unsafe.getFloat (iPtr + 4);
        float jx = Unsafe.getFloat (jPtr);
        float jy = Unsafe.getFloat (jPtr + 4);
        double factor = 0;
        double cx = 0, cy = 0;
        double area = 0;

        do
          {
            factor = (ix * jy - jx * iy);
            cx += (ix + jx) * factor;
            cy += (iy + jy) * factor;
            area += factor;

            ix = jx;
            iy = jy;
            jPtr += 8;
            jx = Unsafe.getFloat (jPtr);
            jy = Unsafe.getFloat (jPtr + 4);
          }
        while (--vertexCount > 0);

        factor = 1.0 / (3.0 * area);       // Area is really (area/2).
        cx *= factor;
        cy *= factor;

        return new PointF ((float) cx, (float) cy); 
      }
    }
}
