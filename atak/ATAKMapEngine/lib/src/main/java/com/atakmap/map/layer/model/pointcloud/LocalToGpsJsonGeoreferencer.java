package com.atakmap.map.layer.model.pointcloud;

import android.util.JsonReader;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.Georeferencer;
import com.atakmap.map.layer.model.ModelFileUtils;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class LocalToGpsJsonGeoreferencer implements Georeferencer {

    private static final String TAG = "LocalToGpsJsonGeoreferencer";

    public final static LocalToGpsJsonGeoreferencer INSTANCE = new LocalToGpsJsonGeoreferencer();

    @Override
    public boolean locate(ModelInfo info) {

        String uri = info.uri;
        if (uri == null) {
            return false;
        }

        File file;
        try {
            String path = uri;
            if (uri.startsWith("zip://"))
                path = uri.substring(6);
            if (FileSystemUtils.isZipPath(path))
                file = new ZipVirtualFile(path);
            else
                file = new File(path);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create file from URI: " + uri, e);
            return false;
        }

        // Look for localToGpsConversion.json next to file or one directory up
        File geoRefFile = ModelFileUtils.findHereOrAncestorFile(file.getParentFile(),
                "localToGpsConversion",
                new String[]{".json"},
                1);

        if (geoRefFile != null) {
            try(InputStream fr = IOProviderFactory.getInputStream(geoRefFile)) {
                double lat = 0.0;
                double lng = 0.0;
                double rotation = 0.0;
                double hae = 0.0;
                ModelInfo.AltitudeMode altMode = ModelInfo.AltitudeMode.Relative;

                JSONObject json = new JSONObject(FileSystemUtils.copyStreamToString(fr, true,
                        FileSystemUtils.UTF8_CHARSET));
                Iterator<String> keys = json.keys();
                while(keys.hasNext()) {
                    String name = keys.next();
                    switch (name) {
                        case "MapToGpsRotationRad":
                            rotation = json.getDouble(name);
                            break;
                        case "ZeroLatDecDeg":
                            lat = json.getDouble(name);
                            break;
                        case "ZeroLonDecDeg":
                            lng = json.getDouble(name);
                            break;
                        case "HAE":
                            hae = json.getDouble(name);
                            altMode = ModelInfo.AltitudeMode.Absolute;
                            break;
                        default:
                            break;
                    }

                    //TODO-- waiting on Altitude solution; potential pitch and roll
                }

                GeoPoint origin = new GeoPoint(lat, lng, hae);
                info.location = origin;
                info.altitudeMode = altMode;

                final double rlat = Math.toRadians(origin.getLatitude());
                final double metersLat = 111132.92 - 559.82 * Math.cos(2 * rlat) + 1.175 * Math.cos(4 * rlat);
                final double metersLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3 * rlat);

                PointD p = new PointD(0d, 0d, 0d);
                ProjectionFactory.getProjection(4326).forward(origin, p);

                // translate -> scale -> rotate
                info.localFrame = Matrix.getIdentity();
                info.localFrame.translate(p.x, p.y, p.z);
                info.localFrame.scale(1d / metersLng, 1d / metersLat, 1);
                info.localFrame.rotate(rotation, 0, 0, 1);

                info.srid = 4326;

                return true;

            } catch(Exception e) {
                Log.d(TAG, "failed to load or parse " + geoRefFile, e);
            }
        }

        return false;
    }
}
