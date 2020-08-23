package com.atakmap.map.layer.model.pointcloud;

import android.util.JsonReader;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.Georeferencer;
import com.atakmap.map.layer.model.ModelFileUtils;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
            if (path.toLowerCase().contains(".zip"))
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
            JsonReader jsonReader = null;
            FileReader fr = null;
            try {
                double lat = 0.0;
                double lng = 0.0;
                double rotation = 0.0;
                double hae = 0.0;
                ModelInfo.AltitudeMode altMode = ModelInfo.AltitudeMode.Relative;

                jsonReader = new JsonReader(fr = new FileReader(geoRefFile));
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    switch (name) {
                        case "MapToGpsRotationRad":
                            rotation = jsonReader.nextDouble();
                            break;
                        case "ZeroLatDecDeg":
                            lat = jsonReader.nextDouble();
                            break;
                        case "ZeroLonDecDeg":
                            lng = jsonReader.nextDouble();
                            break;
                        case "HAE":
                            hae = jsonReader.nextDouble();
                            altMode = ModelInfo.AltitudeMode.Absolute;
                            break;
                        default:
                            jsonReader.skipValue();
                            break;
                    }

                    //TODO-- waiting on Altitude solution; potential pitch and roll
                }
                jsonReader.endObject();

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
            } finally {
                if (jsonReader != null) {
                    try {
                        jsonReader.close();
                    } catch (IOException e) {
                        Log.d(TAG, "failed to close " + geoRefFile, e);
                    }
                }
                if (fr != null) {
                    try {
                        fr.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        return false;
    }
}
