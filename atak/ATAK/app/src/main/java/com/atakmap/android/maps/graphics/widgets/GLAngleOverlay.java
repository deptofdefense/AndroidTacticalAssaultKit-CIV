
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import com.atakmap.android.maps.graphics.GLFloatArray;
import com.atakmap.android.maps.graphics.GLIcon;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

public class GLAngleOverlay extends GLAutoSizeAngleOverlay {

    private GLIcon _icon;
    private final float[] _textPoint = new float[2];
    private final AngleOverlayShape sw;

    public GLAngleOverlay(MapRenderer surface, AngleOverlayShape subject) {
        super(surface, subject);
        sw = subject;
        offsetAngle = sw.getOffsetAngle();
    }

    private boolean _projectVerts(final GLMapView ortho) {
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

    private static PointD unwrapAndForward(GLMapView ortho, GeoPoint g, PointD xyz) {
        if(g != ortho.scratch.geo)
            ortho.scratch.geo.set(g);
        // handle IDL crossing
        if(ortho.drawSrid == 4326 && ortho.currentPass.crossesIDL && ortho.currentPass.drawLng*ortho.scratch.geo.getLongitude() < 0d)
            ortho.scratch.geo.set(ortho.scratch.geo.getLatitude(), ortho.scratch.geo.getLongitude()+(360d*Math.signum(ortho.drawLng)));
        if(xyz == null)
            xyz = new PointD(0d, 0d, 0d);
        ortho.currentPass.scene.forward(ortho.scratch.geo, xyz);
        return xyz;
    }

    private static PointF unwrapAndForward(GLMapView ortho, GeoPoint g) {
        unwrapAndForward(ortho, g, ortho.scratch.pointD);
        return new PointF((float)ortho.scratch.pointD.x, (float)ortho.scratch.pointD.y);
    }

    @Override
    public void draw(GLMapView ortho) {
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
    private void drawIcon() {
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
                    .get(renderContext).getImageCache()
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

    private void drawInnerArrow() {
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

        GLNinePatch _ninePatch = GLRenderGlobals.get(this.renderContext)
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
    private void buildLabelEdgeVisible(GLMapView ortho, final PointF startVert,
            final PointF endVert) {

        final float p0x = startVert.x;
        final float p0y = startVert.y;

        final float p1x = endVert.x;
        final float p1y = endVert.y;

        float xmin = (p0x < p1x) ? p0x : p1x;
        float ymin = (p0x < p1x) ? p0y : p1y;
        float xmax = (p0x > p1x) ? p0x : p1x;
        float ymax = (p0x > p1x) ? p0y : p1y;

        if (p0x == p1x) {
            ymax = Math.max(p0y, p1y);
            ymin = Math.min(p0y, p1y);
        }

        PointF modStartVert = new PointF(xmin, ymin);
        PointF modEndVert = new PointF(xmax, ymax);

        //shrink the view bounds to allow room to show the full label
        double halfLabelHeight = (_label.getStringHeight() + 12) / 2d;
        RectF _view = this.getWidgetViewWithoutActionbarF();
        _view.bottom += halfLabelHeight;
        _view.left += halfLabelHeight;
        _view.top -= halfLabelHeight;
        _view.right -= halfLabelHeight;
        PointF[] ip = _getIntersectionPoint(_view, modStartVert, modEndVert);

        //if no intersection point is found return NAN
        if (ip == null || (ip[1] == null && ip[0] == null)) {
            _textPoint[0] = Float.NaN;
            _textPoint[1] = Float.NaN;
            return;
        }

        //if one intersection point was found use that point
        if (ip[1] != null && ip[0] == null) {
            _textPoint[0] = ip[1].x;
            _textPoint[1] = ip[1].y;
            return;
        } else if (ip[0] != null && ip[1] == null) {
            _textPoint[0] = ip[0].x;
            _textPoint[1] = ip[0].y;
            return;
        }

        PointF p1 = new PointF(ip[0].x, ip[0].y);
        PointF p2 = new PointF(ip[1].x, ip[1].y);

        //attempt to find the closest intersection point to the outside of the spoke
        Double dist0 = Math.sqrt((Math.abs(p1.x - endVert.x) * Math.abs(p1.x
                - endVert.x))
                + (Math.abs(p1.y - endVert.y) * Math.abs(p1.y - endVert.y)));
        Double dist1 = Math.sqrt((Math.abs(p2.x - endVert.x) * Math.abs(p2.x
                - endVert.x))
                + (Math.abs(p2.y - endVert.y) * Math.abs(p2.y - endVert.y)));
        if (dist0 <= dist1) {
            _textPoint[0] = ip[0].x;
            _textPoint[1] = ip[0].y;
        } else {
            _textPoint[0] = ip[1].x;
            _textPoint[1] = ip[1].y;
        }

    }
}
