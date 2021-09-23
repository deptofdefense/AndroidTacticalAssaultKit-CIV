
package com.atakmap.android.maps.graphics.widgets;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLFloatArray;
import com.atakmap.android.maps.graphics.GLIcon;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.List;

/**
 * @deprecated Use {@link com.atakmap.android.maps.graphics.GLAngleOverlay2}
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
public class GLAngleOverlay extends GLAutoSizeAngleOverlay {

    protected GLIcon _icon;
    protected final float[] _textPoint = new float[2];
    protected final AngleOverlayShape sw;

    public GLAngleOverlay(MapRenderer surface, AngleOverlayShape subject) {
        super(surface, subject);
        sw = subject;
        offsetAngle = sw.getOffsetAngle();
    }

    protected boolean _projectVerts(final GLMapView ortho) {
        offsetAngle = sw.getOffsetAngle();

        ortho.scratch.geo.set(sw.getCenter().get());
        ortho.scratch.geo.set(GeoPoint.UNKNOWN);
        centerD = unwrapAndForward(ortho, ortho.scratch.geo, centerD);
        center = new PointF((float) centerD.x, (float) centerD.y);
        GeoPoint[] arrowEndPoints = sw.getInnerArrowPoints();

        top = unwrapAndForward(ortho, arrowEndPoints[0]);

        if (!sw.showSimpleSpokeView()) {
            double dist = ortho.inverse(top).distanceTo(sw.getCenter().get());
            if (dist > 100000 && !centerMoved)
                return false;
        }

        float xDist = Math.abs(center.x - top.x);
        float yDist = Math.abs(center.y - top.y);
        float newRadius = (float) Math.sqrt((xDist * xDist) + (yDist * yDist));

        if (newRadius < ((offset + hashLength) * 1.5))
            return false;

        offsetX = (float) Math.cos(Math.toRadians(sw.getOffsetAngle()))
                * offset;
        offsetY = (float) Math.sin(Math.toRadians(sw.getOffsetAngle()))
                * offset;

        radius = newRadius;

        right = unwrapAndForward(ortho, arrowEndPoints[1]);

        bottom = unwrapAndForward(ortho, arrowEndPoints[2]);

        left = unwrapAndForward(ortho, arrowEndPoints[3]);

        return true;
    }

    private static PointD unwrapAndForward(GLMapView ortho, GeoPoint g,
            PointD xyz) {
        if (g != ortho.scratch.geo)
            ortho.scratch.geo.set(g);
        // handle IDL crossing
        if (ortho.drawSrid == 4326 && ortho.currentPass.crossesIDL
                && ortho.currentPass.drawLng
                        * ortho.scratch.geo.getLongitude() < 0d)
            ortho.scratch.geo.set(ortho.scratch.geo.getLatitude(),
                    ortho.scratch.geo.getLongitude()
                            + (360d * Math.signum(ortho.drawLng)));
        if (xyz == null)
            xyz = new PointD(0d, 0d, 0d);
        ortho.currentPass.scene.forward(ortho.scratch.geo, xyz);
        return xyz;
    }

    protected static PointF unwrapAndForward(GLMapView ortho, GeoPoint g) {
        unwrapAndForward(ortho, g, ortho.scratch.pointD);
        return new PointF((float) ortho.scratch.pointD.x,
                (float) ortho.scratch.pointD.y);
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!sw.getVisible())
            return;
        this.ortho = ortho;

        if (drawVersion != ortho.drawVersion || invalid) {
            if (invalid)
                invalid = false;
            if (!_projectVerts(ortho)) {
                drawIcon();
                return;
            }
        }

        drawVersion = ortho.drawVersion;

        //check to see if the overlay should be an ellipse
        if (sw.getProjectionProportition()) {
            PointF offsetX = unwrapAndForward(ortho, sw.getXTestOffset());
            PointF offsetY = unwrapAndForward(ortho, sw.getYTestOffset());

            double xOffset = Math.abs(center.x - offsetX.x);
            double yOffset = Math.abs(center.y - offsetY.y);

            double diff = Math.abs(xOffset - yOffset);
            double diffPercentage = diff / Math.max(xOffset, yOffset);
            if (diffPercentage > 0.15d) {
                if (yOffset > xOffset) {
                    xProportion = (float) (xOffset / yOffset);
                    yProportion = 1;

                    float xDist = Math.abs(center.x - top.x);
                    float yDist = Math.abs(center.y - top.y);
                    radius = (float) Math.sqrt((xDist * xDist)
                            + (yDist * yDist));
                    radius *= yProportion / xProportion;
                } else {
                    xProportion = 1;
                    yProportion = (float) (yOffset / xOffset);

                    float xDist = Math.abs(center.x - top.x);
                    float yDist = Math.abs(center.y - top.y);
                    radius = (float) Math.sqrt((xDist * xDist)
                            + (yDist * yDist));
                    radius *= xProportion / yProportion;
                }
            } else {
                xProportion = 1;
                yProportion = 1;
            }
        }

        if (sw.showSimpleSpokeView()) {
            drawThirtyHashMarks();
            drawText();
            drawDirectionalArrow();
            return;
        }

        drawInnerArrow();

        drawCircleOutline();

        drawCircleHashMarks();

        drawText();
    }

    /**
     * Draw an icon to represent where the overlay is when zoomed out too far
     */
    protected void drawIcon() {
        //setup the icon if it is not set up
        if (_icon == null) {
            Icon icon = new Icon("asset:/icons/bullseye_icon.png");
            _icon = new GLIcon(icon.getWidth(), icon.getHeight(),
                    icon.getAnchorX(),
                    icon.getAnchorY());
            Icon.Builder backup = new Icon.Builder();

            backup.setImageUri(Icon.STATE_DEFAULT,
                    subject.getMetaString("backupIconUri", null));
            backup.setColor(Icon.STATE_DEFAULT, Color.GREEN);
            GLImageCache.Entry iconEntry = GLRenderGlobals
                    .get(context).getImageCache()
                    .fetchAndRetain(
                            icon.getImageUri(Icon.STATE_DEFAULT), true);
            _icon.updateCacheEntry(iconEntry);
        }

        _icon.validate();
        int _color = Color.argb(255, 0, 255, 0);
        if (sw.isShowingEdgeToCenter())
            _color = Color.argb(255, 255, 0, 0);

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef((float) centerD.x, (float) centerD.y,
                (float) centerD.z);
        GLES20FixedPipeline.glRotatef(
                (float) (ortho.drawRotation - sw.getOffsetAngle()), 0f, 0f,
                1f);
        _icon.drawColor(_color);

        GLES20FixedPipeline.glPopMatrix();
    }

    protected void drawInnerArrow() {
        // Save the matrix
        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef(center.x, center.y, 0f);
        GLES20FixedPipeline.glRotatef((float) ortho.drawRotation, 0f, 0f, 1f);

        // Set up our styles for the ring
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        // Set up our styles for the lines
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH + 2);

        //draw the 3 short segments
        GLFloatArray lineVertices = new GLFloatArray(2, 6);
        int index = 0;
        for (int j = 180; j <= 360; j += 90) {
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(j - offsetAngle))
                            * (radius / 3f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(j - offsetAngle))
                            * (radius / 3f) * yProportion);
            index++;
            lineVertices.setX(index, 0f);
            lineVertices.setY(index, 0f);
            index++;
        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        GLES20FixedPipeline.glColor4f(1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH);
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        //draw the red segment
        lineVertices = new GLFloatArray(2, 2);
        index = 0;
        lineVertices.setX(index,
                (float) Math.cos(Math.toRadians(90 - offsetAngle))
                        * radius * xProportion);
        lineVertices.setY(index,
                (float) Math.sin(Math.toRadians(90 - offsetAngle))
                        * radius * yProportion);
        index++;
        lineVertices.setX(index, 0f);
        lineVertices.setY(index, 0f);
        index++;

        GLES20FixedPipeline.glColor4f(1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH + 2);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH);
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    protected void drawTextLabel(PointF startVert, String text, int rotation) {
        if (!sw.showSimpleSpokeView()) {
            super.drawTextLabel(startVert, text, rotation);
            return;
        }

        text = GLText.localize(text);

        RectF _view = this.getWidgetViewWithoutActionbarF();
        if (startVert.x <= _view.left || startVert.x >= _view.right ||
                startVert.y <= _view.bottom || startVert.y >= _view.top) {
            buildLabelEdgeVisible(ortho, center, startVert);
            if (Float.isNaN(_textPoint[0]) || Float.isNaN(_textPoint[1]))
                return;
        } else {
            _textPoint[0] = startVert.x;
            _textPoint[1] = startVert.y;

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
        double totalRot = Math.abs(rotation) % 360d;
        if (totalRot > 90 && totalRot < 270)
            rotation = (rotation + 180) % 360;

        GLES20FixedPipeline.glTranslatef(_textPoint[0], _textPoint[1], 0);
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
            final PointF endVert) {

        GLText _label = GLText.getInstance(MapView.getDefaultTextFormat());

        final float p0x = startVert.x;
        final float p0y = startVert.y;

        final float p1x = endVert.x;
        final float p1y = endVert.y;

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

    /**
     * Retrieve the bounding RectF of the current state of the Map. This accounts for the
     * OrthoMapView's focus, so DropDowns will be accounted for.
     *
     * NOTE- the RectF this returns is not a valid RectF since the origin coordinate
     * is in the lower left (ll is 0,0). Therefore the RectF.contains(PointF) method
     * will not work to determine if a point falls inside the bounds.
     *
     * @return The bounding RectF
     */
    private RectF getWidgetViewWithoutActionbarF() {
        // Could be in half or third display of dropdown, so use the offset;
        float right = ((GLMapView) this.context).focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        //float top = this.orthoView.focusy * 2;
        float top = ((GLMapView) this.context).getTop();
        return new RectF(0f + 20,
                top - (MapView.getMapView().getActionBarHeight() + 20),
                right - 20, 0f + 20);
    }
}
