
package com.atakmap.android.user.filter;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * List of nestable overlay configurations
 * 
 * 
 */
@Root(name = "overlays")
public class MapOverlayFilters {

    private static final String TAG = "MapOverlayFilters";

    @ElementList(entry = "overlay", inline = true, required = true)
    private List<MapOverlayConfig> _overlays;

    @Attribute(name = "version")
    private static final int VERSION = 1;

    public MapOverlayFilters() {
        _overlays = new ArrayList<>();
    }

    public List<MapOverlayConfig> getOverlays() {
        if (_overlays == null)
            _overlays = new ArrayList<>();

        return _overlays;
    }

    public boolean isValid() {
        if (FileSystemUtils.isEmpty(_overlays))
            return false;

        for (MapOverlayConfig o : _overlays) {
            if (o == null || !o.isValid())
                return false;
        }

        return true;
    }

    public static MapOverlayFilters Load(InputStream input) {
        MapOverlayFilters overlays = null;
        try {
            Serializer serializer = new Persister();
            overlays = serializer.read(MapOverlayFilters.class, input);
            Log.d(TAG, "Loaded " + overlays.getOverlays().size()
                    + " top level filter overlays");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load overlays", e);
        }

        return overlays;
    }

    public List<MapOverlayFilter> getAllFilters() {
        List<MapOverlayFilter> filters = new ArrayList<>();
        for (MapOverlayConfig config : getOverlays()) {
            List<MapOverlayFilter> f = config.getAllFilters();
            if (!FileSystemUtils.isEmpty(f))
                filters.addAll(f);
        }

        return filters;
    }
}
