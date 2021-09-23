
package com.atakmap.interop;

public final class Pointer {
    public final static int RAW = 0;
    public final static int SHARED = 1;
    public final static int UNIQUE = 2;
    public final static int REFERENCE = 3;

    public final static Pointer NULL = new Pointer(0L, 0L, RAW, 0L);

    public long value;
    public long raw;
    public int type;
    private long deleter;

    Pointer(long value, long raw, int type, long deleter) {
        this.value = value;
        this.raw = raw;
        this.type = type;
        this.deleter = deleter;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Pointer))
            return false;
        final Pointer other = (Pointer) o;
        return (other.raw == this.raw);
    }

    @Override
    public int hashCode() {
        return (int) (this.raw ^ (this.raw >>> 32));
    }
}
