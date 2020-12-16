package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES10;
import android.opengl.Matrix;
import android.util.Pair;


import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.lang.Objects;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicPointStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLText;
import com.atakmap.opengl.GLTextureAtlas;

public class GLBatchPoint extends GLBatchGeometry {

    static GLTextureAtlas ICON_ATLAS = new GLTextureAtlas(1024,
            (int) Math.ceil(GLRenderGlobals.getRelativeScaling() * 32));
    static float iconAtlasDensity = GLRenderGlobals.getRelativeScaling();

    final static double defaultMinLabelRenderResolution = 13d;

    private final static String defaultIconUri = "asset:/icons/reference_point.png";

    final static Map<String, Pair<Future<Bitmap>, int[]>> iconLoaders = new HashMap<String, Pair<Future<Bitmap>, int[]>>();

    private GLText glText = null;

    private static ByteBuffer tiltLineBuffer = null;
    private static long tiltLineBufferPtr = 0L;


    // https://www.gaia-gis.it/gaia-sins/BLOB-Geometry.html
    private static final int TYPE_XY = 1;
    private static final int TYPE_XYZ = 1001;
    private static final int TYPE_XYZM = 3001;


    /**************************************************************************/

    public double latitude;
    public double longitude;
    public double altitude = 0d;
    public Feature.AltitudeMode altitudeMode = Feature.AltitudeMode.ClampToGround;
    public double extrude = 0d;

    public PointD posProjected;
    public float screenX, screenY;
    int posProjectedSrid;
    double posProjectedEl;
    double posProjectedLng;
    
    PointD surfaceProjected;
    int surfaceProjectedSrid;
    double surfaceProjectedEl;

    int color;
    float colorR;
    float colorG;
    float colorB;
    float colorA;

    String iconUri;
    long textureKey;
    int textureId;
    int textureIndex;
    int iconWidth;
    int iconHeight;
    Future<Bitmap> iconLoader;
    String iconLoaderUri;
    boolean iconDirty;
    int terrainVersion;
    double localTerrainValue;



    ByteBuffer texCoords;
    ByteBuffer verts;

    private int labelAlignX = 0;
    private int labelAlignY = 0;
    private int labelTextColor = Color.WHITE;
    private int labelBgColor = Color.argb((int)(0.6f*255f), 0, 0, 0);

    private int labelTextSize;

    private float labelRotation = 0;
    private boolean labelRotationAbsolute = false;

    private double labelMinRenderResolution = defaultMinLabelRenderResolution;


    public GLBatchPoint(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchPoint(MapRenderer surface) {
        super(surface, 0);

        this.color = 0xFFFFFFFF;
        this.posProjected = new PointD(0d, 0d);
        this.posProjectedEl = 0d;
        this.posProjectedSrid = -1;
        
        this.surfaceProjected = new PointD(0d, 0d);
        this.surfaceProjectedEl = 0d;
        this.surfaceProjectedSrid = -1;
    }

    private void setIcon(String uri, int color) {
        if (Objects.equals(uri, this.iconUri))
            return;

        if (this.iconLoader != null) {
            this.iconLoader = null;
            dereferenceIconLoader(this.iconUri);
        }

        this.iconUri = uri;
        this.color = color;
        this.colorR = Color.red(this.color) / 255f;
        this.colorG = Color.green(this.color) / 255f;
        this.colorB = Color.blue(this.color) / 255f;
        this.colorA = Color.alpha(this.color) / 255f;
        this.iconDirty = true;
    }

    void checkIcon(final RenderContext surface) {
        if (this.textureKey == 0L || this.iconDirty)
            getOrFetchIcon(surface, this);
    }


    /**
     * Helper method that computes the altitude based on the set value of the localTerrainValue and
     * the altitude as set in the batch point.
     * @return the combination of the two values as directed by the Altitude Mode.
     */
    double computeAltitude(GLMapView ortho) {
        validateLocalElevation(ortho);
        switch (altitudeMode) {
            case Absolute:
                return altitude;
            case Relative:
                return altitude + localTerrainValue;
            default:
                return localTerrainValue;
        }
    }

    /**
     * Strictly checks the local terrain value.
     * @param ortho
     * @return
     */
    private double validateLocalElevation(GLMapView ortho) {
        if(ortho.drawTilt > 0d) {
            final int renderTerrainVersion = ortho.getTerrainVersion();
            if(this.terrainVersion != renderTerrainVersion) {
                this.localTerrainValue = ortho.getTerrainMeshElevation(this.latitude, this.longitude);
                this.terrainVersion = renderTerrainVersion;
            }
        }
        return this.localTerrainValue;
    }

    @Override
    public void draw(GLMapView ortho) {
        if (this.iconUri != null)
            this.checkIcon(ortho.getRenderContext());

        final double wrappedLng = ortho.idlHelper.wrapLongitude(this.longitude);
        ortho.scratch.geo.set(this.latitude, wrappedLng);
        
        // Z/altitude
        boolean belowTerrain = false;
        double posEl = 0d;
        if(ortho.drawTilt > 0d) {
            double alt = computeAltitude(ortho);
            if (!Double.isNaN(localTerrainValue) && (alt < localTerrainValue)) {
                // if the explicitly specified altitude is below the terrain,
                // float above and annotate appropriately
                belowTerrain = true;
                alt = localTerrainValue;
            }

            // note: always NaN if source alt is NaN
            double adjustedAlt = (alt+ortho.elevationOffset)*ortho.elevationScaleFactor;
            
            // move up half icon height
            if(this.textureKey != 0L) {
                adjustedAlt += ortho.drawMapResolution*(iconHeight/2d);
            }
    
            // move up ~5 pixels from surface
            adjustedAlt += ortho.drawMapResolution*10d;
            
            ortho.scratch.geo.set(adjustedAlt);
            posEl = Double.isNaN(adjustedAlt) ? 0d : adjustedAlt;
        }

        if( Double.compare(posProjectedEl,posEl) != 0 || posProjectedSrid != ortho.drawSrid || wrappedLng != posProjectedLng) {
            ortho.scene.mapProjection.forward(ortho.scratch.geo, posProjected);
            posProjectedEl = posEl;
            posProjectedSrid = ortho.drawSrid;
            posProjectedLng = wrappedLng;
        }
        
        ortho.scene.forward.transform(posProjected, ortho.scratch.pointD);

        float xpos = (float)ortho.scratch.pointD.x;
        float ypos = (float)ortho.scratch.pointD.y;
        float zpos = (float)ortho.scratch.pointD.z;

        this.screenX = xpos;
        this.screenY = ypos;
        
        //zpos = 0f;

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if(ortho.drawTilt > 0d) {
            final double terrain = this.validateLocalElevation(ortho);
            final double surfaceEl = (Math.min(terrain,  0d)+ GLMapView.elevationOffset)*ortho.elevationScaleFactor;
            
            if(Double.compare(surfaceProjectedEl, surfaceEl) != 0 || surfaceProjectedSrid != ortho.drawSrid) {
                ortho.scratch.geo.set(surfaceEl);
                ortho.scene.mapProjection.forward(ortho.scratch.geo, surfaceProjected);
                surfaceProjectedEl = surfaceEl;
                surfaceProjectedSrid = ortho.drawSrid;
            }
            
            ortho.scene.forward.transform(surfaceProjected, ortho.scratch.pointD);
            
            float xsurface = (float)ortho.scratch.pointD.x;
            float ysurface = (float)ortho.scratch.pointD.y;
            float zsurface = (float)ortho.scratch.pointD.z;
            
            if(tiltLineBuffer == null) {
                tiltLineBuffer = ByteBuffer.allocateDirect(24);
                tiltLineBuffer.order(ByteOrder.nativeOrder());
                tiltLineBufferPtr = Unsafe.getBufferPointer(tiltLineBuffer);
            }
            
            Unsafe.setFloats(tiltLineBufferPtr+0, xpos, ypos, zpos);
            Unsafe.setFloats(tiltLineBufferPtr+12, xsurface, ysurface, zsurface);
            
            GLES20FixedPipeline.glColor4f(this.colorR,
                                          this.colorG,
                                          this.colorB,
                                          this.colorA);
            GLES20FixedPipeline.glLineWidth(2f);
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, tiltLineBuffer);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(xpos, ypos, zpos);

        if (this.textureKey != 0L) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);

            if (this.verts == null) {
                ByteBuffer buf;

                buf = com.atakmap.lang.Unsafe.allocateDirect(4 * 2 * 4);
                buf.order(ByteOrder.nativeOrder());
                this.texCoords = buf;

                final float iconX = ICON_ATLAS.getImageTextureOffsetX(this.textureKey);
                final float iconY = ICON_ATLAS.getImageTextureOffsetY(this.textureKey);

                final float textureSize = ICON_ATLAS.getTextureSize();
                final float iconW = ICON_ATLAS.getImageWidth(this.textureKey);
                final float iconH = ICON_ATLAS.getImageHeight(this.textureKey);

                this.texCoords.putFloat(0, iconX / textureSize);
                this.texCoords.putFloat(4, (iconY + iconH - 1.0f) / textureSize);

                this.texCoords.putFloat(8, (iconX + iconW - 1.0f) / textureSize);
                this.texCoords.putFloat(12, (iconY + iconH - 1.0f) / textureSize);

                this.texCoords.putFloat(16, (iconX + iconW - 1.0f) / textureSize);
                this.texCoords.putFloat(20, iconY / textureSize);

                this.texCoords.putFloat(24, iconX / textureSize);
                this.texCoords.putFloat(28, iconY / textureSize);

                buf = com.atakmap.lang.Unsafe.allocateDirect(4 * 2 * 4);
                buf.order(ByteOrder.nativeOrder());
                this.verts = buf;

                this.verts.putFloat(0, -iconW / 2);
                this.verts.putFloat(4, -iconH / 2);

                this.verts.putFloat(8, iconW / 2);
                this.verts.putFloat(12, -iconH / 2);

                this.verts.putFloat(16, iconW / 2);
                this.verts.putFloat(20, iconH / 2);

                this.verts.putFloat(24, -iconW / 2);
                this.verts.putFloat(28, iconH / 2);
            }

            GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, this.verts);
            GLES20FixedPipeline.glTexCoordPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                    this.texCoords);

            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, this.textureId);

            GLES20FixedPipeline.glColor4f(this.colorR, this.colorG, this.colorB, this.colorA);

            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, 4);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }


        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough
        if (name != null &&
            (GLMapSurface.SETTING_displayLabels &&
             ortho.drawMapResolution < labelMinRenderResolution)) {

            final String text = this.name;
            if (text != null && text.length() > 0) {
                if (glText == null) {
                    MapTextFormat textFormat = AtakMapView.getDefaultTextFormat();
                    if (labelTextSize > 0) {
                        textFormat = new MapTextFormat(
                                textFormat.getTypeface(),
                                textFormat.isOutlined(),
                                (int)labelTextSize);
                    }
                    glText = GLText.getInstance(textFormat);
                }

                float offy;
                float offtx = 0;
                final float textWidth = glText.getStringWidth(text);

                final float textHeight = glText.getStringHeight();// _glText.getBaselineSpacing();
                if (this.textureKey != 0L) {
                    if(labelAlignY > 0)
                        offy = -iconHeight / 2f;
                    else if(labelAlignY < 0)
                        offy = iconHeight / 2f;
                    else
                        offy = -textHeight / 2;

                    if(labelAlignX > 0)
                        offtx = -iconWidth / 2f;
                    else if(labelAlignX < 0)
                        offtx = iconWidth / 2f;

                    if(ortho.drawTilt > 0d)
                        offy = (offy*-1f) + textHeight + glText.getDescent();
                } else {
                    offy = -textHeight / 2;
                }

                float textTx = offtx;
                if(labelAlignX > 0)
                    textTx -= textWidth + textHeight;
                else if(labelAlignX == 0)
                    textTx -= textWidth / 2f;
                else // labelAlignX < 0
                    textTx += textHeight; // no-op, text already aligned to right of xpos

                float textTy = offy;
                if(labelAlignY > 0)
                    textTy -= textHeight;
                else if(labelAlignY == 0)
                    textTy += glText.getDescent();
                else // labelAlignY < 0
                    textTy += textHeight;

                GLES20FixedPipeline.glTranslatef(textTx, textTy, 0f);

                if (labelRotationAbsolute || ortho.drawTilt > 0) {
                    // rotate relative to screen up
                    GLES20FixedPipeline.glRotatef(labelRotation, 0f, 0f, 1f);
                } else {
                    // rotate relative to north (screen up + map rotation)
                    GLES20FixedPipeline.glTranslatef(-textTx, -textTy, 0f);
                    GLES20FixedPipeline.glRotatef((float)ortho.drawRotation + labelRotation, 0f, 0f, 1f);
                    GLES20FixedPipeline.glTranslatef(textTx, textTy, 0f);
                }

                // XXX - avoid cast
                GLNinePatch smallNinePatch = GLRenderGlobals.get(this.renderContext).getSmallNinePatch();
                if (smallNinePatch != null && Color.alpha(labelBgColor) != 0) {
                    GLES20FixedPipeline.glColor4f(Color.red(labelBgColor) / 255f,
                                                  Color.green(labelBgColor) / 255f,
                                                  Color.blue(labelBgColor) / 255f,
                                                  Color.alpha(labelBgColor) / 255f);
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(-4f, -glText.getDescent(), 0f);
                    smallNinePatch.draw(textWidth + 8f, textHeight);
                    GLES20FixedPipeline.glPopMatrix();
                }

                glText.draw(text, Color.red(labelTextColor) / 255f,
                        Color.green(labelTextColor) / 255f,
                        Color.blue(labelTextColor) / 255f,
                        Color.alpha(labelTextColor) / 255f);
            }
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void release() {
        Unsafe.free(this.texCoords);
        this.texCoords = null;
        Unsafe.free(this.verts);
        this.verts = null;
        this.textureKey = 0L;
        this.iconHeight = 0;
        this.iconWidth = 0;
        if (this.iconLoader != null) {
            this.iconLoader = null;
            dereferenceIconLoader(this.iconUri);
        }
    }


    @Override
    protected void setGeometryImpl(ByteBuffer blob, int type) {
        this.longitude = blob.getDouble();
        this.latitude = blob.getDouble();
        if (type == TYPE_XYZ || type == TYPE_XYZM)
            this.altitude = blob.getDouble();



        this.posProjectedSrid = -1;
        this.surfaceProjectedSrid = -1;
        this.terrainVersion = 0;
    }

    @Override
    public void setStyle(Style style) {
        int iconColor = -1;
        String iconUri = null;

        int lblAlignX = 0;
        int lblAlignY = 0;
        int lblTextColor = Color.WHITE;
        int lblBgColor = Color.argb((int)(0.6f*255f), 0, 0, 0);
        float lblRotation = 0;
        boolean lblRotationAbsolute = false;
        int lblTextSize = AtakMapView.getDefaultTextFormat().getFontSize();
        double lblMinRenderResolution = defaultMinLabelRenderResolution;

        IconPointStyle istyle = (style instanceof IconPointStyle) ? (IconPointStyle)style : null;
        BasicPointStyle bstyle = (style instanceof BasicPointStyle) ? (BasicPointStyle) style : null;
        LabelPointStyle lstyle = (style instanceof LabelPointStyle) ? (LabelPointStyle) style : null;

        if(style instanceof CompositeStyle) {
            istyle = (IconPointStyle)CompositeStyle.find((CompositeStyle) style, IconPointStyle.class);
            bstyle = (BasicPointStyle) CompositeStyle.find((CompositeStyle) style, BasicPointStyle.class);
            lstyle = (LabelPointStyle) CompositeStyle.find((CompositeStyle) style, LabelPointStyle.class);
        }
        
        if(istyle != null) {
            iconColor = istyle.getColor();
            iconUri = istyle.getIconUri();
            lblAlignY = 1;
        } else if(bstyle != null) {
            iconColor = bstyle.getColor();
            iconUri = defaultIconUri;
            lblAlignY = 1;
        }

        // if a label style is present, override default label settings (aka name)
        if(lstyle != null) {

            // mimics GoogleEarth when the label point style does not contain a textString
            if (!FileSystemUtils.isEmpty(lstyle.getText()))
                this.name = GLText.localize(lstyle.getText());
            lblAlignX = lstyle.getLabelAlignmentX();
            lblAlignY = lstyle.getLabelAlignmentY();
            lblTextColor = lstyle.getTextColor();
            lblBgColor = lstyle.getBackgroundColor();
            lblRotationAbsolute = lstyle.isRotationAbsolute();
            lblRotation = (float)Math.toDegrees(lstyle.getLabelRotation());
            lblTextSize = Math.round(lstyle.getTextSize());
            lblMinRenderResolution = lstyle.getLabelMinRenderResolution();
        }

        if ((iconUri == null || iconUri.trim().isEmpty()) && this.name == null)
            iconUri = defaultIconUri;

        this.labelBgColor = lblBgColor;
        this.labelTextColor = lblTextColor;
        this.labelAlignY = lblAlignY;
        this.labelAlignX = lblAlignX;
        this.labelRotationAbsolute = lblRotationAbsolute;
        this.labelRotation = lblRotation;
        this.labelMinRenderResolution = lblMinRenderResolution;

        if (lblTextSize > 0f)
            this.labelTextSize = lblTextSize;

        final String u = iconUri;
        final int c = iconColor;
        if(this.renderContext.isRenderThread())
            setIcon(u, c);
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    setIcon(u, c);
                }
            });
    }
    
    public void setGeometry(final Point point) {
        this.setGeometry(point, -1);
    }

    @Override
    protected void setGeometryImpl(Geometry geometry) {
        final Point point = (Point)geometry;
        this.latitude = point.getY();
        this.longitude = point.getX();
        this.altitude = point.getZ();
        
        this.posProjectedSrid = -1;
        this.surfaceProjectedSrid = -1;
    }

    @Override
    public void setAltitudeMode(Feature.AltitudeMode altitudeMode) {
        this.altitudeMode = altitudeMode;
    }

    @Override
    public void setExtrude(double value) {
        this.extrude = value;
    }

    /**************************************************************************/
    // GLMapBatchable2

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if(!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            return;

        view.scratch.geo.set(this.latitude, view.idlHelper.wrapLongitude(this.longitude));
        
        // Z/altitude
        boolean belowTerrain = false;
        double posEl = 0d;
        if(view.drawTilt > 0d) {
            // XXX - altitude (for now will always make sure alt == terrain based on the if check.)
            // in the future will need to make sure that alt and terrain are both !NaN.
            double alt = computeAltitude(view);
            if (!Double.isNaN(localTerrainValue) && (alt < localTerrainValue)) {
                // if the explicitly specified altitude is below the terrain,
                // float above and annotate appropriately
                belowTerrain = true;
                alt = localTerrainValue;
            }
    
            // note: always NaN if source alt is NaN
            double adjustedAlt = (alt+ GLMapView.elevationOffset)*view.elevationScaleFactor;
            
            // move up half icon height
            if(this.textureKey != 0L) {
                adjustedAlt += view.drawMapResolution*(iconHeight/2d);
            }
    
            // move up ~5 pixels from surface
            adjustedAlt += view.drawMapResolution*10d;
            
            view.scratch.geo.set(adjustedAlt);
            posEl = Double.isNaN(adjustedAlt) ? 0d : adjustedAlt;
        }

        if( Double.compare(posProjectedEl,posEl) != 0 || posProjectedSrid != view.drawSrid) {
            view.scene.mapProjection.forward(view.scratch.geo, posProjected);
            posProjectedEl = posEl;
            posProjectedSrid = view.drawSrid;
        }
        
        view.scene.forward.transform(posProjected, view.scratch.pointD);
        float xpos = (float)view.scratch.pointD.x;
        float ypos = (float)view.scratch.pointD.y;
        float zpos = (float)view.scratch.pointD.z;

        this.screenX = xpos;
        this.screenY = ypos;
        
        //zpos = 0f;

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if(view.drawTilt > 0d) {
            final double surfaceEl = GLMapView.elevationOffset *view.elevationScaleFactor;
            
            if(Double.compare(surfaceProjectedEl,surfaceEl) != 0 || surfaceProjectedSrid != view.drawSrid) {
                view.scratch.geo.set(surfaceEl);
                view.scene.mapProjection.forward(view.scratch.geo, surfaceProjected);
                surfaceProjectedEl = surfaceEl;
                surfaceProjectedSrid = view.drawSrid;
            }
            
            view.scene.forward.transform(surfaceProjected, view.scratch.pointD);
            float xsurface = (float)view.scratch.pointD.x;
            float ysurface = (float)view.scratch.pointD.y;
            float zsurface = (float)view.scratch.pointD.z;
                        
            batch.setLineWidth(2f);
            batch.batch(xpos, ypos, zpos,
                        xsurface, ysurface, zsurface,
                        this.colorR,
                        this.colorG,
                        this.colorB,
                        this.colorA);
        }

        if (this.iconUri != null)
            this.checkIcon(view.getRenderContext());

        if (this.textureKey != 0L) {
            final float iconX = ICON_ATLAS.getImageTextureOffsetX(this.textureIndex);
            final float iconY = ICON_ATLAS.getImageTextureOffsetY(this.textureIndex);

            final float textureSize = ICON_ATLAS.getTextureSize();

            final float ulx = xpos - (iconWidth / 2f);
            final float uly = ypos - (iconHeight / 2f);
            final float lrx = xpos + (iconWidth / 2f);
            final float lry = ypos + (iconHeight / 2f);
            final float ulu = iconX / textureSize;
            final float ulv = (iconY + iconWidth - 1.0f) / textureSize;
            final float lru = (iconX + iconHeight - 1.0f) / textureSize;
            final float lrv = iconY / textureSize;

            batch.batch(this.textureId,
                        ulx, uly, zpos,
                        lrx, uly, zpos,
                        lrx, lry, zpos,
                        ulx, lry, zpos,
                        ulu, ulv,
                        lru, ulv,
                        lru, lrv,
                        ulu, lrv,
                        this.colorR, this.colorG, this.colorB, this.colorA);
        }
        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough
        if (name != null && GLMapSurface.SETTING_displayLabels && view.drawMapResolution < labelMinRenderResolution) {
            final String text = this.name;
            if (text != null && text.length() > 0) {
                if (glText == null) {
                    MapTextFormat textFormat = AtakMapView.getDefaultTextFormat();
                    if (labelTextSize > 0) {
                        textFormat = new MapTextFormat(
                                textFormat.getTypeface(),
                                textFormat.isOutlined(),
                                (int)labelTextSize);
                    }
                    glText = GLText.getInstance(textFormat);
                }
                float offy;
                float offtx = 0;
                final float textWidth = glText.getStringWidth(text);

                final float textHeight = glText.getStringHeight();// _glText.getBaselineSpacing();
                if (this.textureKey != 0L) {
                    if(labelAlignY > 0)
                        offy = -iconHeight / 2f;
                    else if(labelAlignY < 0)
                        offy = iconHeight / 2f;
                    else
                        offy = -textHeight / 2;

                    if(labelAlignX > 0)
                        offtx = -iconWidth / 2f;
                    else if(labelAlignX < 0)
                        offtx = iconWidth / 2f;
                    
                    if(view.drawTilt > 0d)
                        offy = (offy*-1f) + textHeight + glText.getDescent();
                } else {
                    offy = -textHeight / 2;
                }

                float textTx = xpos + offtx;
                if(labelAlignX > 0)
                    textTx -= textWidth + textHeight;
                else if(labelAlignX == 0)
                    textTx -= textWidth / 2f;
                else // labelAlignX < 0
                    textTx += textHeight; // no-op, text already aligned to right of xpos

                float textTy = ypos + offy;
                if(labelAlignY > 0)
                    textTy -= textHeight;
                else if(labelAlignY == 0)
                    textTy += glText.getDescent();
                else // labelAlignY < 0
                    textTy += textHeight;


                batch.pushMatrix(GLES10.GL_MODELVIEW);

                // reset the matrix and set up for the rotation
                Matrix.setIdentityM(view.scratch.matrixF, 0);
                Matrix.translateM(view.scratch.matrixF, 0, xpos, ypos, zpos);
                if (labelRotationAbsolute || view.drawTilt > 0) {
                    Matrix.rotateM(view.scratch.matrixF, 0, labelRotation, 0, 0, 1);
                } else {
                    Matrix.rotateM(view.scratch.matrixF, 0, (float) view.drawRotation + labelRotation, 0, 0, 1);
                }
                Matrix.translateM(view.scratch.matrixF, 0, -xpos, -ypos, -zpos);
                batch.setMatrix(GLES10.GL_MODELVIEW, view.scratch.matrixF, 0);

                // XXX - avoid cast
                GLNinePatch smallNinePatch = GLRenderGlobals.get(this.renderContext).getSmallNinePatch();
                if (smallNinePatch != null && Color.alpha(labelBgColor) != 0)
                    smallNinePatch.batch(batch,
                            textTx - 4f,
                            textTy - glText.getDescent(),
                            zpos,
                            textWidth + 8f,
                            textHeight,
                            Color.red(labelBgColor) / 255f,
                            Color.green(labelBgColor) / 255f,
                            Color.blue(labelBgColor) / 255f,
                            Color.alpha(labelBgColor) / 255f);

                glText.batch(batch,
                        text,
                        textTx,
                        textTy,
                        zpos,
                        Color.red(labelTextColor) / 255f,
                        Color.green(labelTextColor) / 255f,
                        Color.blue(labelTextColor) / 255f,
                        Color.alpha(labelTextColor) / 255f);


                batch.popMatrix(GLES10.GL_MODELVIEW);
            }
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    /**************************************************************************/

    synchronized static void getOrFetchIcon(RenderContext surface, GLBatchPoint point) {
        do {
            if (point.iconUri == null)
                return;

            long key = ICON_ATLAS.getTextureKey(point.iconUri);
            if (key != 0L) {
                point.textureKey = key;
                point.textureId = ICON_ATLAS.getTexId(point.textureKey);
                point.textureIndex = ICON_ATLAS.getIndex(point.textureKey);
                point.iconLoader = null;
                point.iconLoaderUri = null;
                point.iconDirty = false;
                point.iconWidth = ICON_ATLAS.getImageWidth(point.textureKey);
                point.iconHeight = ICON_ATLAS.getImageHeight(point.textureKey);
                if(point.verts != null) {
                    Unsafe.free(point.verts);
                    point.verts = null;
                    Unsafe.free(point.texCoords);
                    point.texCoords = null;
                }
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
                    // XXX -
                    if(bitmap != null && bitmap.isRecycled()) {
                        point.iconLoader = null;
                        continue;
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
                    point.textureIndex = ICON_ATLAS.getIndex(point.textureKey);
                    point.iconWidth = ICON_ATLAS.getImageWidth(point.textureKey);
                    point.iconHeight = ICON_ATLAS.getImageHeight(point.textureKey);
                    point.iconDirty = false;
                    if(point.verts != null) {
                        Unsafe.free(point.verts);
                        point.verts = null;
                        Unsafe.free(point.texCoords);
                        point.texCoords = null;
                    }

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

    public static void invalidateIconAtlas() {
        ICON_ATLAS.release();
    }
}
