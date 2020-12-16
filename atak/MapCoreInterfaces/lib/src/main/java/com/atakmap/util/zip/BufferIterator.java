/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

import java.nio.ByteBuffer;

final class BufferIterator {
    ByteBuffer buffer;
    final int baseOff;

    public BufferIterator(ByteBuffer buffer) {
        this.buffer = buffer;
        this.baseOff = this.buffer.position();
    }

    public int readInt() {
        return this.buffer.getInt();
    }

    public void seek(int i) {
        this.buffer.position(this.baseOff + i);
    }

    public short readShort() {
        return this.buffer.getShort();
    }

    public void skip(int i) {
        this.buffer.position(this.buffer.position() + i);
    }
}
