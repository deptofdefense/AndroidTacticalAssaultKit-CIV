package com.atakmap.map;

import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;

public class MapCamera {

    /**
     * The projection matrix.
     */
    public Matrix projection;
    
    /**
     * The model-view matrix.
     */
    public Matrix modelView;
    
    /**
     * The camera's location, in model coordinates.
     */
    public PointD location;
    
    /**
     * The camera's pointing target, in model coordinates.
     */
    public PointD target;
    
    /**
     * The roll angle, in degrees along the vector between {@link #location} and
     * {@link #target}.
     */
    public double roll;

    public double elevation;
    public double azimuth;

    public double fov;
    public double aspectRatio;
    public double near;
    public double far;

    public boolean perspective;
}
