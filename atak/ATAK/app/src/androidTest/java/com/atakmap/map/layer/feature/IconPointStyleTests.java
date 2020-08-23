
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.IconPointStyle;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class IconPointStyleTests extends ATAKInstrumentedTest {

    @Test
    public void IconPointStyle_constructor_1_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        IconPointStyle style = new IconPointStyle(color, uri);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
    }

    @Test(expected = RuntimeException.class)
    public void IconPointStyle_constructor_1_null_uri_throws() {
        final String uri = null;
        final int color = RandomUtils.rng().nextInt();
        IconPointStyle style = new IconPointStyle(color, uri);
        Assert.fail();
    }

    @Test
    public void IconPointStyle_constructor_2_align_CC_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        IconPointStyle style = new IconPointStyle(color, uri, width, height,
                alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
        Assert.assertTrue(width == style.getIconWidth());
        Assert.assertTrue(height == style.getIconHeight());
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getIconAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getIconAligmnentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getIconRotation(), 0.00001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void IconPointStyle_constructor_2_align_LC_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = -1;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        IconPointStyle style = new IconPointStyle(color, uri, width, height,
                alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
        Assert.assertTrue(width == style.getIconWidth());
        Assert.assertTrue(height == style.getIconHeight());
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getIconAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getIconAligmnentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getIconRotation(), 0.00001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void IconPointStyle_constructor_2_align_RC_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 1;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        IconPointStyle style = new IconPointStyle(color, uri, width, height,
                alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
        Assert.assertTrue(width == style.getIconWidth());
        Assert.assertTrue(height == style.getIconHeight());
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getIconAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getIconAligmnentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getIconRotation(), 0.00001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void IconPointStyle_constructor_2_align_CA_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = -1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        IconPointStyle style = new IconPointStyle(color, uri, width, height,
                alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
        final float iw = style.getIconWidth();
        Assert.assertTrue(width == style.getIconWidth());
        Assert.assertTrue(height == style.getIconHeight());
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getIconAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getIconAligmnentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getIconRotation(), 0.00001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void IconPointStyle_constructor_2_align_CB_roundtrip() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = 1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        IconPointStyle style = new IconPointStyle(color, uri, width, height,
                alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getColor(), color);
        Assert.assertEquals(style.getIconUri(), uri);
        Assert.assertTrue(width == style.getIconWidth());
        Assert.assertTrue(height == style.getIconHeight());
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getIconAlignmentX()));
        final int ay = style.getIconAligmnentY();
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getIconAligmnentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getIconRotation(), 0.00001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test(expected = RuntimeException.class)
    public void IconPointStyle_constructor_2_null_uri_throws() {
        IconPointStyle style = new IconPointStyle(-1, null, 0f, 0f, 0, 0, 0f,
                true);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void IconPointStyle_constructor_2_negative_width_throws() {
        final String uri = UUID.randomUUID().toString();
        IconPointStyle style = new IconPointStyle(-1, uri, -1f, 0f, 0, 0, 0f,
                true);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void IconPointStyle_constructor_2_negative_height_throws() {
        final String uri = UUID.randomUUID().toString();
        IconPointStyle style = new IconPointStyle(-1, uri, 0f, -1f, 0, 0, 0f,
                true);
        Assert.fail();
    }

    static IconPointStyle random() {
        final String uri = UUID.randomUUID().toString();
        final int color = RandomUtils.rng().nextInt();
        final float width = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final float height = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = RandomUtils.rng().nextInt(3) - 1;
        final int alignY = RandomUtils.rng().nextInt(3) - 1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        return new IconPointStyle(color, uri, width, height, alignX, alignY,
                rotation, rotationAbsolute);
    }
}
