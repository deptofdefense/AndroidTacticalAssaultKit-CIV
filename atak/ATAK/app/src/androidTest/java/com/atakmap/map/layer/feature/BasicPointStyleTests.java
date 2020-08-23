
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.BasicPointStyle;

import org.junit.Assert;
import org.junit.Test;

public class BasicPointStyleTests extends ATAKInstrumentedTest {

    @Test
    public void BasicPointStyle_constructor_roundtrip() {
        final int color = RandomUtils.rng().nextInt();
        final float size = (RandomUtils.rng().nextFloat() * 128 + 1f);
        BasicPointStyle style = new BasicPointStyle(color, size);
        Assert.assertEquals(color, style.getColor());
        final float ssize = style.getSize();
        Assert.assertTrue(size == style.getSize());
    }

    @Test(expected = RuntimeException.class)
    public void BasicPointStyle_constructor_negative_size_throws() {
        final int color = RandomUtils.rng().nextInt();
        final float size = (RandomUtils.rng().nextFloat() * 128 + 1f) * -1f;
        BasicPointStyle style = new BasicPointStyle(color, size);
        Assert.fail();
    }

    static BasicPointStyle random() {
        final int color = RandomUtils.rng().nextInt();
        final float size = (RandomUtils.rng().nextFloat() * 128 + 1f);
        return new BasicPointStyle(color, size);
    }
}
