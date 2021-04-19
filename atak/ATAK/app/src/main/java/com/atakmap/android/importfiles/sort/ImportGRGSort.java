
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.grg.MCIAGRGLayerInfoSpi;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.ImageryFileType;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;

import org.gdal.gdal.Dataset;

import java.io.File;

/**
 * Sorter class that handles GRG files and directories.
 */
public class ImportGRGSort extends ImportInPlaceResolver {

    // Max Length 100MB
    private static final int MAX_GDAL_LENGTH = 100 * 1024 * 1024;

    public ImportGRGSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(null, "grg", validateExt, copyFile, importInPlace,
                ResourceUtil.getString(context, R.string.civ_grg_file,
                        R.string.grg_file),
                context.getDrawable(R.drawable.ic_overlay_gridlines));

    }

    // This method currently handles four types of files: Small GeoTiff files,
    // KML/Z files that are properly formatted, small NITF files, and MCIAGRG style directories.
    @Override
    public boolean match(File file) {
        if (IOProviderFactory.isDirectory(file)) {
            if (MCIAGRGLayerInfoSpi.isMCIAGRG(file)) {
                return true;
            }
        } else {
            ImageryFileType.AbstractFileType type = ImageryFileType
                    .getFileType(file);

            // Unsupported file extension/mime type
            if (type == null)
                return false;

            // For PDF files, check if this is a GeoPDF
            if (type.getID() == ImageryFileType.PDF && !isGeoPDF(file))
                return false;

            // Check if path matches
            String path = type.getPath(file);
            if ("grg".equals(path)) {
                return true;
            }

            // If the file is a small nitf, it might be a GRG.
            if (type.getID() == ImageryFileType.GDAL &&
                    IOProviderFactory.length(file) < MAX_GDAL_LENGTH) {
                return true;
            }

            // The ImageryFileType getFileType method
            // returns "overlays" for KML/KMZ files.
            if (FileSystemUtils.OVERLAYS_DIRECTORY.equals(path)
                    && KmzLayerInfoSpi.containsTag(file, "GroundOverlay")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean directoriesSupported() {
        return true;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(GRGMapComponent.IMPORTER_CONTENT_TYPE,
                GRGMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
    }

    private boolean isGeoPDF(File file) {
        Dataset dataset = null;
        try {
            dataset = GdalLibrary.openDatasetFromFile(file);
            if (dataset == null)
                return false;
            return GdalDatasetProjection2.getInstance(dataset) != null;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (dataset != null)
                dataset.delete();
        }
    }
}
