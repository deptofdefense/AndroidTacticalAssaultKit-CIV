
package com.atakmap.map.layer.feature.ogr;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gdal.ogr.Driver;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import com.atakmap.coremap.log.Log;
import android.util.Pair;

import com.atakmap.interop.Pointer;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.NativeFeatureDataSource;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.math.MathUtils;

/**
 * Support ingesting ESRI Shapefiles
 */
public final class OgrFeatureDataSource extends NativeFeatureDataSource {

    private static final String TAG = "OgrFeatureDataSource";

    private final static String PROVIDER_NAME = "ogr";

    public final static String SYS_PROP_DEFAULT_STROKE_WIDTH = "TAK.Engine.Feature.DefaultDriverDefinition2.defaultStrokeWidth";
    public final static String SYS_PROP_DEFAULT_STROKE_COLOR = "TAK.Engine.Feature.DefaultDriverDefinition2.defaultStrokeColor";
    public final static String SYS_PROP_DEFAULT_ICON_URI = "TAK.Engine.Feature.DefaultDriverDefinition2.defaultIconUri";

    public final static int GEOMETRY = 1;
    public final static int GEOMETRY_COLLECTION = 2;

    public static boolean metadataEnabled = false;

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    public OgrFeatureDataSource() {
        super(create());
        if(!initialized.getAndSet(true))
            registerDrivers();
    }

    static native Pointer create();

    public static AttributeSet ogr2attr(org.gdal.ogr.Feature feature) {
        AttributeSet retval = new AttributeSet();
        for(int i = 0; i < feature.GetFieldCount(); i++) {
            FieldDefn def = feature.GetFieldDefnRef(i);
            if (def != null) {
                if(def.GetFieldType() == ogrConstants.OFTInteger) {
                    retval.setAttribute(def.GetName(), feature.GetFieldAsInteger(i));
                } else if(def.GetFieldType() == ogrConstants.OFTInteger64) {
                    retval.setAttribute(def.GetName(), feature.GetFieldAsInteger64(i));
                } else if(def.GetFieldType() == ogrConstants.OFTReal) {
                    retval.setAttribute(def.GetName(), feature.GetFieldAsDouble(i));
                } else if(def.GetFieldType() == ogrConstants.OFTString) {
                    retval.setAttribute(def.GetName(), feature.GetFieldAsString(i));
                }
            }
        }
        return retval;
    }

    private static native void registerDrivers();
}
