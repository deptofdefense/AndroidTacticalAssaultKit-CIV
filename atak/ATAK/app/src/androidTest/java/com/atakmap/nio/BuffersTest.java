
package com.atakmap.nio;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public class BuffersTest extends ATAKInstrumentedTest {
    private final int TEST_SIZE = 1024 * 1024 * 64;

    @org.junit.Test
    public void skip() {
        ByteBuffer b = ByteBuffer.allocate(TEST_SIZE);

        Buffers.skip(b, 100);
        assert (b.position() == 100);

    }

    @org.junit.Test
    public void shift() {
        ByteBuffer b = ByteBuffer.allocate(TEST_SIZE);

        b.put(0, (byte) 67);
        Buffers.shift(b);
        assert (b.position() == 0);
        b.position(1);
        assert (b.get() == (byte) 67);

        b = ByteBuffer.allocateDirect(TEST_SIZE);
        b.put(0, (byte) 68);
        Buffers.shift(b);
        Buffers.shift(b);
        assert (b.position() == 0);
        b.position(2);
        assert (b.get() == (byte) 68);

    }
}
