package com.atakmap.map.layer.feature.wfs;

import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;

public class DefaultWFSSchemaHandler implements WFSSchemaHandler {

    public final static WFSSchemaFeatureNameResolver DEFAULT_NAME_RESOLVER = new WFSSchemaFeatureNameResolver() {
        @Override
        public String getName(AttributeSet metadata) {
            String nameCol = null;

            Set<String> schema = metadata.getAttributeNames();
            for(String columnName : schema) {
                if(columnName == null)
                    continue;
                if(!String.class.equals(metadata.getAttributeType(columnName)))
                    continue;
                String test = columnName.toLowerCase(LocaleUtil.getCurrent());
                if(test.equals("name") || test.startsWith("name") || test.endsWith("name")) {
                    nameCol = columnName;
                }
                
                if(nameCol != null)
                    break;
            }
            
            if(nameCol == null)
                return null;

            final Class<?> colType = metadata.getAttributeType(nameCol);
            if(String.class.equals(colType))
                return metadata.getStringAttribute(nameCol);
            else if(Double.class.equals(colType))
                return String.valueOf(metadata.getDoubleAttribute(nameCol));
            else if(Integer.class.equals(colType))
                return String.valueOf(metadata.getIntAttribute(nameCol));
            else if(Long.class.equals(colType))
                return String.valueOf(metadata.getLongAttribute(nameCol));
            else
                return null;
        }
    };
    
    private final static Style DEFAULT_POINT = new IconPointStyle(-1, "asset:/icons/reference_point.png");
    public final static WFSSchemaFeatureStyler DEFAULT_POINT_STYLER = new WFSSchemaFeatureStyler() {
        @Override
        public Style getStyle(AttributeSet metadata) {
            return DEFAULT_POINT;
        }
    };
    private final static Style DEFAULT_LINE = new BasicStrokeStyle(-1, 2.0f);
    public final static WFSSchemaFeatureStyler DEFAULT_LINE_STYLER = new WFSSchemaFeatureStyler() {
        @Override
        public Style getStyle(AttributeSet metadata) {
            return DEFAULT_LINE;
        }
    }; 
    public final static WFSSchemaFeatureStyler DEFAULT_POLYGON_STYLER = DEFAULT_LINE_STYLER;

    private final String uri;
    private final Map<String, String> layerNameToFeatureNameColumn;

    public DefaultWFSSchemaHandler(String uri) {
        this.uri = uri;
        this.layerNameToFeatureNameColumn = new HashMap<String, String>();
    }

    private void validateFeatureNameColumn(String layerName, AttributeSet metadata) {
        String nameCol = null;

        Set<String> schema = metadata.getAttributeNames();
        for(String columnName : schema) {
            if(columnName == null)
                continue;
            if(!String.class.equals(metadata.getAttributeType(columnName)))
                continue;
            columnName = columnName.toLowerCase(LocaleUtil.getCurrent());
            if(columnName.equals("name") || columnName.startsWith("name") || columnName.endsWith("name")) {
                nameCol = columnName;
            }
            
            if(nameCol != null)
                break;
        }
        
        if(nameCol != null)
            this.layerNameToFeatureNameColumn.put(layerName, nameCol);
        else
            this.layerNameToFeatureNameColumn.put(layerName, null);
    }

    /**************************************************************************/
    // WFSSchemaHandler
    
    @Override
    public String getName() {
        return this.uri;
    }

    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public boolean ignoreLayer(String layer) {
        return false;
    }

    @Override
    public boolean isLayerVisible(String layer) {
        return true;
    }

    @Override
    public String getLayerName(String remote) {
        return remote;
    }

    @Override
    public boolean ignoreFeature(String layer, AttributeSet metadata) {
        return false;
    }

    @Override
    public boolean isFeatureVisible(String layer, AttributeSet metadata) {
        return true;
    }

    @Override
    public String getFeatureName(String layer, AttributeSet metadata) {
        if(!this.layerNameToFeatureNameColumn.containsKey(layer))
            this.validateFeatureNameColumn(layer, metadata);
        final String nameCol = this.layerNameToFeatureNameColumn.get(layer);
        if(nameCol != null && metadata.containsAttribute(nameCol))
            return metadata.getStringAttribute(nameCol);
        return layer + "-" + UUID.randomUUID().toString();
    }

    @Override
    public Style getFeatureStyle(String layer, AttributeSet metadata, Class<? extends Geometry> geomType) {
        if(Point.class.equals(geomType))
            return DEFAULT_POINT_STYLER.getStyle(metadata);
        else if(LineString.class.equals(geomType))
            return DEFAULT_LINE_STYLER.getStyle(metadata);
        else if(Polygon.class.equals(geomType))
            return DEFAULT_POLYGON_STYLER.getStyle(metadata);
        else
            return null;
    }
}
