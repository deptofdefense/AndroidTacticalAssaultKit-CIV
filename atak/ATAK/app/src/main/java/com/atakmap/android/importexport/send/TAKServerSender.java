
package com.atakmap.android.importexport.send;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIContentSender;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.CopyTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.http.VideoSyncClient;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Send content to a specific TAK server
 */
public class TAKServerSender extends MissionPackageSender {

    private static final String TAG = "TAKServerSender";

    private final AtakPreferences _prefs;

    public TAKServerSender(MapView mapView) {
        super(mapView);
        _prefs = new AtakPreferences(mapView);
    }

    @Override
    public String getName() {
        return _context.getString(R.string.select_server);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_server_success);
    }

    @Override
    public boolean isSupported(String contentURI) {
        return super.isSupported(contentURI)
                || contentURI.startsWith(URIScheme.VIDEO);
    }

    @Override
    public boolean sendContent(String contentURI, Callback callback) {
        if (contentURI.startsWith(URIScheme.VIDEO)) {
            ConnectionEntry entry = URIHelper.getVideoAlias(contentURI);
            if (entry != null) {
                UploadRequest req = new UploadRequest();
                req.video = entry;
                req.callback = callback;
                return req.promptForServer();
            }
            return false;
        }
        return super.sendContent(contentURI, callback);
    }

    @Override
    public boolean sendMissionPackage(final MissionPackageManifest mpm,
            MissionPackageBaseTask.Callback mpCallback, final Callback cb) {
        UploadRequest req = new UploadRequest();
        req.mpm = mpm;
        req.callback = cb;
        return req.promptForServer();
    }

    private class UploadRequest implements ServerListDialog.Callback,
            MissionPackageBaseTask.Callback,
            VideoSyncClient.VideoPostCallback {

        ConnectionEntry video;
        MissionPackageManifest mpm;
        boolean isTempManifest;
        TAKServer server;
        URIContentSender.Callback callback;

        boolean promptForServer() {
            MissionPackageMapComponent mpcom = MissionPackageMapComponent
                    .getInstance();
            if (mpcom == null || !mpcom.checkFileSharingEnabled())
                return false;

            TAKServer[] servers;
            if (_prefs.get("filesharingAllServers", false))
                servers = TAKServerListener.getInstance().getServers();
            else
                servers = TAKServerListener.getInstance()
                        .getConnectedServers();
            ServerListDialog d = new ServerListDialog(_mapView);
            if (FileSystemUtils.isEmpty(servers)) {
                Log.w(TAG,
                        "Cannot Send to server without valid server URL");
                d.promptNetworkSettings();
                return false;
            }

            // Prompt to select server
            d.show(_context.getString(R.string.select_server), servers, this);
            return true;
        }

        void upload() {
            if (this.server == null)
                return;
            if (this.mpm != null) {
                this.isTempManifest = !mpm.pathExists();
                MissionPackageFileIO io = MissionPackageMapComponent
                        .getInstance().getFileIO();
                if (this.isTempManifest)
                    io.saveAndSend(mpm, this);
                else
                    io.send(mpm, this);
            } else if (this.video != null) {
                VideoSyncClient client = new VideoSyncClient(_context);
                client.post(this.video, this.server.getURL(false), this);
            }
        }

        @Override
        public void onSelected(TAKServer server) {
            if (server == null) {
                Log.d(TAG, "No configured server selected");
                return;
            }
            this.server = server;
            upload();
        }

        @Override
        public void onMissionPackageTaskComplete(
                MissionPackageBaseTask task, boolean success) {
            if (task instanceof CompressionTask && success) {

            } else if (task instanceof CopyTask) {
                if (success) {
                    File file = ((CopyTask) task).getDestination();
                    Log.d(TAG,
                            "Deployed and sending to server: "
                                    + file.getAbsolutePath());
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            MissionPackageReceiver.MISSIONPACKAGE_POST)
                                    .putExtra("manifest", mpm)
                                    .putExtra("serverConnectString",
                                            server.getConnectString())
                                    .putExtra("filepath",
                                            file.getAbsolutePath()));
                    if (isTempManifest)
                        MissionPackageApi.Delete(task.getContext(), mpm);
                }
                if (callback != null)
                    callback.onSentContent(TAKServerSender.this,
                            URIHelper.getURI(mpm), success);
            } else {
                //TODO toast?
                Log.w(TAG, "Failed to deploy and send to server: "
                        + task.getManifest().getPath());
            }
        }

        @Override
        public void onPostVideo(ConnectionEntry video, String serverURL) {
            if (callback != null)
                callback.onSentContent(TAKServerSender.this,
                        URIHelper.getURI(video), true);
        }
    }
}
