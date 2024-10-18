
package com.atakmap.android.rubbersheet.data.export;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.BitmapPyramid;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.File;
import java.io.IOException;

/**
 * Task for creating a rubber sheet from an image file
 */
public class ExportGRGTask extends ExportFileTask {

    private static final String TAG = "ExportGRGTask";

    private final RubberImage _item;

    public ExportGRGTask(MapView mapView, RubberImage item, Callback callback) {
        super(mapView, item, callback);
        _item = item;
    }

    @Override
    protected String getProgressMessage() {
        return _context.getString(R.string.exporting_kmz, _item.getTitle());
    }

    @Override
    protected int getProgressStages() {
        return 0;
    }

    @Override
    protected File doInBackground(Void... params) {
        BitmapPyramid image = _item.getImage();
        if (image == null)
            return null;

        // Create temporary zip directory
        File img = image.getFile();
        File tmpDir = new File(RubberSheetManager.DIR, ".tmp_"
                + image.getFile().getName());
        if (IOProviderFactory.exists(tmpDir))
            FileSystemUtils.delete(tmpDir);
        if (!IOProviderFactory.mkdirs(tmpDir)) {
            Log.d(TAG, "Failed to create temp dir: " + tmpDir);
            return null;
        }
        addOutputFile(tmpDir);

        // Create KML document
        String name = _item.getTitle();
        StringBuilder cs = new StringBuilder();
        GeoPointMetaData[] p = _item.getGeoPoints();
        if (p.length < 4)
            return null;

        // Assuming points are in clockwise order starting from north-west
        // Need to be counter-clockwise order starting from south-west for KML
        GeoBounds bnds = _item.getBounds(null);
        bnds.setWrap180(_wrap180);
        for (int i = 3; i >= 0; i--) {
            double lng = p[i].get().getLongitude();
            if (lng > 0 && bnds.crossesIDL())
                lng -= 360;
            cs.append(lng).append(",");
            cs.append(p[i].get().getLatitude()).append(",0");
            if (i > 0)
                cs.append(" ");
        }
        String docSkel = String.format(LocaleUtil.getCurrent(), DOC_KML,
                CotEvent.escapeXmlText(name),
                CotEvent.escapeXmlText(img.getName()), cs);
        writeToFile(new File(tmpDir, "doc.kml"), docSkel);

        if (isCancelled())
            return null;

        // Copy image
        try {
            FileSystemUtils.copyFile(img, new File(tmpDir, img.getName()));
        } catch (IOException ioe) {
            Log.e(TAG, "error occurred copying image: " + img, ioe);
            return null;
        }

        if (isCancelled())
            return null;

        if (name.contains("."))
            name = name.substring(0, name.lastIndexOf("."));

        // perform file name sanity check
        name = FileSystemUtils.sanitizeFilename(name);

        File kmzFile = new File(RubberSheetManager.DIR, name + ".kmz");
        addOutputFile(kmzFile);
        try {
            FileSystemUtils.zipDirectory(tmpDir, kmzFile);
        } catch (IOException ioe) {
            Log.e(TAG, "error occurred zipping KMZ: " + kmzFile, ioe);
            return null;
        }

        // Cleanup
        FileSystemUtils.delete(tmpDir);

        return kmzFile;
    }

    private static final String DOC_KML = "<?xml version='1.0' encoding='UTF-8'?>\n"
            +
            "<kml xmlns='http://www.opengis.net/kml/2.2' " +
            "xmlns:gx='http://www.google.com/kml/ext/2.2' " +
            "xmlns:kml='http://www.opengis.net/kml/2.2' " +
            "xmlns:atom='http://www.w3.org/2005/Atom'>\n" +
            "<GroundOverlay>\n" +
            "  <name>%1$s</name>\n" +
            "  <Icon>\n" +
            "    <href>%2$s</href>\n" +
            "    <viewBoundScale>0.75</viewBoundScale>\n" +
            "  </Icon>\n" +
            "  <gx:LatLonQuad>\n" +
            "    <coordinates>%3$s</coordinates>\n" +
            "  </gx:LatLonQuad>\n" +
            "</GroundOverlay>\n" +
            "</kml>\n";
}
