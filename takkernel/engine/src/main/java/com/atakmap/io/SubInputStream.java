
package com.atakmap.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SubInputStream extends FilterInputStream {
    private long remaining;

    public SubInputStream(InputStream in, long limit) {
        super(in);

        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (this.remaining == 0)
            return -1;
        final int retval = super.read();
        this.remaining--;
        return retval;
    }

    @Override
    public int read(final byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        if (this.remaining == 0L && length > 0)
            return -1;
        else if (this.remaining == 0L && length == 0)
            return 0;
        if (length > this.remaining)
            length = (int) this.remaining;
        final int retval = super.read(buf, offset, length);
        if (retval < 0)
            this.remaining = 0L;
        else
            this.remaining -= retval;
        return retval;
    }

    @Override
    public int available() throws IOException {
        int retval = super.available();
        if (retval > this.remaining)
            retval = (int) this.remaining;
        return retval;
    }

    @Override
    public long skip(long n) throws IOException {
        if (this.remaining == 0L)
            return 0L;
        if (n > this.remaining)
            n = this.remaining;
        final long retval = super.skip(n);
        this.remaining -= retval;
        return retval;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readLimit) {
    }

    @Override
    public void reset() {
    }

}
