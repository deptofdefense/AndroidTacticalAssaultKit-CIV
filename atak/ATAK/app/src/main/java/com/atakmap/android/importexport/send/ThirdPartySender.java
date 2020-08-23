
package com.atakmap.android.importexport.send;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.CopyTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Send content to a third-party app
 */
public class ThirdPartySender extends MissionPackageSender {

    private static final String TAG = "ThirdPartySender";

    public ThirdPartySender(MapView mapView) {
        super(mapView);
    }

    @Override
    public String getName() {
        return _context.getString(R.string.choose_app);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_android_display_settings);
    }

    @Override
    public boolean sendContent(String uri, Callback callback) {

        if (uri == null)
            return false;

        if (uri.startsWith(URIScheme.MPM)) {
            return super.sendContent(uri, callback);
        } else if (uri.startsWith(URIScheme.FILE))
            return send(uri, URIHelper.getFile(uri), callback);
        return false;
    }

    @Override
    public boolean sendMissionPackage(MissionPackageManifest manifest,
            MissionPackageBaseTask.Callback mpCallback, final Callback cb) {
        MissionPackageBaseTask.Callback callback = new MissionPackageBaseTask.Callback() {
            @Override
            public void onMissionPackageTaskComplete(MissionPackageBaseTask t,
                    boolean success) {
                if (t instanceof CompressionTask && success) {
                    //no-op
                } else if (t instanceof CopyTask && success) {
                    File file = ((CopyTask) t).getDestination();
                    Log.d(TAG, "Deployed and sending via external app: "
                            + file.getAbsolutePath());
                    send(URIHelper.getURI(t.getManifest()), file, cb);
                } else {
                    //TODO toast?
                    Log.w(TAG, "Failed to deploy and send via external app: "
                            + t.getManifest().getPath());
                }
            }
        };

        //deploy package to "transfer" folder, then send via external app
        MissionPackageFileIO io = MissionPackageMapComponent.getInstance()
                .getFileIO();
        if (!manifest.pathExists())
            io.saveAndSend(manifest, callback);
        else
            io.send(manifest, callback);
        return true;
    }

    private boolean send(String uri, File f, Callback cb) {
        if (!FileSystemUtils.isFile(f))
            return false;

        ResourceFile.MIMEType m = ResourceFile.getMIMETypeForFile(f.getName());
        String mimeType = m != null ? m.MIME : null;

        Log.d(TAG, "3rd party ACTION_SEND for: " + f);
        Uri fileUri = com.atakmap.android.util.FileProviderHelper
                .fromFile(_context, f);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        com.atakmap.android.util.FileProviderHelper.setReadAccess(shareIntent);
        if (!FileSystemUtils.isEmpty(mimeType))
            shareIntent.setType(mimeType);
        try {
            _context.startActivity(Intent.createChooser(shareIntent,
                    _context.getString(R.string.importmgr_share_via,
                            f.getName())));
        } catch (Exception e) {
            Log.w(TAG, "Failed to ACTION_SEND", e);
            Toast.makeText(_context,
                    R.string.importmgr_no_compatible_apps_found,
                    Toast.LENGTH_LONG)
                    .show();
        }
        if (cb != null)
            cb.onSentContent(this, uri, true);
        return true;
    }
}
