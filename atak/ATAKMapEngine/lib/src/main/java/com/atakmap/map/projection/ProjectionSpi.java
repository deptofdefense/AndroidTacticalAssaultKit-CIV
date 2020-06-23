
package com.atakmap.map.projection;

public interface ProjectionSpi {
    public Projection create(int srid);
}
