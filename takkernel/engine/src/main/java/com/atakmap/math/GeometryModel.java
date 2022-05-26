package com.atakmap.math;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface GeometryModel {
    PointD intersect(Ray ray);
}
