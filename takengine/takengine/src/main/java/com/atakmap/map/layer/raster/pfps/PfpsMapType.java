
package com.atakmap.map.layer.raster.pfps;

/**
 * @author Developer
 */
public class PfpsMapType {

    public final static int SCALE_UNIT_SCALE = 0;
    public final static int SCALE_UNIT_METER = 4;

    public final String shortName;
    public final double scale;
    public final int scaleUnits;
    public final String folderName;
    public final String productName;
    public final String seriesName;
    public final String category;

    /** Creates a new instance of MapType */
    public PfpsMapType(String shortName, double scale, int scaleUnits,
            String folderName, String productName, String seriesName,
            String category) {
        switch (scaleUnits) {
            case SCALE_UNIT_SCALE:
            case SCALE_UNIT_METER:
                break;
            default:
                throw new IllegalArgumentException("scaleUnits");
        }

        this.shortName = shortName;
        this.scale = scale;
        this.scaleUnits = scaleUnits;
        this.folderName = folderName;
        this.productName = productName;
        this.seriesName = seriesName;
        this.category = category;
    }

}
