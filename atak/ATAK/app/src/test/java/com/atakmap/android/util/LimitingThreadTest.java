
package com.atakmap.android.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class LimitingThreadTest {
    int count;

    @Test
    public void exec1() {
        count = 0;
        LimitingThread lt = new LimitingThread("LimitingThreadTest",
                new Runnable() {
                    @Override
                    public void run() {
                        count++;
                    }
                });
        lt.exec();
        try {
            Thread.sleep(1000);
        } catch (Exception ignored) {
        }
        lt.exec();
        lt.dispose(true);
        assertTrue(count != 0);
    }

    @Test
    public void exec2() {
        count = 0;
        LimitingThread lt = new LimitingThread("LimitingThreadTest",
                new Runnable() {
                    @Override
                    public void run() {
                        count++;
                        try {
                            Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                    }
                });
        lt.dispose();
        lt.exec(); // should not run
        lt.exec(); // should not run
        lt.exec(); // should not run
        lt.exec(); // should not run
        lt.exec(); // should not run
        lt.dispose(true);
        assertEquals(0, count);
    }

}
