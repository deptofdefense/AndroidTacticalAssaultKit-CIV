
package com.atakmap.android.importexport;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExporterManager {

    private static final String TAG = "ExporterManager";

    public static class ExportMarshalMetadata {
        private final String type;
        private final Class<? extends ExportMarshal> clazz;
        private final Drawable icon;

        private ExportMarshalMetadata(String type, Drawable icon,
                Class<? extends ExportMarshal> clazz) {
            super();
            this.type = type;
            this.icon = icon;
            this.clazz = clazz;
        }

        public String getType() {
            return type;
        }

        public Drawable getIcon() {
            return icon;
        }

        public Class<? extends ExportMarshal> getTargetClass() {
            return clazz;
        }

        public ExportMarshal getExportMarshal(Context context) {
            try {
                Constructor<?> ctor = getTargetClass().getConstructor(
                        Context.class);
                return (ExportMarshal) ctor.newInstance(context);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Exporter type: " + type, e);
                return null;
            }
        }
    }

    private final static Map<String, ExportMarshalMetadata> exporters = new HashMap<>();

    private ExporterManager() {
    }

    public static synchronized void registerExporter(String type, Drawable icon,
            Class<? extends ExportMarshal> clazz) {
        if (FileSystemUtils.isEmpty(type) || clazz == null)
            return;

        Log.d(TAG, "Adding exporter: " + type + ", " + clazz.getName());
        exporters.put(type, new ExportMarshalMetadata(type, icon, clazz));
    }

    public static synchronized void registerExporter(String type, int iconId,
            Class<? extends ExportMarshal> clazz) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        registerExporter(type, mv.getContext().getDrawable(iconId), clazz);
    }

    public static synchronized void unregisterExporter(String type) {
        if (FileSystemUtils.isEmpty(type))
            return;

        exporters.remove(type);
    }

    public static synchronized ExportMarshal findExporter(Context context,
            String type) {
        if (FileSystemUtils.isEmpty(type))
            return null;

        ExportMarshalMetadata meta = exporters.get(type);
        if (meta == null || meta.getTargetClass() == null) {
            Log.e(TAG, "Exporter type not found: " + type);
            return null;
        }

        return meta.getExportMarshal(context);
    }

    /**
     * Get alphabetical list of exporters 
     * 
     * @return
     */
    public static List<ExportMarshalMetadata> getExporterTypes() {
        List<ExportMarshalMetadata> types;
        synchronized (ExporterManager.class) {
            types = new ArrayList<>(exporters.values());
            if (exporters.size() < 1)
                return types;
        }
        Collections.sort(types, AlphabeticComparator);
        return types;
    }

    private static final Comparator<ExportMarshalMetadata> AlphabeticComparator = new Comparator<ExportMarshalMetadata>() {

        @Override
        public int compare(ExportMarshalMetadata lhs,
                ExportMarshalMetadata rhs) {
            return lhs.getType().compareTo(rhs.getType());
        }
    };
}
