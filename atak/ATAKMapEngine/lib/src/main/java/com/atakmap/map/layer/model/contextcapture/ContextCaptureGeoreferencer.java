package com.atakmap.map.layer.model.contextcapture;

import android.content.res.XmlResourceParser;
import android.util.Xml;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.Georeferencer;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import com.atakmap.util.zip.IoUtils;
import org.xmlpull.v1.XmlPullParser;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

final class ContextCaptureGeoreferencer implements Georeferencer {
    public final static Georeferencer INSTANCE = new ContextCaptureGeoreferencer();

    public static final String TAG = "ContextCaptureGeoreferencer";

    @Override
    public boolean locate(ModelInfo model) {
        File path = locateMetadataFile(model.uri);
        if(path == null)
            return false;

        XmlPullParser parser = null;
        InputStream stream = null;
        try {
            if(path instanceof  ZipVirtualFile)
                stream = ((ZipVirtualFile)path).openStream();
            else
                stream = IOProviderFactory.getInputStream(path);
            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setFeature(Xml.FEATURE_RELAXED, true);
            parser.setInput(stream, null);

            /*
            <ModelMetadata version="1">
    <!--Spatial Reference System-->
    <SRS>ENU:35.76484,-120.76987</SRS>
    <!--Origin in Spatial Reference System-->
    <SRSOrigin>0,0,0</SRSOrigin>
    <Texture>
        <ColorSource>Visible</ColorSource>
    </Texture>
</ModelMetadata>
             */
            int eventType;
            String version = null;
            String srs = null;
            String srsOrigin = null;
            do {
                eventType = parser.nextToken();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equalsIgnoreCase("ModelMetadata"))
                            version = parser.getAttributeValue(null, "version");
                        else if (parser.getName().equalsIgnoreCase("SRS"))
                            srs = parser.nextText();
                        else if (parser.getName().equalsIgnoreCase("SRSOrigin"))
                            srsOrigin = parser.nextText();
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        break;
                    case XmlPullParser.ENTITY_REF:
                        throw new IOException("Entity Reference Error");
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

            if(version == null || srs == null)
                return false;
            try {
                int v = Integer.parseInt(version);
                if(v != 1)
                    return false;
            } catch(NumberFormatException e) {
                return false;
            }
            if(!srs.matches("ENU\\:[\\+\\-]?\\d+(\\.\\d+)?\\,\\s*[\\+\\-]?\\d+(\\.\\d+)?"))
                return false;
            String[] ll = srs.substring(4).split(",");
            GeoPoint origin = new GeoPoint(Double.parseDouble(ll[0]), Double.parseDouble(ll[1]));


            PointD tx = new PointD(0d, 0d, 0d);
            if(srsOrigin != null && srsOrigin.matches("[\\+\\-]?\\d+(\\.\\d+)?\\,[\\+\\-]?\\d+(\\.\\d+)?\\,[\\+\\-]?\\d+(\\.\\d+)?")) {
                String[] xyz = srsOrigin.split(",");
                tx.x = Double.parseDouble(xyz[0]);
                tx.y = Double.parseDouble(xyz[1]);
                tx.z = Double.parseDouble(xyz[2]);
            }


            model.srid = 4326;
            model.location = origin;
            model.altitudeMode = ModelInfo.AltitudeMode.Absolute;

            // compute local degrees to meters
            final double metersLat = GeoCalculations.approximateMetersPerDegreeLatitude(origin.getLatitude());
            final double metersLng = GeoCalculations.approximateMetersPerDegreeLongitude(origin.getLatitude());

            PointD p = new PointD(0d, 0d, 0d);
            ProjectionFactory.getProjection(4326).forward(origin, p);

            model.localFrame = Matrix.getIdentity();
            model.localFrame.translate(p.x, p.y, p.z);
            model.localFrame.scale(1d/metersLng, 1d/metersLat, 1d);
            model.localFrame.translate(tx.x, tx.y, tx.z);

            return true;
        } catch(Throwable t) {
            Log.e(TAG, "error", t);
            return false;
        } finally {
            IoUtils.close(stream);
            if(parser instanceof XmlResourceParser)
                IoUtils.close((Closeable) parser);
        }
    }

    public static File locateMetadataFile(String objPath) {
        File datasetDir = getDatasetDir(objPath);
        if(datasetDir == null)
            return null;
        if(datasetDir instanceof  ZipVirtualFile)
            return new ZipVirtualFile(datasetDir, "metadata.xml");
        else
            return new File(datasetDir, "metadata.xml");
    }

    public static String getDatasetName(String objPath) {
        File datasetDir = getDatasetDir(objPath);
        if(datasetDir == null)
            return null;
        return datasetDir.getName();
    }

    static File getDatasetDir(String objPath) {
        File f = new File(objPath);
        f = f.getParentFile();
        if(f == null)
            return null;
        if(!f.getName().matches("Tile_[\\+\\-]\\d+_[\\+\\-]\\d+"))
            return null;
        f = f.getParentFile();
        // parent file may be "Data" for unsegmented or some arbitrary name for segmented
        f = f.getParentFile();

        f = new File(f, "metadata.xml");
        if(FileSystemUtils.isZipPath(f)) {
            try {
                f = new ZipVirtualFile(f);
            } catch(Throwable ignored) {}
        }
        if(!IOProviderFactory.exists(f))
            return null;
        return f.getParentFile();
    }

}
