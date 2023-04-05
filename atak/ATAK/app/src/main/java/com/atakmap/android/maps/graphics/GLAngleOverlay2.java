
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;
import com.atakmap.util.Visitor;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

class GLAngleOverlay2 extends GLShape2 implements
        Shape.OnPointsChangedListener,
        AutoSizeAngleOverlayShape.OnPropertyChangedListener {

    static final float LINE_WIDTH = (float) Math
            .ceil(1f * MapView.DENSITY);
    static int DIRECTION_IN_COLOR = 0x7FFF0000;
    static int DIRECTION_OUT_COLOR = 0x7F00FF00;

    final static float OVERLAY_OFFSET = 40;
    final static float OVERLAY_HASH_LENGTH = 20;
    final static float HALF_TICK_RADIUS_PIXELS = 12;

    GLMapView ortho;
    GeoPoint centerGP;
    PointF center = new PointF();
    PointD tmpPoint = new PointD();
    double offsetAngle = 0;

    boolean invalid = false;

    float radius;

    final GLLabelManager labelManager;
    final GeoPoint[] labelPoints = new GeoPoint[12];
    final int[] labelIds = new int[12];
    GLText _label;

    SurfaceRendererControl surfaceCtrl;

    final AutoSizeAngleOverlayShape sw;

    protected final float[] _textPoint = new float[2];
    protected final boolean isAutoSize;

    protected Polyline[] thirtyHash;
    private GLPolyline[] glthirtyHash;
    private Polyline directionArrow;
    private GLPolyline gldirectionArrow;
    private GeoPoint[] outline;
    private GLBatchLineString gloutlineCircle;
    private GLBatchLineString[] glcardinals;
    private GLBatchLineString[] glticks;
    protected Marker centerMarker;
    private GLMarker2 glcenterMarker;
    protected double radiusMeters;
    private boolean initialized;
    private double halfTickRadiusMeters;
    private boolean lastSurface;
    private boolean isProjectedProportion;
    private double validateResolution;

    public GLAngleOverlay2(MapRenderer surface,
            AutoSizeAngleOverlayShape subject) {
        super(surface, subject,
                GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES);
        labelManager = ((GLMapView) surface).getLabelManager();
        Arrays.fill(labelIds, GLLabelManager.NO_ID);
        isAutoSize = !(subject instanceof AngleOverlayShape);

        sw = subject;

        int screenHeight = MapView.getMapView().getContext().getResources()
                .getDisplayMetrics().heightPixels;
        int screenWidth = MapView.getMapView().getContext().getResources()
                .getDisplayMetrics().widthPixels;

        int min = Math.min(screenHeight, screenWidth);
        //set the radius to fill up the same portion of any screen
        radius = min / 3.6f;

        centerGP = sw.getCenter().get();
        sw.getBounds(bounds);

        if (context instanceof MapRenderer3)
            surfaceCtrl = ((MapRenderer3) context)
                    .getControl(SurfaceRendererControl.class);
        else
            context.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    surfaceCtrl = object;
                }
            }, SurfaceRendererControl.class);

        offsetAngle = sw.getOffsetAngle();
        initialized = false;

        // XXX - bad practice, should always be deferred to object that created
        //       the renderable
        startObserving();
    }

    @Override
    public void startObserving() {
        super.startObserving();
        sw.addOnPointsChangedListener(this);
        sw.addOnPropertyChangedListener(this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        sw.removeOnPointsChangedListener(this);
        sw.removeOnPropertyChangedListener(this);
    }

    protected void init(GLMapView ortho) {
        if (initialized)
            return;
        initialized = true;
        if (thirtyHash == null) {
            thirtyHash = new Polyline[12];
            glthirtyHash = new GLPolyline[thirtyHash.length];
            for (int i = 0; i < thirtyHash.length; i++) {
                Polyline line = new Polyline(sw.getUID());
                line.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
                line.setStrokeWeight(LINE_WIDTH);
                thirtyHash[i] = line;

                glthirtyHash[i] = new GLPolyline(ortho, thirtyHash[i]);
                glthirtyHash[i].startObserving();
            }
        }
        if (directionArrow == null) {
            directionArrow = new Polyline(sw.getUID());
            directionArrow.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
            directionArrow.setStrokeWeight(LINE_WIDTH + 2);

            gldirectionArrow = new GLPolyline(ortho, directionArrow);
            gldirectionArrow.startObserving();
        }
        if (centerMarker == null) {
            centerMarker = new Marker(sw.getUID());
            // XXX - abuses the API a little..
            Icon.Builder builder = new Icon.Builder();
            builder.setImageUri(Marker.STATE_DEFAULT,
                    "asset:/icons/bullseye_icon.png");
            builder.setColor(Marker.STATE_DEFAULT,
                    DIRECTION_OUT_COLOR | 0xFF000000);
            builder.setImageUri(Marker.STATE_DISABLED_MASK,
                    "asset:/icons/bullseye_icon.png");
            builder.setColor(Marker.STATE_DISABLED_MASK,
                    DIRECTION_IN_COLOR | 0xFF000000);
            centerMarker.setIcon(builder.build());
            glcenterMarker = new GLMarker2(ortho, centerMarker);
            glcenterMarker.startObserving();
        }
        if (gloutlineCircle == null) {
            gloutlineCircle = new GLBatchLineString(ortho);
            gloutlineCircle.init(0L, null);
            gloutlineCircle.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
            gloutlineCircle.setStyle(new BasicStrokeStyle(
                    argb(.5f, .85f, .85f, .85f),
                    (LINE_WIDTH * 2f) / ortho.currentPass.relativeScaleHint));
        }
        if (glcardinals == null) {
            glcardinals = new GLBatchLineString[4];
            for (int i = 0; i < 4; i++) {
                glcardinals[i] = new GLBatchLineString(ortho);
                glcardinals[i].init(0L, null);
                glcardinals[i]
                        .setAltitudeMode(Feature.AltitudeMode.ClampToGround);
                Style style;
                if (i == 0) {
                    style = new CompositeStyle(new Style[] {
                            new BasicStrokeStyle(-1, (LINE_WIDTH * 3f)
                                    / ortho.currentPass.relativeScaleHint),
                            new BasicStrokeStyle(Color.RED, (LINE_WIDTH)
                                    / ortho.currentPass.relativeScaleHint),
                    });
                } else {
                    style = new CompositeStyle(new Style[] {
                            new BasicStrokeStyle(Color.BLACK, (LINE_WIDTH * 2f
                                    + 2f)
                                    / ortho.currentPass.relativeScaleHint),
                            new BasicStrokeStyle(-1, (LINE_WIDTH * 2f)
                                    / ortho.currentPass.relativeScaleHint),
                    });
                }
                glcardinals[i].setStyle(style);
            }
        }
        if (glticks == null) {
            glticks = new GLBatchLineString[71];
            for (int i = 0; i < glticks.length; i++) {
                glticks[i] = new GLBatchLineString(ortho);
                glticks[i].init(0L, null);
                glticks[i].setAltitudeMode(Feature.AltitudeMode.ClampToGround);
                glticks[i].setStyle(new BasicStrokeStyle(-1, (LINE_WIDTH * 2f)
                        / ortho.currentPass.relativeScaleHint));
            }
        }
        this.lastSurface = false;
        this.invalid = true;
        this.halfTickRadiusMeters = 0d;
        this.validateResolution = 0d;
    }

    static int argb(float a, float r, float g, float b) {
        return Color.argb((int) (a * 255f), (int) (r * 255f), (int) (g * 255f),
                (int) (b * 255f));
    }

    @Override
    public void release() {
        super.release();

        for (int i = 0; i < labelIds.length; ++i) {
            if (labelIds[i] != GLLabelManager.NO_ID) {
                labelManager.removeLabel(labelIds[i]);
                labelIds[i] = GLLabelManager.NO_ID;
            }
        }
        glthirtyHash = release(glthirtyHash);
        thirtyHash = release(thirtyHash);
        if (glcenterMarker != null) {
            glcenterMarker.stopObserving();
            glcenterMarker.release();
            glcenterMarker = null;
        }
        if (centerMarker != null) {
            centerMarker.dispose();
            centerMarker = null;
        }
        if (gloutlineCircle != null) {
            gloutlineCircle.release();
            gloutlineCircle = null;
        }
        glcardinals = release(glcardinals);
        glticks = release(glticks);

        halfTickRadiusMeters = 0d;

        initialized = false;
    }

    static Polyline[] release(Polyline[] lines) {
        if (lines != null) {
            for (Polyline line : lines)
                line.dispose();
        }
        return null;
    }

    static GLPolyline[] release(GLPolyline[] lines) {
        if (lines != null) {
            for (GLPolyline line : lines) {
                line.stopObserving();
                line.release();
            }
        }
        return null;
    }

    static GLBatchLineString[] release(GLBatchLineString[] lines) {
        if (lines != null) {
            for (GLBatchLineString line : lines) {
                line.release();
            }
        }
        return null;
    }

    protected void _projectVerts(final GLMapView ortho) {
        if (this.invalid) {
            offsetAngle = sw.getOffsetAngle();
            final int color = sw.isShowingEdgeToCenter() ? DIRECTION_IN_COLOR
                    : DIRECTION_OUT_COLOR;

            final GeoPoint centerSurface = new GeoPoint(centerGP.getLatitude(),
                    centerGP.getLongitude());
            centerMarker.setPoint(centerSurface);
            centerMarker.setState(
                    sw.isShowingEdgeToCenter() ? Marker.STATE_DISABLED_MASK
                            : Marker.STATE_DEFAULT);

            if (isAutoSize) {
                radiusMeters = radius * ortho.currentScene.drawMapResolution;
            } else {
                GeoPoint[] arrowEndPoints = ((AngleOverlayShape) sw)
                        .getInnerArrowPoints();
                radiusMeters = arrowEndPoints[0].distanceTo(centerSurface);
            }
            if (radiusMeters > 100000 && (isAutoSize
                    || !((AngleOverlayShape) sw).showSimpleSpokeView()))
                return;

            GeoPoint center = new GeoPoint(
                    centerSurface.getLatitude(),
                    centerSurface.getLongitude(),
                    ElevationManager.getElevation(centerSurface.getLatitude(),
                            centerSurface.getLongitude(), null));

            // build the circle outline

            // compute ellipse radii based on projection
            double rx = 1d;
            double ry = 1d;
            if (sw.getProjectionProportition()
                    && ortho.currentScene.drawSrid == 4326) {
                final GeoPoint north = GeoCalculations.pointAtDistance(center,
                        0d, radiusMeters);
                final GeoPoint east = GeoCalculations.pointAtDistance(center,
                        90d, radiusMeters);

                // measure distance in projected space
                final double dx = MathUtils.distance(east.getLongitude(),
                        east.getLatitude(), center.getLongitude(),
                        center.getLatitude());
                final double dy = MathUtils.distance(north.getLongitude(),
                        north.getLatitude(), center.getLongitude(),
                        center.getLatitude());

                // scale the ellipse to constant distance in the projected space
                rx = Math.max(dx, dy) / dx;
                ry = Math.max(dx, dy) / dy;
            }
            isProjectedProportion = (sw.getProjectionProportition()
                    && ortho.currentScene.drawSrid == 4326);

            outline = new GeoPoint[73];
            for (int j = 0; j < 72; j++) {
                final double angle = (360d / (outline.length - 1)) * j
                        + offsetAngle;
                // compute the outline radius at the current angle
                final double outliner = MathUtils.distance(
                        Math.sin(Math.toRadians(angle)) * rx * radiusMeters,
                        Math.cos(Math.toRadians(angle)) * ry * radiusMeters,
                        0, 0);
                outline[j] = GeoPoint.createMutable();
                outline[j].set(GeoCalculations.pointAtDistance(centerSurface,
                        angle, outliner));
            }

            outline[outline.length - 1] = outline[0];
            double[] els = new double[outline.length];
            ElevationManager.getElevation(Arrays.asList(outline).iterator(),
                    els, null);
            for (int i = 0; i < outline.length; i++)
                outline[i].set(els[i]);
            setGeometry(gloutlineCircle, outline);

            // build the "thirty hash"
            for (int j = 0; j < thirtyHash.length; j++) {
                thirtyHash[j].setPoints(new GeoPoint[] {
                        center,
                        outline[j * 6],
                });
                thirtyHash[j].setColor(color);
            }

            // build the cardinals
            double northAlt = 0;
            for (int i = 0; i < 4; i++) {
                final double angle = i * 90d + offsetAngle;
                final double angler = Math.toRadians(angle);
                // compute the radius at the current angle
                final double rm = MathUtils.distance(
                        Math.sin(angler) * rx * radiusMeters,
                        Math.cos(angler) * ry * radiusMeters,
                        0, 0);
                final double radius = (i == 0) ? rm : rm / 3d;
                GeoPoint cardinal = GeoCalculations.pointAtDistance(centerGP,
                        angle, radius);
                double alt = ElevationManager.getElevation(
                        cardinal.getLatitude(),
                        cardinal.getLongitude(), null);
                cardinal = new GeoPoint(cardinal.getLatitude(),
                        cardinal.getLongitude(), alt);
                setGeometry(glcardinals[i], new GeoPoint[] {
                        centerGP, cardinal
                });
                if (i == 0)
                    northAlt = alt;
            }

            // build the "direction arrow"
            final double dirArrowEndPct = !sw.isShowingEdgeToCenter() ? 0.75
                    : 0.85;
            final double dirArrowTipPct = !sw.isShowingEdgeToCenter() ? 0.85
                    : 0.75;

            GeoPoint[] arrowPts = {
                    GeoCalculations.pointAtDistance(centerSurface,
                            offsetAngle - 5, radiusMeters * dirArrowEndPct),
                    GeoCalculations.pointAtDistance(centerSurface, offsetAngle,
                            radiusMeters * dirArrowTipPct),
                    GeoCalculations.pointAtDistance(centerSurface,
                            offsetAngle + 5, radiusMeters * dirArrowEndPct),
            };
            // Altitude correction
            for (int i = 0; i < arrowPts.length; i++) {
                GeoPoint p = arrowPts[i];
                double pct = i == 1 ? dirArrowTipPct : dirArrowEndPct;
                double alt = center.getAltitude() * (1 - pct) + northAlt * pct;
                arrowPts[i] = new GeoPoint(p.getLatitude(), p.getLongitude(),
                        alt);
            }

            directionArrow.setPoints(arrowPts);
            directionArrow.setColor(color);

            if (isAutoSize) {
                if (_label == null)
                    _label = GLText.getInstance(MapView.getDefaultTextFormat());
                final double r = radiusMeters
                        + ortho.currentScene.drawMapResolution
                                * _label.getCharHeight();
                GeoPoint north = GeoCalculations.pointAtDistance(center, 0d,
                        MathUtils.distance(Math.sin(0d) * rx * r,
                                Math.cos(0d) * ry * r, 0, 0));
                GeoPoint west = GeoCalculations.pointAtDistance(center, 270d,
                        MathUtils.distance(Math.sin(Math.PI * 3d / 2d) * rx * r,
                                Math.cos(Math.PI * 3d / 2d) * ry * r, 0, 0));
                GeoPoint south = GeoCalculations.pointAtDistance(center, 180d,
                        MathUtils.distance(Math.sin(Math.PI) * rx * r,
                                Math.cos(Math.PI) * ry * r, 0, 0));
                GeoPoint east = GeoCalculations.pointAtDistance(center, 90d,
                        MathUtils.distance(Math.sin(Math.PI / 2d) * rx * r,
                                Math.cos(Math.PI / 2d) * ry * r, 0, 0));
                bounds.set(new GeoPoint[] {
                        north, west, south, east
                });
            }
            // force refresh of hash ticks
            this.invalid = false;
            halfTickRadiusMeters = 0d;
            validateResolution = ortho.currentScene.drawMapResolution;
        }
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!sw.getVisible())
            return;

        if (!bounds.intersects(ortho.northBound, ortho.westBound,
                ortho.southBound, ortho.eastBound))
            return;

        final boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);
        final boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);

        init(ortho);

        // XXX - pass through as variable, don't hold instance
        this.ortho = ortho;

        // force geometry rebuild if proportional representation was requested
        // and the geometry is not currently adjusted
        if ((sw.getProjectionProportition()
                && ortho.currentScene.drawSrid == 4326) != isProjectedProportion)
            this.invalid = true;
        if (isAutoSize && !isWithinPercent(
                validateResolution / ortho.currentScene.drawMapResolution,
                0.1d))
            this.invalid = true;
        _projectVerts(ortho);

        if (this.invalid ||
                ((radiusMeters
                        / ortho.currentScene.drawMapResolution) < ((OVERLAY_OFFSET
                                + OVERLAY_HASH_LENGTH) * 1.5d))) {

            if (!isAutoSize && MathUtils.hasBits(renderPass,
                    GLMapView.RENDER_PASS_SPRITES))
                drawIcon(ortho);
            return;
        }

        boolean overlayRendered = false;

        // render the surface graphics
        do {
            // render as sprite without tilt for cleanest representation
            boolean shouldRenderOverlay = (ortho.currentScene.drawTilt > 0
                    && surface) ||
                    (ortho.currentScene.drawTilt == 0 && sprites);

            if (!shouldRenderOverlay) {
                if (surface && lastSurface) {
                    // the overlay was previously rendered on the surface, but
                    // will now render as a sprite. clear it from the surface
                    // texture immediately
                    SurfaceRendererControl ctrl = ortho
                            .getControl(SurfaceRendererControl.class);
                    if (ctrl != null)
                        ctrl.markDirty(new Envelope(bounds.getWest(),
                                bounds.getSouth(), 0d, bounds.getEast(),
                                bounds.getNorth(), 0d), true);
                    lastSurface = false;
                    break;
                } else if (sprites && !lastSurface) {
                    // the overlay was previously rendered as a sprite, but
                    // the surface texture has not yet been updated. Queue the
                    // bounds for an immediate refresh
                    SurfaceRendererControl ctrl = ortho
                            .getControl(SurfaceRendererControl.class);
                    if (ctrl != null)
                        ctrl.markDirty(new Envelope(bounds.getWest(),
                                bounds.getSouth(), 0d, bounds.getEast(),
                                bounds.getNorth(), 0d), true);

                    // drop through to continue rendering as sprite until the
                    // overlay has been added to the surface
                } else {
                    // representation is in sync
                    break;
                }
            } else {
                lastSurface = surface;
            }

            if (!isAutoSize && ((AngleOverlayShape) sw).showSimpleSpokeView()) {
                drawThirtyHashMarks(ortho);
                drawDirectionalArrow();
            } else {
                drawInnerArrow(ortho);
                drawCircleOutline();
                drawCircleHashMarks();
            }
            overlayRendered = true;
        } while (false);

        // render the text
        if (((!isAutoSize && ((AngleOverlayShape) sw).showSimpleSpokeView())
                && sprites) ||
                ((isAutoSize || !((AngleOverlayShape) sw).showSimpleSpokeView())
                        && overlayRendered)) {

            drawText();
        }
    }

    void updateLabelPoints() {
        ortho.currentPass.scene.forward(centerGP, center);
        for (int d = 0; d < thirtyHash.length; d++) {
            if (labelIds[d] == GLLabelManager.NO_ID) {
                labelIds[d] = labelManager.addLabel();
                labelManager.setVisible(labelIds[d], false);
            }
            final GeoPoint[] points = thirtyHash[d].getPoints();
            // Playstore Crash Log: GLAngleOverlay2 ArrayOutOfBoundsException
            // seems like this could only happen when calling release improperly.
            if (points.length > 1) {
                GeoPoint point = points[1];
                double alt = point.getAltitude();
                if (Double.isNaN(alt))
                    alt = 0d;

                labelPoints[d] = point;
                labelManager.setGeometry(labelIds[d], new Point(
                        point.getLongitude(), point.getLatitude(), alt));
            }
        }
    }

    void drawCircleOutline() {
        gloutlineCircle.draw(ortho, GLMapView.RENDER_PASS_SURFACE);
    }

    void drawCircleHashMarks() {
        final double passHalfTickMeters = (ortho.currentScene.drawMapResolution
                * HALF_TICK_RADIUS_PIXELS);
        // rebuild if we have more than a 20% difference between desired and actual
        final boolean rebuild = !isWithinPercent(
                halfTickRadiusMeters / passHalfTickMeters, 0.2d);

        if (rebuild) {
            for (int i = 0; i < glticks.length; i++) {
                final double angle = (i + 1) * (360d / (glticks.length + 1))
                        + offsetAngle;
                GeoPoint backtick = GeoCalculations.pointAtDistance(
                        outline[i + 1], angle + 180d,
                        (1 + (i % 2)) * passHalfTickMeters);
                backtick = new GeoPoint(backtick.getLatitude(),
                        backtick.getLongitude(), outline[i + 1].getAltitude());
                setGeometry(glticks[i], new GeoPoint[] {
                        backtick, outline[i + 1]
                });
            }
            halfTickRadiusMeters = passHalfTickMeters;
        }

        for (GLBatchLineString gltick : glticks)
            gltick.draw(ortho, GLGeometry.VERTICES_PROJECTED);
    }

    /**
     * Draw an icon to represent where the overlay is when zoomed out too far
     */
    private void drawIcon(GLMapView ortho) {
        if (glcenterMarker != null)
            glcenterMarker.draw(ortho, GLMapView.RENDER_PASS_SPRITES);
    }

    void drawDirectionalArrow() {
        if (gldirectionArrow != null)
            gldirectionArrow.draw(ortho, GLMapView.RENDER_PASS_SURFACE);
    }

    private void drawInnerArrow(GLMapView ortho) {
        // draw cardinals
        for (GLBatchLineString glcardinal : glcardinals)
            glcardinal.draw(ortho, GLGeometry.VERTICES_PROJECTED);
    }

    void drawTextLabel(int idx, String text, int rotation, boolean flipped) {
        if (isAutoSize || !((AngleOverlayShape) sw).showSimpleSpokeView()) {
            //Move labels outside the ring here
            double halfLabelHeight = (labelManager.getSize(labelIds[idx],
                    null).Height + 8)
                    / 2.0;
            double yOffset = flipped ? -halfLabelHeight : halfLabelHeight;

            labelManager.setDesiredOffset(labelIds[idx], 0, yOffset, 0);
            labelManager.setVerticalAlignment(labelIds[idx],
                    GLLabelManager.VerticalAlignment.Middle);
            labelManager.setAlignment(labelIds[idx],
                    GLLabelManager.TextAlignment.Right);
            labelManager.setVisible(labelIds[idx], true);

            return;
        }

        ortho.currentPass.scene.forward(labelPoints[idx], tmpPoint);
        PointD startVert = tmpPoint;

        text = GLText.localize(text);

        GLText _label = GLText.getInstance(MapView.getDefaultTextFormat());

        RectF _view = this.getWidgetViewWithoutActionbarF();
        if (startVert.x <= _view.left || startVert.x >= _view.right ||
                startVert.y <= _view.bottom || startVert.y >= _view.top) {
            buildLabelEdgeVisible(ortho, center, startVert);
            if (Float.isNaN(_textPoint[0]) || Float.isNaN(_textPoint[1]))
                return;
        } else {
            _textPoint[0] = (float) startVert.x;
            _textPoint[1] = (float) startVert.y;

            //Move labels outside the ring here
            double halfLabelHeight = (_label.getStringHeight() + 8) / 2d;
            double yOffset = Math.cos(Math.toRadians(rotation))
                    * halfLabelHeight;
            double xOffset = Math.sin(Math.toRadians(rotation))
                    * halfLabelHeight;

            _textPoint[0] += xOffset;
            _textPoint[1] += yOffset;
        }

        GLNinePatch _ninePatch = GLRenderGlobals.get(this.context)
                .getMediumNinePatch();

        final float labelWidth = _label.getStringWidth(text);
        final float labelHeight = _label.getStringHeight();

        GLES20FixedPipeline.glPushMatrix();

        //make sure not to display upside down text views
        double totalRot = Math.abs(rotation - ortho.currentScene.drawRotation)
                % 360d;
        if (totalRot > 90 && totalRot < 270)
            rotation = (rotation + 180) % 360;
        rotation -= ortho.currentPass.drawRotation;

        GLES20FixedPipeline.glTranslatef(_textPoint[0], _textPoint[1],
                (float) startVert.z);
        GLES20FixedPipeline.glRotatef((float) (rotation * -1), 0f, 0f, 1f);
        GLES20FixedPipeline.glTranslatef(-labelWidth / 2, -labelHeight / 2 + 4,
                0);

        GLES20FixedPipeline.glPushMatrix();
        float outlineOffset = -((GLText.getLineCount(text) - 1) * _label
                .getBaselineSpacing())
                - 4;
        GLES20FixedPipeline.glTranslatef(-8f, outlineOffset - 4f, 0f);
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.4f);
        if (_ninePatch != null) {
            _ninePatch.draw(labelWidth + 16f, labelHeight);
        }
        GLES20FixedPipeline.glPopMatrix();
        GLES20FixedPipeline.glColor4f(1, 1, 1, 1);
        _label.draw(text, 1, 1, 1, 1);
        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * find the location the label should be placed and the angle it should be rotated.
     */
    protected void buildLabelEdgeVisible(GLMapView ortho,
            final PointF startVert,
            final PointD endVert) {

        GLText _label = GLText.getInstance(MapView.getDefaultTextFormat());

        final float p0x = startVert.x;
        final float p0y = startVert.y;

        final float p1x = (float) endVert.x;
        final float p1y = (float) endVert.y;

        float xmin = Math.min(p0x, p1x);
        float ymin = (p0x < p1x) ? p0y : p1y;
        float xmax = Math.max(p0x, p1x);
        float ymax = (p0x > p1x) ? p0y : p1y;

        if (p0x == p1x) {
            ymax = Math.max(p0y, p1y);
            ymin = Math.min(p0y, p1y);
        }

        Vector2D modStartVert = new Vector2D(xmin, ymin);
        Vector2D modEndVert = new Vector2D(xmax, ymax);

        //shrink the view bounds to allow room to show the full label
        double labelPadding = (_label.getStringHeight() + 12) / 2d;
        RectF view = this.getWidgetViewWithoutActionbarF();
        view.bottom += labelPadding;
        view.left += labelPadding;
        view.top -= labelPadding;
        view.right -= labelPadding;
        Vector2D[] viewPoly = {
                new Vector2D(view.left, view.top),
                new Vector2D(view.right, view.top),
                new Vector2D(view.right, view.bottom),
                new Vector2D(view.left, view.bottom),
                new Vector2D(view.left, view.top)
        };
        List<Vector2D> ip = Vector2D.segmentIntersectionsWithPolygon(
                modStartVert, modEndVert, viewPoly);

        //if no intersection point is found return NAN
        if (ip.isEmpty()) {
            _textPoint[0] = Float.NaN;
            _textPoint[1] = Float.NaN;
            return;
        }

        //if one intersection point was found use that point
        if (ip.size() == 1) {
            Vector2D p = ip.get(0);
            _textPoint[0] = (float) p.x;
            _textPoint[1] = (float) p.y;
            return;
        }

        Vector2D p1 = ip.get(0);
        Vector2D p2 = ip.get(1);

        //attempt to find the closest intersection point to the outside of the spoke
        double dist0 = Math.hypot(p1.x - endVert.x, p1.y - endVert.y);
        double dist1 = Math.hypot(p2.x - endVert.x, p2.y - endVert.y);
        if (dist0 <= dist1) {
            _textPoint[0] = (float) p1.x;
            _textPoint[1] = (float) p1.y;
        } else {
            _textPoint[0] = (float) p2.x;
            _textPoint[1] = (float) p2.y;
        }

    }

    void drawText() {
        if (_label == null)
            _label = GLText.getInstance(MapView.getDefaultTextFormat());

        updateLabelPoints();

        String azimuth;
        if (sw.getNorthRef() == NorthReference.TRUE)
            azimuth = "T";
        else if (sw.getNorthRef() == NorthReference.MAGNETIC)
            azimuth = "M";
        else
            azimuth = "G";
        int roundedOffsetAngle = (int) Math.round(offsetAngle);
        //check if the labels should show in to out or out to in direction
        for (int i = 0; i < labelIds.length; i++) {
            String text;
            int rotation;
            int index360 = !sw.isShowingEdgeToCenter() ? 0
                    : (labelIds.length / 2);
            if (i == index360) {
                text = sw.isShowingMils()
                        ? AngleUtilities.formatNoUnitsNoDecimal(360,
                                Angle.DEGREE, Angle.MIL) + "mils"
                                + azimuth
                        : "360" + Angle.DEGREE_SYMBOL + azimuth;
                rotation = !sw.isShowingEdgeToCenter() ? roundedOffsetAngle
                        : 180 + roundedOffsetAngle;
            } else {
                final int degrees = !sw.isShowingEdgeToCenter() ? i * 30
                        : ((i * 30) + 180) % 360;
                text = sw.isShowingMils()
                        ? AngleUtilities.formatNoUnitsNoDecimal(degrees,
                                Angle.DEGREE, Angle.MIL)
                        : String.valueOf(degrees);
                rotation = (i * 30) + roundedOffsetAngle;
            }
            //make sure not to display upside down text views
            int drawRotation = 360 - (rotation % 360);
            boolean flipped = false;
            if (drawRotation > 90 && drawRotation < 270) {
                drawRotation = (drawRotation + 180) % 360;
                flipped = true;
            }
            labelManager.setText(labelIds[i], text);
            labelManager.setRotation(labelIds[i], drawRotation, true);
            labelManager.setBackgroundColor(labelIds[i],
                    Color.argb(102, 0, 0, 0));
            labelManager.setFill(labelIds[i], true);
            drawTextLabel(i, text, rotation, flipped);
        }
    }

    protected void drawThirtyHashMarks(GLMapView ortho) {
        if (glthirtyHash == null)
            return;
        for (GLPolyline i : glthirtyHash)
            i.draw(ortho, GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public void onPointsChanged(Shape s) {
        final GeoBounds newBounds = sw.getBounds(null);
        final GeoPoint newCenter = sw.getCenter().get();
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                centerGP = newCenter;
                bounds.set(newBounds);
                dispatchOnBoundsChanged();
                invalid = true;
                markDirty();
            }
        });
    }

    @Override
    public void onPropertyChanged() {
        context.queueEvent(new Runnable() {
            public void run() {
                invalid = true;
                markDirty();
            }
        });
    }

    void markDirty() {
        if (surfaceCtrl == null)
            return;
        if (centerGP == null)
            return;

        // not strictly the best bounds computation, but should provide rough
        // enough estimate for dirty region marking
        final double distance = MathUtils.max(
                centerGP.distanceTo(new GeoPoint(
                        (bounds.getNorth() + bounds.getSouth()) / 2d,
                        bounds.getWest())),
                centerGP.distanceTo(new GeoPoint(
                        (bounds.getNorth() + bounds.getSouth()) / 2d,
                        bounds.getEast())),
                centerGP.distanceTo(
                        new GeoPoint(bounds.getNorth(), bounds.getWest())),
                centerGP.distanceTo(
                        new GeoPoint(bounds.getSouth(), bounds.getEast())));

        final GeoPoint north = GeoCalculations.pointAtDistance(centerGP, 0d,
                distance);
        final GeoPoint south = GeoCalculations.pointAtDistance(centerGP, 180d,
                distance);
        final GeoPoint east = GeoCalculations.pointAtDistance(centerGP, 90d,
                distance);
        final GeoPoint west = GeoCalculations.pointAtDistance(centerGP, 270d,
                distance);

        // check for IDL crossing
        if ((east.getLongitude() < centerGP.getLongitude())
                || (west.getLongitude() > centerGP.getLongitude())) {
            surfaceCtrl.markDirty(new Envelope(west.getLongitude(),
                    south.getLatitude(), 0d, 180d, north.getLatitude(), 0d),
                    true);
            surfaceCtrl.markDirty(
                    new Envelope(-180d, south.getLatitude(), 0d,
                            east.getLongitude(), north.getLatitude(), 0d),
                    true);
        } else {
            surfaceCtrl.markDirty(
                    new Envelope(west.getLongitude(), south.getLatitude(), 0d,
                            east.getLongitude(), north.getLatitude(), 0d),
                    true);
        }
    }

    protected RectF getWidgetViewWithoutActionbarF() {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) this.context).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        //float top = this.orthoView.focusy * 2;
        float top = ((GLMapView) this.context).getTop();
        return new RectF(0f + 20, top - 20, right - 20, 0f + 20);
    }

    static void setGeometry(GLBatchLineString glls, GeoPoint[] pts) {
        ByteBuffer buf = ByteBuffer.allocate((pts.length * 3 * 8) + 4);
        buf.putInt(pts.length);
        for (GeoPoint pt : pts) {
            buf.putDouble(pt.getLongitude());
            buf.putDouble(pt.getLatitude());
            final double alt = Double.isNaN(pt.getAltitude()) ? 0d
                    : pt.getAltitude();
            buf.putDouble(alt);
        }
        buf.flip();
        glls.setGeometry(buf, 1000, 0);
    }

    /**
     *
     * @param v     Normalized value, representing ratio between to values
     * @param pct   Percent expressed as normalized value (e.g. 0.25d is 25%)
     * @return
     */
    static boolean isWithinPercent(double v, double pct) {
        return (v >= (1d - pct)) && (v <= (1d + pct));
    }
}
