package com.atakmap.map.layer.feature.style.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLPoint;
import com.atakmap.map.layer.feature.geometry.opengl.GLPolygon;
import com.atakmap.map.layer.feature.style.BasicPointStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLTextureAtlas;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public abstract class GLIconPointStyle extends GLStyle {
    public final static GLStyleSpi SPI = new GLStyleSpi() {

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLStyle create(Pair<Style, Geometry> object) {
            Style s = object.first;
            Geometry g = object.second;
            if(s == null || g == null)
                return null;
            // XXX - capture basic point style here as well to avoid having to
            //       implement a new GLStyle using GL_POINTs rendering right now
            if(s instanceof BasicPointStyle) {
                final BasicPointStyle basic = (BasicPointStyle)s;
                s = new IconPointStyle(basic.getColor(),
                                       defaultIconUri,
                                       basic.getSize(),
                                       basic.getSize(),
                                       0,
                                       0,
                                       0,
                                       false);
            }
            if(!(s instanceof IconPointStyle))
                return null;
            if(g instanceof com.atakmap.map.layer.feature.geometry.Point)
                return new Point((IconPointStyle)s);
            else if(g instanceof com.atakmap.map.layer.feature.geometry.LineString)
                return new LineString((IconPointStyle)s);
            else if(g instanceof com.atakmap.map.layer.feature.geometry.Polygon)
                return new Polygon((IconPointStyle)s);
            else if(g instanceof GeometryCollection)
                return new GLGeometryCollectionStyle(s, this);
            return null;
        }
    };

    private static GLTextureAtlas ICON_ATLAS = new GLTextureAtlas(1024);
    static String defaultIconUri = "asset:/icons/reference_point.png";
    private static Map<String, Pair<Future<Bitmap>, int[]>> iconLoaders = new HashMap<String, Pair<Future<Bitmap>, int[]>>();
    
    private final float colorR;
    private final float colorG;
    private final float colorB;
    private final float colorA;
    
    private final float iconWidth;
    private final float iconHeight;
    
    private final int alignX;
    private final int alignY;
    
    private final float rotation;
    private final boolean rotationAbsolute;

    public GLIconPointStyle(IconPointStyle style) {
        super(style);
        
        this.iconWidth = style.getIconWidth();
        this.iconHeight = style.getIconHeight();

        this.alignX = style.getIconAlignmentX();
        this.alignY = style.getIconAligmnentY();

        this.rotation = style.getIconRotation();
        this.rotationAbsolute = style.isRotationAbsolute();

        this.colorR = Color.red(style.getColor()) / 255f;
        this.colorG = Color.green(style.getColor()) / 255f;
        this.colorB = Color.blue(style.getColor()) / 255f;
        this.colorA = Color.alpha(style.getColor()) / 255f;
    }

    // XXX - NEED TO IMPLEMENT ROTATION  !!!!!
    
    void drawAt(GLMapView view, float xpos, float ypos, StyleRenderContext ctx) {
        IconStyleRenderContext context = (IconStyleRenderContext)ctx;
        if(context.checkIcon(view.getRenderContext(), true) == 0L)
            return;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(xpos, ypos, 0f);

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, context.verts);
        GLES20FixedPipeline.glTexCoordPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                context.texCoords);

        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, context.textureId);

        GLES20FixedPipeline.glColor4f(this.colorR, this.colorG, this.colorB, this.colorA);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, 4);

        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        
        GLES20FixedPipeline.glPopMatrix();
    }

    void batchAt(GLMapView view, GLRenderBatch batch, float xpos, float ypos, StyleRenderContext ctx) {
        IconStyleRenderContext context = (IconStyleRenderContext)ctx;
        if(context.checkIcon(view.getRenderContext(), false) == 0L)
            return;
        
        final float textureSize = ICON_ATLAS.getTextureSize();

        final float density = (float)GLRenderGlobals.getRelativeScaling();

        final float renderWidth;
        if(this.iconWidth == 0.0f)
            renderWidth = context.atlasIconWidth*density;
        else
            renderWidth = this.iconWidth*density;
        final float renderHeight;
        if(this.iconHeight == 0.0f)
            renderHeight = context.atlasIconHeight*density;
        else
            renderHeight = this.iconHeight*density;

        final float iconOffsetX;
        if(this.alignX < 0)
            iconOffsetX = -renderWidth;
        else if(this.alignX == 0)
            iconOffsetX = -(renderWidth/2);
        else if(this.alignX > 0)
            iconOffsetX = 0.0f;
        else
            throw new IllegalStateException();

        final float iconOffsetY;
        if(this.alignY < 0)
            iconOffsetY = 0.0f;
        else if(this.alignY == 0)
            iconOffsetY = -(renderHeight/2);
        else if(this.alignY > 0)
            iconOffsetY = -renderHeight;
        else
            throw new IllegalStateException();

        batch.addSprite(context.textureId,
                xpos + iconOffsetX,
                ypos + iconOffsetY,
                xpos + (iconOffsetX+renderWidth),
                ypos + (iconOffsetY+renderHeight),
                context.iconAtlasTextureOffsetX / textureSize,
                (context.iconAtlasTextureOffsetY + context.atlasIconHeight - 1.0f) / textureSize,
                (context.iconAtlasTextureOffsetX + context.atlasIconWidth - 1.0f) / textureSize,
                context.iconAtlasTextureOffsetY / textureSize,
                this.colorR, this.colorG, this.colorB, this.colorA);
    }

    @Override
    public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
        return (this.rotation == 0.0f && !this.rotationAbsolute);
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geometry) {
        return new IconStyleRenderContext((IconPointStyle)this.style);
    }

    @Override
    public void releaseRenderContext(StyleRenderContext ctx) {
        if(ctx != null) {
            IconStyleRenderContext context = (IconStyleRenderContext)ctx;
            context.release();
        }
    }

    /**************************************************************************/

    synchronized static void getOrFetchIcon(RenderContext surface, IconStyleRenderContext point) {
        do {
            if (point.iconUri == null)
                return;

            long key = ICON_ATLAS.getTextureKey(point.iconUri);
            if (key != 0L) {
                point.textureKey = key;
                point.textureId = ICON_ATLAS.getTexId(point.textureKey);
                point.atlasIconWidth = ICON_ATLAS.getImageWidth(point.textureKey);
                point.atlasIconHeight = ICON_ATLAS.getImageHeight(point.textureKey);
                point.iconAtlasTextureOffsetX = ICON_ATLAS.getImageTextureOffsetX(point.textureKey);
                point.iconAtlasTextureOffsetY = ICON_ATLAS.getImageTextureOffsetY(point.textureKey);
                point.iconLoader = null;
                point.iconLoaderUri = null;
                dereferenceIconLoaderNoSync(point.iconUri);
                return;
            }

            if (point.iconLoader != null) {
                if (point.iconLoader.isDone()) {
                    Bitmap bitmap = null;
                    if (!point.iconLoader.isCancelled()) {
                        try {
                            bitmap = point.iconLoader.get();
                        } catch (ExecutionException ignored) {
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (bitmap == null) {
                        if (point.iconUri.equals(defaultIconUri))
                            throw new IllegalStateException("Failed to load default icon");

                        // the icon failed to load, switch the default icon
                        point.iconLoader = null;
                        dereferenceIconLoaderNoSync(point.iconUri);
                        point.iconUri = defaultIconUri;

                        continue;
                    }

                    try {
                        point.textureKey = ICON_ATLAS.addImage(point.iconUri, bitmap);
                    } finally {
                        bitmap.recycle();
                    }
                    point.textureId = ICON_ATLAS.getTexId(point.textureKey);
                    point.atlasIconWidth = ICON_ATLAS.getImageWidth(point.textureKey);
                    point.atlasIconHeight = ICON_ATLAS.getImageHeight(point.textureKey);
                    point.iconAtlasTextureOffsetX = ICON_ATLAS.getImageTextureOffsetX(point.textureKey);
                    point.iconAtlasTextureOffsetY = ICON_ATLAS.getImageTextureOffsetY(point.textureKey);
                    point.iconLoader = null;
                    dereferenceIconLoaderNoSync(point.iconLoaderUri);
                    point.iconLoaderUri = null;
                }
                return;
            }

            Pair<Future<Bitmap>, int[]> iconLoader = iconLoaders.get(point.iconUri);
            if (iconLoader == null) {
                iconLoader = new Pair<Future<Bitmap>, int[]>(GLRenderGlobals.get(surface).getBitmapLoader()
                        .loadBitmap(point.iconUri, Bitmap.Config.ARGB_8888), new int[] {
                        1
                });
                if (iconLoader.first == null) {
                    point.iconUri = defaultIconUri;
                    continue;
                }
                iconLoaders.put(point.iconUri, iconLoader);
            } else {
                iconLoader.second[0]++;
            }
            point.iconLoader = iconLoader.first;
            point.iconLoaderUri = point.iconUri;

            break;
        } while (true);
    }

    private synchronized static void dereferenceIconLoader(String iconUri) {
        dereferenceIconLoaderNoSync(iconUri);
    }

    private static void dereferenceIconLoaderNoSync(String iconUri) {
        Pair<Future<Bitmap>, int[]> iconLoader = iconLoaders.get(iconUri);
        if (iconLoader == null)
            return;
        iconLoader.second[0]--;
        if (iconLoader.second[0] <= 0) {
            if (iconLoader.first.isDone() && !iconLoader.first.isCancelled()) {
                try {
                    Bitmap bitmap = iconLoader.first.get();
                    if (bitmap != null)
                        bitmap.recycle();
                } catch (InterruptedException ignored) {
                } catch (ExecutionException ignored) {
                }
            } else if (!iconLoader.first.isCancelled()) {
                iconLoader.first.cancel(false);
            }
            iconLoaders.remove(iconUri);
        }
    }
    
    /**************************************************************************/

    private class IconStyleRenderContext extends StyleRenderContext {
    
        private String iconUri;
        long textureKey;
        int textureId;
        private Future<Bitmap> iconLoader;
        private String iconLoaderUri;

        float atlasIconHeight;
        float atlasIconWidth;
        float iconAtlasTextureOffsetX;
        float iconAtlasTextureOffsetY;

        FloatBuffer texCoords;
        FloatBuffer verts;

        public IconStyleRenderContext(IconPointStyle style) {
            this.iconUri = style.getIconUri();
        }

        long checkIcon(RenderContext surface, boolean draw) {
            if (this.textureKey == 0L)
                getOrFetchIcon(surface, this);

            if(draw && this.verts == null) {
                ByteBuffer buf;

                buf = com.atakmap.lang.Unsafe.allocateDirect(4 * 2 * 4);
                buf.order(ByteOrder.nativeOrder());
                this.texCoords = buf.asFloatBuffer();

                final float density = (float)GLRenderGlobals.getRelativeScaling();

                final float textureSize = ICON_ATLAS.getTextureSize();
                
                final float renderWidth;
                if(GLIconPointStyle.this.iconWidth == 0.0f)
                    renderWidth = this.atlasIconWidth*density;
                else
                    renderWidth = GLIconPointStyle.this.iconWidth*density;
                final float renderHeight;
                if(GLIconPointStyle.this.iconHeight == 0.0f)
                    renderHeight = this.atlasIconHeight*density;
                else
                    renderHeight = GLIconPointStyle.this.iconHeight*density;

                final float iconOffsetX;
                if(GLIconPointStyle.this.alignX < 0)
                    iconOffsetX = -renderWidth;
                else if(GLIconPointStyle.this.alignX == 0)
                    iconOffsetX = -(renderWidth/2);
                else if(GLIconPointStyle.this.alignX > 0)
                    iconOffsetX = 0.0f;
                else
                    throw new IllegalStateException();

                final float iconOffsetY;
                if(GLIconPointStyle.this.alignY < 0)
                    iconOffsetY = 0.0f;
                else if(GLIconPointStyle.this.alignY == 0)
                    iconOffsetY = -(renderHeight/2);
                else if(GLIconPointStyle.this.alignY > 0)
                    iconOffsetY = -renderHeight;
                else
                    throw new IllegalStateException();

                this.texCoords.put(0, this.iconAtlasTextureOffsetX / textureSize);
                this.texCoords.put(1, (this.iconAtlasTextureOffsetY + this.atlasIconHeight - 1.0f) / textureSize);

                this.texCoords.put(2, (this.iconAtlasTextureOffsetX + this.atlasIconWidth - 1.0f) / textureSize);
                this.texCoords.put(3, (this.iconAtlasTextureOffsetY + this.atlasIconHeight - 1.0f) / textureSize);

                this.texCoords.put(4, (this.iconAtlasTextureOffsetX + this.atlasIconWidth - 1.0f) / textureSize);
                this.texCoords.put(5, this.iconAtlasTextureOffsetY / textureSize);

                this.texCoords.put(6, this.iconAtlasTextureOffsetX / textureSize);
                this.texCoords.put(7, this.iconAtlasTextureOffsetY / textureSize);

                buf = com.atakmap.lang.Unsafe.allocateDirect(4 * 2 * 4);
                buf.order(ByteOrder.nativeOrder());
                this.verts = buf.asFloatBuffer();

                this.verts.put(0, iconOffsetX);
                this.verts.put(1, iconOffsetY);

                this.verts.put(2, iconOffsetX+renderWidth);
                this.verts.put(3, iconOffsetY);

                this.verts.put(4, iconOffsetX+renderWidth);
                this.verts.put(5, iconOffsetY+renderHeight);

                this.verts.put(6, iconOffsetX);
                this.verts.put(7, iconOffsetY+renderHeight);
            }
            return this.textureKey;
        }

        public void release() {
            this.texCoords = null;
            this.verts = null;
            this.textureKey = 0L;
            if (this.iconLoader != null) {
                this.iconLoader = null;
                dereferenceIconLoader(this.iconUri);
            }
        }
    }
    
    /**************************************************************************/
    
    private static final class Point extends GLIconPointStyle {

        Point(IconPointStyle style) {
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
    
    private static class LineString extends GLIconPointStyle {

        public LineString(IconPointStyle style) {
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
    
    private static class Polygon extends GLIconPointStyle {

        public Polygon(IconPointStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            GLPolygon p = (GLPolygon)geometry;
            FloatBuffer buffer;
            long pointer;
            for(int i = 0; i < p.getNumInteriorRings()+1; i++) {
                buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL, i);
                pointer = Unsafe.getRelativeBufferPointer(buffer);
                for(int j = 0; j < p.getNumVertices(i); j++) {
                    this.drawAt(view,
                                Unsafe.getFloat(pointer),
                                Unsafe.getFloat(pointer+4),
                            ctx);
                    pointer += 8;
                }
            }
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry,
                StyleRenderContext ctx) {

            GLPolygon p = (GLPolygon)geometry;
            FloatBuffer buffer;
            long pointer;
            for(int i = 0; i < p.getNumInteriorRings()+1; i++) {
            //for(int i = 0; i < 1; i++) {
                buffer = p.getVertices(view, GLGeometry.VERTICES_PIXEL, i);
                pointer = Unsafe.getRelativeBufferPointer(buffer);
                for(int j = 0; j < p.getNumVertices(i); j++) {
                    this.batchAt(view,
                                 batch,
                                 Unsafe.getFloat(pointer),
                                 Unsafe.getFloat(pointer+4),
                            ctx);
                    pointer += 8;
                }
            }
        }
    }

    public static void invalidateIconAtlas() {
        ICON_ATLAS.release();;
    }
}
