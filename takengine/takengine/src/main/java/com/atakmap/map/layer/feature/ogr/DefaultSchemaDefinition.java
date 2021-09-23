package com.atakmap.map.layer.feature.ogr;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.ogr;

public class DefaultSchemaDefinition implements SchemaDefinition {

    public final static SchemaDefinition INSTANCE = new DefaultSchemaDefinition();

    @Override
    public List<String> getNameFieldCandidates(File file, FeatureDefn layerDefn) {
        // compile a list of all of the fields that contain the
        // substring "name" to derive the feature name
        List<String> nameFieldCandidates = new LinkedList<String>();
        boolean hasNameField = false;
        int layerDefnCount = 0;
        if (layerDefn != null) {
            layerDefnCount = layerDefn.GetFieldCount();
            FieldDefn fieldDef;
            String fieldName;
            for (int i = 0; i < layerDefnCount; i++) {
                fieldDef = layerDefn.GetFieldDefn(i);
                if (fieldDef == null || fieldDef.GetFieldType() != ogr.OFTString)
                    continue;
                fieldName = fieldDef.GetName();
                if (fieldName == null || fieldName.trim().length() < 1)
                    continue;
                fieldName = fieldName.toLowerCase(LocaleUtil.getCurrent());
                if (fieldName.equals("name"))
                    hasNameField = true;
                if (fieldName.contains("name"))
                    nameFieldCandidates.add(fieldName);
            }
        }

        // if there was an explicit "name" field, put it at the head
        // of the list
        if (hasNameField)
            nameFieldCandidates.add(0, "name");
        
        return nameFieldCandidates;
    }

    @Override
    public boolean matches(File file, FeatureDefn fieldDefn) {
        return true;
    }
}
