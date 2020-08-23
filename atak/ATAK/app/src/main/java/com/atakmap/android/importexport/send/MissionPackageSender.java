
package com.atakmap.android.importexport.send;

import android.content.Context;

import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.data.URIContentSender;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.event.MissionPackageShapefileHandler;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.coremap.log.Log;

import java.io.File;

public abstract class MissionPackageSender implements URIContentSender {

    private static final String TAG = "MissionPackageSender";

    protected final MapView _mapView;
    protected final Context _context;

    protected MissionPackageSender(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    @Override
    public boolean isSupported(String contentURI) {

        if (contentURI == null)
            return false;

        return contentURI.startsWith(URIScheme.MPM)
                || contentURI.startsWith(URIScheme.FILE);
    }

    @Override
    public boolean sendContent(String contentURI, Callback callback) {

        if (contentURI == null)
            return false;

        if (contentURI.startsWith(URIScheme.MPM)) {
            MissionPackageManifest mpm = URIHelper.getManifest(contentURI);
            if (mpm == null || !mpm.isValid())
                return false;
            MissionPackageBaseTask.Callback mpCallback = null;
            if (!mpm.pathExists())
                mpCallback = new DeleteAfterSendCallback();
            return sendMissionPackage(URIHelper.getManifest(contentURI),
                    mpCallback, callback);
        } else if (contentURI.startsWith(URIScheme.FILE)) {
            File f = URIHelper.getFile(contentURI);
            if (f == null)
                return false;

            MissionPackageManifest manifest = MissionPackageApi
                    .CreateTempManifest(f.getName(), true, true,
                            null);

            MIMEType m = ResourceFile.getMIMETypeForFile(f.getName());
            if (m == MIMEType.SHP) {
                if (!MissionPackageShapefileHandler.add(_context, manifest, f)
                        || manifest.isEmpty()) {
                    Log.w(TAG, "Unable to add shapefile to Mission Package");
                    return false;
                }
            } else {
                if (!manifest.addFile(f, null) || manifest.isEmpty()) {
                    Log.w(TAG, "Unable to add file to Mission Package");
                    return false;
                }
            }

            // send null contact list so Contact List is displayed, delete local
            // mission package after sent
            sendMissionPackage(manifest, new DeleteAfterSendCallback(),
                    callback);
        }
        return false;
    }

    /**
     * Send a Mission Package
     * @param manifest Mission Package manifest
     * @param mpCallback Mission Package task callback
     * @return True if send successful
     */
    public abstract boolean sendMissionPackage(MissionPackageManifest manifest,
            MissionPackageBaseTask.Callback mpCallback, Callback cb);
}
