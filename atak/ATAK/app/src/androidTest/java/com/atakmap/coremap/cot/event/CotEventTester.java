
package com.atakmap.coremap.cot.event;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class CotEventTester extends ATAKInstrumentedTest {

    @Test
    public void invalid() {
        CotEvent ce = null;
        try {
            ce = CotEvent.parse(getInvalid1());
        } catch (Exception ignored) {
        }
        assertFalse(ce.isValid());

        try {
            ce = CotEvent.parse(getInvalid2());
        } catch (Exception ignored) {
        }
        assertFalse(ce.isValid());

        // test permissive (invalid case)
        try {
            ce = CotEvent.parse(getInvalid3());
        } catch (Exception ignored) {
        }
        assertTrue(ce.isValid());

    }

    @Test
    public void valid() {
        CotEvent ce = null;

        ce = CotEvent.parse(getValid1());
        assertEquals("904a7ff3-fe24-4809-86fd-0d486444a809", ce.getUID());

        ce = CotEvent.parse(getValid2());
        assertEquals("904a7ff3-fe24-4809-86fd-0d486444a809", ce.getUID());
    }

    private String getInvalid1() {
        return "invalid";
    }

    private String getInvalid2() {
        return null;
    }

    private String getInvalid3() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<event version=\"2.0\" uid=\"904a7ff3-fe24-4809-86fd-0d486444a809\" type=\"u-d-p\"\n"
                +
                "how=\"h-e\" time=\"2014-10-29T02:40:00.677Z\" start=\"2014-10-29T02:40:00.677Z\"\n"
                +
                "stale=\"2014-10-30T02:40:00.677Z\"><point ce=\"9999999.0\" le=\"9999999.0\"\n"
                +
                "hae=\"9999999.0\" lat=\"41.11903\" lon=\"-75.42835\"/><detail><contact\n"
                +
                "callsign=\"BCI\"/><color value=\"-16711681\"/><precisionlocation geopointsrc=\"???\"\n"
                +
                "altsrc=\"???\"/><_flow-tags_ marti1=\"2014-10-28T22:40:15.341Z\"/></detail>\n";
    }

    private String getValid1() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<event version=\"2.0\" uid=\"904a7ff3-fe24-4809-86fd-0d486444a809\" type=\"u-d-p\"\n"
                +
                "how=\"h-e\" time=\"2014-10-29T02:40:00.677Z\" start=\"2014-10-29T02:40:00.677Z\"\n"
                +
                "stale=\"2014-10-30T02:40:00.677Z\"><point ce=\"9999999.0\" le=\"9999999.0\"\n"
                +
                "hae=\"9999999.0\" lat=\"41.11903\" lon=\"-75.42835\"/><detail><contact\n"
                +
                "callsign=\"BCI\"/><color value=\"-16711681\"/><precisionlocation geopointsrc=\"???\"\n"
                +
                "altsrc=\"???\"/><_flow-tags_ marti1=\"2014-10-28T22:40:15.341Z\"/></detail></event>\n";
    }

    private String getValid2() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<event version=\"2.0\" uid=\"904a7ff3-fe24-4809-86fd-0d486444a809\" type=\"u-d-p\"\n"
                +
                "how=\"h-e\" time=\"2014-10-29T02:40:00.677Z\" start=\"2014-10-29T02:40:00.677Z\"\n"
                +
                "stale=\"2014-10-30T02:40:00.677Z\"><point ce=\"9999999.0\" le=\"9999999.0\"\n"
                +
                "hae=\"9999999.0\" lat=\"41.11903\" lon=\"-75.42835\"/></event>";

    }

}
