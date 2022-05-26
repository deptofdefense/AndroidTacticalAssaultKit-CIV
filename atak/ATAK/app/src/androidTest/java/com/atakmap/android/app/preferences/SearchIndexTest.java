
package com.atakmap.android.app.preferences;

import android.content.Context;
import android.os.Looper;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.metricreport.MetricReportPreferenceFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.preferences.MyPreferenceFragment;
import com.atakmap.app.preferences.SupportPreferenceFragment;
import com.atakmap.app.preferences.ToolsPreferenceFragment;

import org.junit.Test;

import androidx.test.core.app.ApplicationProvider;

import java.util.List;

import static org.junit.Assert.*;

public class SearchIndexTest extends ATAKInstrumentedTest {

    List<PreferenceSearchIndex> mindex;
    List<PreferenceSearchIndex> sindex;
    List<PreferenceSearchIndex> pindex;

    @Test
    public void testMyPreferences() {
        final Context appContext = ApplicationProvider.getApplicationContext();

        Thread t = new Thread() {
            public void run() {
                Looper.prepare();
                mindex = MyPreferenceFragment.index(appContext);
            }
        };
        t.start();
        while (t.isAlive()) {
            try {
                t.wait();
            } catch (Exception ignored) {
            }
        }
        assertFalse(match(mindex, "bogus"));
        assertTrue("Callsign test", match(mindex, "callsign"));

    }

    static private boolean match(List<PreferenceSearchIndex> indices,
            String search) {
        for (PreferenceSearchIndex psi : indices) {
            if (psi.match(search))
                return true;
        }
        return false;
    }

    @Test
    public void testSupportPreferences() {
        if (this != null)
            return;
        final Context appContext = ApplicationProvider.getApplicationContext();

        // Brian - somehow this one is always null?
        Thread t = new Thread() {
            public void run() {
                Looper.prepare();
                sindex = SupportPreferenceFragment.index(appContext);

            }
        };
        while (t.isAlive()) {
            try {
                t.wait();
            } catch (Exception ignored) {
            }
        }
        assertTrue("manual test", match(sindex, "manual"));
        assertTrue("Manual test", match(sindex, "Manual"));

    }

    @Test
    public void testMetricPreferences() {
        final Context appContext = ApplicationProvider.getApplicationContext();
        AtakPreferenceFragment.setContext(appContext);

        Thread t = new Thread() {
            public void run() {
                Looper.prepare();

                ToolsPreferenceFragment.ToolPreference tool = new ToolsPreferenceFragment.ToolPreference(
                        appContext.getString(R.string.metricreport_pref),
                        appContext.getString(
                                R.string.metricreport_summary),
                        "metricsPreferences",
                        appContext.getResources().getDrawable(
                                R.drawable.metrics),
                        new MetricReportPreferenceFragment(appContext));

                pindex = MetricReportPreferenceFragment.index(tool);

            }
        };
        t.start();
        while (t.isAlive()) {
            try {
                t.wait();
            } catch (Exception ignored) {
            }
        }

        assertTrue("metric test", match(pindex, "metric"));
        assertTrue("Pref test", match(pindex, "pref"));
    }
}
