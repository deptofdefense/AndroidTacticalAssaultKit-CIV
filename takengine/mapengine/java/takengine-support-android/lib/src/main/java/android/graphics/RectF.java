package android.graphics;

public class RectF {
    public float left;
    public float right;
    public float top;
    public float bottom;

    public RectF() {
        this(0, 0, 0, 0);
    }

    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() {
        return right-left;
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
