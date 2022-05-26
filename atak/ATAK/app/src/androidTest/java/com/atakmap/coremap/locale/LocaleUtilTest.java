
package com.atakmap.coremap.locale;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class LocaleUtilTest extends ATAKInstrumentedTest {

    @Test
    public void LocaleUtilTest_setLocale() {
        Locale arabic = Locale.forLanguageTag("ar");
        LocaleUtil.setLocale(arabic);
        assertSame(LocaleUtil.getCurrent(), arabic);
        assertTrue(LocaleUtil.isRTL());
    }

    @Test
    public void LocaleUtilTest_getDecimalFormat() {

        NumberFormat nf = LocaleUtil.getDecimalFormat("#0.000");
        assertEquals("3.146", nf.format(3.145678d));

    }

    @Test
    public void LocaleUtilTest_getNaturalNumber() {

        assertEquals("1234.5", LocaleUtil.getNaturalNumber("١٢٣٤.٥"));
    }
}
