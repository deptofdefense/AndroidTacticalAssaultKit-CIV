
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.graphics.PointF;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.android.maps.graphics.GLFloatArray;
import com.atakmap.android.maps.graphics.GLShape;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape.OnPropertyChangedListener;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import java.util.ArrayList;

public class GLAutoSizeAngleOverlay extends GLShape implements
        OnPointsChangedListener, OnPropertyChangedListener {

    public GLAutoSizeAngleOverlay(MapRenderer surface,
            AutoSizeAngleOverlayShape subject) {
        super(surface, subject);
        sw = subject;
        startObserving();
        dpi = MapView.getMapView().getContext().getResources()
                .getDisplayMetrics().xdpi;

        int screenHeight = MapView.getMapView().getContext().getResources()
                .getDisplayMetrics().heightPixels;
        int screenWidth = MapView.getMapView().getContext().getResources()
                .getDisplayMetrics().widthPixels;

        int min = Math.min(screenHeight, screenWidth);
        //set the radius to fill up the same portion of any screen
        radius = min / 3.6f;

        centerGP = sw.getCenter().get();
        sw.getBounds(bounds);
        _verts = new GLTriangle.Fan(2, 2);
        verts = new float[] {
                0, 0,
                0, 0,
        };
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

    private boolean _projectVerts(final GLMapView ortho) {

        ortho.scratch.geo.set(sw.getCenter().get());
        ortho.scratch.geo.set(GeoPoint.UNKNOWN);
        centerD = ortho.forward(ortho.scratch.geo, centerD);
        center = new PointF((float) centerD.x, (float) centerD.y);
        top = new PointF(center.x, center.y + radius);

        //check if it is too far out to show the overlay
        // Also check if the distance calculation returns NaN, otherwise we get some weird behavior
        // if globe display mode is enabled and the user zooms out to view the entire globe
        double dist = ortho.inverse(top).distanceTo(sw.getCenter().get());
        if ((dist > 100000 && !centerMoved) || Double.isNaN(dist)) {
            return false;
        }

        offsetX = (float) Math.cos(Math.toRadians(sw.getOffsetAngle()))
                * offset;
        offsetY = (float) Math.sin(Math.toRadians(sw.getOffsetAngle()))
                * offset;

        centerMoved = false;

        return true;
    }

    protected void _setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    @Override
    public void draw(GLMapView ortho) {
        if (!sw.getVisible())
            return;
        this.ortho = ortho;

        //before redrawing, check to see if the screen has moved or if 
        //the center of the overlay has changed
        if (drawVersion != ortho.drawVersion || invalid) {
            if (invalid)
                invalid = false;
            if (!_projectVerts(ortho))
                return;
        }
        offsetAngle = sw.getOffsetAngle();
        drawVersion = ortho.drawVersion;

        //check to see if the overlay should be an ellipse
        if (sw.getProjectionProportition()) {
            PointF offsetX = ortho.forward(sw.getXTestOffset());
            PointF offsetY = ortho.forward(sw.getYTestOffset());

            double xOffset = Math.abs(center.x - offsetX.x);
            double yOffset = Math.abs(center.y - offsetY.y);

            double diff = Math.abs(xOffset - yOffset);
            double diffPercentage = diff / Math.max(xOffset, yOffset);
            if (diffPercentage > 0.15d) {
                if (yOffset > xOffset) {
                    xProportion = (float) (xOffset / yOffset);
                    yProportion = 1;
                } else {
                    xProportion = 1;
                    yProportion = (float) (yOffset / xOffset);
                }
            } else {
                xProportion = 1;
                yProportion = 1;
            }
        }

        GLES20FixedPipeline.glPushMatrix();

        ortho.scratch.geo.set(centerGP);
        ortho.scratch.geo.set(GeoPoint.UNKNOWN);
        ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
        float tx = (float) ortho.scratch.pointD.x;
        float ty = (float) ortho.scratch.pointD.y;
        float tz = (float) ortho.scratch.pointD.z;

        //GLES20FixedPipeline.glTranslatef(0f, 0f, tz);
        //GLES20FixedPipeline.glRotatef((float)-ortho.drawTilt, 1f, 0f, 0f);
        //GLES20FixedPipeline.glTranslatef(-tx, -ty, -tz);

        drawInnerArrow();

        drawCircleOutline();

        drawCircleHashMarks();

        drawText();

        GLES20FixedPipeline.glPopMatrix();
    }

    protected void drawText() {
        if (_label == null)
            _label = GLText.getInstance(MapView.getDefaultTextFormat());

        double width_div = radius * xProportion;
        double height_div = radius * yProportion;
        double angRad = -Math.toRadians(ortho.drawRotation + 90);
        double angRad_cos = Math.cos(angRad);
        double angRad_sin = Math.sin(angRad);
        int labelIndex = 0;
        for (int d = 360; d > 0; d -= 30) {
            double angdev = Math.toRadians(d - 90 - offsetAngle);
            double x = Math.cos(angdev) * width_div;
            double y = Math.sin(angdev) * height_div;
            double x2 = angRad_sin * x - angRad_cos * y;
            double y2 = angRad_cos * x + angRad_sin * y;
            labelPoints[labelIndex] = new PointF((float) (center.x + x2),
                    (float) (center.y + y2));
            labelIndex++;
        }
        String azimuth;
        if (sw.getNorthRef() == NorthReference.TRUE)
            azimuth = "T";
        else if (sw.getNorthRef() == NorthReference.MAGNETIC)
            azimuth = "M";
        else
            azimuth = "G";
        int roundedOffsetAngle = (int) Math.round(offsetAngle
                - ortho.drawRotation);
        //check if the labels should show in to out or out to in direction
        if (!sw.isShowingEdgeToCenter()) {
            if (sw.isShowingMils()) {
                for (int i = 0; i < labelPoints.length; i++) {
                    if (i == 0)
                        drawTextLabel(
                                labelPoints[i],
                                AngleUtilities.formatNoUnitsNoDecimal(360,
                                        Angle.DEGREE, Angle.MIL) + "mils"
                                        + azimuth,
                                0 + roundedOffsetAngle);
                    else
                        drawTextLabel(labelPoints[i],
                                AngleUtilities.formatNoUnitsNoDecimal(i * 30,
                                        Angle.DEGREE, Angle.MIL),
                                (i * 30) + roundedOffsetAngle);
                }
            } else {
                for (int i = 0; i < labelPoints.length; i++) {
                    if (i == 0)
                        drawTextLabel(labelPoints[i],
                                "360" + Angle.DEGREE_SYMBOL + azimuth,
                                0 + roundedOffsetAngle);
                    else
                        drawTextLabel(labelPoints[i], String.valueOf(i * 30),
                                (i * 30) + roundedOffsetAngle);
                }
            }
        } else {

            if (sw.isShowingMils()) {
                for (int i = 0; i < labelPoints.length; i++) {
                    if (i == labelPoints.length / 2)
                        drawTextLabel(
                                labelPoints[i],
                                AngleUtilities.formatNoUnitsNoDecimal(360,
                                        Angle.DEGREE, Angle.MIL) + "mils"
                                        + azimuth,
                                180 + roundedOffsetAngle);
                    else {
                        double degVal = ((i * 30) + 180) % 360;
                        drawTextLabel(labelPoints[i],
                                AngleUtilities.formatNoUnitsNoDecimal(degVal,
                                        Angle.DEGREE, Angle.MIL),
                                (i * 30)
                                        + roundedOffsetAngle);
                    }
                }
            } else {
                for (int i = 0; i < labelPoints.length; i++) {
                    if (i == labelPoints.length / 2)
                        drawTextLabel(labelPoints[i],
                                "360" + Angle.DEGREE_SYMBOL + azimuth,
                                180 + roundedOffsetAngle);
                    else
                        drawTextLabel(labelPoints[i],
                                String.valueOf(((i * 30) + 180) % 360),
                                (i * 30)
                                        + roundedOffsetAngle);
                }
            }
        }
    }

    /**
     * 
     * @param startVert
     * @param text  The <B>non-localized</B> text.
     * @param rotation
     */
    protected void drawTextLabel(PointF startVert, String text, int rotation) {
        text = GLText.localize(text);

        float[] _textPoint = new float[2];
        _textPoint[0] = startVert.x;
        _textPoint[1] = startVert.y;

        //Move labels outside the ring here
        double halfLabelHeight = (_label.getStringHeight() + 8) / 2d;
        double yOffset = Math.cos(Math.toRadians(rotation)) * halfLabelHeight;
        double xOffset = Math.sin(Math.toRadians(rotation)) * halfLabelHeight;

        _textPoint[0] += xOffset;
        _textPoint[1] += yOffset;

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

    protected void drawCircleOutline() {
        // Save the matrix
        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef(center.x, center.y, 0f);
        GLES20FixedPipeline.glRotatef((float) ortho.drawRotation, 0f, 0f, 1f);

        // Calculate the dimensions we'll use for the ring
        float innerRadius = radius;
        float outerRadius = innerRadius + 7;

        // Set up our styles for the ring
        GLES20FixedPipeline.glColor4f(.85f, .85f, .85f, .5f);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        // Calculate all the points on the circle
        GLFloatArray vertices = new GLFloatArray(2, 94);
        for (int i = 0; i < 94; i += 2) {
            vertices.setX(i,
                    (float) (Math.cos(Math.toRadians(i * 4 - offsetAngle))
                            * outerRadius * xProportion));
            vertices.setY(i,
                    (float) (Math.sin(Math.toRadians(i * 4 - offsetAngle))
                            * outerRadius * yProportion));

            vertices.setX(i + 1,
                    (float) ((Math.cos(Math.toRadians(i * 4 - offsetAngle))
                            * innerRadius) * xProportion));
            vertices.setY(i + 1,
                    (float) ((Math.sin(Math.toRadians(i * 4 - offsetAngle))
                            * innerRadius) * yProportion));
        }

        // Draw the circle
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                vertices.getBuffer());
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                0, 94);

        // Set up our styles for the lines
        GLES20FixedPipeline.glLineWidth(3f);
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, .8f);

        // We're going to go through and calculate the endpoints for all of the lines at 30 deg
        GLFloatArray lineVertices = new GLFloatArray(2, 24);
        int index = 0;
        for (int j = 0; j < 360; j += 30) {
            // Leave out north, we'll draw that later in red
            if (j != 90) {
                float cosOffset = (float) Math.cos(Math.toRadians(j
                        - offsetAngle))
                        * outerRadius * xProportion;
                float sinOffset = (float) Math.sin(Math.toRadians(j
                        - offsetAngle))
                        * outerRadius * yProportion;
                lineVertices.setX(index, cosOffset);
                lineVertices.setY(index, sinOffset);

                index++;

                lineVertices.setX(index,
                        (float) Math.cos(Math.toRadians(j - offsetAngle))
                                * innerRadius * xProportion);
                lineVertices.setY(index,
                        (float) Math.sin(Math.toRadians(j - offsetAngle))
                                * innerRadius * yProportion);
                index++;
            }
        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        // now draw north in red
        lineVertices
                .setX(0, (float) Math.cos(Math.toRadians(90 - offsetAngle))
                        * outerRadius * xProportion);
        lineVertices
                .setY(0, (float) Math.sin(Math.toRadians(90 - offsetAngle))
                        * outerRadius * yProportion);
        index++;

        lineVertices
                .setX(1, (float) Math.cos(Math.toRadians(90 - offsetAngle))
                        * innerRadius * xProportion);
        lineVertices
                .setY(1, (float) Math.sin(Math.toRadians(90 - offsetAngle))
                        * innerRadius * yProportion);

        index++;

        GLES20FixedPipeline.glLineWidth(5f);
        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .8f);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);

        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

        // Pop the matrix
        GLES20FixedPipeline.glPopMatrix();
    }

    protected void drawCircleHashMarks() {
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

        float innerRadius = radius - 25;
        float outerRadius = radius;

        // Set up our styles for the lines
        GLES20FixedPipeline.glLineWidth(3f);
        GLES20FixedPipeline.glColor4f(1f, 1f, 1f, .8f);

        // We're going to go through and calculate the endpoints for all of the lines at 30 deg
        GLFloatArray lineVertices = new GLFloatArray(2, 72);
        int index = 0;
        for (int j = 0; j < 360; j += 10) {
            // Leave out north
            if (j != 90) {
                lineVertices.setX(index,
                        (float) Math.cos(Math.toRadians(j - offsetAngle))
                                * outerRadius * xProportion);
                lineVertices.setY(index,
                        (float) Math.sin(Math.toRadians(j - offsetAngle))
                                * outerRadius * yProportion);
                index++;
                float cosOffset = (float) Math.cos(Math.toRadians(j
                        - offsetAngle))
                        * innerRadius * xProportion;
                float sinOffset = (float) Math.sin(Math.toRadians(j
                        - offsetAngle))
                        * innerRadius * yProportion;
                lineVertices.setX(index, cosOffset);
                lineVertices.setY(index, sinOffset);
                index++;
            }
        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        innerRadius = radius - 12;

        index = 0;
        for (int j = 5; j < 360; j += 10) {
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(j - offsetAngle))
                            * outerRadius * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(j - offsetAngle))
                            * outerRadius * yProportion);
            index++;
            float cosOffset = (float) Math.cos(Math.toRadians(j - offsetAngle))
                    * innerRadius * xProportion;
            float sinOffset = (float) Math.sin(Math.toRadians(j - offsetAngle))
                    * innerRadius * yProportion;
            lineVertices.setX(index, cosOffset);
            lineVertices.setY(index, sinOffset);
            index++;

        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        GLES20FixedPipeline.glPopMatrix();

    }

    protected void drawThirtyHashMarks() {
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

        float outerRadius = radius;

        // Set up our styles for the lines
        GLES20FixedPipeline.glLineWidth(3f);

        //TODO: set up dashed line style

        if (!sw.isShowingEdgeToCenter())
            GLES20FixedPipeline.glColor4f(0f, 1f, 0f, .5f);
        else
            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .5f);

        // We're going to go through and calculate the endpoints for all of the lines at 30 deg
        GLFloatArray lineVertices = new GLFloatArray(2, 24);
        int index = 0;
        for (int j = 0; j < 360; j += 30) {
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(j - offsetAngle))
                            * outerRadius * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(j - offsetAngle))
                            * outerRadius * yProportion);
            index++;

            lineVertices.setX(index, 0f);
            lineVertices.setY(index, 0f);
            index++;
        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

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

    protected void drawDirectionalArrow() {
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

        float outerRadius = radius;

        // Set up our styles for the lines
        GLES20FixedPipeline.glLineWidth(5f);

        // We're going to go through and calculate the endpoints for all of the lines at 30 deg
        GLFloatArray lineVertices = new GLFloatArray(2, 4);
        int index = 0;
        //draw an arrow either in or out

        if (!sw.isShowingEdgeToCenter()) {
            GLES20FixedPipeline.glColor4f(0f, 1f, 0f, .5f);
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(85 - offsetAngle))
                            * (outerRadius * .75f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(85 - offsetAngle))
                            * (outerRadius * .75f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .85f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .85f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .85f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .85f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(95 - offsetAngle))
                            * (outerRadius * .75f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(95 - offsetAngle))
                            * (outerRadius * .75f) * yProportion);
            index++;
        } else {
            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .5f);
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(86 - offsetAngle))
                            * (outerRadius * .85f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(86 - offsetAngle))
                            * (outerRadius * .85f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .75f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .75f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .75f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(90 - offsetAngle))
                            * (outerRadius * .75f) * yProportion);
            index++;
            lineVertices.setX(index,
                    (float) Math.cos(Math.toRadians(94 - offsetAngle))
                            * (outerRadius * .85f) * xProportion);
            lineVertices.setY(index,
                    (float) Math.sin(Math.toRadians(94 - offsetAngle))
                            * (outerRadius * .85f) * yProportion);
            index++;

        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void onPointsChanged(Shape s) {
        final GeoBounds newBounds = sw.getBounds(null);
        final GeoPoint newCenter = sw.getCenter().get();
        renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                //if the subject has changed locations
                centerMoved = !centerGP.equals(newCenter);
                centerGP = newCenter;
                bounds.set(newBounds);
                OnBoundsChanged();
                invalid = true;
            }
        });
    }

    protected static final float LINE_WIDTH = (float) Math
            .ceil(1f * MapView.DENSITY);

    protected GLMapView ortho;
    protected GeoPoint centerGP;
    protected PointF center;
    protected PointD centerD;
    protected PointF top;
    protected PointF right;
    protected PointF bottom;
    protected PointF left;

    protected float xProportion = 1;
    protected float yProportion = 1;
    protected double offsetAngle = 0;

    protected boolean invalid = false;
    protected boolean centerMoved = false;

    protected float radius = 300;

    protected GLTriangle.Fan _verts;

    protected final PointF[] labelPoints = new PointF[12];

    protected ArrayList<Float> hashMarks = null;
    protected float[] verts;
    protected float dpi;

    protected final static float offset = 40;
    protected final static float hashLength = 20;

    protected GLText _label;

    protected float offsetX = 40;
    protected float offsetY = 40;

    protected int drawVersion;

    private final AutoSizeAngleOverlayShape sw;

    @Override
    public void onPropertyChanged() {
        invalid = true;
    }
}
