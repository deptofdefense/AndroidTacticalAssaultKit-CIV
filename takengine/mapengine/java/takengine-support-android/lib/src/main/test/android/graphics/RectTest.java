package android.graphics;

import org.junit.Assert;
import org.junit.Test;

public class RectTest {
    @Test
    public void empty_constructor() {
        Rect r = new Rect();
        Assert.assertEquals(0, r.left);
        Assert.assertEquals(0, r.top);
        Assert.assertEquals(0, r.right);
        Assert.assertEquals(0, r.bottom);
    }

    @Test
    public void constructor_roundtrip() {
        final int left = 1;
        final int top = 2;
        final int right = 4;
        final int bottom = 6;
        Rect r = new Rect(left, top, right, bottom);
        Assert.assertEquals(left, r.left);
        Assert.assertEquals(top, r.top);
        Assert.assertEquals(right, r.right);
        Assert.assertEquals(bottom, r.bottom);
    }

    @Test
    public void set_roundtrip() {
        Rect r = new Rect();

        final int left = 1;
        final int top = 2;
        final int right = 4;
        final int bottom = 6;
        r.set(left, top, right, bottom);
        Assert.assertEquals(left, r.left);
        Assert.assertEquals(top, r.top);
        Assert.assertEquals(right, r.right);
        Assert.assertEquals(bottom, r.bottom);

    }
}
