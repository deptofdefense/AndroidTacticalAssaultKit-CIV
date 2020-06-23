
package com.atakmap.spatial.file.export;

import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.gpx.GpxBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for exporting map items to GPX
 * 
 * 
 */
public class GPXExportWrapper {

    /**
     * List of exports
     */
    private final List<GpxBase> _exports;

    private String _name;

    public GPXExportWrapper() {
        _exports = new ArrayList<>();
    }

    public GPXExportWrapper(GpxBase e) {
        _exports = new ArrayList<>();
        _exports.add(e);
    }

    public GPXExportWrapper(Gpx f) {
        _exports = new ArrayList<>();
        if (f == null)
            return;

        if (f.getWaypoints() != null && f.getWaypoints().size() > 0)
            _exports.addAll(f.getWaypoints());
        if (f.getTracks() != null && f.getTracks().size() > 0)
            _exports.addAll(f.getTracks());
        if (f.getRoutes() != null && f.getRoutes().size() > 0)
            _exports.addAll(f.getRoutes());
    }

    public List<GpxBase> getExports() {
        return _exports;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
    }

    /**
     * Add all data from the specified folder to 'this' folder
     * @param folder the folder to read from.
     */
    public void add(GPXExportWrapper folder) {
        if (folder == null)
            return;

        if (folder.getExports() != null && folder.getExports().size() > 0)
            _exports.addAll(folder.getExports());
    }

    public boolean isEmpty() {
        return _exports == null || _exports.size() < 1;
    }
}
