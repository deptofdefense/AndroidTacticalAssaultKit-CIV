
package com.atakmap.android.track;

import android.content.Context;
import android.util.Xml;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLConversion;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Schema;
import com.ekito.simpleKML.model.SchemaData;
import com.ekito.simpleKML.model.SimpleArrayData;
import com.ekito.simpleKML.model.SimpleField;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.TimePrimitive;
import com.ekito.simpleKML.model.TimeSpan;
import com.ekito.simpleKML.model.TimeStamp;
import com.ekito.simpleKML.model.Track;

import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SimpleKML appears to work well as a complete KML v2.2 data model, but the serialization is slow
 * for many files or many KML elements. Support for efficiently reading/writing KML track log data,
 * using the SimpleKML data model
 * 
 * 
 */
public class TrackLogKMLSerializer {

    protected static final String TAG = "TrackLogKMLSerializer";
    private static final String KML_GX_NAMESPACE = "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">";

    /**
     * Simply pull out all the Placemarks with their style
     * 
     * 
     */
    private static class TrackLogFeatureWriter implements
            FeatureHandler<Placemark> {

        final File _file;
        final List<Placemark> tracks;
        final List<Placemark> checkpoints;

        public TrackLogFeatureWriter(File file) {
            _file = file;
            tracks = new ArrayList<>();
            checkpoints = new ArrayList<>();
        }

        @Override
        public boolean process(Placemark placemark) {
            if (placemark == null) {
                Log.w(TAG,
                        "Unable to parse Placemark to write track: "
                                + _file.getName());
                return false;
            }

            Geometry geom = KMLUtil.getFirstGeometry(placemark, Geometry.class);
            if (geom == null) {
                Log.w(TAG,
                        "Unable to parse Placemark to write track: "
                                + _file.getName());
                return false;
            }

            if (geom instanceof Track)
                tracks.add(placemark);
            else
                checkpoints.add(placemark);

            // process all Placemarks, so return false
            return false;
        }

        public boolean hasPlacemarks() {
            return !FileSystemUtils.isEmpty(tracks)
                    || !FileSystemUtils.isEmpty(checkpoints);
        }
    }

    /**
     * Write the specified track log out to the specified file
     * 
     * @param trackLog may contain one or more LineString placemarks
     * @param file
     * @throws Exception
     */
    public static boolean write(Context context,
            final Kml trackLog,
            final File file)
            throws Exception {
        // this works but is slow... so instead we use PullParser
        // com.ekito.simpleKml.Serializer.write(trackLog, file);

        // one or more Placemark per KML (currently one for a TrackLog, or possibly more for Self
        // Track History export)
        TrackLogFeatureWriter trackLogFeatureWriter = new TrackLogFeatureWriter(
                file);
        KMLUtil.deepFeatures(trackLog, trackLogFeatureWriter, Placemark.class);

        if (!trackLogFeatureWriter.hasPlacemarks())
            throw new Exception("Unable to parse any Tracks from: "
                    + file.getAbsolutePath());

        Document document = (Document) trackLog.getFeature();
        if (document == null)
            throw new Exception("Unable to parse Document from: "
                    + file.getAbsolutePath());

        Log.d(TAG, "Serializing Track Log: " + file.getAbsolutePath());

        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();

            final XmlSerializer xmlSerializer = Xml.newSerializer();
            xmlSerializer.setOutput(baos, FileSystemUtils.UTF8_CHARSET.name());
            xmlSerializer.startDocument(FileSystemUtils.UTF8_CHARSET.name(),
                    Boolean.TRUE);

            final String xmlpull = context
                    .getString(R.string.xmlpull);
            xmlSerializer.setFeature(xmlpull, true);

            xmlSerializer.startTag("", "kml");
            xmlSerializer.startTag("", "Document");

            if (!FileSystemUtils.isEmpty(document.getName())) {
                xmlSerializer.startTag("", "name");
                xmlSerializer.text(document.getName());
                xmlSerializer.endTag("", "name");
            }

            if (!FileSystemUtils.isEmpty(document.getAuthor())) {
                xmlSerializer.startTag("", "atom:author");
                xmlSerializer.text(document.getAuthor());
                xmlSerializer.endTag("", "atom:author");
            }

            if (!FileSystemUtils.isEmpty(document.getDescription())) {
                xmlSerializer.startTag("", "description");
                xmlSerializer.text(document.getDescription());
                xmlSerializer.endTag("", "description");
            }

            if (document.getOpen() != null) {
                xmlSerializer.startTag("", "open");
                xmlSerializer.text(String.valueOf(document.getOpen()));
                xmlSerializer.endTag("", "open");
            }

            // loop all styles
            List<Style> styles = KMLUtil.getStyles(trackLog, Style.class);
            if (styles == null || styles.size() < 1)
                Log.w(TAG,
                        "Unable to parse any Tracks from: "
                                + file.getAbsolutePath());

            if (styles != null) {
                for (Style style : styles) {
                    if (style == null) {
                        Log.w(TAG,
                                "Unable to parse Style to write track: "
                                        + file.getName());
                        continue;
                    }

                    appendStyle(xmlSerializer, style);
                }
            }

            // now loop schemas
            if (!FileSystemUtils.isEmpty(document.getSchemaList())) {
                for (Schema s : document.getSchemaList()) {
                    xmlSerializer.startTag("", "Schema");
                    xmlSerializer.attribute("", "id", s.getId());

                    //Note, we currently just support gx:SimpleArrayField
                    for (SimpleField sf : s.getSimpleFieldList()) {
                        xmlSerializer.startTag("", "gx:SimpleArrayField");
                        xmlSerializer.attribute("", "name", sf.getName());
                        xmlSerializer.attribute("", "type", sf.getType());
                        xmlSerializer.startTag("", "displayName");
                        xmlSerializer.text(sf.getDisplayName());
                        xmlSerializer.endTag("", "displayName");
                        xmlSerializer.endTag("", "gx:SimpleArrayField");
                    }

                    xmlSerializer.endTag("", "Schema");
                }
            }

            if (!FileSystemUtils.isEmpty(trackLogFeatureWriter.tracks)) {
                xmlSerializer.startTag("", "Folder");

                if (!FileSystemUtils.isEmpty(document.getName())) {
                    xmlSerializer.startTag("", "name");
                    xmlSerializer.text("Tracks");
                    xmlSerializer.endTag("", "name");
                }

                for (Placemark placemark : trackLogFeatureWriter.tracks) {
                    if (placemark == null) {
                        Log.w(TAG, "Unable to parse Placemark to write track: "
                                + file.getName());
                        continue;
                    }

                    appendPlacemark(xmlSerializer, placemark);
                }

                xmlSerializer.endTag("", "Folder");
            }

            if (!FileSystemUtils.isEmpty(trackLogFeatureWriter.checkpoints)) {
                xmlSerializer.startTag("", "Folder");

                if (!FileSystemUtils.isEmpty(document.getName())) {
                    xmlSerializer.startTag("", "name");
                    xmlSerializer.text("Checkpoints");
                    xmlSerializer.endTag("", "name");
                }

                for (Placemark placemark : trackLogFeatureWriter.checkpoints) {
                    if (placemark == null) {
                        Log.w(TAG,
                                "Unable to parse Placemark to write checkpoints: "
                                        + file.getName());
                        continue;
                    }

                    appendPlacemark(xmlSerializer, placemark);
                }

                xmlSerializer.endTag("", "Folder");
            }

            xmlSerializer.endTag("", "Document");
            xmlSerializer.endTag("", "kml");

            xmlSerializer.endDocument();
            xmlSerializer.flush();

            //fixup gx namespace
            //TODO anyway to have the XmlSerializer do this?
            String tempKml = new String(baos.toByteArray(),
                    FileSystemUtils.UTF8_CHARSET);
            String opengis = context
                    .getString(R.string.TrackLogKMLSerializier_opengis);
            String google = context
                    .getString(R.string.TrackLogKMLSerializier_google);
            String KML_GX_NAMESPACE = "<kml xmlns=\"" + opengis
                    + "\" xmlns:gx=\"" + google + "\">";

            tempKml = tempKml.replace("<kml>", KML_GX_NAMESPACE);

            //write out to file
            KMLUtil.write(tempKml, file);

            Log.d(TAG, "Wrote KML " + file.getName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write: " + file.getName(), e);
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close byte stream for KML Track: "
                            + file.getName(), e);
                }
            }
        }

        return false;
    }

    protected static void appendPlacemark(XmlSerializer xmlSerializer,
            Placemark placemark)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "Placemark");
        if (!FileSystemUtils.isEmpty(placemark.getId())) {
            xmlSerializer.attribute("", "id", placemark.getId());
        }

        appendExtendedData(xmlSerializer, placemark);
        if (!FileSystemUtils.isEmpty(placemark.getName())) {
            xmlSerializer.startTag("", "name");
            xmlSerializer.text(placemark.getName());
            xmlSerializer.endTag("", "name");
        }

        if (!FileSystemUtils.isEmpty(placemark.getDescription())) {
            xmlSerializer.startTag("", "description");
            xmlSerializer.text(placemark.getDescription());
            xmlSerializer.endTag("", "description");
        }

        if (!FileSystemUtils.isEmpty(placemark.getStyleUrl())) {
            xmlSerializer.startTag("", "styleUrl");
            xmlSerializer.text(placemark.getStyleUrl());
            xmlSerializer.endTag("", "styleUrl");
        }

        LineString lineString = KMLUtil.getFirstGeometry(placemark,
                LineString.class);
        if (lineString != null) {
            appendLineString(xmlSerializer, lineString);
        }

        TimePrimitive timePrim = placemark.getTimePrimitive();
        if (timePrim instanceof TimeSpan) {
            appendTimeSpan(xmlSerializer, (TimeSpan) timePrim);
        } else if (timePrim instanceof TimeStamp) {
            appendTimeStamp(xmlSerializer, (TimeStamp) timePrim);
        }

        Track track = KMLUtil.getFirstGeometry(placemark, Track.class);
        if (track != null) {
            appendTrack(xmlSerializer, track);
        }

        Point point = KMLUtil.getFirstGeometry(placemark, Point.class);
        if (point != null && point.getCoordinates() != null) {
            appendPoint(xmlSerializer, point);
        }

        xmlSerializer.endTag("", "Placemark");
    }

    private static void appendExtendedData(XmlSerializer xmlSerializer,
            Placemark placemark)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        if (placemark.getExtendedData() == null
                || placemark.getExtendedData().getDataList() == null
                || placemark.getExtendedData().getDataList().size() < 1)
            return;

        xmlSerializer.startTag("", "ExtendedData");
        for (Data d : placemark.getExtendedData().getDataList()) {
            if (FileSystemUtils.isEmpty(d.getName())
                    || FileSystemUtils.isEmpty(d.getValue()))
                continue;

            xmlSerializer.startTag("", "Data");
            xmlSerializer.attribute("", "name", d.getName());
            xmlSerializer.startTag("", "value");
            xmlSerializer.text(d.getValue());
            xmlSerializer.endTag("", "value");
            xmlSerializer.endTag("", "Data");
        }
        xmlSerializer.endTag("", "ExtendedData");
    }

    private static void appendLineString(XmlSerializer xmlSerializer,
            LineString lineString)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "LineString");
        xmlSerializer.startTag("", "altitudeMode");
        xmlSerializer.text(lineString.getAltitudeMode());
        xmlSerializer.endTag("", "altitudeMode");
        xmlSerializer.startTag("", "coordinates");
        xmlSerializer.text(KMLConversion.toString(lineString.getCoordinates(),
                false));
        xmlSerializer.endTag("", "coordinates");
        xmlSerializer.endTag("", "LineString");
    }

    private static void appendTrack(XmlSerializer xmlSerializer, Track track)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "gx:Track");
        xmlSerializer.startTag("", "altitudeMode");
        xmlSerializer.text(track.getAltitudeMode());
        xmlSerializer.endTag("", "altitudeMode");

        for (int i = 0; i < track.getWhen().size(); i++) {
            xmlSerializer.startTag("", "when");
            xmlSerializer.text(track.getWhen().get(i));
            xmlSerializer.endTag("", "when");
        }

        for (int i = 0; i < track.getCoord().size(); i++) {
            xmlSerializer.startTag("", "gx:coord");
            xmlSerializer.text(track.getCoord().get(i));
            xmlSerializer.endTag("", "gx:coord");
        }

        for (int i = 0; i < track.getAngles().size(); i++) {
            xmlSerializer.startTag("", "gx:angles");
            xmlSerializer.text(track.getAngles().get(i));
            xmlSerializer.endTag("", "gx:angles");
        }

        if (track.getExtendedData() != null
                && !FileSystemUtils.isEmpty(track.getExtendedData()
                        .getSchemaDataList())) {
            xmlSerializer.startTag("", "ExtendedData");
            for (SchemaData s : track.getExtendedData().getSchemaDataList()) {
                xmlSerializer.startTag("", "SchemaData");
                xmlSerializer.attribute("", "schemaUrl", s.getSchemaUrl());

                if (!FileSystemUtils.isEmpty(s.getSchemaDataExtension())) {

                    //Note, we currently just support gx:SimpleArrayField
                    for (Object sde : s.getSchemaDataExtension()) {
                        if (sde instanceof SimpleArrayData) {
                            SimpleArrayData sar = (SimpleArrayData) sde;

                            xmlSerializer.startTag("", "gx:SimpleArrayData");
                            xmlSerializer.attribute("", "name", sar.getName());

                            for (String sv : sar.getValue()) {
                                xmlSerializer.startTag("", "gx:value");
                                xmlSerializer.text(sv);
                                xmlSerializer.endTag("", "gx:value");
                            }

                            xmlSerializer.endTag("", "gx:SimpleArrayData");
                        }
                    } //end getSchemaDataExtension   
                }
                xmlSerializer.endTag("", "SchemaData");
            } //end getExtendedData().getSchemaDataList()
            xmlSerializer.endTag("", "ExtendedData");
        }
        xmlSerializer.endTag("", "gx:Track");
    }

    private static void appendPoint(XmlSerializer xmlSerializer, Point point)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "Point");
        xmlSerializer.startTag("", "altitudeMode");
        xmlSerializer.text(point.getAltitudeMode());
        xmlSerializer.endTag("", "altitudeMode");

        xmlSerializer.startTag("", "coordinates");
        xmlSerializer.text(point.getCoordinates().toString());
        xmlSerializer.endTag("", "coordinates");

        xmlSerializer.endTag("", "Point");
    }

    private static void appendTimeSpan(XmlSerializer xmlSerializer,
            TimeSpan timeSpan)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "TimeSpan");
        xmlSerializer.startTag("", "begin");
        xmlSerializer.text(timeSpan.getBegin());
        xmlSerializer.endTag("", "begin");
        xmlSerializer.startTag("", "end");
        xmlSerializer.text(timeSpan.getEnd());
        xmlSerializer.endTag("", "end");
        xmlSerializer.endTag("", "TimeSpan");
    }

    private static void appendTimeStamp(XmlSerializer xmlSerializer,
            TimeStamp timeStamp)
            throws IllegalArgumentException, IllegalStateException,
            IOException {
        xmlSerializer.startTag("", "TimeStamp");
        xmlSerializer.startTag("", "when");
        xmlSerializer.text(timeStamp.getWhen());
        xmlSerializer.endTag("", "when");
        xmlSerializer.endTag("", "TimeStamp");
    }

    protected static void appendStyle(XmlSerializer xmlSerializer, Style style)
            throws IllegalArgumentException, IllegalStateException,
            IOException {

        xmlSerializer.startTag("", "Style");
        xmlSerializer.attribute("", "id", style.getId());

        LineStyle lineStyle = style.getLineStyle();
        if (lineStyle != null
                && !FileSystemUtils.isEmpty(lineStyle.getColor())) {
            xmlSerializer.startTag("", "LineStyle");
            xmlSerializer.startTag("", "color");
            xmlSerializer.text(lineStyle.getColor());
            xmlSerializer.endTag("", "color");
            if (lineStyle.getWidth() != null) {
                xmlSerializer.startTag("", "width");
                xmlSerializer.text(lineStyle.getWidth().toString());
                xmlSerializer.endTag("", "width");
            }
            xmlSerializer.endTag("", "LineStyle");
        }

        IconStyle iconStyle = style.getIconStyle();
        if (iconStyle != null
                && !FileSystemUtils.isEmpty(iconStyle.getColor())) {
            xmlSerializer.startTag("", "IconStyle");
            xmlSerializer.startTag("", "color");
            xmlSerializer.text(iconStyle.getColor());
            xmlSerializer.endTag("", "color");
            xmlSerializer.endTag("", "IconStyle");
        }
        xmlSerializer.endTag("", "Style");
    }
}
