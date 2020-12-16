
package com.atakmap.spatial.file;

import android.content.Context;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.http.HttpUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.FeatureDataSource;

import java.io.File;

public class GeoJsonFileDatabase extends FileDatabase {
    private static final String TAG = "GeoJsonFileDatabase";

    public static final String GEOJSON_FILE_MIME_TYPE = HttpUtil.MIME_JSON;
    public static final File GEOJSON_DIRECTORY = FileSystemUtils
            .getItem("geojson");
    private static final String GEOJSON_CONTENT_TYPE = "GeoJSON";
    private static final String ICON_PATH = "asset://icons/geojson.png";

    public GeoJsonFileDatabase(Context context, MapView view) {
        super(DATABASE_FILE, context, view);
    }

    @Override
    public boolean accept(File file) {
        // XXX - extension ???
        //String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
        return IOProviderFactory.isFile(file);
    }

    @Override
    public File getFileDirectory() {
        return GEOJSON_DIRECTORY;
    }

    @Override
    public String getFileMimeType() {
        return GEOJSON_FILE_MIME_TYPE;
    }

    @Override
    protected String getIconPath() {
        return ICON_PATH;
    }

    @Override
    protected void processFile(File file, MapGroup fileGrp) {
        insertFromGeoJsonFile(file, fileGrp);
    }

    private void insertFromGeoJsonFile(File geoJsonFile, MapGroup fileGrp) {
        Log.w(TAG, "GeoJSON is not yet supported.");

        // faked data

        String name = "geojson";

        String wktGeom = "POINT(" + "97.03125" + " " + "39.7265625" + ")";

        FeatureDataSource.FeatureDefinition feature = new FeatureDataSource.FeatureDefinition();
        feature.name = name;
        feature.rawGeom = wktGeom;
        feature.geomCoding = FeatureDataSource.FeatureDefinition.GEOM_WKT;

        createMapItem(feature, fileGrp);
        Log.d(TAG, "Got GeoJSON: (" + name + ")");
    }

    @Override
    public String getContentType() {
        return GEOJSON_CONTENT_TYPE;
    }
}
