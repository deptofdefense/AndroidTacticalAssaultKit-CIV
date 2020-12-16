
package com.atakmap.android.net;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class AtakAuthenticationDatabaseTest extends ATAKInstrumentedTest {

    @Test
    public void storeAndRetreive() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        AtakAuthenticationCredentials aac = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.test");
        assertNull(aac);

        AtakAuthenticationDatabase.initialize(appContext);
        AtakAuthenticationDatabase.clear();
        AtakAuthenticationDatabase.dispose();

        AtakAuthenticationDatabase.saveCredentials(
                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                "com.atakmap.app.test", "apple", "core",
                false);

        aac = AtakAuthenticationDatabase.getCredentials(
                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                "com.atakmap.app.test");
        assertNull(aac);

        AtakAuthenticationDatabase.initialize(appContext);

        AtakAuthenticationDatabase.saveCredentials(
                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                "com.atakmap.app.test", "apple", "core",
                false);

        aac = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.test");
        assertEquals("core", aac.password);
        assertEquals("apple", aac.username);

        AtakAuthenticationCredentials aac1 = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.test1");
        assertFalse(aac1.password.equals("core"));
        assertFalse(aac1.username.equals("apple"));

        AtakAuthenticationDatabase.saveCredentials(
                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                "com.atakmap.app.test", "apple1", "core1",
                false);

        AtakAuthenticationCredentials aac2 = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.test1");
        assertFalse(aac2.password.equals("core"));
        assertFalse(aac2.username.equals("apple"));

    }

}
