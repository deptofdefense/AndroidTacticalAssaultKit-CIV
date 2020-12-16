
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
        List<MapItem> items = AttachmentManager.findAttachmentItems(
                _mapView.getRootGroup());

        //now gather list of attachments for each marker
        List<GalleryItem> attachments = new ArrayList<>();
        for (MapItem m : items) {
            List<File> files = AttachmentManager.getAttachments(m.getUID());
            if (FileSystemUtils.isEmpty(files))
                continue;
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
