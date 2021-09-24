
package com.atakmap.android.maps.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.atakmap.opengl.GLES20FixedPipeline;

public class GLBackground {

    public static final int BKGRND_TYPE_SOLID = 7;

    private ByteBuffer pointer;

    public GLBackground(float x0, float y0, float x1, float y1) {
        pointer = com.atakmap.lang.Unsafe.allocateDirect(8 * 4);
        pointer.order(ByteOrder.nativeOrder());

        final float extentY = Math.abs(y1-y0);
        final float extentX = Math.abs(x1-x0);

        final float maxExtent = Math.max(extentY, extentX);

        final float expandedExtent = maxExtent * 2.3f;

        pointer.putFloat(-maxExtent);
        pointer.putFloat(-maxExtent);
        pointer.putFloat(-maxExtent);
        pointer.putFloat(expandedExtent);
        pointer.putFloat(expandedExtent);
        pointer.putFloat(expandedExtent);
        pointer.putFloat(expandedExtent);
        pointer.putFloat(-maxExtent);

        pointer.rewind();
    }

    public void draw(int type, float red, float green, float blue, float alpha,
            boolean blend) {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadIdentity();

        if (type == BKGRND_TYPE_SOLID) {
            GLES20FixedPipeline.glVertexPointer(2,
                    GLES20FixedPipeline.GL_FLOAT, 0, pointer);

            if (alpha < 1.0f && !blend) {
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline.glBlendFunc(
                        GLES20FixedPipeline.GL_SRC_ALPHA,
                        GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
            }
            GLES20FixedPipeline.glColor4f(red, green, blue, alpha);
            GLES20FixedPipeline.glDrawArrays(
                    GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, 4);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

}
