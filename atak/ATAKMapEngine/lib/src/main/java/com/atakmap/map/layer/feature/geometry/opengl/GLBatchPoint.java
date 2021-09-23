package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Pair;


import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
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
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLTextureAtlas;

public class GLBatchPoint extends GLBatchGeometry {

    static GLTextureAtlas ICON_ATLAS = new GLTextureAtlas(1024,
            (int) Math.ceil(GLRenderGlobals.getRelativeScaling() * 32));
    static float iconAtlasDensity = GLRenderGlobals.getRelativeScaling();

    final static double defaultMinLabelRenderResolution = 13d;

    private final static String defaultIconUri = "asset:/icons/reference_point.png";

    final static Map<String, Pair<Future<Bitmap>, int[]>> iconLoaders = new HashMap<String, Pair<Future<Bitmap>, int[]>>();

    private LabelState label;

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
    public AltitudeMode altitudeMode = AltitudeMode.ClampToGround;
    public double extrude = 0d;

    public PointD posProjected;
    public float screenX, screenY;
    int posProjectedSrid;
    double posProjectedEl;
    double posProjectedLng;
    
    PointD surfaceProjected;
    int surfaceProjectedSrid;
    double surfaceProjectedEl;
    double surfaceProjectedLng;

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
    private float labelScale = 1.0f;

    float iconScale = 1.0f;

    private static class LabelState {
        int id;
        int layoutVersion = -1;
        boolean visible = true;
        AltitudeMode altMode;
        GLLabelManager _labelManager;

        LabelState(GLLabelManager labelManager, String txt, int labelTextSize) {
            _labelManager = labelManager;
            id = _labelManager.addLabel(txt);
            setTextFormat(labelTextSize);
            _labelManager.setFill(id, true);
            _labelManager.setVerticalAlignment(id, GLLabelManager.VerticalAlignment.Middle);
            _labelManager.setAlignment(id, GLLabelManager.TextAlignment.Center);
        }

        public void setTextFormat(int labelTextSize) {
            MapTextFormat textFormat = AtakMapView.getDefaultTextFormat();
            if (labelTextSize > 0) {
                textFormat = new MapTextFormat(
                        textFormat.getTypeface(),
                        textFormat.isOutlined(),
                        (int)labelTextSize);
            }
            setTextFormat(textFormat);
        }

        public void setTextFormat(MapTextFormat textFormat) {
            _labelManager.setTextFormat(id, textFormat);
        }

        public void release() {
            if (id != GLLabelManager.NO_ID) {
                _labelManager.removeLabel(id);
                id = GLLabelManager.NO_ID;
            }
        }

        public void setAlignment(double iconWidth, double iconHeight,
                                    boolean drawTilt,
                                    int alignX, int alignY) {

            double offy = 0;
            double offx = 0;

            GLLabelManager.VerticalAlignment valign = GLLabelManager.VerticalAlignment.Middle;
            GLLabelManager.TextAlignment talign = GLLabelManager.TextAlignment.Center;

            if (drawTilt)
                alignY = -1;

            if(alignY > 0) {
                offy = -iconHeight / 2.0;
                valign = GLLabelManager.VerticalAlignment.Bottom;
            } else if(alignY < 0) {
                offy = iconHeight / 2.0;
                valign = GLLabelManager.VerticalAlignment.Top;
            }

            if(alignX > 0) {
                offx = -iconWidth / 2.0;
                talign = GLLabelManager.TextAlignment.Left;
            } else if(alignX < 0) {
                //offx = iconWidth / 2f;
                talign = GLLabelManager.TextAlignment.Right;
            }

            _labelManager.setDesiredOffset(id, (int)offx, (int)offy, 0);
            _labelManager.setAlignment(id, talign);
            _labelManager.setVerticalAlignment(id, valign);
        }

        public void setText(String text) {
            _labelManager.setText(id, text);
        }

        public void setPlacement(GeoPoint geo, double alt, float labelRotation, boolean labelRotationAbsolute) {
            Point labelPoint = new Point(geo.getLongitude(), geo.getLatitude(),
                    alt);
            _labelManager.setGeometry(id, labelPoint);
            _labelManager.setRotation(id, labelRotation, labelRotationAbsolute);
        }

        public void updateVisible(boolean visible) {
            if (this.visible != visible) {
                _labelManager.setVisible(id, visible);
                this.visible = visible;
            }
        }

        public void updateLayout(int layoutVersion, GeoPoint geo, double alt, float labelRotation, boolean labelRotationAbsolute,
                                 double iconWidth, double iconHeight,
                                 boolean drawTilt,
                                 int alignX, int alignY) {
            if (layoutVersion != this.layoutVersion) {
                setPlacement(geo, alt, labelRotation, labelRotationAbsolute);
                setAlignment(iconWidth, iconHeight, drawTilt, alignX, alignY);
                this.layoutVersion = layoutVersion;
            }
        }

        public void setColors(int color, int bgColor) {
            _labelManager.setColor(id, color);
            _labelManager.setBackgroundColor(id, bgColor);
        }

        public void setMinResolution(double lblMinRenderResolution) {
            _labelManager.setMaxDrawResolution(id, lblMinRenderResolution);
        }

        public void setAltMode(AltitudeMode altitudeMode) {
            if (altitudeMode != altMode) {
                altMode = altitudeMode;
                _labelManager.setAltitudeMode(id, altitudeMode);
                layoutVersion = -1;
            }
        }
    }

    public GLBatchPoint(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchPoint(MapRenderer surface) {
        super(surface, 0);

        this.color = 0xFFFFFFFF;
        this.colorR = 1.0f;
        this.colorG = 1.0f;
        this.colorB = 1.0f;
        this.colorA = 1.0f;
        this.posProjected = new PointD(0d, 0d);
        this.posProjectedEl = 0d;
        this.posProjectedSrid = -1;
        
        this.surfaceProjected = new PointD(0d, 0d);
        this.surfaceProjectedEl = 0d;
        this.surfaceProjectedSrid = -1;
    }

    private void setIcon(String uri, int color) {
        if (color != this.color) {
            this.color = color;
            this.colorR = Color.red(this.color) / 255f;
            this.colorG = Color.green(this.color) / 255f;
            this.colorB = Color.blue(this.color) / 255f;
            this.colorA = Color.alpha(this.color) / 255f;
            this.iconDirty = true;
        }


        if (Objects.equals(uri, this.iconUri))
            return;

        this.iconDirty = true;

        if (this.iconLoader != null) {
            this.iconLoader = null;
            dereferenceIconLoader(this.iconUri);
        }

        this.iconUri = uri;
    }

    void checkIcon(final RenderContext surface) {
        if (this.textureKey == 0L || this.iconDirty)
            getOrFetchIcon(surface, this);
    }

    protected AltitudeMode getAltitudeMode() {
        return isNadirClampEnabled() ? AltitudeMode.ClampToGround : altitudeMode;
    }

    /**
     * Helper method that computes the altitude based on the set value of the localTerrainValue and
     * the altitude as set in the batch point.
     * @return the combination of the two values as directed by the Altitude Mode.
     */
    double computeAltitude(GLMapView ortho) {
        validateLocalElevation(ortho);
        switch (getAltitudeMode()) {
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
        final int renderTerrainVersion = ortho.getTerrainVersion();
        if(this.terrainVersion != renderTerrainVersion) {
            this.localTerrainValue = ortho.getTerrainMeshElevation(this.latitude, this.longitude);
            this.terrainVersion = renderTerrainVersion;
        }
        return this.localTerrainValue;
    }

    @Override
    public void draw(GLMapView ortho) {
        if (this.iconUri != null)
            this.checkIcon(ortho.getRenderContext());

        updateNadirClamp(ortho);

        final double wrappedLng = ortho.idlHelper.wrapLongitude(this.longitude);
        ortho.scratch.geo.set(this.latitude, wrappedLng);
        
        // Z/altitude
        boolean belowTerrain = false;
        double posEl = 0d;
        double alt = computeAltitude(ortho);
        if (!Double.isNaN(localTerrainValue) && (alt < localTerrainValue)) {
            // if the explicitly specified altitude is below the terrain,
            // float above and annotate appropriately
            belowTerrain = true;
            alt = localTerrainValue;
        }

        // note: always NaN if source alt is NaN
        double adjustedAlt = (alt+ortho.elevationOffset)*ortho.elevationScaleFactor;

        ortho.scratch.geo.set(adjustedAlt);
        posEl = Double.isNaN(adjustedAlt) ? 0d : adjustedAlt;

        validateLabel(ortho, GLMapSurface.SETTING_displayLabels, ortho.scratch.geo, posEl);

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

        boolean tilted = ortho.currentScene.drawTilt > 0d;
        boolean renderZ = (tilted || ortho.currentScene.scene.camera.perspective
                && !isNadirClampEnabled());

        if (tilted && this.textureKey != 0L) {
            // move up half icon height
            ypos += iconHeight / 2d;
        }

        this.screenX = xpos;
        this.screenY = ypos;
        
        //zpos = 0f;

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if(renderZ && (getLollipopsVisible() || !tilted) ) {
            final double terrain = this.validateLocalElevation(ortho);
            final double surfaceEl = (Math.min(terrain,  0d)+ GLMapView.elevationOffset)*ortho.elevationScaleFactor;
            
            if(Double.compare(surfaceProjectedEl, surfaceEl) != 0
                    || surfaceProjectedSrid != ortho.drawSrid
                    || surfaceProjectedLng != wrappedLng) {
                ortho.scratch.geo.set(surfaceEl);
                ortho.scene.mapProjection.forward(ortho.scratch.geo, surfaceProjected);
                surfaceProjectedEl = surfaceEl;
                surfaceProjectedSrid = ortho.drawSrid;
                surfaceProjectedLng = wrappedLng;
            }
            
            ortho.scene.forward.transform(surfaceProjected, ortho.scratch.pointD);
            float x1 = (float) ortho.scratch.pointD.x;
            float y1 = (float) ortho.scratch.pointD.y;
            float z1 = (float) ortho.scratch.pointD.z;

            float x0 = xpos;
            float y0 = ypos;
            float z0 = zpos;

            // if the lollipop end is behind the camera, recompute to avoid
            // rendering artifacts as non perspective adjusted coordinates
            // get mirrored
            if(zpos >= 1f) {
                // get camera position in LLA
                ortho.currentPass.scene.mapProjection.inverse(
                        ortho.currentPass.scene.camera.location, ortho.scratch.geo);
                // compute lollipop top at camera height
                ortho.scratch.geo.set(latitude, longitude,
                        ortho.scratch.geo.getAltitude()-ortho.currentPass.scene.camera.nearMeters);
                ortho.currentPass.scene.forward(ortho.scratch.geo, ortho.scratch.pointD);
                x0 = (float) ortho.scratch.pointD.x;
                y0 = (float) ortho.scratch.pointD.y;
                z0 = (float) ortho.scratch.pointD.z;
            }

            if(z0 < 1f && z1 < 1f) {
                if (tiltLineBuffer == null) {
                    tiltLineBuffer = Unsafe.allocateDirect(24);
                    tiltLineBuffer.order(ByteOrder.nativeOrder());
                    tiltLineBufferPtr = Unsafe.getBufferPointer(tiltLineBuffer);
                }

                Unsafe.setFloats(tiltLineBufferPtr+0, x0, y0, z0);
                Unsafe.setFloats(tiltLineBufferPtr+12, x1, y1, z1);

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

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glScalef(iconScale, iconScale, 1.0f);

            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, 4);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

            GLES20FixedPipeline.glPopMatrix();
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void releaseLabel() {
        if (this.label != null)
            this.label.release();
        this.label = null;
    }

    @Override
    public void release() {
        releaseLabel();
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
        float iconScale = 1.0f;

        int lblAlignX = 0;
        int lblAlignY = 0;
        int lblTextColor = Color.WHITE;
        int lblBgColor = Color.argb((int)(0.6f*255f), 0, 0, 0);
        float lblRotation = 0;
        boolean lblRotationAbsolute = false;
        int lblTextSize = AtakMapView.getDefaultTextFormat().getFontSize();
        double lblMinRenderResolution = defaultMinLabelRenderResolution;
        float lblScale = 1.0f;

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
            float s = istyle.getIconScaling();
            if (s != 0)
                iconScale = s;
            // else keep scale at 1.0
        } else if(bstyle != null) {
            iconColor = bstyle.getColor();
            iconUri = defaultIconUri;
            lblAlignY = 1;
        }

        // if a label style is present, override default label settings (aka name)
        if(lstyle != null) {
            // mimics GoogleEarth when the label point style does not contain a textString
            if (!FileSystemUtils.isEmpty(lstyle.getText()))
                this.name = lstyle.getText();
            lblAlignX = lstyle.getLabelAlignmentX();
            lblAlignY = lstyle.getLabelAlignmentY();
            lblTextColor = lstyle.getTextColor();
            lblBgColor = lstyle.getBackgroundColor();
            lblRotationAbsolute = lstyle.isRotationAbsolute();
            lblRotation = (float)Math.toDegrees(lstyle.getLabelRotation());
            lblTextSize = Math.round(lstyle.getTextSize());
            lblMinRenderResolution = lstyle.getLabelMinRenderResolution();
            lblScale  = lstyle.getLabelScale();
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

        this.labelScale = lblScale;
        this.iconScale = iconScale;

        if (lblTextSize > 0f) {
            this.labelTextSize = lblTextSize;
        } else {
            this.labelTextSize = AtakMapView.getDefaultTextFormat().getFontSize();
        }
        this.labelTextSize *= this.labelScale;

        if (label != null) {
            label.setMinResolution(lblMinRenderResolution);
            label.setColors(lblTextColor, lblBgColor);
            label.setText(this.name);
            label.setTextFormat(labelTextSize);
        }

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
    public void setAltitudeMode(AltitudeMode altitudeMode) {
        this.altitudeMode = altitudeMode;
        if (label != null)
            label.setAltMode(getAltitudeMode());
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

        updateNadirClamp(view);

        view.scratch.geo.set(this.latitude, view.idlHelper.wrapLongitude(this.longitude));
        
        // Z/altitude
        boolean belowTerrain = false;
        double posEl = 0d;

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

        view.scratch.geo.set(adjustedAlt);
        posEl = Double.isNaN(adjustedAlt) ? 0d : adjustedAlt;

        validateLabel(view, GLMapSurface.SETTING_displayLabels, view.scratch.geo, posEl);

        if( Double.compare(posProjectedEl,posEl) != 0 || posProjectedSrid != view.drawSrid) {
            view.scene.mapProjection.forward(view.scratch.geo, posProjected);
            posProjectedEl = posEl;
            posProjectedSrid = view.drawSrid;
        }
        
        view.scene.forward.transform(posProjected, view.scratch.pointD);
        float xpos = (float)view.scratch.pointD.x;
        float ypos = (float)view.scratch.pointD.y;
        float zpos = (float)view.scratch.pointD.z;

        boolean tilted = view.currentScene.drawTilt > 0d;
        boolean renderZ = (tilted || view.currentScene.scene.camera.perspective
                && !isNadirClampEnabled());

        if (tilted && this.textureKey != 0L) {
            // move up half icon height
            ypos += iconHeight / 2d;
        }

        this.screenX = xpos;
        this.screenY = ypos;
        
        //zpos = 0f;

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if(renderZ && (getLollipopsVisible() || !tilted)) {
            final double surfaceEl = localTerrainValue + GLMapView.elevationOffset * view.elevationScaleFactor;
            
            if(Double.compare(surfaceProjectedEl,surfaceEl) != 0 || surfaceProjectedSrid != view.drawSrid) {
                view.scratch.geo.set(surfaceEl);
                view.scene.mapProjection.forward(view.scratch.geo, surfaceProjected);
                surfaceProjectedEl = surfaceEl;
                surfaceProjectedSrid = view.drawSrid;
            }
            
            view.scene.forward.transform(surfaceProjected, view.scratch.pointD);
            float x1 = (float) view.scratch.pointD.x;
            float y1 = (float) view.scratch.pointD.y;
            float z1 = (float) view.scratch.pointD.z;

            float x0 = xpos;
            float y0 = ypos;
            float z0 = zpos;

            // if the lollipop end is behind the camera, recompute to avoid
            // rendering artifacts as non perspective adjusted coordinates
            // get mirrored
            if(z0 >= 1f) {
                // get camera position in LLA
                view.currentPass.scene.mapProjection.inverse(
                        view.currentPass.scene.camera.location, view.scratch.geo);
                // compute lollipop top at camera height
                view.scratch.geo.set(latitude, longitude,
                        view.scratch.geo.getAltitude()-view.currentPass.scene.camera.nearMeters);
                view.currentPass.scene.forward(view.scratch.geo, view.scratch.pointD);
                x0 = (float) view.scratch.pointD.x;
                y0 = (float) view.scratch.pointD.y;
                z0 = (float) view.scratch.pointD.z;
            }

            if(z0 < 1f && z1 < 1f) {
                batch.setLineWidth(2f);
                batch.batch(x0, y0, z0,
                            x1, y1, z1,
                            this.colorR,
                            this.colorG,
                            this.colorB,
                            this.colorA);
            }
        }

        if (this.iconUri != null)
            this.checkIcon(view.getRenderContext());

        final float iconRenderW = iconWidth * iconScale;
        final float iconRenderH = iconHeight * iconScale;
        if (this.textureKey != 0L) {
            final float iconX = ICON_ATLAS.getImageTextureOffsetX(this.textureIndex);
            final float iconY = ICON_ATLAS.getImageTextureOffsetY(this.textureIndex);

            final float textureSize = ICON_ATLAS.getTextureSize();

            final float ulx = xpos - (iconRenderW / 2f);
            final float uly = ypos - (iconRenderH / 2f);
            final float lrx = xpos + (iconRenderW / 2f);
            final float lry = ypos + (iconRenderH / 2f);
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
    }

    private void validateLabel(GLMapView ortho, boolean visible, GeoPoint geo, double adjustedEl) {

        String text = this.name;
        if (!FileSystemUtils.isEmpty(text)) {
            if (label == null) {
                label = new LabelState(ortho.getLabelManager(), text, labelTextSize);
                label.setMinResolution(this.labelMinRenderResolution);
                label.setColors(labelTextColor, labelBgColor);
            }

            label.setText(text);
            label.setAltMode(getAltitudeMode());
            label.updateVisible(visible);

            label.updateLayout(ortho.currentScene.drawVersion,
                    geo,
                    adjustedEl,
                    labelRotation,
                    labelRotationAbsolute,
                    iconWidth * iconScale,
                    iconHeight * iconScale,
                    ortho.currentScene.drawTilt > 0,
                    labelAlignX,
                    labelAlignY);
        } else if (label != null)
            releaseLabel();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public HitTestResult hitTest(MapRenderer3 renderer, HitTestQueryParameters params) {
        if (params.rect.contains(this.screenX, this.screenY))
            return new HitTestResult(this.featureId,
                    new GeoPoint(latitude, longitude, altitude));
        return null;
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
