package com.atakmap.math;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class Ray {
    public PointD origin;
    public Vector3D direction;

    public Ray(PointD origin, Vector3D direction)
    {
        this.origin=origin;
        this.direction=direction.normalize();
    }

    public String toString()
    {
        return origin.toString()+" | "+direction.toString();
    }
}
