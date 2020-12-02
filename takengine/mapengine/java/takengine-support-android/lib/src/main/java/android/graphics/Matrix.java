package android.graphics;

public class Matrix {
    final float[] mx;

    public Matrix() {
        mx = new float[16];
        android.opengl.Matrix.setIdentityM(mx, 0);
    }

    public void reset() {
        android.opengl.Matrix.setIdentityM(mx, 0);
    }

    public void setScale(float x, float  y) {
        android.opengl.Matrix.setIdentityM(mx, 0);
        android.opengl.Matrix.scaleM(mx, 0, (float)x, (float)y, 1f);
    }
    public void scale(float x, float y) {
        android.opengl.Matrix.scaleM(mx, 0, (float)x, (float)y, 1f);
    }
    public void mapRect(RectF r) {
        float[] p = new float[8];
        float rleft;
        float rtop;
        float rright;
        float rbottom;

        p[0] = r.left;
        p[1] = r.top;
        p[2] = 0f;
        p[3] = 1f;
        android.opengl.Matrix.multiplyMV(p, 4, mx, 0, p, 0);
        rleft = p[4] / p[7];
        rtop = p[5] / p[7];
        rright = p[4] / p[7];
        rbottom = p[5] / p[7];

        p[0] = r.right;
        p[1] = r.top;
        p[2] = 0f;
        p[3] = 1f;
        android.opengl.Matrix.multiplyMV(p, 4, mx, 0, p, 0);
        rleft = Math.min(p[4] / p[7], rleft);
        rtop = Math.min(p[5] / p[7], rtop);
        rright = Math.max(p[4] / p[7], rright);
        rbottom = Math.max(p[5] / p[7], rbottom);

        p[0] = r.right;
        p[1] = r.bottom;
        p[2] = 0f;
        p[3] = 1f;
        android.opengl.Matrix.multiplyMV(p, 4, mx, 0, p, 0);
        rleft = Math.min(p[4] / p[7], rleft);
        rtop = Math.min(p[5] / p[7], rtop);
        rright = Math.max(p[4] / p[7], rright);
        rbottom = Math.max(p[5] / p[7], rbottom);

        p[0] = r.left;
        p[1] = r.bottom;
        p[2] = 0f;
        p[3] = 1f;
        android.opengl.Matrix.multiplyMV(p, 4, mx, 0, p, 0);
        rleft = Math.min(p[4] / p[7], rleft);
        rtop = Math.min(p[5] / p[7], rtop);
        rright = Math.max(p[4] / p[7], rright);
        rbottom = Math.max(p[5] / p[7], rbottom);
    }

    public boolean isAffine() {
        return mx[8] == 0f &&
               mx[9] == 0f &&
               mx[2] == 0f && mx[6] == 0f && mx[10] == 1f && mx[14] == 0f &&
               mx[3] == 0f && mx[7] == 0f && mx[11] == 0f && mx[15] == 1f;
    }

    public void getValues(float[] v) {
        v[0] = mx[0]; v[1] = mx[4]; v[2] = mx[12];
        v[3] = mx[1]; v[4] = mx[5]; v[5] = mx[13];
        v[6] = mx[3]; v[7] = mx[7]; v[8] = mx[15];
    }
    public void mapPoints(float[] dst, int dstIdx, float[] src, int srcIdx, int pointCount) {
        float[] p = new float[8];
        for(int i = 0; i < pointCount; i++) {
            p[0] = src[srcIdx+(i*2)];
            p[1] = src[srcIdx+(i*2)+1];
            p[2] = 0f;
            p[3] = 1f;
            android.opengl.Matrix.multiplyMV(p, 4, mx, 0, p,0);
            dst[dstIdx+(i*2)] = p[4]/p[7];
            dst[dstIdx+(i*2)+1] = p[5]/p[7];
        }
    }
    public void mapPoints(float[] dst, float[] src) {
        mapPoints(dst, 0, src, 0, src.length/2);
    }
    public void mapPoints(float[] pts) {
        mapPoints(pts, pts);
    }
}
