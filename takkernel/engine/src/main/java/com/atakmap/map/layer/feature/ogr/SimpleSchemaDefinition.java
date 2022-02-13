package com.atakmap.map.layer.feature.ogr;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;

public class SimpleSchemaDefinition implements SchemaDefinition {

    protected final List<String> nameColumn;
    protected final Set<String> columnNames;
    
    
    public SimpleSchemaDefinition(String nameColumn, Set<String> columns) {
        this.nameColumn = Collections.singletonList(nameColumn);
        this.columnNames = new HashSet<String>();
        for(String column : columns)
            this.columnNames.add(column.toLowerCase(LocaleUtil.getCurrent()));
    }
    
    @Override
    public List<String> getNameFieldCandidates(File ogrFile, FeatureDefn layerDefn) {
        return this.nameColumn;
    }
    
    @Override
    public boolean matches(File file, FeatureDefn layerDefn) {
        List<String> schema = new LinkedList<String>();

        int layerDefnCount = layerDefn.GetFieldCount();
        FieldDefn fieldDef;
        String fieldName;
        for (int i = 0; i < layerDefnCount; i++) {
            fieldDef = layerDefn.GetFieldDefn(i);
            if (fieldDef == null)
                continue;
            fieldName = fieldDef.GetName();
            if (fieldName == null || fieldName.trim().length() < 1)
                continue;
            schema.add(fieldName.toLowerCase(LocaleUtil.getCurrent()));
        }

        return (this.columnNames.size() == schema.size() && this.columnNames.containsAll(schema));
    }
}
