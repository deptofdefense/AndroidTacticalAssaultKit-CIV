package com.atakmap.map.layer.raster;

import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;

public class ProjectiveTransformProjection2 extends ProjectiveTransformProjectionBase {
    public ProjectiveTransformProjection2(Matrix img2geo) {
        super(-1);
        
        try {
            this.init(img2geo, img2geo.createInverse());
        } catch(NoninvertibleTransformException e) {
            throw new IllegalArgumentException("img2geo cannot be inverted.");
        }
    }
    
    public ProjectiveTransformProjection2(int srid, Matrix img2proj) {
        super(srid);
        
        try {
            this.init(img2proj, img2proj.createInverse());
        } catch(NoninvertibleTransformException e) {
            throw new IllegalArgumentException("img2geo cannot be inverted.");
        }
    }
}
