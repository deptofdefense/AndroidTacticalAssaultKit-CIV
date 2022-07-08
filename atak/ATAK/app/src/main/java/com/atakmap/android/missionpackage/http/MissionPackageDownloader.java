
package com.atakmap.android.missionpackage.http;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackagePreferenceListener;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageConfiguration;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.missionpackage.http.datamodel.FileTransfer;
import com.atakmap.android.missionpackage.http.datamodel.MissionPackageQueryResult;
import com.atakmap.android.missionpackage.http.rest.FileTransferRequest;
import com.atakmap.android.missionpackage.http.rest.GetFileTransferOperation;
import com.atakmap.android.missionpackage.http.rest.PostMissionPackageOperation;
import com.atakmap.android.missionpackage.http.rest.PostMissionPackageRequest;
import com.atakmap.android.missionpackage.http.rest.QueryMissionPackageOperation;
import com.atakmap.android.missionpackage.http.rest.QueryMissionPackageRequest;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import com.foxykeep.datadroid.requestmanager.RequestManager.RequestListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP download support for File Transfers. Leverages Android Service to offload async HTTP
 * requests
 * 
 * 
 */
public class MissionPackageDownloader implements RequestListener {

    protected static final String TAG = "MissionPackageDownloader";

    /**
     * Default user setting
     */
    public static final int DEFAULT_DOWNLOAD_RETRIES = 10;

    /**
     * By default when searching Mission Packages, only display packages labeled public
     */
    public static final String SERVER_TOOL_PUBLIC = "public";
    public static final String SERVER_TOOL_PRIVATE = "private";

    /**
     * Core class members
     */
    private MapView _mapView;
    private Context _context;
    private MissionPackageReceiver _receiver;
    private int _downloadAttempts;

    private int curNotificationId = 82000;

    private final ProgressDialog _progressDialog;

    // List of file transfers successfully downloaded and extracted this session
    private final Map<String, FileTransfer> _downloaded = new HashMap<>();

    // ATAK Mission Package File Transfer Requests
    public final static int REQUEST_TYPE_FILETRANSFER_GET_FILE;
    public final static int REQUEST_TYPE_POST_MISSIONPACKAGE;
    public final static int REQUEST_TYPE_QUERY_MISSIONPACKAGE;

    static {
        REQUEST_TYPE_FILETRANSFER_GET_FILE = NetworkOperationManager
                .register(
                        "com.atakmap.android.missionpackage.http.rest.GetFileTransferOperation",
                        new com.atakmap.android.missionpackage.http.rest.GetFileTransferOperation());

        REQUEST_TYPE_POST_MISSIONPACKAGE = NetworkOperationManager
                .register(
                        "com.atakmap.android.missionpackage.http.rest.PostMissionPackageOperation",
                        new com.atakmap.android.missionpackage.http.rest.PostMissionPackageOperation());

        REQUEST_TYPE_QUERY_MISSIONPACKAGE = NetworkOperationManager
                .register(
                        "com.atakmap.android.missionpackage.http.rest.QueryMissionPackageOperation",
                        new com.atakmap.android.missionpackage.http.rest.QueryMissionPackageOperation());
    }

    /**
     * ctor
     * 
     * @param mapView
     * @param receiver
     */
    public MissionPackageDownloader(MapView mapView,
            MissionPackageReceiver receiver) {
        _mapView = mapView;
        _context = mapView.getContext();
        _receiver = receiver;

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(_context);
        _downloadAttempts = Integer.parseInt(pref.getString(
                MissionPackagePreferenceListener.fileshareDownloadAttempts,
                String.valueOf(DEFAULT_DOWNLOAD_RETRIES)));
        _progressDialog = new ProgressDialog(_context);
    }

    public void dispose() {
        _mapView = null;
        _receiver = null;
    }

    public int getDownloadAttempts() {
        return _downloadAttempts;
    }

    public void setDownloadAttempts(int downloadAttempts) {
        this._downloadAttempts = downloadAttempts;
    }

    public boolean isAlreadyDownloaded(FileTransfer ftr) {
        if (ftr == null)
            return false;
        String key = getDownloadKey(ftr);
        if (_downloaded.containsKey(key)) {
            Log.d(TAG, "Already downloaded: " + key);
            return true;
        }
        Log.d(TAG, "Not downloaded: " + key);
        return false;
    }

    public void setAlreadyDownloaded(FileTransfer ftr) {
        if (ftr != null) {
            Log.d(TAG, "Finished: " + getDownloadKey(ftr));
            _downloaded.put(getDownloadKey(ftr), ftr);
        }
    }

    /**
     * Download specified file asynchronously
     * 
     * @param fileTransfer
     */
    public void download(FileTransfer fileTransfer) {

        FileTransferRequest request = new FileTransferRequest(fileTransfer, 1,
                curNotificationId++,
                _receiver.getConnectionTimeoutMS(),
                _receiver.getTransferTimeoutMS());

        // notify user
        Log.d(TAG,
                "File Transfer download request created for: "
                        + request);
        String tickerFilename = MissionPackageUtils.abbreviateFilename(
                fileTransfer.getName(), 20);
        String sender = MissionPackageReceiver.getSender(fileTransfer);

        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                R.drawable.missionpackage_icon,
                NotificationUtil.WHITE,
                _context.getString(
                        R.string.mission_package_download_started),
                _context.getString(
                        R.string.mission_package_downloading_from,
                        tickerFilename, sender),
                _context.getString(
                        R.string.mission_package_downloading_from,
                        tickerFilename, sender));

        // Kick off async HTTP request to get file transfer from remote ATAK
        HTTPRequestManager.from(_context).execute(
                request.createFileTransferDownloadRequest(), this);
    }

    /**
     * Post file to server asynchronously
     * @param request
     */
    public void post(PostMissionPackageRequest request) {

        request.setNotificationId(curNotificationId++);

        // notify user
        Log.d(TAG,
                "Mission Package Post request created for: "
                        + request);
        String tickerFilename = MissionPackageUtils.abbreviateFilename(
                request.getName(), 20);

        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                R.drawable.missionpackage_icon,
                NotificationUtil.WHITE,
                _context.getString(
                        R.string.mission_package_upload_started),
                _context.getString(R.string.mission_package_uploading_to,
                        tickerFilename, request.getServerConnectString()),
                _context.getString(R.string.mission_package_uploading_to,
                        tickerFilename, request.getServerConnectString()));

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(_context).execute(
                request.createPostMissionPackageRequest(), this);
    }

    public void query(String serverConnectString, String tool) {

        NetConnectString s = NetConnectString.fromString(serverConnectString);
        if (s != null) {
            if (!ServerVersion.includeToolParam(s.getHost())) {
                tool = null;
            } else if (FileSystemUtils.isEmpty(tool)) {
                //by default only display public packages
                tool = SERVER_TOOL_PUBLIC;
            }
        } else {
            Log.w(TAG, "Unable to parse hostname");
        }

        QueryMissionPackageRequest request = new QueryMissionPackageRequest(
                serverConnectString, curNotificationId++, tool);
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Cannot query without valid request");
            return;
        }

        // notify user
        Log.d(TAG,
                "Mission Package query request created for: "
                        + request);

        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                R.drawable.missionpackage_icon,
                NotificationUtil.WHITE,
                _context.getString(
                        R.string.mission_package_query_started),
                _context.getString(R.string.mission_package_searching_url,
                        ServerListDialog.getBaseUrl(request
                                .getServerConnectString())),
                _context.getString(R.string.mission_package_searching_url,
                        ServerListDialog.getBaseUrl(request
                                .getServerConnectString())));

        // Show progress dialog while querying
        _progressDialog
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        Toast.makeText(_context,
                                R.string.mission_package_query_cancelled,
                                Toast.LENGTH_LONG).show();
                    }
                });
        _progressDialog.setCanceledOnTouchOutside(false);
        _progressDialog.setMessage(_context.getString(
                R.string.mission_package_query_busy));
        _progressDialog.show();

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(_context).execute(
                request.createQueryMissionPackageRequest(), this);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        // HTTP response received successfully
        if (request
                .getRequestType() == MissionPackageDownloader.REQUEST_TYPE_FILETRANSFER_GET_FILE) {
            if (resultData == null) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to obtain results");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.file_transfer_download_failed),
                                _context.getString(
                                        R.string.mission_package_unable_to_obtain_results),
                                _context.getString(
                                        R.string.mission_package_unable_to_obtain_results));
                return;
            }

            // the initial request that was sent out
            FileTransferRequest initialRequest = resultData
                    .getParcelable(GetFileTransferOperation.PARAM_GETFILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                // TODO fatal error?
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to parse request");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.file_transfer_download_failed),
                                _context.getString(
                                        R.string.mission_package_unable_to_parse_request),
                                _context.getString(
                                        R.string.mission_package_unable_to_parse_request));
                return;
            }

            if (isAlreadyDownloaded(request)) {
                NotificationUtil.getInstance().clearNotification(
                        initialRequest.getNotificationId());
                return;
            }

            FileTransfer ftr = initialRequest.getFileTransfer();
            String sender = MissionPackageReceiver.getSender(ftr);

            // if it was a Mission Package, the contents will be in the result data from
            // GetFileTransferOperation
            MissionPackageManifest manifest = resultData
                    .getParcelable(
                            GetFileTransferOperation.PARAM_MISSION_PACKAGE_MANIFEST);
            if (manifest == null || !manifest.isValid()) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to parse request manifest");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.file_transfer_download_failed),
                                _context.getString(
                                        R.string.mission_package_unable_to_parse_request_manifest),
                                _context.getString(
                                        R.string.mission_package_unable_to_parse_request_manifest));
                return;
            }

            MissionPackageConfiguration.ImportInstructions inst = manifest
                    .getConfiguration().getImportInstructions();

            // log it
            FileInfoPersistanceHelper.instance().insertLog(new FileTransferLog(
                    FileTransferLog.TYPE.RECV, manifest.getName(),
                    _context.getString(R.string.mission_package_received_from,
                            sender),
                    ftr.getSize()));

            // handle single file or Mission Package
            NameValuePair p = null;
            File downloadedFile = new File(manifest.getPath());
            if (!inst.isDelete()) {
                // all file contents should exist
                Log.d(TAG,
                        "Package received contents: " + manifest);
                if (manifest.hasFiles()) {
                    for (MissionPackageContent content : manifest
                            .getFiles()) {
                        if (content.isIgnore())
                            continue; // don't validate b/c extractor skips it

                        p = content
                                .getParameter(
                                        MissionPackageContent.PARAMETER_LOCALPATH);
                        if (p == null || !p.isValid()
                                || !FileSystemUtils.isFile(p.getValue())) {
                            Log.e(TAG,
                                    "File Transfer Download Failed - Failed to extract Package: "
                                            + manifest.getName()
                                            + ", "
                                            + (p == null ? ""
                                                    : p.getValue()));
                            String msg = _context.getString(
                                    R.string.mission_package_failed_to_extract,
                                    manifest.getName());
                            NotificationUtil.getInstance().postNotification(
                                    initialRequest.getNotificationId(),
                                    R.drawable.ic_network_error_notification_icon,
                                    NotificationUtil.RED,
                                    _context.getString(
                                            R.string.file_transfer_download_failed),
                                    msg, msg);

                            notifySenderFailure(initialRequest,
                                    "Failed to extract contents");
                            return;
                        }

                    }
                }

                // now add to UI
                if (_receiver != null)
                    _receiver.add(manifest, sender);
            }

            // send ACK back to the remote user who sent us the CoT File Transfer
            //_receiver.sendFileTransferAck(ftr, true, "Download complete");

            // downloaded file transfer successfully...
            Log.d(TAG,
                    "File Transfer Download Complete - Successfully downloaded file: "
                            + ftr
                            + " to "
                            + downloadedFile.getAbsolutePath()
                            + ", number attempts: "
                            + initialRequest.getRetryCount());
            String tickerFilename = MissionPackageUtils.abbreviateFilename(
                    ftr.getName(), 20);

            // Notify local user that file was transferred successfully, tap intent to
            //view package in MPT UI
            Intent notificationIntent = null;
            if (!inst.isDelete()) {
                tickerFilename = manifest.getName();
                notificationIntent = new Intent();
                notificationIntent
                        .setAction(MissionPackageReceiver.MISSIONPACKGE_DETAIL);
                notificationIntent.putExtra("missionPackageUID",
                        manifest.getUID());
            } else if (manifest.getMapItemCount() == 1
                    && manifest.getFileCount() >= 1) {
                // Marker w/ attachments
                MissionPackageContent item = manifest.getMapItems().get(0);
                if (item != null && item.hasParameter("uid")) {
                    String uid = item.getParameter("uid").getValue();
                    notificationIntent = new Intent();
                    notificationIntent.putExtra("uid", uid);
                    boolean hasImage = false;
                    for (MissionPackageContent c : manifest.getFiles()) {
                        if (c.hasParameter("localpath")) {
                            File img = new File(c.getParameter("localpath")
                                    .getValue());
                            if (FileSystemUtils.isFile(img)
                                    && IOProviderFactory.isFile(img)
                                    && ImageDropDownReceiver.ImageFileFilter
                                            .accept(img.getParentFile(),
                                                    img.getName())) {
                                hasImage = true;
                                break;
                            }
                        }
                    }
                    if (manifest.getFileCount() == 1 && hasImage) {
                        // Opens image viewer on single image and focuses
                        notificationIntent.setAction(
                                "com.atakmap.android.maps.FOCUS_DISPLAY");
                    } else {
                        // Opens attachment gallery view
                        notificationIntent.setAction(
                                ImageGalleryReceiver.VIEW_ATTACHMENTS);
                        notificationIntent.putExtra("focusmap", true);
                    }
                }
            }

            // Downloaded file transfer successfully...
            NotificationUtil.getInstance().postNotification(
                    initialRequest.getNotificationId(),
                    R.drawable.missionpackage_sent,
                    NotificationUtil.GREEN,
                    _context.getString(
                            R.string.file_transfer_download_complete),
                    _context.getString(
                            R.string.mission_package_sent_message,
                            sender, tickerFilename),
                    notificationIntent, true);

            // Add to downloaded list
            //setAlreadyDownloaded(ftr);

            // log it
            // FileInfoPersistanceHelper.instance().insertLog(
            // new FileTransactionLog(FileTransactionLog.FORMAT.RECV, downloadedFile.getName(),
            // sender + " sent: " + tickerFilename, size, System.currentTimeMillis()));

            // lastly see if package has post processing instructions: OnReceiveAction
            String onReceiveAction = null;
            p = manifest.getConfiguration().getParameter(
                    MissionPackageConfiguration.PARAMETER_OnReceiveAction);
            if (p != null && p.isValid())
                onReceiveAction = p.getValue();

            if (!FileSystemUtils.isEmpty(onReceiveAction)) {
                Log.d(TAG, "Sending OnReceiveParams intent: "
                        + onReceiveAction);

                // Generate custom intent for the invoking tool
                Intent onReceiveIntent = new Intent();
                onReceiveIntent.setAction(onReceiveAction);
                onReceiveIntent.putExtra(
                        MissionPackageApi.INTENT_EXTRA_SENDERCALLSIGN,
                        MissionPackageReceiver
                                .getSender(initialRequest.getFileTransfer()));
                onReceiveIntent
                        .putExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                manifest);
                onReceiveIntent.putExtra(
                        MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                        initialRequest.getNotificationId());
                AtakBroadcast.getInstance().sendBroadcast(onReceiveIntent);
            }

        } else if (request
                .getRequestType() == MissionPackageDownloader.REQUEST_TYPE_POST_MISSIONPACKAGE) {
            if (resultData == null) {
                Log.e(TAG,
                        "Mission Package Post Failed - Unable to obtain results");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.mission_package_upload_failed),
                        _context.getString(
                                R.string.mission_package_unable_to_obtain_results),
                        _context.getString(
                                R.string.mission_package_unable_to_obtain_results));
                return;
            }

            // the initial request that was sent out
            PostMissionPackageRequest initialRequest = resultData
                    .getParcelable(PostMissionPackageOperation.PARAM_POSTFILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                // TODO fatal error?
                Log.e(TAG,
                        "Mission Package Post Failed - Unable to parse request");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.mission_package_upload_failed),
                        _context.getString(
                                R.string.mission_package_unable_to_parse_request),
                        _context.getString(
                                R.string.mission_package_unable_to_parse_request));
                return;
            }

            //Update notification
            NotificationUtil.getInstance().postNotification(
                    initialRequest.getNotificationId(),
                    R.drawable.missionpackage_sent,
                    NotificationUtil.GREEN,
                    _context.getString(R.string.mission_package_uploaded),
                    _context.getString(
                            R.string.mission_package_uploaded_message,
                            initialRequest.getName(),
                            _context.getString(R.string.MARTI_sync_server)),
                    _context.getString(
                            R.string.mission_package_uploaded_message,
                            initialRequest.getName(),
                            _context.getString(R.string.MARTI_sync_server)));
        } else if (request
                .getRequestType() == MissionPackageDownloader.REQUEST_TYPE_QUERY_MISSIONPACKAGE) {
            if (!_progressDialog.isShowing())
                return;
            _progressDialog.dismiss();
            if (resultData == null) {
                Log.e(TAG,
                        "Mission Package Query Failed - Unable to obtain results");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.mission_package_query_failed),
                        _context.getString(
                                R.string.mission_package_unable_to_obtain_results),
                        _context.getString(
                                R.string.mission_package_unable_to_obtain_results));
                return;
            }

            // the initial request that was sent out
            QueryMissionPackageRequest initialRequest = resultData
                    .getParcelable(QueryMissionPackageOperation.PARAM_QUERY);
            if (initialRequest == null || !initialRequest.isValid()) {
                // TODO fatal error?
                Log.e(TAG,
                        "Mission Package Query Failed - Unable to parse request");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.mission_package_query_failed),
                        _context.getString(
                                R.string.mission_package_unable_to_parse_request),
                        _context.getString(
                                R.string.mission_package_unable_to_parse_request));
                return;
            }

            //parse the JSON list
            List<MissionPackageQueryResult> results = null;

            try {
                results = MissionPackageQueryResult.fromResultJSON(
                        new JSONObject(resultData.getString(
                                QueryMissionPackageOperation.PARAM_JSONLIST)));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse JSON", e);
            }

            if (FileSystemUtils.isEmpty(results)) {
                if (results == null) {
                    Log.e(TAG,
                            "Error occurred deserializing the Mission Packages");
                } else {
                    Log.d(TAG, "No results available");
                }

                NotificationUtil.getInstance().postNotification(
                        initialRequest.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.no_mission_packages_available2),
                        _context.getString(
                                R.string.mission_package_no_packages_available,
                                _context.getString(R.string.MARTI_sync_server)),
                        _context.getString(
                                R.string.mission_package_no_packages_available,
                                _context.getString(
                                        R.string.MARTI_sync_server)));
                return;
            }

            Log.d(TAG, "Queried: " + results.size() + " Mission Packages");

            NotificationUtil.getInstance().clearNotification(
                    initialRequest.getNotificationId());
            _receiver
                    .showQueryResultsView(results,
                            initialRequest.getServerConnectString());
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        _progressDialog.dismiss();
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, detail);

        //if a query or post, just display error
        QueryMissionPackageRequest queryRequest = (QueryMissionPackageRequest) request
                .getParcelable(QueryMissionPackageOperation.PARAM_QUERY);
        if (queryRequest != null) {
            Log.e(TAG,
                    "Mission Package Query Failed - Connection Data Error: "
                            + detail);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _context.getString(
                            R.string.mission_package_search_failed),
                    detail,
                    detail);
            return;
        }

        PostMissionPackageRequest postRequest = (PostMissionPackageRequest) request
                .getParcelable(PostMissionPackageOperation.PARAM_POSTFILE);
        if (postRequest != null) {
            Log.e(TAG,
                    "Mission Package Post Failed - Connection Data Error: "
                            + detail);
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _context.getString(
                            R.string.mission_package_upload_failed),
                    detail,
                    detail);
            return;
        }

        // Get request data
        FileTransferRequest fileRequest = (FileTransferRequest) request
                .getParcelable(GetFileTransferOperation.PARAM_GETFILE);
        if (fileRequest == null) {
            Log.e(TAG,
                    "Mission Package Download Failed - Connection Data Error - Unable to serialize file transfer retry request");
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _context.getString(
                            R.string.mission_package_download_failed),
                    _context.getString(
                            R.string.mission_package_unable_to_serialize_file_transfer,
                            detail),
                    _context.getString(
                            R.string.mission_package_unable_to_serialize_file_transfer,
                            detail));
            return;
        }

        if (!fileRequest.isValid()) {
            Log.e(TAG,
                    "Mission Package Download Failed - Connection Data Error - Unable to serialize invalid file transfer retry request");
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _context.getString(
                            R.string.mission_package_download_failed),
                    _context.getString(
                            R.string.mission_package_unable_to_serialize_invalid_file_transfer,
                            detail),
                    _context.getString(
                            R.string.mission_package_unable_to_serialize_invalid_file_transfer,
                            detail));
            return;
        }

        // Check if the package has already been downloaded from a different server
        if (isAlreadyDownloaded(request)) {
            NotificationUtil.getInstance().clearNotification(
                    fileRequest.getNotificationId());
            return;
        }

        // Hack! Since GetFileTransferOperation cant currently modify the request upon error
        // lets check flag indicating that some progress was made
        if (ce.getStatusCode() == GetFileTransferOperation.PROGRESS_MADE_STATUS_CODE) {
            Log.d(TAG, "Resetting retry count for: "
                    + fileRequest.getFileTransfer().toString());
            fileRequest.setRetryCount(1);
        }

        // Upon error for package download, retry to download the file
        String sender = MissionPackageReceiver.getSender(
                fileRequest.getFileTransfer());
        String tickerFilename = MissionPackageUtils.abbreviateFilename(
                fileRequest.getFileTransfer().getName(), 20);

        // see if already hit max retries
        int currentAttempts = fileRequest.getRetryCount();
        if (currentAttempts >= _downloadAttempts) {
            // delete the temp/partial file
            File temp = new File(
                    MissionPackageFileIO.getMissionPackageIncomingDownloadPath(
                            FileSystemUtils.getRoot().getAbsolutePath()),
                    fileRequest.getFileTransfer()
                            .getUID());
            if (IOProviderFactory.exists(temp))
                FileSystemUtils.deleteFile(temp);

            Log.e(TAG,
                    "Mission Package Download Failed - Connection Data Error - "
                            + currentAttempts
                            + " Retry attempts failed for file: "
                            + fileRequest.getFileTransfer().toString());
            NotificationUtil.getInstance().postNotification(
                    fileRequest.getNotificationId(),
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _context.getString(
                            R.string.mission_package_download_failed),
                    _context.getString(
                            R.string.mission_package_download_from_sender_failed_after_attempts,
                            tickerFilename, sender, currentAttempts, detail),
                    _context.getString(
                            R.string.mission_package_download_from_sender_failed_after_attempts,
                            tickerFilename, sender, currentAttempts, detail));

            notifySenderFailure(fileRequest, "Download failed after "
                    + currentAttempts
                    + " attempts");
            return;
        }

        // update notification
        NotificationUtil.getInstance().postNotification(
                fileRequest.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                _context.getString(R.string.mission_package_download_failing),
                _context.getString(
                        R.string.mission_package_download_from_sender_has_failed_attempts,
                        tickerFilename, sender,
                        currentAttempts, _downloadAttempts),
                _context.getString(
                        R.string.mission_package_download_from_sender_has_failed_attempts,
                        tickerFilename, sender, currentAttempts,
                        _downloadAttempts));

        // attempt download again
        fileRequest.setRetryCount(currentAttempts + 1);
        HTTPRequestManager.from(_context).execute(
                fileRequest.createFileTransferDownloadRequest(), this);
        Log.d(TAG, "Mission Package download attempt #" + (currentAttempts + 1)
                + " created for: "
                + fileRequest);
    }

    /**
     * Notify the sender that I failed to download the file
     * 
     * @param fileRequest
     * @param reason
     */
    private void notifySenderFailure(FileTransferRequest fileRequest,
            String reason) {
        if (fileRequest == null) {
            Log.e(TAG,
                    "Failed to notify sender of download failure - Unable to serialize file transfer retry request");
            return;
        }

        if (!fileRequest.isValid()) {
            Log.e(TAG,
                    "Failed to notify sender of download failure - Unable to serialize invalid file transfer retry request");
        }

        // send NACK
        //_receiver.sendFileTransferAck(fileRequest.getFileTransfer(), false,
        //        reason);
    }

    @Override
    public void onRequestDataError(Request request) {
        _progressDialog.dismiss();
        if (isAlreadyDownloaded(request))
            return;
        Log.e(TAG,
                "Mission Package Remote Operation Failed - Request Data Error");
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                _context.getString(
                        R.string.mission_package_failure),
                _context.getString(
                        R.string.mission_package_request_data_error),
                _context.getString(
                        R.string.mission_package_request_data_error));
        notifySenderFailure(
                (FileTransferRequest) request
                        .getParcelable(GetFileTransferOperation.PARAM_GETFILE),
                "Download failed - Request Data Error");
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        _progressDialog.dismiss();
        if (isAlreadyDownloaded(request))
            return;
        Log.e(TAG,
                "Mission Package Remote Operation Failed - Request Custom Error");
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                _context.getString(
                        R.string.mission_package_failure),
                _context.getString(
                        R.string.mission_package_request_custom_error),
                _context.getString(
                        R.string.mission_package_request_custom_error));
        notifySenderFailure(
                (FileTransferRequest) request
                        .getParcelable(GetFileTransferOperation.PARAM_GETFILE),
                "Download failed - Request Custom Error");
    }

    /**
     * Get the download key for this transfer
     * Used to check for exact matching transfers from 2 or more servers
     * Note: The transfer UID is unique to every transfer,
     *       but just to be extra safe we also include the name, hash, etc.
     * @param ftr File transfer
     * @return Download key
     */
    private String getDownloadKey(FileTransfer ftr) {
        return ftr.getName() + "," + ftr.getSize() + "," + ftr.getUID() + ","
                + ftr.getLocalPath() + "," + ftr.getSenderUID() + ","
                + ftr.getSHA256(false);
    }

    private boolean isAlreadyDownloaded(Request req) {
        // Must be a file transfer request
        Object o = req.getParcelable(GetFileTransferOperation.PARAM_GETFILE);
        if (!(o instanceof FileTransferRequest))
            return false;

        // Must contain a valid file transfer
        FileTransfer ftr = ((FileTransferRequest) o).getFileTransfer();
        return isAlreadyDownloaded(ftr);
    }
}
