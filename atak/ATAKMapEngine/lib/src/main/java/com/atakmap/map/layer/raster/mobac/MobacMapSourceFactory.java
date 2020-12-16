
package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Stack;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import org.apache.commons.lang.StringEscapeUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.util.Xml;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;

public class MobacMapSourceFactory {
    private MobacMapSourceFactory() {
    }

    public static MobacMapSource create(final File f) throws IOException {
        if (f.getName().toLowerCase(LocaleUtil.getCurrent()).endsWith(".xml"))
            return parseXmlMapSource(f);
        else
            return null;
    }

    public static MobacMapSource create(File f, MobacMapSource.Config config) throws IOException {
        final MobacMapSource mmc = create(f);
        if (mmc != null && config != null)
            mmc.setConfig(config);
        return mmc;
    }

    public static MobacMapSource create(InputStream s, MobacMapSource.Config config) throws IOException {
        final MobacMapSource mmc = parseXmlMapSource(s);
        if (mmc != null && config != null)
            mmc.setConfig(config);
        return mmc;
    }

    private static MobacMapSource parseXmlMapSource(File f) throws IOException {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = IOProviderFactory.getInputStream(f);
            return parseXmlMapSource(fileInputStream);
        } finally {
            if(fileInputStream != null)
                try {
                    fileInputStream.close();
                } catch(IOException ignored) {}
        }
    }

    private static MobacMapSource parseXmlMapSource(InputStream stream) throws IOException {
        XmlPullParser parser = null;
        try {
            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setFeature(Xml.FEATURE_RELAXED, true);
            parser.setInput(stream, null);

            MobacMapSource retval = null;
            int eventType;
            do {
                eventType = parser.nextToken();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equals("customMapSource"))
                            retval = parseCustomMapSource(parser);
                        else if (parser.getName().equals("customWmsMapSource"))
                            retval = parseCustomWmsMapSource(parser);
                        else if (parser.getName().equals("customMultiLayerMapSource"))
                            retval = parseCustomMultiLayerMapSource(parser);
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

            return retval;
        } catch(RuntimeException e) {
            // XXX - https://code.google.com/p/android/issues/detail?id=54499
            // XXX - ATAK bug 5815
            Log.e("MobacMapSourceFactory", "Failed to parse XML");
            IOException toThrow = new IOException("General error occurred parsing XML", e);
            throw toThrow;
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        } finally { 
            if (parser != null) {
                if (parser instanceof AutoCloseable)
                    try {
                        ((AutoCloseable) parser).close();
                    } catch(Exception ignored) {}
            }
        }
    }

    private static MobacMapSource parseCustomMapSource(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        checkAtTag(parser, "customMapSource");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        int minZoom = 0;
        int maxZoom = -1;
        String name = null;
        String url = null;
        String type = null;
        String[] serverParts = null;
        boolean invertYAxis = false;
        int backgroundColor = 0;
        int srid = 3857;
        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT: {
                    if (tagStack.size() < 1)
                        throw new IllegalStateException();

                    final String inTag = tagStack.peek();
                    if (inTag.equals("name")) {
                        name = parser.getText();
                    } else if (inTag.equals("url")) {
                        url = StringEscapeUtils.unescapeHtml(parser.getText());
                    } else if (inTag.equals("minZoom")) {
                        minZoom = Integer.parseInt(parser.getText());
                    } else if (inTag.equals("maxZoom")) {
                        maxZoom = Integer.parseInt(parser.getText());
                    } else if (inTag.equals("tileType")) {
                        type = parser.getText();
                    } else if (inTag.equals("serverParts")) {
                        if (parser.getText().trim().length() > 0)
                            serverParts = parser.getText().split("\\s+");
                    } else if (inTag.equals("invertYCoordinate")) {
                        invertYAxis = parser.getText().equals("true");
                    } else if (inTag.equals("backgroundColor")) {
                        if (parser.getText().matches("\\#[0-9A-Fa-f]"))
                            backgroundColor = Integer.parseInt(parser.getText().substring(1), 16);
                    } else if (inTag.equals("coordinatesystem")) {
                        if (parser.getText().matches("EPSG\\:\\d+"))
                            srid = Integer.parseInt(parser.getText().split("\\:")[1]);
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if (name == null || url == null || maxZoom == -1)
            throw new RuntimeException(
                    "customMapSource definition does not contain required elements.");

        return new CustomMobacMapSource(name,
                srid,
                256,
                minZoom,
                maxZoom,
                type,
                url,
                serverParts,
                backgroundColor,
                invertYAxis);
    }

    private static MobacMapSource parseCustomMultiLayerMapSource(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        checkAtTag(parser, "customMultiLayerMapSource");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        String name = null;
        LinkedList<MobacMapSource> layers = new LinkedList<MobacMapSource>();
        int backgroundColor = 0;
        float[] layersAlpha = null;

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (tagStack.size() < 1)
                        throw new IllegalStateException();

                    if (tagStack.peek().equals("layers")) {
                        if (parser.getName().equals("customMapSource"))
                            layers.add(parseCustomMapSource(parser));
                        else if (parser.getName().equals("customWmsMapSource"))
                            layers.add(parseCustomWmsMapSource(parser));
                        else if (parser.getName().equals("customMultiLayerMapSource"))
                            layers.add(parseCustomMultiLayerMapSource(parser));
                    } else {
                        tagStack.push(parser.getName());
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT: {
                    if (tagStack.size() < 1)
                        throw new IllegalStateException();
                    final String inTag = tagStack.peek();
                    if (inTag.equals("name")) {
                        name = parser.getText();
                    } else if (inTag.equals("backgroundColor")) {
                        if (parser.getText().matches("\\#[0-9A-Fa-f]"))
                            backgroundColor = Integer.parseInt(parser.getText().substring(1), 16);
                    } else if (inTag.equals("layersAlpha")) {
                        String[] splits = parser.getText().split("\\s+");
                        layersAlpha = new float[splits.length];
                        boolean allValid = true;
                        for (int i = 0; i < splits.length; i++) {
                            allValid &= splits[i].matches("\\d+(\\.\\d+)?");
                            if (!allValid)
                                break;
                            layersAlpha[i] = Float.parseFloat(splits[i]);
                        }
                        if (!allValid)
                            layersAlpha = null;
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if (name == null || (layersAlpha != null && layersAlpha.length != layers.size()))
            throw new RuntimeException();

        return new CustomMultiLayerMobacMapSource(name,
                layers.toArray(new MobacMapSource[0]),
                layersAlpha,
                backgroundColor);
    }

    private static MobacMapSource parseCustomWmsMapSource(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        checkAtTag(parser, "customWmsMapSource");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        int minZoom = -1;
        int maxZoom = -1;
        int backgroundColor = 0;
        String url = null;
        int srid = 4326;
        String layers = null;
        String style = null;
        String additionalParameters = "";
        String version = null;
        String name = null;
        String tileFormat = null;

        double north = Double.NaN;
        double south = Double.NaN;
        double east = Double.NaN;
        double west = Double.NaN;

        int eventType;
        String inTag;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    tagStack.push(parser.getName());
                    break;
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                    inTag = tagStack.peek();
                    if (inTag.equals("minZoom")) {
                        minZoom = Integer.parseInt(parser.getText());
                    } else if (inTag.equals("maxZoom")) {
                        maxZoom = Integer.parseInt(parser.getText());
                    } else if (inTag.equals("backgroundColor")) {
                        if (parser.getText().matches("\\#[0-9A-Fa-f]"))
                            backgroundColor = Integer.parseInt(parser.getText().substring(1), 16);
                    } else if (inTag.equals("url")) {
                        url = StringEscapeUtils.unescapeHtml(parser.getText());
                    } else if (inTag.equals("coordinatesystem")) {
                        if (parser.getText().matches("EPSG\\:\\d+"))
                            srid = Integer.parseInt(parser.getText().split("\\:")[1]);
                    } else if (inTag.equals("layers")) {
                        layers = parser.getText();
                    } else if (inTag.equals("styles")) {
                        style = parser.getText();
                    } else if (inTag.equals("additionalparameters")
                            || inTag.equals("aditionalparameters")) {
                        additionalParameters = StringEscapeUtils.unescapeHtml(parser.getText());
                    } else if (inTag.equals("name")) {
                        name = parser.getText();
                    } else if (inTag.equals("tileType")) {
                        tileFormat = parser.getText();
                        if (tileFormat != null)
                            tileFormat = tileFormat.toUpperCase(LocaleUtil.getCurrent());
                    } else if (inTag.equals("version")) {
                        version = parser.getText();
                    } else if (inTag.equals("north")) {
                        try {
                            north = Double.parseDouble(parser.getText());
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (inTag.equals("south")) {
                        try {
                            south = Double.parseDouble(parser.getText());
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (inTag.equals("east")) {
                        try {
                            east = Double.parseDouble(parser.getText());
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (inTag.equals("west")) {
                        try {
                            west = Double.parseDouble(parser.getText());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if (name == null || url == null || layers == null || maxZoom == -1 || tileFormat == null)
            throw new RuntimeException(
                    "customWmsMapSource definition does not contain required elements.");

        GeoBounds bounds;
        if (Double.isNaN(north) ||
                Double.isNaN(east) ||
                Double.isNaN(south) ||
                Double.isNaN(west))
            bounds = null;
        else
            bounds = new GeoBounds(south, west, north, east);

        return new CustomWmsMobacMapSource(name,
                srid,
                256,
                minZoom,
                maxZoom,
                tileFormat,
                url,
                layers,
                style,
                version,
                additionalParameters,
                backgroundColor,
                bounds);
    }

    private static void checkAtTag(XmlPullParser parser, String tagName)
            throws XmlPullParserException {
        if (parser.getEventType() != XmlPullParser.START_TAG || !parser.getName().equals(tagName))
            throw new IllegalStateException();
    }
}
