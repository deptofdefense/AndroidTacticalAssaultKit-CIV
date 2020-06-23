
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.app.R;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;

import java.io.File;

public class ImportLayersSort extends ImportInPlaceResolver {

    public ImportLayersSort(Context context) {
        super(null, null, false, false, true, context
                .getString(R.string.imagery));
    }

    @Override
    public String getExt() {
        // There are many different extensions, as well as directories without extensions
        // supported, so return null here.
        return null;
    }

    @Override
    public boolean match(File file) {
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
