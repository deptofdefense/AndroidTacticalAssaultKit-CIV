
package com.atakmap.android.importexport.send;

import android.content.Context;

import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.data.URIContentRecipient;
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
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.List;

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
        return sendContent(contentURI, null, callback);
    }

    /**
     * Default implementation for {@link URIContentRecipient.Sender#selectRecipients(String, URIContentRecipient.Callback)}
     * @param contentURI Content URI
     * @param recipients List of recipients
     * @param callback Send callback
     * @return True if successful
     */
    public boolean sendContent(String contentURI,
            List<? extends URIContentRecipient> recipients,
            Callback callback) {

        if (contentURI == null)
            return false;

        if (contentURI.startsWith(URIScheme.MPM)) {
            MissionPackageManifest mpm = URIHelper.getManifest(contentURI);
            if (mpm == null || !mpm.isValid())
                return false;
            MissionPackageBaseTask.Callback mpCallback = null;
            if (!mpm.pathExists())
                mpCallback = new DeleteAfterSendCallback();
            return sendImpl(URIHelper.getManifest(contentURI), recipients,
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
            return sendImpl(manifest, recipients, new DeleteAfterSendCallback(),
                    callback);
        }
        return false;
    }

    /**
     * Send a Mission Package to pre-selected recipients
     * @param manifest Mission Package manifest
     * @param recipients List of recipients to send to
     * @param mpCallback Mission Package task callback
     * @param cb Send callback
     * @return True if send successful
     */
    public boolean sendMissionPackage(MissionPackageManifest manifest,
            List<? extends URIContentRecipient> recipients,
            MissionPackageBaseTask.Callback mpCallback, Callback cb) {
        // Fallback to prompting the user for recipients - assumes this sender
        // does not implement URIContentRecipient.Sender
        return sendMissionPackage(manifest, mpCallback, cb);
    }

    /**
     * Send a Mission Package (no pre-selected recipients)
     * @param manifest Mission Package manifest
     * @param mpCallback Mission Package task callback
     * @param cb Send callback
     * @return True if send successful
     */
    public abstract boolean sendMissionPackage(MissionPackageManifest manifest,
            MissionPackageBaseTask.Callback mpCallback, Callback cb);

    private boolean sendImpl(MissionPackageManifest manifest,
            List<? extends URIContentRecipient> recipients,
            MissionPackageBaseTask.Callback mpCallback, Callback cb) {
        // Attempts to send using recipients method and falls back to
        // non-recipients method if failed
        if (!FileSystemUtils.isEmpty(recipients)
                && sendMissionPackage(manifest, recipients, mpCallback, cb))
            return true;
        return sendMissionPackage(manifest, mpCallback, cb);
    }
}
