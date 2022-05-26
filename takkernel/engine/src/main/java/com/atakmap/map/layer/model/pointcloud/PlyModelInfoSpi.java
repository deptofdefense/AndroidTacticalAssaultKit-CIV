package com.atakmap.map.layer.model.pointcloud;

import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.map.layer.model.SingleFileModelInfoSpi;

public class PlyModelInfoSpi extends SingleFileModelInfoSpi {
    static final String TAG = "PlyModelInfoSpi";
    public static final ModelInfoSpi INSTANCE = new PlyModelInfoSpi();
    public static final String TYPE = "PLY";

    public PlyModelInfoSpi() {
        super("POINTCLOUD", 1, "PLY", "ply");
    }
}
