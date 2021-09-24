package com.atakmap.map.layer.feature.wfs;

import java.util.Collection;

import com.atakmap.map.layer.feature.AttributeSet;

public final class CompoundAttributeSetFilter implements AttributeSetFilter {

    private Collection<AttributeSetFilter> filters;
    
    public CompoundAttributeSetFilter(Collection<AttributeSetFilter> filters) {
        this.filters = filters;
    }

    @Override
    public boolean matches(AttributeSet metadata) {
        for(AttributeSetFilter filter : this.filters)
            if(!filter.matches(metadata))
                return false;
        return true;
    }
    
    public String toString() {
        return "{" + filters + "}";
    }
}
