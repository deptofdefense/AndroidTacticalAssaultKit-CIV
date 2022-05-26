package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;

public final class LiteralWFSSchemaFeatureNameResolver implements WFSSchemaFeatureNameResolver {
    private final String name;
    
    public LiteralWFSSchemaFeatureNameResolver(String name) {
        this.name = name;
    }
    
    @Override
    public String getName(AttributeSet attributes) {
        return this.name;
    }
    
    public String toString() {
        return name;
    }
}
