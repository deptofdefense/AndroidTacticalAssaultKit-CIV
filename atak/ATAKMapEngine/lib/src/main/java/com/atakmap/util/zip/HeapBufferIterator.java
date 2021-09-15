/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class HeapBufferIterator {
    public static BufferIterator iterator(byte[] cdeHdrBuf, int off, int length,
            ByteOrder littleEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(cdeHdrBuf);
        buffer.limit(off + length);
        buffer.position(off);
        buffer.order(littleEndian);
        return new BufferIterator(buffer);
    }
}
