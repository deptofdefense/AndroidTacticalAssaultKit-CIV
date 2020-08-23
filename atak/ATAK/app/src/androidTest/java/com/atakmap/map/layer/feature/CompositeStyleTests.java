
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.layer.feature.style.Style;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class CompositeStyleTests extends ATAKInstrumentedTest {

    @Test(expected = NullPointerException.class)
    public void CompositeStyle_constructor_null_throws() {
        CompositeStyle style = new CompositeStyle(null);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void CompositeStyle_constructor_empty_roundtrip() {
        CompositeStyle style = new CompositeStyle(new Style[0]);
        Assert.assertEquals(style.getNumStyles(), 0);
    }

    @Test
    public void CompositeStyle_constructor_roundtrip() {
        Style[] styles = new Style[4];
        styles[0] = new BasicFillStyle(-1);
        styles[1] = new BasicStrokeStyle(-1, 3f);
        styles[2] = new IconPointStyle(-1, UUID.randomUUID().toString());
        styles[3] = new LabelPointStyle(UUID.randomUUID().toString(), -1, 0,
                LabelPointStyle.ScrollMode.DEFAULT);
        CompositeStyle style = new CompositeStyle(styles);
        Assert.assertEquals(styles.length, style.getNumStyles());
    }

    static CompositeStyle random() {
        Style[] styles = new Style[RandomUtils.rng().nextInt(10) + 2];
        for (int i = 0; i < styles.length; i++)
            styles[i] = StyleTestUtils.randomStyle(false);
        return new CompositeStyle(styles);
    }

}
