package com.atakmap.map.layer.feature.ogr;

import java.io.File;
import java.util.List;

import org.gdal.ogr.FeatureDefn;

public interface SchemaDefinition {
    public boolean matches(File file, FeatureDefn fieldDefn);
    public List<String> getNameFieldCandidates(File file, FeatureDefn fieldDefn);
}
