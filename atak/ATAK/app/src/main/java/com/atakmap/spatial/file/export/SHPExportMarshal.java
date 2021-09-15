
package com.atakmap.spatial.file.export;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.importfiles.sort.ImportSHPSort;
import com.atakmap.android.importfiles.sort.ImportSHPZSort;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper.NamedGeometry;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.ZipOutputStream;

/**
 * Marshals <code>Export</code> instances to a SHP file
 * 
 * 
 */
public class SHPExportMarshal extends OGRExportMarshal {

    private static final String TAG = "SHPExportMarshal";

    private File exportedFile;

    public SHPExportMarshal(Context context) {
        super(context, ShapefileSpatialDb.SHP_CONTENT_TYPE,
                ShapefileSpatialDb.SHP_FILE_MIME_TYPE,
                "ESRI Shapefile", ShapefileSpatialDb.SHP_FILE_ICON_ID);
        exportedFile = null;
    }

    @Override
    public File getFile() throws IOException {
        //once exported, the data is zipped
        if (exportedFile != null)
            return exportedFile;

        //put files in subfolder of "export"
        //TODO if we just provide a directory, will GDAL name all layers based on layer name?

        //OGR creates separate shapefile for each layer, so if we have
        //multiple layers, grab name of first layer. Other files will be
        //named by OGR based on layer name
        String tempFilename = filename;
        if (geometries.size() > 1) {
            for (Entry<Pair<String, Integer>, List<NamedGeometry>> g : geometries
                    .entrySet()) {
                if (g.getValue() != null && g.getValue().size() > 0) {
                    tempFilename = g.getKey().first + "."
                            + ShapefileSpatialDb.SHP_TYPE;
                    break;
                }
            }
        }

        String path = FileSystemUtils.stripExtension(filename);
        if (FileSystemUtils.isEmpty(path)) {
            path = FileSystemUtils.EXPORT_DIRECTORY;
        } else {
            path = FileSystemUtils.EXPORT_DIRECTORY + File.separatorChar + path;
        }
        return new File(FileSystemUtils.getItem(path),
                FileSystemUtils.validityScan(tempFilename));
    }

    @Override
    protected String getExtension() {
        return ShapefileSpatialDb.SHP_TYPE;
    }

    /**
     * Compress the base shapefile and all related files into a ZIP file
     * @param baseSHP the base shape file to be zipped for export
     * @return the zipped file, null if the baseSHP file is missing.
     * @throws IOException if unable to write the zip file
     */
    public static File zipShapefile(File baseSHP) throws IOException {
        if (!FileSystemUtils.isFile(baseSHP)) {
            Log.w(TAG, "Cannot zip missing SHP file");
            return null;
        }

        if (FileSystemUtils.checkExtension(baseSHP, "zip")) {
            Log.d(TAG,
                    "Shapefile already zipped: " + baseSHP.getAbsolutePath());
            return baseSHP;
        }

        List<File> files = ImportSHPSort.GetShapefileMemberFiles(baseSHP);

        File shpz = new File(baseSHP.getAbsolutePath() + ".zip");
        if (files != null) {
            try (ZipOutputStream zos = FileSystemUtils
                    .getZipOutputStream(shpz)) {

                //loop and add all files
                for (File file : files) {
                    FileSystemUtils.addFile(zos, file);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create SHPZ file", e);
                throw new IOException(e);
            }
        }

        //validate the required files
        if (ImportSHPZSort.HasSHP(shpz)) {
            Log.d(TAG, "Exported: " + shpz.getAbsolutePath());
            return shpz;
        } else {
            Log.w(TAG,
                    "Failed to export valid shapefile: "
                            + shpz.getAbsolutePath());
            return null;
        }
    }

    /**
     * Compress the directory contents into a ZIP file
     * 
     * @param dir the directory
     * @return the zipped file or null if there is a failure
     * @throws IOException error if unable to create a zipped file
     */
    public static File zipDirectory(final File dir) throws IOException {
        File shpz = FileSystemUtils.zipDirectory(dir,
                new File(dir, dir.getName() + "_shapefile.zip"));
        if (!FileSystemUtils.isFile(shpz)) {
            Log.w(TAG, "Failed to zip SHP file");
            return null;
        }

        //validate the required files
        if (ImportSHPZSort.HasSHP(shpz)) {
            Log.d(TAG, "Exported: " + shpz.getAbsolutePath());
            return shpz;
        } else {
            Log.w(TAG,
                    "Failed to export valid shapefile: "
                            + shpz.getAbsolutePath());
            return null;
        }
    }

    @Override
    protected void cancelMarshal() {
        //delete the entire directory

        //get path to first shapefile
        File file = null;
        try {
            file = getFile();
        } catch (IOException e) {
            Log.e(TAG, "error occurred", e);
        }
        if (file != null) {
            //folder containing partially exported shapefiles
            File parent = file.getParentFile();
            if (parent != null) {
                //shpexport folder
                parent = file.getParentFile();
                if (parent != null
                        && IOProviderFactory.exists(parent)
                        && FileSystemUtils.EXPORT_DIRECTORY.equals(parent
                                .getName())) {
                    FileSystemUtils.deleteDirectory(parent, false);
                    return;
                }
            }
            Log.d(TAG,
                    "Did not delete canceled export: "
                            + file.getAbsolutePath());
        }
    }

    @Override
    public void postMarshal() {
        //zip up the export prior to transfer

        try {
            exportedFile = getFile();
            exportedFile = SHPExportMarshal.zipDirectory(exportedFile
                    .getParentFile());
        } catch (IOException e) {
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    contentType + " Export Failed",
                    "Failed to compress shapefile",
                    "Failed to compress shapefile");
            return;
        }

        if (FileSystemUtils.isFile(exportedFile)) {
            Log.d(TAG,
                    "Zipped exported shapefile: "
                            + exportedFile.getAbsolutePath());
        } else {
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    contentType + " Export Failed",
                    "Failed to compress shapefile",
                    "Failed to compress shapefile");
            return;
        }

        //now allow parent to notify user and/or send the file
        super.postMarshal();
    }
}
