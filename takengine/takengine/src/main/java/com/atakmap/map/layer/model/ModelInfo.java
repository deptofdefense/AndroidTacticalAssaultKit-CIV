package com.atakmap.map.layer.model;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.util.Map;

public class ModelInfo {
    public enum AltitudeMode {
        /**
         * Altitude values are relative to the surface
         */
        Relative,
        /**
         * Altitude values are absolute, meters HAE
         */
        Absolute,
    }


    public double minDisplayResolution = Double.NaN;
    public double maxDisplayResolution = Double.NaN;
    public String uri = null;
    public GeoPoint location = null;
    public PointD originOffset = null;
    /**
     * Returns the Spatial Reference ID (EPSG code) for the position vertex
     * data of the model. If a value of <code>-1</code> is returned, the model
     * is not georeferenced.
     */
    public int srid = -1;
    public String name = null;
    public String type = null;

    /**
     * Returns the local frame matrix used to convert vertex position data into
     * the Spatial Reference. If <code>null</code>, the vertex position data is
     * already in the Spatial Reference Coordinate System and no local frame is
     * used.
     */
    public Matrix localFrame = null;

    /**
     * Rotation and scale applied to the model's local frame
     * For use with tools which utilize this data separately from the local frame
     * Rotation {x = tilt, y = heading, z = roll}
     */
    public PointD rotation = null;
    public PointD scale = null;

    public AltitudeMode altitudeMode = AltitudeMode.Absolute;

    /**
     * Aliasing table for model resources (e.g. texture paths)
     */
    public Map<String, String> resourceMap = null;

    public ModelInfo() {

    }

    public ModelInfo(ModelInfo other) {
        this.location = other.location;
        if(other.localFrame != null) {
            this.localFrame = Matrix.getIdentity();
            this.localFrame.set(other.localFrame);
        }
        if (other.originOffset != null)
            this.originOffset = new PointD(other.originOffset);
        if (other.rotation != null)
            this.rotation = new PointD(other.rotation);
        if (other.scale != null)
            this.scale = new PointD(other.scale);
        this.altitudeMode = other.altitudeMode;
        this.srid = other.srid;
        this.name = other.name;
        this.uri = other.uri;
        this.maxDisplayResolution = other.maxDisplayResolution;
        this.minDisplayResolution = other.minDisplayResolution;
        this.type = other.type;
    }
}
