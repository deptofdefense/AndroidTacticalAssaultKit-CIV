
package com.atakmap.android.importexport.send;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

/**
 * Send content to an FTP server
 */
public class FTPSender extends MissionPackageSender {

    public FTPSender(MapView mapView) {
        super(mapView);
    }

    @Override
    public String getName() {
        return _context.getString(R.string.ftp);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_upload_file);
    }

    @Override
    public boolean sendContent(String uri, Callback callback) {

        if (uri == null)
            return false;

        if (uri.startsWith(URIScheme.MPM)) {
            return super.sendContent(uri, callback);
        } else if (uri.startsWith(URIScheme.FILE))
            return upload(URIHelper.getFile(uri));
        else
            return false;
    }

    @Override
    public boolean sendMissionPackage(final MissionPackageManifest mpm,
            MissionPackageBaseTask.Callback mpCb, final Callback cb) {
        final String uri = URIHelper.getURI(mpm);
        if (mpm.pathExists()) {
            boolean success = upload(new File(mpm.getPath()));
            if (cb != null)
                cb.onSentContent(this, uri, success);
            return success;
        }

        // Need to save the package before uploading
        MissionPackageMapComponent.getInstance().getFileIO().save(mpm,
                new MissionPackageBaseTask.Callback() {
                    @Override
                    public void onMissionPackageTaskComplete(
                            MissionPackageBaseTask t,
                            boolean success) {
                        if (!(t instanceof CompressionTask))
                            return;
                        boolean s = upload(new File(t.getManifest().getPath()));
                        if (cb != null)
                            cb.onSentContent(FTPSender.this, uri, s);
                    }
                });
        return true;
    }

    private boolean upload(File file) {
        if (!FileSystemUtils.isFile(file))
            return false;

        //KML, GPX are ASCII
        //KMZ, SHP are binary
        MIMEType m = ResourceFile.getMIMETypeForFile(file.getName());
        boolean binaryTransferMode = (m == MIMEType.ZIP
                || m == MIMEType.SHP || m == MIMEType.SHPZ
                || m == MIMEType.KMZ);

        Intent intent = new Intent(
                ImportExportMapComponent.FTP_UPLOAD_FILE_ACTION);
        intent.putExtra("filepath", file.getAbsolutePath());
        intent.putExtra("binary", binaryTransferMode);
        AtakBroadcast.getInstance().sendBroadcast(intent);
        return true;
    }
}
