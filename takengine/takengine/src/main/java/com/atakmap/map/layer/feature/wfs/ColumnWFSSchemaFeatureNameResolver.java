package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;

public final class ColumnWFSSchemaFeatureNameResolver implements WFSSchemaFeatureNameResolver {

    private final String column;

    public ColumnWFSSchemaFeatureNameResolver(String column) {
        this.column = column;
    }

    @Override
    public String getName(AttributeSet metadata) {
        if(!metadata.containsAttribute(this.column))
            return null;
        Class<?> type = metadata.getAttributeType(this.column);
        if(String.class.equals(type))
            return metadata.getStringAttribute(this.column);
        else if(Double.class.equals(type))
            return String.valueOf(metadata.getDoubleAttribute(this.column));
        else if(Integer.class.equals(type))
            return String.valueOf(metadata.getIntAttribute(this.column));
        else if(Long.class.equals(type))
            return String.valueOf(metadata.getLongAttribute(this.column));
        else
            return null;
    }
    
    public String toString() {
        return "@" + column;
    }

}
