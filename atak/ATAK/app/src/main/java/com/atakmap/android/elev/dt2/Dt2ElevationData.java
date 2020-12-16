
package com.atakmap.android.elev.dt2;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.AbstractElevationData;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationDataSpi;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.math.Rectangle;

public class Dt2ElevationData extends AbstractElevationData {

    public final static ElevationDataSpi SPI = new ElevationDataSpi() {
        @Override
        public ElevationData create(ImageInfo object) {
            final DtedFormat fmt = DTED_TYPES.get(object.type);
            if (fmt == null)
                return null;
            final File file = new File(object.path);
            if (!IOProviderFactory.exists(file))
                return null;
            return new Dt2ElevationData(file, fmt, object);
        }

        @Override
        public int getPriority() {
            return 0;
        }
    };

    public enum DtedFormat {
        DTED0("DTED0", ".dt0", 1000d),
        DTED1("DTED1", ".dt1", 100d),
        DTED2("DTED2", ".dt2", 30d),
        DTED3("DTED3", ".dt3", 10d);

        public final String type;
        public final String extension;
        public final double resolution;

        DtedFormat(String type, String ext, double resolution) {
            this.type = type;
            this.extension = ext;
            this.resolution = resolution;
        }
    }

    private final static Map<String, DtedFormat> DTED_TYPES = new HashMap<>();
    static {
        DTED_TYPES.put(DtedFormat.DTED0.type, DtedFormat.DTED0);
        DTED_TYPES.put(DtedFormat.DTED1.type, DtedFormat.DTED1);
        DTED_TYPES.put(DtedFormat.DTED2.type, DtedFormat.DTED2);
        DTED_TYPES.put(DtedFormat.DTED3.type, DtedFormat.DTED3);
    }

    private final File file;
    private final ImageInfo info;

    // XXX - properties on info redundant with format???

    private Dt2ElevationData(final File file, final DtedFormat fmt,
            final ImageInfo info) {
        super(MODEL_TERRAIN, fmt.type, fmt.resolution);

        this.file = file;
        this.info = info;
    }

    @Override
    public double getElevation(double lat, double lng) {
        if (!Rectangle.contains(this.info.lowerLeft.getLongitude(),
                this.info.lowerLeft.getLatitude(),
                this.info.upperRight.getLongitude(),
                this.info.upperRight.getLatitude(),
                lng, lat)) {

            return Double.NaN;
        }

        double altMSL = Dt2ElevationModel._fromDtXFile(file, lat, lng);

        // Return expects elevation in meters HAE
        return EGM96.getHAE(lat, lng, altMSL);
    }

    @Override
    public void getElevation(Iterator<GeoPoint> points, double[] elevations,
            Hints hints) {

        Dt2ElevationModel._bulkFromDtXFile(file, this.info, points, elevations,
                this.info.lowerLeft.getLatitude(),
                this.info.lowerLeft.getLongitude());

    }
}
