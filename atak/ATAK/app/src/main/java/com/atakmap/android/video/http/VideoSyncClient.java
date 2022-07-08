
package com.atakmap.android.video.http;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.VideoListDialog;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.android.video.manager.VideoXMLHandler;
import com.atakmap.android.video.http.rest.GetVideoListOperation;
import com.atakmap.android.video.http.rest.GetVideoListRequest;
import com.atakmap.android.video.http.rest.PostVideoListOperation;
import com.atakmap.android.video.http.rest.PostVideoListRequest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class VideoSyncClient implements RequestManager.RequestListener {

    protected static final String TAG = "VideoSyncClient";

    /**
     * Core class members
     */
    private final Context _context;
    private final VideoXMLHandler _xmlHandler;
    private VideoPostCallback postCallback;

    private int curNotificationId = 85000;

    // ATAK Mission Package File Transfer Requests
    public final static int REQUEST_TYPE_GET_VIDEOS;
    public final static int REQUEST_TYPE_POST_VIDEOS;

    static {
        REQUEST_TYPE_GET_VIDEOS = NetworkOperationManager
                .register(
                        "com.atakmap.android.video.http.rest.GetVideoListOperation",
                        new GetVideoListOperation());
        REQUEST_TYPE_POST_VIDEOS = NetworkOperationManager
                .register(
                        "com.atakmap.android.video.http.rest.PostVideoListOperation",
                        new PostVideoListOperation());
    }

    /**
     * ctor
     */
    public VideoSyncClient(Context context) {
        _context = context;
        _xmlHandler = new VideoXMLHandler();
    }

    /**
     * Query the server for video aliases and pass it the given callback for
     * when aliases are selected. 
     * 
     * @param serverUrl - the URL of the server
     */
    public void query(String serverUrl) {

        try {
            GetVideoListRequest request = new GetVideoListRequest(serverUrl,
                    curNotificationId++);
            if (request == null || !request.isValid()) {
                Log.w(TAG, "Cannot query without valid request");
                return;
            }

            // notify user
            Log.d(TAG,
                    "Video query request created for: " + request);

            NotificationUtil.getInstance().postNotification(
                    request.getNotificationId(),
                    R.drawable.video,
                    NotificationUtil.BLUE,
                    _context.getString(R.string.video_text2),
                    _context.getString(R.string.searching_with_space)
                            + request.getBaseUrl(),
                    _context.getString(R.string.searching_with_space)
                            + request.getBaseUrl());

            // Kick off async HTTP request to post to server
            HTTPRequestManager.from(_context).execute(
                    request.createGetVideoListRequest(), this);
        } catch (Exception e) {
            Log.e(TAG, "Exception in query!", e);
        }
    }

    public void post(ConnectionEntry ce, String serverUrl,
            VideoPostCallback callback) {
        this.postCallback = callback;
        try {
            List<ConnectionEntry> connectionEntries = new LinkedList<>();
            connectionEntries.add(ce);
            String videoXml = VideoXMLHandler.serialize(connectionEntries);

            PostVideoListRequest request = new PostVideoListRequest(serverUrl,
                    videoXml, curNotificationId++);
            if (request == null || !request.isValid()) {
                Log.w(TAG, "Cannot post without valid request");
                return;
            }

            // notify user
            Log.d(TAG, "Video post request created for: " + request);

            NotificationUtil.getInstance().postNotification(
                    request.getNotificationId(),
                    R.drawable.video,
                    NotificationUtil.BLUE,
                    _context.getString(R.string.video_text3),
                    _context.getString(R.string.sending_to)
                            + request.getBaseUrl(),
                    _context.getString(R.string.sending_to)
                            + request.getBaseUrl());

            // Kick off async HTTP request to post to server
            HTTPRequestManager.from(_context).execute(
                    request.createPostVideoListRequest(), this);
        } catch (Exception e) {
            Log.e(TAG, "Exception in post!", e);
        }
    }

    public void post(ConnectionEntry ce, String serverUrl) {
        post(ce, serverUrl, null);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        try {
            // HTTP response received successfully
            if (request
                    .getRequestType() == VideoSyncClient.REQUEST_TYPE_GET_VIDEOS) {
                if (resultData == null) {
                    Log.e(TAG,
                            "Video Query Failed - Unable to obtain results");
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(R.string.video_text4),
                            _context.getString(R.string.video_text5),
                            _context.getString(R.string.video_text5));
                    return;
                }

                // the initial request that was sent out
                GetVideoListRequest initialRequest = resultData
                        .getParcelable(GetVideoListOperation.PARAM_QUERY);
                if (initialRequest == null || !initialRequest.isValid()) {
                    // TODO fatal error?
                    Log.e(TAG,
                            "Video Query Failed - Unable to parse request");
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(R.string.video_text4),
                            _context.getString(R.string.video_text5),
                            _context.getString(R.string.video_text5));
                    return;
                }

                NotificationUtil.getInstance().clearNotification(
                        initialRequest.getNotificationId());

                String videoConnectionsXML = resultData.getString(
                        GetVideoListOperation.PARAM_XMLLIST);

                List<ConnectionEntry> connectionEntries = _xmlHandler.parse(
                        videoConnectionsXML);
                if (connectionEntries == null
                        || connectionEntries.size() == 0) {

                    AlertDialog.Builder b = new AlertDialog.Builder(_context);
                    b.setTitle(R.string.video_text6);
                    b.setIcon(R.drawable.video);
                    b.setMessage(R.string.video_text7);
                    b.setPositiveButton(R.string.ok, null);
                    b.show();
                } else {
                    //select videos
                    MapView mv = MapView.getMapView();
                    if (mv != null) {
                        new VideoListDialog(mv).show(
                                _context.getString(R.string.video_text6),
                                connectionEntries, true,
                                new VideoListDialog.Callback() {
                                    @Override
                                    public void onVideosSelected(
                                            List<ConnectionEntry> selected) {
                                        VideoManager.getInstance().addEntries(
                                                new ArrayList<>(new HashSet<>(
                                                        selected)));
                                    }
                                });
                    }
                }
            } else {
                if (resultData == null) {
                    Log.e(TAG,
                            "Video Post Failed - Unable to obtain results");
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(R.string.video_text8),
                            _context.getString(R.string.video_text5),
                            _context.getString(R.string.video_text5));
                    return;
                }

                // the initial request that was sent out
                PostVideoListRequest initialRequest = resultData
                        .getParcelable(PostVideoListOperation.PARAM_REQUEST);
                if (initialRequest == null || !initialRequest.isValid()) {
                    // TODO fatal error?
                    Log.e(TAG,
                            "Video Post Failed - Unable to parse request");
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(R.string.video_text8),
                            _context.getString(R.string.video_text9),
                            _context.getString(R.string.video_text9));
                    return;
                }

                List<ConnectionEntry> entries = _xmlHandler.parse(
                        initialRequest.getVideoXml());
                if (FileSystemUtils.isEmpty(entries))
                    return;

                ConnectionEntry entry = entries.get(0);
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.send_video_alias);
                b.setIcon(R.drawable.video);
                b.setMessage(_context.getString(R.string.video_text10)
                        + "\n" + entry.getAlias());
                b.setPositiveButton(R.string.ok, null);
                b.show();

                NotificationUtil.getInstance().clearNotification(
                        initialRequest.getNotificationId());

                if (postCallback != null)
                    postCallback.onPostVideo(entry,
                            initialRequest.getBaseUrl());
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in OnRequestFinished!", e);
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "Video Sync Operation Failed - Connection Error: " + detail);
        String error = request
                .getRequestType() == VideoSyncClient.REQUEST_TYPE_GET_VIDEOS
                        ? _context.getString(R.string.video_text4)
                        : _context
                                .getString(R.string.video_text8);
        //TODO use request notificationt ID to update the existing notification
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                error,
                detail,
                detail);
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "Video Sync Operation Failed - Data Error");
        String error = request
                .getRequestType() == VideoSyncClient.REQUEST_TYPE_GET_VIDEOS
                        ? _context.getString(R.string.video_text4)
                        : _context
                                .getString(R.string.video_text8);
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                error,
                _context.getString(R.string.video_text12),
                _context.getString(R.string.video_text12));
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "Video Sync Operation Failed - Custom Error");
        String error = request
                .getRequestType() == VideoSyncClient.REQUEST_TYPE_GET_VIDEOS
                        ? _context.getString(R.string.video_text4)
                        : _context
                                .getString(R.string.video_text8);
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                error,
                _context.getString(R.string.video_text11),
                _context.getString(R.string.video_text11));
    }

    public interface VideoPostCallback {
        void onPostVideo(ConnectionEntry video, String serverURL);
    }
}
