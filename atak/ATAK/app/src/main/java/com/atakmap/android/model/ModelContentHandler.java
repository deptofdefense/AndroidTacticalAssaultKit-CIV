
package com.atakmap.android.model;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.SystemClock;

import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Send;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.model.viewer.DetailedModelViewerDropdownReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.obj.ObjUtils;
import com.atakmap.spatial.file.FileOverlayContentHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ModelContentHandler extends FileOverlayContentHandler implements
        ILocation, GoTo, Visibility, Send, Export {

    private static final String TAG = "ModelContentHandler";

    private final FeatureDataStore2 _dataStore;
    private long _fsid;

    ModelContentHandler(MapView mv, File file, FeatureDataStore2 dataStore,
            FeatureSet featureSet, Envelope bounds) {
        super(mv, file, bounds);
        _dataStore = dataStore;
        _fsid = featureSet.getId();
    }

    @Override
    public String getContentType() {
        return ModelImporter.CONTENT_TYPE;
    }

    @Override
    public String getMIMEType() {
        return "application/octet-stream";
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_model_building);
    }

    @Override
    public int getIconColor() {
        return _bounds != null ? Color.WHITE : Color.YELLOW;
    }

    @Override
    public boolean goTo(boolean select) {
        if (_bounds == null) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    DetailedModelViewerDropdownReceiver.SHOW_3D_VIEW)
                            .putExtra(
                                    DetailedModelViewerDropdownReceiver.EXTRAS_URI,
                                    getFile().getAbsolutePath()));
            return true;
        }
        return super.goTo(select);
    }

    @Override
    public boolean isVisible() {
        if (!isConditionVisible())
            return false;
        FeatureSetQueryParameters params = new FeatureSetQueryParameters();
        params.ids = Collections.singleton(_fsid);
        params.visibleOnly = true;
        try {
            return _dataStore.queryFeatureSetsCount(params) > 0;
        } catch (DataStoreException ignore) {
        }
        return false;
    }

    @Override
    public boolean setVisibleImpl(boolean visible) {
        FeatureSetQueryParameters params = new FeatureSetQueryParameters();
        params.ids = Collections.singleton(_fsid);
        try {
            _dataStore.setFeatureSetsVisible(params, visible);
        } catch (DataStoreException ignore) {
        }
        return visible;
    }

    @Override
    public boolean isActionSupported(Class<?> action) {
        // GoTo is allowed when bounds are missing
        return action != null && action.isInstance(this);
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (MissionPackageExportWrapper.class.equals(target)) {
            MissionPackageExportWrapper mp = new MissionPackageExportWrapper();
            String ext = FileSystemUtils.getExtension(_file, true, false);
            File f = ext.equals("OBJ") ? zipOBJ(false) : null;
            if (f == null)
                f = _file;
            mp.addFile(f);
            return mp;
        }
        return null;
    }

    @Override
    public boolean promptSend() {
        // Check if this model is ready to send
        String ext = FileSystemUtils.getExtension(_file, true, false);
        if (ext.equals("OBJ"))
            new OBJCompressionTask().execute();
        else
            promptSendImpl(_file);
        return true;
    }

    private void promptSendImpl(File f) {
        if (!FileSystemUtils.isFile(f))
            return;
        String baseName = f.getName();
        if (baseName.contains("."))
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        SendDialog.Builder b = new SendDialog.Builder(_mapView);
        b.setName(baseName);
        b.setIcon(getIcon());
        b.addFile(f);
        b.show();
    }

    private class OBJCompressionTask extends AsyncTask<Void, Integer, File> {

        private final ProgressDialog _pd;

        OBJCompressionTask() {
            _pd = new ProgressDialog(_context);
        }

        @Override
        protected void onPreExecute() {
            _pd.setMessage(_context.getString(
                    R.string.mission_package_compressing, _file.getName()));
            _pd.show();
        }

        @Override
        protected File doInBackground(Void... params) {
            return zipOBJ(true);
        }

        @Override
        protected void onPostExecute(File dest) {
            _pd.dismiss();
            promptSendImpl(dest);
        }
    }

    /**
     * Find all OBJ dependencies and ZIP
     * Note: This is a VERY expensive operation - should never be ran on UI
     * See ATAK-10510 - We need to do this to properly prepare unzipped models
     * for sending.
     * @param compress True to compress (true if sending, false if including within MP)
     */
    private File zipOBJ(boolean compress) {
        // Find dependencies for this model
        Set<File> dependencies = new HashSet<>();
        dependencies.add(_file);
        File dir = _file.getParentFile();

        long start = SystemClock.elapsedRealtime();

        String base = _file.getName();
        int lastDot = base.lastIndexOf('.');
        if (lastDot > -1)
            base = base.substring(0, lastDot);
        int s3dm = base.lastIndexOf("_simplified_3d_mesh");
        if (s3dm > -1)
            base = base.substring(0, s3dm);

        // OBJ file dependencies (.mtl, .xyz, .prj, textures)
        dependencies.add(FileSystemUtils.findFile(dir, base,
                new String[] {
                        "_offset.xyz", ".xyz"
                }));
        dependencies.add(FileSystemUtils.findFile(dir, base,
                new String[] {
                        "_wkt.prj", ".prj"
                }));

        // Find material dependencies in the .obj file
        File libFile = ObjUtils.findMaterialLibrary(_file);
        if (libFile != null) {
            // Find dependencies within the lib file itself
            dependencies.add(libFile);
            dependencies.addAll(ObjUtils.findMaterials(libFile));
        }

        // Add all the dependencies to a temp zip
        File dest = FileSystemUtils.getItemOnSameRoot(_file, "tmp");
        dest = new File(dest, base + ".zip");
        if (IOProviderFactory.exists(dest))
            FileSystemUtils.delete(dest);
        try {
            FileSystemUtils.zipDirectory(new ArrayList<>(dependencies),
                    dest, compress);
        } catch (Exception e) {
            Log.e(TAG, "Failed to zip OBJ: " + dest, e);
        }

        long end = SystemClock.elapsedRealtime();
        Log.d(TAG,
                "Took " + (end - start) + "ms to find create temp zip for "
                        + dest);
        return dest;
    }
}
