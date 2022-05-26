
package com.atakmap.android.ipc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AtakBroadcastTest extends ATAKInstrumentedTest {

    final String ACTION = "ataktestaction";
    final String DESCRIPTION = "this is a test";

    boolean local_register_success = false;
    boolean system_register_success = false;

    @Test
    public void register() {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                local_register_success = true;
            }
        };

        BroadcastReceiver systembr = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                system_register_success = true;
            }
        };

        Context appContext = ApplicationProvider.getApplicationContext();
        AtakBroadcast.init(appContext);

        AtakBroadcast.DocumentedIntentFilter intentFilter = new AtakBroadcast.DocumentedIntentFilter(
                ACTION, DESCRIPTION);

        assertEquals(intentFilter.getDocumentation(ACTION).description,
                DESCRIPTION);

        AtakBroadcast.getInstance().registerReceiver(br, intentFilter);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(ACTION));

        try {
            Thread.sleep(250);
        } catch (Exception ignored) {
        }

        assertTrue(local_register_success);
        assertFalse(system_register_success);

        local_register_success = false;

        AtakBroadcast.getInstance().unregisterReceiver(br);

        AtakBroadcast.getInstance().registerSystemReceiver(systembr,
                intentFilter);

        AtakBroadcast.getInstance().sendBroadcast(new Intent(ACTION));

        assertFalse(local_register_success);

        AtakBroadcast.getInstance().sendSystemBroadcast(new Intent(ACTION));

        try {
            Thread.sleep(250);
        } catch (Exception ignored) {
        }

        assertTrue(system_register_success);
    }

}
