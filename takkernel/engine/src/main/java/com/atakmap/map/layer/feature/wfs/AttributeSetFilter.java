package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;

public interface AttributeSetFilter {
    public boolean matches(AttributeSet metadata);
}
