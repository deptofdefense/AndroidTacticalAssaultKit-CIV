package gov.tak.platform.widgets.opengl;

import java.nio.FloatBuffer;

final class GLTriangle {

    public static float[] createRectangle(float x, float y, float width,
                                          float height, float[] out) {

        if (out == null || out.length < 8) {
            out = new float[8];
        }

        out[0] = x;
        out[1] = y;

        out[2] = x;
        out[3] = y + height;

        out[4] = x + width;
        out[5] = y;

        out[6] = x + width;
        out[7] = y + height;

        return out;
    }
    public static FloatBuffer createRectangleFloatBuffer(float x, float y, float width,
                                          float height) {

        float[]verts = createRectangle(x,y,width,height,null);
        FloatBuffer fb = createFloatBuffer(4, 2);
        fb.put(verts);
        fb.position(0);

        return fb;
    }
    public static FloatBuffer createFloatBuffer(int numPoints, int pointSize) {
        return com.atakmap.lang.Unsafe.allocateDirect(numPoints*pointSize, FloatBuffer.class);
    }
}
