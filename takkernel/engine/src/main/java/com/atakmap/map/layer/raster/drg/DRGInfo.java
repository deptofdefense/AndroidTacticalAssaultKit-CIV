package com.atakmap.map.layer.raster.drg;

import java.io.File;

public final class DRGInfo {
    public double minLat;
    public double minLng;
    public double maxLat;
    public double maxLng;

    public static DRGInfo parse(File file) {
        if(file == null)
            throw new NullPointerException();
        double[] bounds = new double[4];
        if(!parse(file.getName(), bounds))
            return null;
        DRGInfo drg = new DRGInfo();
        drg.minLat = bounds[0];
        drg.minLng = bounds[1];
        drg.maxLat = bounds[2];
        drg.maxLng = bounds[3];
        return drg;
    }

    private static native boolean parse(String filename, double[] bounds);
}
