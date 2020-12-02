package android.graphics;

public class Rect {
    public int left;
    public int right;
    public int top;
    public int bottom;

    public Rect() {
        this(0, 0, 0, 0);
    }
    public Rect(int left, int top, int right, int bottom) {
        this.set(left, top, right, bottom);
    }
    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
