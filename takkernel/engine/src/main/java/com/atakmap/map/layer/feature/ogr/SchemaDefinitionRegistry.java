package com.atakmap.map.layer.feature.ogr;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.gdal.ogr.FeatureDefn;

public final class SchemaDefinitionRegistry {

    private static Set<SchemaDefinition> definitions = new HashSet<SchemaDefinition>();
    
    private SchemaDefinitionRegistry() {}
    
    public static synchronized void register(SchemaDefinition defn) {
        definitions.add(defn);
    }
    
    public static synchronized void unregister(SchemaDefinition defn) {
        definitions.remove(defn);
    }

    public static synchronized SchemaDefinition getDefinition(File file, FeatureDefn layerDefn) {
        for(SchemaDefinition defn : definitions)
            if(defn.matches(file, layerDefn))
                return defn;
        return null;
    }
}
