package gov.tak.api.engine.math;

import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

public final class PointD {
    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null) return null;
                return (T)new PointD(
                        ((com.atakmap.math.PointD)in).x,
                        ((com.atakmap.math.PointD)in).y,
                        ((com.atakmap.math.PointD)in).z
                );
            }
        }, PointD.class, com.atakmap.math.PointD.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null) return null;
                return (T)new com.atakmap.math.PointD(
                        ((PointD)in).x,
                        ((PointD)in).y,
                        ((PointD)in).z
                );
            }
        }, com.atakmap.math.PointD.class, PointD.class);
    }
    public double x;
    public double y;
    public double z;

    public PointD() {
        this(0d, 0d, 0d);
    }

    public PointD(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PointD(PointD other) {
        this(other.x, other.y, other.z);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof PointD))
            return false;
        final PointD other = (PointD)o;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }
}
