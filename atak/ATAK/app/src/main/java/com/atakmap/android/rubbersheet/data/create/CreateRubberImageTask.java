
package com.atakmap.android.rubbersheet.data.create;

import android.graphics.Bitmap;

import com.atakmap.android.image.nitf.NITFHelper;
import com.atakmap.android.imagecapture.TiledCanvas;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.RubberImageData;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.gdal.GdalLibrary;

import org.gdal.gdal.Dataset;
import org.gdal.gdalconst.gdalconst;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Task for creating a rubber sheet from an image file
 */
public class CreateRubberImageTask extends AbstractCreationTask {

    private static final String TAG = "CreateRubberImageTask";

    public CreateRubberImageTask(MapView mapView, RubberImageData data,
            boolean background, Callback callback) {
        super(mapView, data, background, callback);
    }

    @Override
    protected String getProgressMessage() {
        return _context.getString(R.string.creating_rubber_sheet,
                _data.file.getName());
    }

    @Override
    protected int getProgressStages() {
        return 0;
    }

    @Override
    public String getFailMessage() {
        return _context.getString(R.string.failed_to_read_image,
                _data.file.getName());
    }

    @Override
    protected RubberImage doInBackground(Void... params) {
        if (super.doInBackground(params) != Boolean.TRUE)
            return null;

        File f = _data.file;
        if (!ImageFileFilter.accept(null, f.getName())) {
            Log.d(TAG, "File is not an image: " + f);
            return null;
        }

        String name = f.getName();
        String ext = name.substring(name.lastIndexOf(".") + 1).toLowerCase(
                LocaleUtil.getCurrent());
        name = name.substring(0, name.lastIndexOf("."));

        File dir = RubberSheetManager.DIR;
        String label = null;
        GeoPoint[] points = null;
        if (ext.equals("ntf") || ext.equals("nitf") || ext.equals("nsf")) {
            // Retrieve geo transform and name
            Dataset ds = GdalLibrary.openDatasetFromFile(f,
                    gdalconst.GA_ReadOnly);
            if (ds != null) {
                label = ds.GetMetadataItem(NITFHelper.FILE_TITLE);
                points = getTransform(ds);
                ds.delete();
            }

            // Copy out image to JPEG file
            TiledCanvas tc = new TiledCanvas(f, 1024, 1024);
            File outFile = new File(dir, name + ".jpg");
            if (tc.copyToFile(outFile, Bitmap.CompressFormat.JPEG, 100)) {
                Log.d(TAG, "Successfully copied NITF to JPG: " + outFile);
                f = outFile;
            } else {
                Log.e(TAG, "Failed to copy NITF to JPG: " + outFile);
                return null;
            }
        } else if (ext.equals("kmz")) {
            File destDir = new File(dir, name + "_extracted");
            try {
                // Extract KMZ content
                FileSystemUtils.extract(f, destDir, true);
                File doc = new File(destDir, "doc.kml");
                if (!IOProviderFactory.exists(doc)) {
                    Log.e(TAG, "Invalid KMZ: " + f);
                    return null;
                }

                // Parse doc.kml
                KMLParser parser = new KMLParser(doc);

                // Move image out of temp directory
                File img = parser.getImageFile();
                if (img == null || !IOProviderFactory.exists(img)
                        || !ImageFileFilter.accept(
                                null, img.getName())) {
                    Log.e(TAG, "KMZ missing valid image: " + f);
                    return null;
                }
                File imgDest = new File(dir, img.getName());
                if (!IOProviderFactory.renameTo(img, imgDest)) {
                    Log.e(TAG, "Failed to move KMZ image " + img
                            + " to " + imgDest);
                    return null;
                }

                // Retrieve name and coordinates
                label = parser.getName();
                points = parser.getCoordinates();
                f = imgDest;
            } catch (Exception e) {
                Log.e(TAG, "Failed to extract KMZ archive: " + f, e);
                return null;
            } finally {
                FileSystemUtils.delete(destDir);
            }
        }

        if (isCancelled())
            return null;

        RubberImageData data = new RubberImageData(f);
        data.points = points;
        RubberImage rs = RubberImage.create(_mapView, data);
        if (rs != null) {
            if (!FileSystemUtils.isEmpty(label))
                rs.setTitle(label.trim());
            rs.setLoadState(LoadState.SUCCESS);
        }
        return rs;
    }

    @Override
    protected void onCancelled() {
        toast(R.string.read_image_cancelled, _data.file.getName());
    }

    private static final FilenameFilter ImageFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            if (FileSystemUtils.isEmpty(filename))
                return false;

            filename = filename.toLowerCase(LocaleUtil.getCurrent());
            for (String ext : RubberImageData.EXTS) {
                if (filename.endsWith("." + ext))
                    return true;
            }
            return false;
        }
    };

    private static GeoPoint[] getTransform(Dataset nitf) {
        if (nitf == null)
            return null;
        List<GeoPoint> points = new ArrayList<>();
        String coordRep = nitf.GetMetadataItem(NITFHelper.COORDINATE_SYSTEM);
        String coordStr = nitf.GetMetadataItem(NITFHelper.COORDINATE_STRING);
        if (!FileSystemUtils.isEmpty(coordStr)
                && !FileSystemUtils.isEmpty(coordRep)
                && coordStr.length() >= 60) {
            GeoPoint lastPoint = null;
            for (int i = 0; i < 60; i += 15) {
                String coord = coordStr.substring(i, i + 15);
                GeoPoint gp = NITFHelper.readCoordinate(coordRep, coord);
                if (gp != null) {
                    if (lastPoint != null && lastPoint.equals(gp)) {
                        // Duplicate point means the bounds are probably invalid
                        // or constrained to a single point
                        break;
                    }
                    points.add(gp);
                    lastPoint = gp;
                }
            }
        }
        return points.size() == 4 ? points.toArray(new GeoPoint[4]) : null;
    }
}
