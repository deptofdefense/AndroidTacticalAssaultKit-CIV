package com.atakmap.map.layer.feature.wfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.res.XmlResourceParser;

import android.util.Xml;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

public class XMLWFSSchemaHandler implements WFSSchemaHandler {

    private Map<String, LayerDefinition> layerDefinitions;
    private WFSSchemaFeatureFilter defaultFeatureFilter;
    private Set<WFSSchemaFeatureFilter> globalFeatureFilters;
    public static final String WFS_CONFIG_ROOT = "takWfsConfig";

    private String displayName;
    private String uri;
    private File file;

    public XMLWFSSchemaHandler(File schema) throws XmlPullParserException, IOException {
        this(schema, IOProviderFactory.getInputStream(schema), true);
    }
    
    public XMLWFSSchemaHandler(InputStream schema) throws XmlPullParserException, IOException {
        this(null, schema, false);
    }
    
    private XMLWFSSchemaHandler(final File file, InputStream stream, final boolean closeStream) throws XmlPullParserException, IOException {
        this.file = file;
        this.layerDefinitions = new HashMap<String, LayerDefinition>();

        this.globalFeatureFilters = new HashSet<WFSSchemaFeatureFilter>();

        XmlPullParser parser = null;
        try {
            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setFeature(Xml.FEATURE_RELAXED, true);
            parser.setInput(stream, null);

            Stack<String> tagStack = new Stack<String>();
            int eventType;
            do {
                eventType = parser.next();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                    {
                        String tag = parser.getName();
                        if (tag.equals(WFS_CONFIG_ROOT) && !tagStack.isEmpty()) {
                            // nested configs not supported
                            throw new RuntimeException("<takWfsConfig> must be root tag");
                        } else if(!tag.equals(WFS_CONFIG_ROOT) && tagStack.isEmpty()) {
                            // unknown schema
                            throw new RuntimeException("<takWfsConfig> must be root tag");
                        } else if(tag.equals("layer")) {
                            LayerDefinition layer = parseLayer(parser);
                            if(layer != null)
                                this.layerDefinitions.put(layer.remoteName, layer);
                        } else if(tag.equals("feature")) {
                            boolean[] globalFilter = {false};
                            WFSSchemaFeatureFilter filter = parseFeatureFilter(globalFilter, parser);
                            if(globalFilter[0] && filter != null) {
                                // configure the default, catch-all filter
                                if(this.defaultFeatureFilter != null)
                                    throw new RuntimeException("Only one default filter may be specified");
                                this.defaultFeatureFilter = filter; 
                            } else if(filter != null) {
                                // create a global filter
                                this.globalFeatureFilters.add(filter);
                            }
                        } else {
                            tagStack.push(tag);
                        }
                        break;
                    }
                    case XmlPullParser.END_TAG:
                        tagStack.pop();
                        break;
                    case XmlPullParser.TEXT:
                    {
                        String tag = tagStack.peek();
                        if(tag.equals("uri"))
                            this.uri = parser.getText();
                        else if(tag.equals("displayname"))
                            this.displayName = parser.getText();
                        break;
                    }
                    case XmlPullParser.END_DOCUMENT:
                        break;
                    default:
                        break;
                }
            } while (eventType != XmlPullParser.END_DOCUMENT);

            if(this.uri == null)
                throw new IllegalArgumentException("No WFS URI specified.");

            if(this.displayName == null)
                this.displayName = this.uri;
            
            if(this.defaultFeatureFilter == null) {
                DefaultWFSSchemaFeatureFilter filter = new DefaultWFSSchemaFeatureFilter(false, true);
                filter.setNameResolver(DefaultWFSSchemaHandler.DEFAULT_NAME_RESOLVER);
                filter.addStyler(Point.class, DefaultWFSSchemaHandler.DEFAULT_POINT_STYLER);
                filter.addStyler(LineString.class, DefaultWFSSchemaHandler.DEFAULT_LINE_STYLER);
                filter.addStyler(Polygon.class, DefaultWFSSchemaHandler.DEFAULT_POLYGON_STYLER);

                this.defaultFeatureFilter = filter;
            }
        } catch(RuntimeException e) {
            // XXX - https://code.google.com/p/android/issues/detail?id=54499
            // XXX - ATAK bug 5815

            if (file != null) 
                Log.e("XmlWFSSchemaHandler", "Failed to parse XML " + file.getName(), e);
            else 
                Log.e("XmlWFSSchemaHandler", "Failed to parse XML from InputStream " + stream, e);

            XmlPullParserException toThrow = new XmlPullParserException("General error occurred parsing XML");
            toThrow.initCause(e);
            throw toThrow;
        } finally {
            if (parser != null) {
                if (parser instanceof XmlResourceParser)
                   ((XmlResourceParser)parser).close();
            }

            if(closeStream)
                stream.close();
        }
    }

    public File getSchemaFile() {
        return this.file;
    }

    /**************************************************************************/
    
    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public boolean ignoreLayer(String layer) {
        LayerDefinition def = this.layerDefinitions.get(layer);
        if(def != null)
            return def.ignore;
        return true;
    }

    @Override
    public boolean isLayerVisible(String layer) {
        LayerDefinition def = this.layerDefinitions.get(layer);
        if(def != null)
            return def.visible;
        return false;
    }

    @Override
    public String getLayerName(String remote) {
        LayerDefinition def = this.layerDefinitions.get(remote);
        if(def != null)
            return def.localName;
        return remote;
    }

    @Override
    public boolean ignoreFeature(String layer, AttributeSet metadata) {
        LayerDefinition def = this.layerDefinitions.get(layer);
        if(def != null) {
            // this layer is ignored
            if(def.ignore)
                return true;

            for(WFSSchemaFeatureFilter filter : def.featureFilters)
                if(filter.matches(metadata))
                    return filter.shouldIgnore();
        }
        for(WFSSchemaFeatureFilter filter : globalFeatureFilters)
            if(filter.matches(metadata))
                return filter.shouldIgnore();
        return false;
    }

    @Override
    public boolean isFeatureVisible(String layer, AttributeSet metadata) {
        LayerDefinition def = this.layerDefinitions.get(layer);
        if(def != null) {
            for(WFSSchemaFeatureFilter filter : def.featureFilters)
                if(filter.matches(metadata))
                    return filter.isVisible();
        }
        for(WFSSchemaFeatureFilter filter : globalFeatureFilters)
            if(filter.matches(metadata))
                return filter.isVisible();

        // checked for null previously in this method, if it is null - return true
        if (def == null)
           return true;

        return def.visible;
    }

    @Override
    public String getFeatureName(String layer, AttributeSet metadata) {
        LayerDefinition def = layerDefinitions.get(layer);
        String retval;
        if(def != null) {
            for(WFSSchemaFeatureFilter filter : def.featureFilters) {
                if(filter.matches(metadata)) {
                    retval = filter.getName(metadata);
                    if(retval == null)
                        break;
                    return retval;
                }
            }
            if(def.defaultFilter != null) {
                retval = def.defaultFilter.getName(metadata);
                if(retval != null)
                    return retval;
            }
        }
        for(WFSSchemaFeatureFilter filter : globalFeatureFilters) {
            if(filter.matches(metadata)) {
                retval = filter.getName(metadata);
                if(retval != null)
                    break;
                return retval;
            }
        }
        retval = this.defaultFeatureFilter.getName(metadata);
        if(retval != null)
            retval = DefaultWFSSchemaHandler.DEFAULT_NAME_RESOLVER.getName(metadata);
        return retval;
    }

    @Override
    public Style getFeatureStyle(String layer, AttributeSet metadata, Class<? extends Geometry> geomType) {
        LayerDefinition def = this.layerDefinitions.get(layer);
        Style retval;
        if(def != null) {
            for(WFSSchemaFeatureFilter filter : def.featureFilters) {
                if(filter.matches(metadata)) {
                    retval = filter.getStyle(metadata, geomType);
                    if(retval == null)
                        break;
                    return retval;
                }
            }
            if(def.defaultFilter != null) {
                retval = def.defaultFilter.getStyle(metadata, geomType);
                if(retval != null)
                    return retval;
            }
        }
        for(WFSSchemaFeatureFilter filter : globalFeatureFilters) {
            if(filter.matches(metadata)) {
                retval = filter.getStyle(metadata, geomType);
                if(retval == null)
                    break;
                return retval;
            }
        }
        
        return this.defaultFeatureFilter.getStyle(metadata, geomType);
    }

    /**************************************************************************/

    private static LayerDefinition parseLayer(XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "layer");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        LayerDefinition definition = new LayerDefinition();
        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                {
                    final String tag = parser.getName();
                    if (tag.equals("feature")) {
                        boolean[] globalFilter = {false};
                        WFSSchemaFeatureFilter filter = parseFeatureFilter(globalFilter, parser);
                        if(globalFilter[0] && filter != null) {
                            if(definition.defaultFilter != null)
                                throw new RuntimeException("Only one default filter may be specified per Layer");
                            definition.defaultFilter = filter;
                        } else if(filter != null) {
                            definition.featureFilters.add(filter);
                        }
                    } else {
                        tagStack.push(tag);
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                {
                    if (tagStack.size() < 1)
                        throw new IllegalStateException();

                    final String inTag = tagStack.peek();
                    if(inTag.equals("remotename")) {
                        definition.remoteName = parser.getText();
                    } else if (inTag.equals("displayname")) {
                        definition.localName = parser.getText();
                    } else if (inTag.equals("visible")) {
                        definition.visible = parseBoolean(parser.getText(), true);
                    } else if (inTag.equals("ignore")) {
                        definition.ignore = parseBoolean(parser.getText(), false);
                        
                        // all layer content is going to be ignored, don't
                        // bother building out the rest of the schema
                        if(definition.ignore)
                            break;
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if(definition.remoteName == null)
            throw new RuntimeException("<remotename> required for <layer>");
        
        return definition;
    }

    private static WFSSchemaFeatureFilter parseFeatureFilter(boolean[] isAcceptAll, XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "feature");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        String displayName = null;
        boolean ignore = false;
        boolean visible = true;
        Map<Class<? extends Geometry>, WFSSchemaFeatureStyler> stylers = new HashMap<Class<? extends Geometry>, WFSSchemaFeatureStyler>();
        Collection<AttributeSetFilter> filters = new LinkedList<AttributeSetFilter>();
        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                {
                    final String tag = parser.getName();
                    if (tag.equals("styles")) {
                        parseStylers(stylers, parser);
                    } else if (tag.equals("filter")) {
                        parseAttributeSetFilter(filters, parser);
                    } else {
                        tagStack.push(tag);
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT:
                {
                    if (tagStack.size() < 1)
                        throw new IllegalStateException();

                    final String inTag = tagStack.peek();
                    if (inTag.equals("displayname")) {
                        displayName = parser.getText();
                    } else if (inTag.equals("visible")) {
                        visible = parseBoolean(parser.getText(), true);
                    } else if (inTag.equals("ignore")) {
                        ignore = parseBoolean(parser.getText(), false);
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);

        if(isAcceptAll != null)
            isAcceptAll[0] = filters.isEmpty();
        
        DefaultWFSSchemaFeatureFilter retval = new DefaultWFSSchemaFeatureFilter(ignore, visible);
        if(displayName != null) {
            if(displayName.charAt(0) == '@')
                retval.setNameResolver(new ColumnWFSSchemaFeatureNameResolver(displayName.substring(1)));
            else
                retval.setNameResolver(new LiteralWFSSchemaFeatureNameResolver(displayName));
        }

        for(AttributeSetFilter filter : filters)
            retval.addFilter(filter);
        
        for(Map.Entry<Class<? extends Geometry>, WFSSchemaFeatureStyler> entry : stylers.entrySet())
            retval.addStyler(entry.getKey(), entry.getValue());
        
        return retval;
    }
    
    private static void parseStylers(Map<Class<? extends Geometry>, WFSSchemaFeatureStyler> stylers, XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "styles");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        WFSSchemaFeatureStyler pointStyler = null;
        WFSSchemaFeatureStyler lineStyler = null;
        WFSSchemaFeatureStyler polygonStyler = null;

        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                {
                    final String tag = parser.getName();
                    if (tag.equals("pointstyle")) {
                        pointStyler = parsePointStyler(parser);
                    } else if (tag.equals("linestyle")) {
                        lineStyler = parseLineStyler(parser);
                    }  else if (tag.equals("polygonstyle")) {
                        polygonStyler = parsePolygonStyler(parser);
                    } else {
                        tagStack.push(tag);
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.TEXT: {
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        if(pointStyler != null)
            stylers.put(Point.class, pointStyler);
        if(lineStyler != null)
            stylers.put(LineString.class, lineStyler);
        if(polygonStyler != null)
            stylers.put(Polygon.class, polygonStyler);
    }
    
    private static WFSSchemaFeatureStyler parsePointStyler(XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "pointstyle");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        boolean label = true;
        String icon = "asset:/icons/reference_point.png";
        int color = -1;
        int iconsize = 32;
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
                    if (inTag.equals("label")) {
                        label = parseBoolean(parser.getText(), true);
                    } else if (inTag.equals("icon")) {
                        icon = parser.getText();
                    } else if (inTag.equals("color")) {
                        color = (int)Long.parseLong(parser.getText(), 16);
                    } else if (inTag.equals("iconsize")) {
                        iconsize = (int)Math.ceil(Float.parseFloat(parser.getText()));
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        return new DefaultPointStyler(icon, color, iconsize, label);
    }
    
    
    private static WFSSchemaFeatureStyler parseLineStyler(XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "linestyle");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        StrokeDef stroke = null;
        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                {
                    final String tag = parser.getName();
                    if (tag.equals("strokestyle")) {
                        stroke = new StrokeDef();
                        parseStroke(stroke, parser);
                    } else {
                        tagStack.push(parser.getName());
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        if(stroke == null)
            return null;
        return new DefaultLineStyler(stroke.color, stroke.width);
    }
    
    private static WFSSchemaFeatureStyler parsePolygonStyler(XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "polygonstyle");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        StrokeDef stroke = null;
        FillDef fill = null;
        int eventType;
        do {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                {
                    final String tag = parser.getName();
                    if (tag.equals("strokestyle")) {
                        stroke = new StrokeDef();
                        parseStroke(stroke, parser);
                    } else if (tag.equals("fillstyle")) {
                        fill = new FillDef();
                        parseFill(fill, parser);
                    } else {
                        tagStack.push(parser.getName());
                    }
                    break;
                }
                case XmlPullParser.END_TAG:
                    tagStack.pop();
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        if(stroke == null && fill == null)
            return null;
        else if(stroke != null && fill != null)
            return new DefaultPolygonStyler(stroke.color, stroke.width, fill.color);
        else if(stroke != null)
            return new DefaultLineStyler(stroke.color, stroke.width);
        else if(fill != null)
            return new DefaultPolygonStyler(0, 0, fill.color);
        else
            throw new IllegalStateException();
    }
    
    private static void parseAttributeSetFilter(Collection<AttributeSetFilter> filters, XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "filter");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

        String key = parser.getAttributeValue(null, "key");
        String value = parser.getAttributeValue(null, "value");
        
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
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        if(key == null || value == null)
            throw new RuntimeException("Both key and value must be supplied for <filter>");
        
        char test = value.charAt(0);
        switch(test) {
            case '<' :
                filters.add(NumberValueAttributeSetFilter.lessThan(key, Double.parseDouble(value.substring(1).trim())));
                break;
            case '>' :
                filters.add(NumberValueAttributeSetFilter.greaterThan(key, Double.parseDouble(value.substring(1).trim())));
                break;
            case '=' :
                filters.add(NumberValueAttributeSetFilter.equalTo(key, Double.parseDouble(value.substring(1).trim())));
                break;
            default :
                filters.add(StringValueAttributeSetFilter.createFilter(key, value, '*'));
                break;
        }
    }
    
    private static void parseStroke(StrokeDef stroke, XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "strokestyle");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

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
                    if (inTag.equals("color")) {
                        stroke.color = (int)Long.parseLong(parser.getText(), 16);
                    } else if (inTag.equals("width")) {
                        stroke.width = Float.parseFloat(parser.getText());
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
    }
    
    private static void parseFill(FillDef fill, XmlPullParser parser) throws XmlPullParserException, IOException {
        checkAtTag(parser, "fillstyle");

        Stack<String> tagStack = new Stack<String>();
        tagStack.push(parser.getName());

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
                    if (inTag.equals("color")) {
                        fill.color = (int)Long.parseLong(parser.getText(), 16);
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document.");
                default:
                    break;
            }
        } while (tagStack.size() > 0);
    }
    
    private static void checkAtTag(XmlPullParser parser, String tagName)
            throws XmlPullParserException {
        if (parser.getEventType() != XmlPullParser.START_TAG || !parser.getName().equals(tagName))
            throw new IllegalStateException();
    }

    private static boolean parseBoolean(String value, boolean def) {
        if(def)
            return !"false".equals(value.toLowerCase(LocaleUtil.getCurrent()));
        else
            return "true".equals(value.toLowerCase(LocaleUtil.getCurrent()));
    }

    /**************************************************************************/

    private static class LayerDefinition {
        public String remoteName;
        public String localName;
        public Collection<WFSSchemaFeatureFilter> featureFilters;
        public WFSSchemaFeatureFilter defaultFilter;
        public boolean ignore;
        public boolean visible;

        public LayerDefinition() {
            this.remoteName = "";
            this.localName = "";
            this.featureFilters = new HashSet<WFSSchemaFeatureFilter>();
            this.defaultFilter = null;
            this.ignore = false;
            this.visible = true;
        }
    }
    
    private static class StrokeDef {
        public int color = 0;
        public float width = 0;
    }
    
    private static class FillDef {
        public int color;
    }
}
