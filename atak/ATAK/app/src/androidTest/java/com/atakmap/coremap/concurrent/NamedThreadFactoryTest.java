
package com.atakmap.coremap.concurrent;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import static org.junit.Assert.assertTrue;

public class NamedThreadFactoryTest extends ATAKInstrumentedTest {

    @org.junit.Test
    public void newThread() {
        NamedThreadFactory ntf = new NamedThreadFactory("test");

        Thread t = ntf.newThread(new Runnable() {
            @Override
            public void run() {
                //
            }
        });
        assertTrue(t.getName().startsWith("test"));

    }
}
