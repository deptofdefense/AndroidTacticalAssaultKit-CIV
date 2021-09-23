package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;

public interface WFSSchemaFeatureNameResolver {
    public String getName(AttributeSet metadata);
}
