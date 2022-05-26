
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.app.R;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.ImageryFileType;

import java.io.File;

public class ImportLayersSort extends ImportInPlaceResolver {

    public ImportLayersSort(Context context) {
        super(null, null, false, false, true,
                context.getString(R.string.imagery),
                context.getDrawable(R.drawable.ic_menu_maps));
    }

    @Override
    public String getExt() {
        // There are many different extensions, as well as directories without extensions
        // supported, so return null here.
        return null;
    }

    @Override
    public boolean match(File file) {

        // Check file type
        ImageryFileType.AbstractFileType fileType = ImageryFileType
                .getFileType(file);

        // DTED is not considered an imagery layer in ATAK
        if (fileType != null && fileType.getID() == ImageryFileType.DTED)
            return false;

        // Check if any of the imagery SPIs support
        return DatasetDescriptorFactory2.isSupported(file);
    }

    @Override
    public boolean directoriesSupported() {
        return true;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(
                LayersMapComponent.IMPORTER_CONTENT_TYPE,
                LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
    }
}
