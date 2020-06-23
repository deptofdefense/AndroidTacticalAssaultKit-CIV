
package com.atakmap.android.grg;

import com.atakmap.map.layer.feature.ogr.SchemaDefinition;
import com.atakmap.map.layer.feature.ogr.SimpleSchemaDefinition;

import java.util.HashSet;
import java.util.Set;

public class MCIA_GRG {

    private final static Set<String> SECTIONS_DBF_SCHEMA = new HashSet<>();
    static {
        SECTIONS_DBF_SCHEMA.add("id");
        SECTIONS_DBF_SCHEMA.add("sector_id");
        SECTIONS_DBF_SCHEMA.add("shape_leng");
        SECTIONS_DBF_SCHEMA.add("shape_area");
    }
    private final static String SECTIONS_NAME_COLUMN = "sector_id";

    private final static Set<String> SUBSECTIONS_DBF_SCHEMA = new HashSet<>();
    static {
        SUBSECTIONS_DBF_SCHEMA.add("id");
        SUBSECTIONS_DBF_SCHEMA.add("subsector_");
        SUBSECTIONS_DBF_SCHEMA.add("shape_leng");
        SUBSECTIONS_DBF_SCHEMA.add("shape_area");
    }
    private final static String SUBSECTIONS_NAME_COLUMN = "subsector_";

    private final static Set<String> BUILDINGS_DBF_SCHEMA = new HashSet<>();
    static {
        BUILDINGS_DBF_SCHEMA.add("id");
        BUILDINGS_DBF_SCHEMA.add("sector_id");
        BUILDINGS_DBF_SCHEMA.add("subsector_");
        BUILDINGS_DBF_SCHEMA.add("compound_i");
        BUILDINGS_DBF_SCHEMA.add("regions");
    }
    private final static String BUILDINGS_NAME_COLUMN = "compound_i";

    final static SchemaDefinition SECTIONS_SCHEMA_DEFN = new SimpleSchemaDefinition(
            SECTIONS_NAME_COLUMN, SECTIONS_DBF_SCHEMA);
    final static SchemaDefinition SUBSECTIONS_SCHEMA_DEFN = new SimpleSchemaDefinition(
            SUBSECTIONS_NAME_COLUMN, SUBSECTIONS_DBF_SCHEMA);
    final static SchemaDefinition BUILDINGS_SCHEMA_DEFN = new SimpleSchemaDefinition(
            BUILDINGS_NAME_COLUMN, BUILDINGS_DBF_SCHEMA);

    private MCIA_GRG() {
    }
}
