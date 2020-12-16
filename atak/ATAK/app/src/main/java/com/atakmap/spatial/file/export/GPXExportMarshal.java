
package com.atakmap.spatial.file.export;

import android.content.Context;

import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.gpx.GpxBase;
import com.atakmap.android.gpx.GpxRoute;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.GpxFileSpatialDb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Marshals <code>Export</code> instances to a GPX file
 * 
 * 
 */
public class GPXExportMarshal extends ExportFileMarshal {

    private static final String TAG = "GPXExportMarshal";

    protected final List<GpxBase> exports = new ArrayList<>();

    public GPXExportMarshal(Context context) {
        super(context, GpxFileSpatialDb.GPX_CONTENT_TYPE,
                GpxFileSpatialDb.GPX_FILE_MIME_TYPE,
                GpxFileSpatialDb.GPX_FILE_ICON_ID);
    }

    @Override
    public Class<?> getTargetClass() {
        return GPXExportWrapper.class;
    }

    @Override
    public File getFile() {
        return new File(
                FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY),
                filename);
    }

    @Override
    protected boolean marshal(Exportable export)
            throws FormatNotSupportedException {
        if (export == null || !export.isSupported(GPXExportWrapper.class)) {
            Log.d(TAG, "Skipping unsupported export "
                    + (export == null ? "" : export.getClass().getName()));
            return false;
        }

        GPXExportWrapper folder = (GPXExportWrapper) export.toObjectOf(
                GPXExportWrapper.class, getFilters());
        if (folder == null || folder.isEmpty()) {
            Log.d(TAG, "Skipping empty folder");
            return false;
        }
        Log.d(TAG, "Adding folder name: " + folder.getName());
        this.exports.addAll(folder.getExports());

        Log.d(TAG, "Added " + folder.getName() + ", feature count: "
                + folder.getExports().size());
        return true;
    }

    protected Gpx getGpx() throws IOException {
        if (exports == null || exports.size() < 1)
            throw new IOException("No features");

        List<GpxRoute> routes = new ArrayList<>();
        List<GpxTrack> tracks = new ArrayList<>();
        List<GpxWaypoint> waypoints = new ArrayList<>();

        for (GpxBase gb : this.exports) {

            //note, we are currently storing UID in desc
            //String uid = gb.getDesc();
            //if(!FileSystemUtils.isEmpty(uid) && exportedUIDs.contains(uid)){
            //    Log.d(TAG, "Skipping duplicate UID: " + uid);
            //    continue;
            //}            
            //exportedUIDs.add(uid);

            if (gb instanceof GpxWaypoint) {
                waypoints.add((GpxWaypoint) gb);
            } else if (gb instanceof GpxTrack) {
                tracks.add((GpxTrack) gb);
            } else if (gb instanceof GpxRoute) {
                routes.add((GpxRoute) gb);
            } else {
                Log.w(TAG, "Skipping unsupported GPX type: "
                        + gb.getClass().getName());
            }
        } //end folder loop

        if (routes.size() == 0 && tracks.size() == 0 && waypoints.size() == 0) {
            return null;
        }

        Gpx gpx = new Gpx();
        if (routes.size() > 0) {
            Log.d(TAG, "Exporting routes: " + routes.size());
            gpx.setRoutes(routes);
        }
        if (tracks.size() > 0) {
            Log.d(TAG, "Exporting tracks: " + tracks.size());
            gpx.setTracks(tracks);
        }
        if (waypoints.size() > 0) {
            Log.d(TAG, "Exporting waypoints: " + waypoints.size());
            gpx.setWaypoints(waypoints);
        }

        return gpx;
    }

    @Override
    public void finalizeMarshal() throws IOException {
        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }

        Gpx gpx = getGpx();
        if (gpx == null) {
            throw new IOException("Failed to serialize GPX");
        }

        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }
        if (hasProgress()) {
            this.progress.publish(94);
        }

        // delete existing file, and then serialize KML out to file
        File file = getFile();
        if (IOProviderFactory.exists(file)) {
            FileSystemUtils.deleteFile(file);
        }

        try {
            RouteGpxIO.write(gpx, file);
        } catch (Exception e) {
            throw new IOException("Failed to serialize GPX", e);
        }

        Log.d(TAG, "Exported: " + file.getAbsolutePath());
    }
}
