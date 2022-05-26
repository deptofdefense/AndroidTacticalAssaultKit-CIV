package com.atakmap.map.layer.raster;

public interface PrecisionImagery {
    /** The Mensurated Product type */
    public String getType();
    /** The basic product properties (e.g. path, name, resolution, corner coords */
    public ImageInfo getInfo();
    /** The Image-to-Ground and Ground-to-Image Point Mensuration functions */
    public DatasetProjection2 getDatasetProjection();
}
