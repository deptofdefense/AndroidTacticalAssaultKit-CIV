
package com.atakmap.coremap.log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LogTest extends ATAKInstrumentedTest {
    final static String TAG = "LogTest";
    final static String MSG = "This is a Test";
    final static Exception ERROR = new Exception("error");

    @org.junit.Test
    public void unregisterLogListener() {
        Log.LogListener ll = new Log.LogListener() {
            @Override
            public void write(String tag, String msg, Throwable tr) {
            }
        };
        Log.registerLogListener(ll);
        Log.unregisterLogListener(ll);
    }

    @org.junit.Test
    public void nullLoggerTest() {
        Log.registerLogListener(null);
    }

    @org.junit.Test
    public void errorLoggerTest() {
        Log.LogListener ll = new Log.LogListener() {
            @Override
            public void write(String tag, String msg, Throwable tr) {
                throw new IllegalArgumentException("error with the logger");
            }
        };
        Log.registerLogListener(ll);
        Log.d(TAG, "test");
    }

    public void begin(final StringBuffer sb) {
        Log.registerLogListener(new Log.LogListener() {
            @Override
            public void write(String tag, String msg, Throwable tr) {
                sb.append(tag).append(":").append(msg).append(tr);
            }
        });
    }

    public void test(final StringBuffer sb) {
        String expected = sb.append(TAG + ":" + MSG + "").append(ERROR)
                .toString();
        assert (sb.toString().equals(expected));
    }

    @org.junit.Test
    public void v() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.v(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void v1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.v(TAG, MSG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void d() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.d(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void d1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.d(TAG, MSG, ERROR);
    }

    @org.junit.Test
    public void i() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.i(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void i1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.i(TAG, MSG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void w() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.w(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void w1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.w(TAG, MSG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void w2() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.w(TAG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void e() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.e(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void e1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.e(TAG, MSG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void wtf() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.wtf(TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void wtf1() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.wtf(TAG, MSG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void wtf2() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.wtf(TAG, ERROR);
        test(sb);
    }

    @org.junit.Test
    public void println() {
        StringBuffer sb = new StringBuffer();
        begin(sb);
        Log.println(Log.DEBUG, TAG, MSG);
        test(sb);
    }

    @org.junit.Test
    public void nullTest() {
        Log.d(TAG, null);
        Log.d(null, null);
    }

}
