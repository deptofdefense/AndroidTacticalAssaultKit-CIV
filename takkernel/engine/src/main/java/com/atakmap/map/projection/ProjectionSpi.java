
package com.atakmap.map.projection;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface ProjectionSpi {
    public Projection create(int srid);
}
