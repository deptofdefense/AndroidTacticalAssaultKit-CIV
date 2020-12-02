
package com.atakmap.android.missionpackage.export;

import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for exporting map items into Mission Package
 * 
 * 
 */
public class MissionPackageExportWrapper implements Exportable {

    private final List<String> uids = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();

    public MissionPackageExportWrapper() {
    }

    // XXX - Why not just use MapItem or File???
    public MissionPackageExportWrapper(boolean bMapItem, String s) {
        if (bMapItem)
            uids.add(s);
        else
            paths.add(s);
    }

    public void addMapItem(MapItem item) {
        if (item != null)
            addUID(item.getUID());
    }

    public void addUID(String uid) {
        this.uids.add(uid);
    }

    /**
     * Get direct reference to the UIDs list
     * XXX - This is a stupid way of adding UIDs
     * Use {@link #addMapItem} or {@link #addUID}
     * @return Reference to UIDs list
     */
    public List<String> getUIDs() {
        return this.uids;
    }

    /**
     * Add a file to the export wrapper
     * @param f File to add
     */
    public void addFile(File f) {
        this.paths.add(f.getAbsolutePath());
    }

    /**
     * Get direct reference to the file paths list
     * XXX - This is a stupid way of adding paths - use {@link #addFile}
     * @return Reference to file paths list
     */
    public List<String> getFilepaths() {
        return this.paths;
    }

    public boolean isEmpty() {
        return this.uids.isEmpty() && this.paths.isEmpty();
    }

    // XXX - What is the point of this override?
    @Override
    public boolean isSupported(Class<?> target) {
        return getClass().equals(target);
    }

    // XXX - What is the point of this override?
    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        return getClass().equals(target) ? this : null;
    }
}
