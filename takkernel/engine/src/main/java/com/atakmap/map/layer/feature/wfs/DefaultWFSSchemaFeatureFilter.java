package com.atakmap.map.layer.feature.wfs;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

public class DefaultWFSSchemaFeatureFilter implements WFSSchemaFeatureFilter {

    protected Collection<AttributeSetFilter> filters;
    protected boolean shouldIgnore;
    protected boolean isVisible;
    protected WFSSchemaFeatureNameResolver nameResolver;
    protected Map<Class<? extends Geometry>, WFSSchemaFeatureStyler> stylers;

    public DefaultWFSSchemaFeatureFilter(boolean shouldIgnore, boolean isVisible) {
        this.nameResolver = null;
        this.shouldIgnore = shouldIgnore;
        this.isVisible = isVisible;
        this.filters = new HashSet<AttributeSetFilter>();
        this.stylers = new HashMap<Class<? extends Geometry>, WFSSchemaFeatureStyler>();
    }
    
    public void setNameResolver(WFSSchemaFeatureNameResolver nameResolver) {
        this.nameResolver = nameResolver;
    }

    public void addFilter(AttributeSetFilter filter) {
        this.filters.add(filter);
    }

    public void addStyler(Class<? extends Geometry> geomType, WFSSchemaFeatureStyler styler) {
        this.stylers.put(geomType, styler);
    }

    /**************************************************************************/
    // WFSSchemaFeatureFilter

    @Override
    public boolean matches(AttributeSet metadata) {
        for(AttributeSetFilter filter : this.filters)
            if(!filter.matches(metadata))
                return false;
        return true;
    }

    @Override
    public boolean shouldIgnore() {
        return this.shouldIgnore;
    }

    @Override
    public boolean isVisible() {
        return this.isVisible;
    }

    @Override
    public Style getStyle(AttributeSet metadata, Class<? extends Geometry> geomType) {
        final WFSSchemaFeatureStyler styler = this.stylers.get(geomType);
        if(styler == null)
            return null;
        return styler.getStyle(metadata);
    }

    @Override
    public String getName(AttributeSet metadata) {
        if(this.nameResolver != null)
            return this.nameResolver.getName(metadata);
        return null;
    }
    
    public String toString() {
        return "feature {name=" + nameResolver + ",styles=" + stylers + ",filters=" + filters + ",visible=" + isVisible + ",ignore=" + shouldIgnore + "}";
    }
} // DefaultWFSSchemaFeatureNameHandler
