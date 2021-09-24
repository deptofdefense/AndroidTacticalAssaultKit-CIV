
package com.atakmap.android.attachment.export;

import com.atakmap.android.attachment.AttachmentMapOverlay;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.List;

public class AttachmentExportWrapper {
    /**
     * List of exports
     */
    private final List<AttachmentMapOverlay.MapItemAttachment> _exports;

    public AttachmentExportWrapper() {
        _exports = new ArrayList<>();
    }

    public AttachmentExportWrapper(AttachmentMapOverlay.MapItemAttachment e) {
        _exports = new ArrayList<>();
        _exports.add(e);
    }

    public List<AttachmentMapOverlay.MapItemAttachment> getExports() {
        return _exports;
    }

    /**
     * Add all data from the specified folder to 'this' folder
     * @param folder the folder to add
     */
    public void add(AttachmentExportWrapper folder) {
        if (folder == null)
            return;

        if (!FileSystemUtils.isEmpty(folder.getExports()))
            _exports.addAll(folder.getExports());
    }

    /**
     * Returns a boolean based on the size of the export list.
     * @return true if the number of exports is 0 or false if the number of exports is greater than 0.
     */
    public boolean isEmpty() {
        return FileSystemUtils.isEmpty(_exports);
    }
}
