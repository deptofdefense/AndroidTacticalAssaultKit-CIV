
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.style.LabelPointStyle;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class LabelPointStyleTests extends ATAKInstrumentedTest {
    @Test
    public void LabelPointStyle_constructor_1_mode_DEFAULT_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), 0);
        Assert.assertEquals(style.getLabelAlignmentY(), 0);
        Assert.assertTrue(style.getLabelRotation() == 0f);
        Assert.assertTrue(style.getScrollMode() == mode);
    }

    @Test
    public void LabelPointStyle_constructor_1_mode_ON_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.ON;
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), 0);
        Assert.assertEquals(style.getLabelAlignmentY(), 0);
        Assert.assertTrue(style.getLabelRotation() == 0f);
        Assert.assertTrue(style.getScrollMode() == mode);
    }

    @Test
    public void LabelPointStyle_constructor_1_mode_OFF_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.OFF;
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), 0);
        Assert.assertEquals(style.getLabelAlignmentY(), 0);
        Assert.assertTrue(style.getLabelRotation() == 0f);
        Assert.assertTrue(style.getScrollMode() == mode);
    }

    @Test(expected = RuntimeException.class)
    public void LabelPointStyle_constructor_1_null_text_throws() {
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        LabelPointStyle style = new LabelPointStyle(null, -1, -1, mode);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void LabelPointStyle_constructor_1_null_mode_throws() {
        final String text = UUID.randomUUID().toString();
        LabelPointStyle style = new LabelPointStyle(text, -1, -1, null);
        Assert.fail();
    }

    @Test
    public void LabelPointStyle_constructor_2_align_CC_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode, textSize, alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), alignX);
        Assert.assertEquals(style.getLabelAlignmentY(), alignY);
        Assert.assertTrue(style.getScrollMode() == mode);
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getLabelAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getLabelAlignmentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getLabelRotation(), 0.001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void LabelPointStyle_constructor_2_align_LC_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = -1;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode, textSize, alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), alignX);
        Assert.assertEquals(style.getLabelAlignmentY(), alignY);
        Assert.assertTrue(style.getScrollMode() == mode);
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getLabelAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getLabelAlignmentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getLabelRotation(), 0.001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void LabelPointStyle_constructor_2_align_RC_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 1;
        final int alignY = 0;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode, textSize, alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), alignX);
        Assert.assertEquals(style.getLabelAlignmentY(), alignY);
        Assert.assertTrue(style.getScrollMode() == mode);
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getLabelAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getLabelAlignmentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getLabelRotation(), 0.001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void LabelPointStyle_constructor_2_align_CA_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = -1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode, textSize, alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), alignX);
        Assert.assertEquals(style.getLabelAlignmentY(), alignY);
        Assert.assertTrue(style.getScrollMode() == mode);
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getLabelAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getLabelAlignmentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getLabelRotation(), 0.001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void LabelPointStyle_constructor_2_align_CB_roundtrip() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = 0;
        final int alignY = 1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        LabelPointStyle style = new LabelPointStyle(text, textColor, bgColor,
                mode, textSize, alignX, alignY, rotation, rotationAbsolute);
        Assert.assertEquals(style.getText(), text);
        Assert.assertEquals(style.getTextColor(), textColor);
        Assert.assertEquals(style.getBackgroundColor(), bgColor);
        Assert.assertEquals(style.getLabelAlignmentX(), alignX);
        Assert.assertEquals(style.getLabelAlignmentY(), alignY);
        Assert.assertTrue(style.getScrollMode() == mode);
        Assert.assertTrue(
                Math.signum(alignX) == Math.signum(style.getLabelAlignmentX()));
        Assert.assertTrue(
                Math.signum(alignY) == Math.signum(style.getLabelAlignmentY()));
        StyleTestUtils.assertAngleInDegreesEqual(rotation,
                style.getLabelRotation(), 0.001d);
        Assert.assertEquals(rotationAbsolute, style.isRotationAbsolute());
    }

    @Test
    public void LabelPointStyle_constructor_roundtrip_minResolution() {
        LabelPointStyle lps = new LabelPointStyle("Landmark Building",
                0xFFFFFFFF, 0xFF000000,
                LabelPointStyle.ScrollMode.OFF, 0f, 0, 0, 45, false, 9);
        Assert.assertEquals(9.0, lps.getLabelMinRenderResolution(), 0.001);

    }

    @Test(expected = RuntimeException.class)
    public void LabelPointStyle_constructor_2_null_text_throws() {
        LabelPointStyle style = new LabelPointStyle(null, -1, -1,
                LabelPointStyle.ScrollMode.DEFAULT, 16f, 0, 0, 0f, true);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void LabelPointStyle_constructor_2_negative_width_throws() {
        final String text = UUID.randomUUID().toString();
        LabelPointStyle style = new LabelPointStyle(text, -1, -1, null, 16f, 0,
                0, 0f, true);
        Assert.fail();
    }

    static LabelPointStyle random() {
        final String text = UUID.randomUUID().toString();
        final int textColor = RandomUtils.rng().nextInt();
        final int bgColor = RandomUtils.rng().nextInt();
        final LabelPointStyle.ScrollMode mode = LabelPointStyle.ScrollMode.DEFAULT;
        final float textSize = (RandomUtils.rng().nextFloat() * 128 + 1f);
        final int alignX = RandomUtils.rng().nextInt(2) - 1;
        final int alignY = RandomUtils.rng().nextInt(2) - 1;
        final float rotation = (RandomUtils.rng().nextFloat() * 3600 + 1f);
        final boolean rotationAbsolute = RandomUtils.rng().nextBoolean();
        return new LabelPointStyle(text, textColor, bgColor, mode, textSize,
                alignX, alignY, rotation, rotationAbsolute);
    }
}
