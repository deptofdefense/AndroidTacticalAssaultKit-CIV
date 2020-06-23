
package com.atakmap.android.image;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;

public interface GalleryItem {
    TiffImageMetadata getExif();

    String getName();

    String getUID();

    String getURI();

    String getAuthor();
}
