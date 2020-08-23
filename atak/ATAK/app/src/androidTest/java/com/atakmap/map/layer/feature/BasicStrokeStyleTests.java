
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;

import org.junit.Assert;
import org.junit.Test;

public class BasicStrokeStyleTests extends ATAKInstrumentedTest {

    @Test
    public void BasicStrokeStyle_constructor_roundtrip() {
        final int color = RandomUtils.rng().nextInt();
        final float width = RandomUtils.rng().nextFloat() * 100;
        BasicStrokeStyle style = new BasicStrokeStyle(color, width);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertTrue(style.getStrokeWidth() == width);
    }

    @Test(expected = RuntimeException.class)
    public void BasicStrokeStyle_constructor_negative_width_throws() {
        final int color = RandomUtils.rng().nextInt();
        BasicStrokeStyle style = new BasicStrokeStyle(color, -1f);
        Assert.fail();
    }

    static BasicStrokeStyle random() {
        final int color = RandomUtils.rng().nextInt();
        final float width = RandomUtils.rng().nextFloat() * 100;
        return new BasicStrokeStyle(color, width);
    }
}
