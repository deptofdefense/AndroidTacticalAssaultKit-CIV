
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.BasicFillStyle;

import org.junit.Assert;
import org.junit.Test;

public class BasicFillStyleTests extends ATAKInstrumentedTest {

    @Test
    public void BasicFillStyle_constructor_roundtrip() {
        final int color = RandomUtils.rng().nextInt();
        BasicFillStyle style = new BasicFillStyle(color);
        Assert.assertEquals(color, style.getColor());
    }

    static BasicFillStyle random() {
        final int color = RandomUtils.rng().nextInt();
        return new BasicFillStyle(color);
    }
}
