
package com.atakmap.android.attachment;

import com.atakmap.android.image.GalleryFileItem;
import com.atakmap.android.image.GalleryItem;
import com.atakmap.android.image.gallery.GalleryContentProvider;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides attachments for the gallery
 */
public class AttachmentGalleryProvider extends GalleryContentProvider {

    private final MapView _mapView;

    public AttachmentGalleryProvider(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public String getName() {
        return _mapView.getContext().getString(R.string.attachments);
    }

    @Override
    public List<GalleryItem> getItems() {
        // TODO: More efficient way of grabbing all map items and associated files
        //  The current method loops through all attachment directories, lists
        //  all the files in each directory, then looks up the associated map items,
        //  then list all the files in each directory a 2nd time.
        //  File I/O calls (especially listing) are not cheap performance-wise
        List<MapItem> items = AttachmentManager.findAttachmentItems(
                _mapView.getRootGroup());

        //now gather list of attachments for each marker
        List<GalleryItem> attachments = new ArrayList<>();
        for (MapItem m : items) {

            // Skip invisible map items (see ATAK-14894)
            if (!m.getVisible())
                continue;

            // Get all attachment files for this map item
            List<File> files = AttachmentManager.getAttachments(m.getUID());

            // No attachments - skip
            if (FileSystemUtils.isEmpty(files))
                continue;

            // Add attachment files
            for (File attachment : files) {
                //skip child directories
                if (!FileSystemUtils.isFile(attachment)
                        || IOProviderFactory.isDirectory(attachment))
                    continue;
                attachments.add(new GalleryFileItem(_mapView, attachment, m));
            }
        }

        return attachments;
    }
}
