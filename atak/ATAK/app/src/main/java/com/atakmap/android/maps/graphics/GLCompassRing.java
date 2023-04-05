
package com.atakmap.android.maps.graphics;

import com.atakmap.android.maps.CompassRing;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLCompassRing extends GLPointMapItem2 {
    public GLCompassRing(MapRenderer surface, CompassRing compassRing) {
        super(surface, compassRing, GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        ortho.forward(point, ortho.scratch.pointD);
        final float posx = (float) ortho.scratch.pointD.x;
        final float posy = (float) ortho.scratch.pointD.y;
        final float posz = (float) ortho.scratch.pointD.z;

        // Save the matrix
        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef(posx, posy, posz);
        GLES20FixedPipeline.glRotatef((float) ortho.currentPass.drawRotation,
                0f, 0f, 1f);
        GLES20FixedPipeline.glRotatef((float) ortho.currentPass.drawTilt, 1f,
                0f, 0f);

        // Calculate the dimensions we'll use for the ring
        float height = (float) ortho.getTop() - (float) ortho.getBottom();
        float width = (float) ortho.getLeft() - (float) ortho.getRight();
        float outerRadius = Math.min(Math.abs(height / 4), Math.abs(width / 4));
        float innerRadius = Math.min(Math.abs(height / 4) - 25,
                Math.abs(width / 4) - 25);

        // Set up our styles for the ring
        GLES20FixedPipeline.glColor4f(.85f, .85f, .85f, .5f);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        // Calculate all the points on the circle
        GLFloatArray vertices = new GLFloatArray(2, 720);
        for (int i = 0; i < 720; i += 2) {
            vertices.setX(i, (float) Math.cos(Math.toRadians(i)) * outerRadius);
            vertices.setY(i, (float) Math.sin(Math.toRadians(i)) * outerRadius);

            vertices.setX(i + 1, (float) Math.cos(Math.toRadians(i))
                    * innerRadius);
            vertices.setY(i + 1, (float) Math.sin(Math.toRadians(i))
                    * innerRadius);
        }

        // Draw the circle
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                vertices.getBuffer());
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                0, 720);

        // Set up our styles for the lines
        GLES20FixedPipeline.glLineWidth(3f);
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, .8f);

        // We're going to go through and calculate the endpoints for all of the lines at 30 deg
        GLFloatArray lineVertices = new GLFloatArray(2, 24);
        int index = 0;
        for (int j = 0; j < 360; j += 30) {
            // Leave out north, we'll draw that later in red
            if (j != 90) {
                lineVertices.setX(index, (float) Math.cos(Math.toRadians(j))
                        * outerRadius);
                lineVertices.setY(index, (float) Math.sin(Math.toRadians(j))
                        * outerRadius);

                index++;
                lineVertices.setX(index, (float) Math.cos(Math.toRadians(j))
                        * innerRadius);
                lineVertices.setY(index, (float) Math.sin(Math.toRadians(j))
                        * innerRadius);

                index++;
            }
        }

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                lineVertices.getBuffer());
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, index);

        // now draw north in red
        lineVertices
                .setX(0, (float) Math.cos(Math.toRadians(90)) * outerRadius);
        lineVertices
                .setY(0, (float) Math.sin(Math.toRadians(90)) * outerRadius);
        index++;

        lineVertices
                .setX(1, (float) Math.cos(Math.toRadians(90)) * innerRadius);
        lineVertices
                .setY(1, (float) Math.sin(Math.toRadians(90)) * innerRadius);
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
}
