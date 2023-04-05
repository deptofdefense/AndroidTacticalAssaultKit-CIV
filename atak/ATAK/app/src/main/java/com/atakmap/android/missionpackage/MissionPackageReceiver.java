
package com.atakmap.android.missionpackage;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.atak.plugins.impl.ClassLoaderHelper;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.coordoverlay.CoordOverlayMapReceiver;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.data.URIFilter;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.DirectoryWatcher.FileUpdateCallback;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.android.filesharing.android.service.WebServer;
import com.atakmap.android.filesharing.android.service.FileInfo;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.api.SaveAndSendCallback;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.file.MissionPackageConfiguration;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractorFactory;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.CopyTask;
import com.atakmap.android.missionpackage.file.task.ExtractMissionPackageTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.android.missionpackage.http.MissionPackageDownloadHandler;
import com.atakmap.android.missionpackage.http.MissionPackageDownloader;
import com.atakmap.android.missionpackage.http.datamodel.FileTransfer;
import com.atakmap.android.missionpackage.http.datamodel.MissionPackageQueryResult;
import com.atakmap.android.missionpackage.http.rest.PostMissionPackageRequest;
import com.atakmap.android.missionpackage.lasso.LassoSelectionDialog;
import com.atakmap.android.missionpackage.ui.FileTransferLogView;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.android.missionpackage.ui.MissionPackageListItem;
import com.atakmap.android.missionpackage.ui.MissionPackageMapOverlay;
import com.atakmap.android.missionpackage.ui.MissionPackageQueryResultView;
import com.atakmap.android.missionpackage.ui.MissionPackageViewUserState;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.missionpackage.MPReceiveInitiator;
import com.atakmap.comms.missionpackage.MPReceiver;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.filesystem.HashingUtils;

import com.atakmap.android.util.BasicNameValuePair;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// XXX - reliance on the CotMapComponent will be cut.

/**
 * Mission Package tool Drop Down View and primary receiver of messages/intents
 *
 * 
 */
public class MissionPackageReceiver extends BroadcastReceiver implements
        FileUpdateCallback, OnStateListener, MPReceiveInitiator,
        FileTransferLog.Listener {

    protected static final String TAG = "MissionPackageReceiver";

    // intents
    /**
     * Open Mission Package tool UI
     */
    public static final String MISSIONPACKAGE = "com.atakmap.android.missionpackage.MISSIONPACKAGE";

    /**
     * Display details of a Mission Package that was received
     */
    public static final String MISSIONPACKGE_DETAIL = "com.atakmap.android.missionpackage._MISSIONPACKGE_DETAIL";

    /**
     * View Log List list
     */
    public static final String MISSIONPACKAGE_LOG = "com.atakmap.android.missionpackage.MISSIONPACKAGE_LOG";

    /**
     * Select Map Item via Map View
     */
    public static final String MISSIONPACKAGE_MAPSELECT = "com.atakmap.android.missionpackage.MISSIONPACKAGE_MAPSELECT";

    /**
     * Support other components saving a Mission Package, and optionally sending it
     */
    public static final String MISSIONPACKAGE_SAVE = "com.atakmap.android.missionpackage.MISSIONPACKAGE_SAVE";

    /**
     * Callback intent sent after selecting send-to contacts from list
     */
    public static final String MISSIONPACKAGE_SEND = "com.atakmap.android.missionpackage.MISSIONPACKAGE_SEND";

    /**
     * Support other components saving changes to an existing Mission Package (without displaying UI/list)
     */
    public static final String MISSIONPACKAGE_UPDATE = "com.atakmap.android.missionpackage.MISSIONPACKAGE_UPDATE";

    /**
     * Support other components deleting an existing Mission Package
     */
    public static final String MISSIONPACKAGE_DELETE = "com.atakmap.android.missionpackage.MISSIONPACKAGE_DELETE";

    /**
     * Support sending a Mission Package to a server
     */
    public static final String MISSIONPACKAGE_POST = "com.atakmap.android.missionpackage.MISSIONPACKAGE_POST";

    /**
     * Support querying Mission Packages available on a server
     */
    public static final String MISSIONPACKAGE_QUERY = "com.atakmap.android.missionpackage.MISSIONPACKAGE_QUERY";

    /**
     * Support downloading Mission Packages from a server
     */
    public static final String MISSIONPACKAGE_DOWNLOAD = "com.atakmap.android.missionpackage.MISSIONPACKAGE_DOWNLOAD";

    /**
     * Remove a list of contents using the lasso tool
     */
    public static final String MISSIONPACKAGE_REMOVE_LASSO = "com.atakmap.android.missionpackage.MISSIONPACKAGE_REMOVE_LASSO";

    // defaults for user settings
    public static final int DEFAULT_FILESIZE_THRESHOLD_NOGO_MB = 20;
    public static final int DEFAULT_CONNECTION_TIMEOUT_SECS = 10;
    public static final int DEFAULT_TRANSFER_TIMEOUT_SECS = 10;

    public static final String TOOL_ID = "com.atakmap.android.missionpackage.MISSIONPACKAGE_TOOL";

    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;
    private int curReceiveNotificationId = 83000;

    private static final List<File> filesToSkip = new ArrayList<>();

    /**
     * HTTP Shared file download support
     */
    private final MissionPackageDownloader _downloader;

    /**
     * Reference to the map component
     */
    private final MissionPackageMapComponent _component;

    // UI Views
    private FileTransferLogView _logListView;
    private MissionPackageQueryResultView _queryListView;

    // store these here because view/adapter are currently recreated each time window is opened
    MissionPackageViewUserState _userState;

    private final Set<MissionPackageDownloadHandler> _dlHandlers = new HashSet<>();

    /**
     * ctor
     *
     * @param mapView
     * @param component
     */
    public MissionPackageReceiver(final MapView mapView,
            MissionPackageMapComponent component) {
        _mapView = mapView;
        _context = mapView.getContext();
        _component = component;
        _downloader = new MissionPackageDownloader(mapView, this);
        _userState = new MissionPackageViewUserState();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
    }

    public MapView getMapView() {
        return _mapView;
    }

    public void dispose() {
        if (_downloader != null)
            _downloader.dispose();
        _userState = null;
        FileInfoPersistanceHelper fiph = FileInfoPersistanceHelper.instance();
        if (fiph != null)
            fiph.dispose();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, action);

        final Bundle extras = intent.getExtras();

        // Register menu items in the tool selector
        if (MISSIONPACKAGE.equals(action)) {
            showMissionPackageView();
        } else if (MISSIONPACKAGE_LOG.equals(action)
                && _component.checkFileSharingEnabled()) {
            // switch to log view, first check if any logs exists
            if (!FileTransferLogView.hasLogs()) {
                Log.d(TAG, "Cannot switch to logView - No log currently exist");
                toast(R.string.mission_package_no_logs_saved);
            } else {
                showLogView();
            }
        } else if (MISSIONPACKGE_DETAIL.equals(action)) {
            if (extras == null)
                return;

            String missionPackageUID = extras.getString("missionPackageUID");

            // show dropdown
            // and then expand and scroll to the proper package in the list
            showMissionPackageView(missionPackageUID);
        }

        // Content selected via map selection tool
        else if (MISSIONPACKAGE_MAPSELECT.equals(action)) {
            if (extras == null)
                return;

            String groupId = extras.getString("MissionPackageUID");
            List<String> uids = new ArrayList<>();
            String[] uidArray = extras.getStringArray("itemUIDs");
            String mapItemUID = extras.getString("itemUID");
            if (!FileSystemUtils.isEmpty(uidArray))
                uids.addAll(Arrays.asList(uidArray));
            else if (mapItemUID != null)
                uids.add(mapItemUID);

            List<String> filtered = new ArrayList<>();
            for (String uid : uids) {
                String newUID = findMapItem(uid);
                if (!FileSystemUtils.isEmpty(newUID))
                    filtered.add(newUID);
            }
            uids = filtered;

            if (FileSystemUtils.isEmpty(uids)) {
                toast(R.string.mission_package_no_map_item_selected);
                showMissionPackageView(groupId);
                return;
            }

            if (FileSystemUtils.isEmpty(groupId)) {
                // Prompt user to create new or add to existing
                showMissionPackageView();
                List<Exportable> exports = new ArrayList<>();
                for (String uid : uids) {
                    MapItem mi = _mapView.getRootGroup().deepFindUID(uid);
                    if (mi != null)
                        exports.add(new MapItemHierarchyListItem(_mapView, mi));
                }
                try {
                    new MissionPackageExportMarshal(_context,
                            _userState.isIncludeAttachments())
                                    .execute(exports);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to export map items to MP", e);
                    toast(R.string.failed_export);
                }
                return;
            }

            // show view, and add user selected map item
            MissionPackageMapOverlay overlay = MissionPackageMapOverlay
                    .getOverlay();
            if (overlay != null) {
                showMissionPackageView(groupId);
                overlay.addMapItems(groupId, getUserState()
                        .isIncludeAttachments(),
                        uids.toArray(new String[0]));
            }
        }

        // Compress data package to ZIP and optionally send
        else if (MISSIONPACKAGE_SAVE.equals(action)) {
            if (extras == null)
                return;

            MissionPackageManifest contents = extras
                    .getParcelable(
                            MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
            boolean bSend = extras
                    .getBoolean(MissionPackageApi.INTENT_EXTRA_SAVEANDSEND);
            //simple send, no save required
            boolean bSendOnly = extras
                    .getBoolean(MissionPackageApi.INTENT_EXTRA_SENDONLY);
            String senderCallbackClassName = intent
                    .getStringExtra(
                            MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME);
            String senderCallbackPackageName = intent
                    .getStringExtra(
                            MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKPACKAGENAME);
            //Log.d(TAG, "SEND tool: " + tool);

            if (contents == null || contents.isEmpty()) {
                Log.w(TAG, "Unable to save Package with no contents");
                toast(R.string.no_mission_package_provided);
                return;
            }

            MissionPackageBaseTask.Callback cb = null;
            // Use reflection to instantiate the callback when package is created and sent
            if (!FileSystemUtils.isEmpty(senderCallbackClassName)) {
                try {
                    Log.d(TAG, "Creating Package sender callback of type: "
                            + senderCallbackClassName + "/"
                            + senderCallbackPackageName);
                    Object object = ClassLoaderHelper.createObject(
                            senderCallbackClassName, senderCallbackPackageName);
                    if (object instanceof SaveAndSendCallback)
                        cb = (MissionPackageBaseTask.Callback) object;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create Package BaseTask.Callback: "
                            + senderCallbackClassName, e);
                    cb = null;
                }
            }

            if (bSend) {
                if (!_component.checkFileSharingEnabled()) {
                    Log.d(TAG, "file sharing is not enabled");
                    return;
                }
                String[] toUIDs = intent
                        .getStringArrayExtra(
                                MissionPackageApi.INTENT_EXTRA_RECEIVERS);

                if (bSendOnly) {
                    Log.d(TAG, "Send Only: " + contents);
                    //no save, just quick send
                    if (toUIDs == null || toUIDs.length < 1) {
                        //no contacts specified, lets ask user how to send
                        Log.d(TAG,
                                "Prompting for send or post: "
                                        + contents);
                        send(contents, null, true, cb);
                    } else {
                        //if contacts are specified, lets just send it to them
                        Log.d(TAG, "Intent Send Only: " + contents);
                        _component.getFileIO().send(contents, toUIDs, cb);
                    }
                } else {
                    //save, then send
                    Log.d(TAG, "Intent Save and Send: " + contents);
                    // Do not delete upon error, invoking component can handle appropriately via their
                    // callback
                    //TODO support save and then post to server if netContact is empty
                    //TODO not now, close to release, this will preserve behavior for existing clients
                    if (toUIDs == null || toUIDs.length < 1) {
                        //no contacts specified, lets ask user how to send
                        Log.d(TAG,
                                "Prompting for save and send or post: "
                                        + contents);

                        final MissionPackageBaseTask.Callback fCb = cb;
                        final MissionPackageManifest fContents = contents;
                        _component.getFileIO().save(contents,
                                new MissionPackageBaseTask.Callback() {
                                    @Override
                                    public void onMissionPackageTaskComplete(
                                            MissionPackageBaseTask task,
                                            boolean success) {
                                        Log.d(TAG,
                                                "saving completed with status: "
                                                        + success);
                                        if (success)
                                            send(fContents, null, true, fCb);
                                        else {
                                            if (fCb != null)
                                                fCb.onMissionPackageTaskComplete(
                                                        task, success);
                                        }
                                    }
                                });
                    } else {
                        //if contacts are specified, lets just send it to them
                        _component.getFileIO().saveAndSendUIDs(contents, cb,
                                false,
                                toUIDs);
                    }
                }
            } else {
                Log.d(TAG, "Intent Save: " + contents);
                _component.getFileIO().save(contents, cb);
            }
        } else if (MISSIONPACKAGE_SEND.equals(action)
                && _component.checkFileSharingEnabled()) {
            // Sending Mission Package to a list of contacts
            String[] contactUIDs = intent.getStringArrayExtra("sendTo");
            MissionPackageManifest mf = extras.getParcelable(
                    MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
            String callbackClass = intent.getStringExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME);
            boolean sendOnly = intent.getBooleanExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDONLY, false);
            if (!FileSystemUtils.isEmpty(contactUIDs) && mf != null)
                MissionPackageApi.SendUIDs(_context, mf, callbackClass,
                        contactUIDs, sendOnly);
        } else if (MISSIONPACKAGE_UPDATE.equals(action)) {
            MissionPackageMapOverlay overlay = MissionPackageMapOverlay
                    .getOverlay();
            if (overlay == null)
                return;

            String missionPackageUID = extras.getString(
                    MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID);
            if (FileSystemUtils.isEmpty(missionPackageUID)) {
                Log.w(TAG,
                        "Unable to save Package with no mission package UID");
                toast(R.string.no_mission_package_provided);
                return;
            }

            boolean bSave = extras.getBoolean("save", true);

            String[] mapItemUIDArray = null;
            if (extras.containsKey("mapitems")) {
                mapItemUIDArray = extras.getStringArray("mapitems");
                if (mapItemUIDArray == null || mapItemUIDArray.length < 1)
                    mapItemUIDArray = null;
            }

            String[] filesArray = null;
            if (extras.containsKey("files")) {
                filesArray = extras.getStringArray("files");
                if (filesArray == null || filesArray.length < 1)
                    filesArray = null;
            }

            if (mapItemUIDArray == null && filesArray == null) {
                Log.w(TAG, "no mapitems or files to update: "
                        + missionPackageUID);
                toast(R.string.no_supported_data_selected_to_update_mission_package);
                return;
            }

            String senderCallbackClassName = intent
                    .getStringExtra(
                            MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME);

            MissionPackageBaseTask.Callback cb = null;
            // Use reflection to instaniate the callback when package is created and sent
            if (!FileSystemUtils.isEmpty(senderCallbackClassName)) {
                try {
                    Log.d(TAG, "Creating Package sender callback of type: "
                            + senderCallbackClassName);
                    Class<?> clazz = Class.forName(senderCallbackClassName);
                    Constructor<?> ctor = clazz.getConstructor();
                    Object object = ctor.newInstance();
                    if (object instanceof SaveAndSendCallback)
                        cb = (MissionPackageBaseTask.Callback) object;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create Package BaseTask.Callback: "
                            + senderCallbackClassName, e);
                    cb = null;
                }
            }

            boolean bIncludeAttachments = extras.getBoolean(
                    "includeAttachments",
                    getUserState().isIncludeAttachments());

            //get ref to the update manifest from the view/adapter
            MissionPackageListGroup group = overlay.getGroup(missionPackageUID);
            if (group == null) {
                Log.w(TAG, "Unable to find manifest: " + missionPackageUID);
                toast(R.string.failed_to_update_mission_package);
                return;
            }

            if (mapItemUIDArray != null && mapItemUIDArray.length > 0) {
                Log.d(TAG, "updating " + missionPackageUID
                        + " with mapitems size " + mapItemUIDArray.length);
                overlay.addMapItems(missionPackageUID, bIncludeAttachments,
                        mapItemUIDArray);
            }

            if (filesArray != null && filesArray.length > 0) {
                Log.d(TAG, "updating " + missionPackageUID
                        + " with files size " + filesArray.length);
                overlay.addFiles(group, filesArray);
            }

            if (bSave) {
                Log.d(TAG, "Saving changes to " + group);
                _component.getFileIO().save(group.getManifest(), cb);
            } else {
                Log.d(TAG, "Not saving changes to " + group);
            }
        } else if (MISSIONPACKAGE_DELETE.equals(action)) {
            if (extras == null)
                return;
            String path = extras.getString(MissionPackageApi.INTENT_EXTRA_PATH);
            if (!FileSystemUtils.isEmpty(path)) {
                MissionPackageFileIO.deletePackage(path,
                        _mapView.getRootGroup());
                return;
            }

            MissionPackageManifest manifest = extras.getParcelable(
                    MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
            if (manifest == null || manifest.isEmpty()) {
                Log.w(TAG, "Unable to delete Package with no manifest");
                toast(R.string.no_mission_package_provided);
                return;
            }

            //remove from DB and file system
            Log.v(TAG, "Deleting Mission Package: " + manifest);
            final String mPath = FileSystemUtils
                    .sanitizeWithSpacesAndSlashes(manifest
                            .getPath());

            FileInfoPersistanceHelper.instance().delete(
                    new File(mPath), TABLETYPE.SAVED);

            MissionPackageFileIO
                    .deletePackageFile(new File(mPath));

            // Remove content marked for deletion
            for (MissionPackageContent mc : manifest.getContents()
                    .getContents()) {
                NameValuePair nvp = mc.getParameter(
                        MissionPackageContent.PARAMETER_DeleteWithPackage);
                if (nvp == null || !nvp.getValue().equals("true"))
                    continue;
                MissionPackageListItem li;
                if (mc.isCoT()) {
                    li = MissionPackageManifestAdapter.UIDContentToUI(
                            _mapView.getRootGroup(), mc);
                } else {
                    NameValuePair pathPair = mc.getParameter(
                            MissionPackageContent.PARAMETER_LOCALPATH);
                    if (pathPair == null)
                        continue;
                    File f = new File(pathPair.getValue());
                    if (!IOProviderFactory.exists(f))
                        continue;
                    li = MissionPackageManifestAdapter.FileContentToUI(mc, f);
                }
                if (li != null)
                    li.removeContent();
            }
        } else if (MissionPackageReceiver.MISSIONPACKAGE_POST.equals(action)
                && _component.checkFileSharingEnabled()) {
            MissionPackageManifest manifest = intent
                    .getParcelableExtra("manifest");
            if (manifest == null || !manifest.isValid()) {
                Log.w(TAG, "Cannot MISSIONPACKAGE_POST without valid manifest");
                return;
            }

            //get the filepath of the deployed zip to transfer, not the user's save package
            String filepath = intent.getStringExtra("filepath");
            if (!FileSystemUtils.isFile(filepath)) {
                Log.w(TAG, "Cannot MISSIONPACKAGE_POST without valid file: "
                        + filepath);
                return;
            }

            String serverConnString = intent
                    .getStringExtra("serverConnectString");
            if (FileSystemUtils.isEmpty(serverConnString)) {
                serverConnString = _component.getConnectedServerString();
                if (FileSystemUtils.isEmpty(serverConnString)) {
                    Log.w(TAG,
                            "Cannot MISSIONPACKAGE_POST without valid server URL");
                    promptForNetworkSettings();
                    return;
                } else {
                    Log.d(TAG, "MISSIONPACKAGE_POST Using default server: "
                            + serverConnString);
                }
            }

            //look up hash in DB, rather than re-compute
            String hash = null;
            AndroidFileInfo fileInfo = FileInfoPersistanceHelper
                    .instance()
                    .getFileInfoFromFilename(
                            new File(
                                    FileSystemUtils
                                            .sanitizeWithSpacesAndSlashes(
                                                    manifest
                                                            .getPath())),
                            FileInfoPersistanceHelper.TABLETYPE.SAVED);
            if (fileInfo != null) {
                hash = fileInfo.sha256sum();
            }

            File file = new File(filepath);
            if (FileSystemUtils.isEmpty(hash)) {
                hash = HashingUtils.sha256sum(file);
            }
            if (FileSystemUtils.isEmpty(hash)) {
                Log.w(TAG, "Cannot MISSIONPACKAGE_POST without valid hash");
                return;
            }

            PostMissionPackageRequest request = new PostMissionPackageRequest(
                    serverConnString, hash, manifest.getName(),
                    MapView.getDeviceUid(),
                    file.getAbsolutePath());
            if (request == null || !request.isValid()) {
                Log.w(TAG, "Cannot MISSIONPACKAGE_POST without valid request");
                return;
            }

            _downloader.post(request);
        } else if (MissionPackageReceiver.MISSIONPACKAGE_QUERY.equals(action)
                && _component.checkFileSharingEnabled()) {
            String serverConnectString = intent
                    .getStringExtra("serverConnectString");
            if (FileSystemUtils.isEmpty(serverConnectString)) {
                serverConnectString = _component.getConnectedServerString();
                if (FileSystemUtils.isEmpty(serverConnectString)) {
                    Log.w(TAG,
                            "Cannot MISSIONPACKAGE_QUERY without valid server URL");
                    promptForNetworkSettings();
                    return;
                } else {
                    Log.d(TAG, "MISSIONPACKAGE_QUERY Using default server: "
                            + serverConnectString);
                }
            }

            String tool = intent.getStringExtra("tool");
            _downloader.query(serverConnectString, tool);
        } else if (MissionPackageReceiver.MISSIONPACKAGE_DOWNLOAD
                .equals(action) && _component.checkFileSharingEnabled()) {
            MissionPackageQueryResult result = intent
                    .getParcelableExtra("package");
            if (result == null || !result.isValid()) {
                Log.w(TAG,
                        "Cannot MISSIONPACKAGE_DOWNLOAD without valid result");
                return;
            }

            String serverConnectString = intent
                    .getStringExtra("serverConnectString");
            if (FileSystemUtils.isEmpty(serverConnectString)) {
                serverConnectString = _component.getConnectedServerString();
                if (FileSystemUtils.isEmpty(serverConnectString)) {
                    Log.w(TAG,
                            "Cannot MISSIONPACKAGE_DOWNLOAD without valid server connectString");
                    promptForNetworkSettings();
                    return;
                } else {
                    Log.d(TAG, "MISSIONPACKAGE_DOWNLOAD Using default server: "
                            + serverConnectString);
                }
            }

            FileTransfer fileTransfer = FileTransfer.fromQuery(_context,
                    result, serverConnectString,
                    _component.getFileIO()
                            .getMissionPackageIncomingDownloadPath());
            if (fileTransfer == null || !fileTransfer.isValid()) {
                Log.w(TAG,
                        "Cannot MISSIONPACKAGE_DOWNLOAD without valid fileTransfer");
                return;
            }

            handleCoTFileTransfer(fileTransfer);
        } else if (MISSIONPACKAGE_REMOVE_LASSO.equals(action)) {
            MissionPackageMapOverlay overlay = MissionPackageMapOverlay
                    .getOverlay();
            if (overlay == null)
                return;

            String dpUID = extras.getString(
                    MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID);
            final MissionPackageListGroup group = overlay.getGroup(dpUID);
            if (group == null) {
                Log.w(TAG, "Unable to save Package with no data package UID");
                toast(R.string.no_mission_package_provided);
                return;
            }

            String shapeUID = intent.getStringExtra("uid");
            MapItem mi = _mapView.getMapItem(shapeUID);
            if (!(mi instanceof DrawingShape))
                return;

            // XXX - Because data packages don't have any quick and easy
            // way of looking up contents by UID/path we have to build
            // a content map first
            final Map<String, MissionPackageListItem> itemMap = new HashMap<>();
            List<MissionPackageListItem> items = group.getItems();
            for (MissionPackageListItem item : items) {
                MissionPackageContent content = item.getContent();
                String uid;
                if (content.isCoT())
                    uid = content.getParameterValue("uid");
                else
                    uid = content.getParameterValue("localpath");
                if (FileSystemUtils.isEmpty(uid))
                    continue;
                itemMap.put(uid, item);
            }

            LassoSelectionDialog d = new LassoSelectionDialog(_mapView);
            d.setLassoShape((DrawingShape) mi);
            d.setFilter(new URIFilter() {
                @Override
                public boolean accept(String uri) {
                    return itemMap.containsKey(URIHelper.getContent(uri));
                }
            });
            d.setCallback(new LassoSelectionDialog.Callback() {
                @Override
                public void onContentSelected(List<String> uris) {
                    // Iterate through URIs to remove
                    for (String uri : uris) {
                        MissionPackageListItem item = null;
                        if (uri.startsWith(URIScheme.MAP_ITEM)) {
                            MapItem mi = URIHelper.getMapItem(_mapView, uri);
                            if (mi != null)
                                item = itemMap.get(mi.getUID());
                        } else if (uri.startsWith(URIScheme.FILE)) {
                            File f = URIHelper.getFile(uri);
                            if (f != null)
                                item = itemMap.get(f.getAbsolutePath());
                        }
                        if (item != null) {
                            group.removeItem(item);
                            item.removeContent();
                        }
                    }
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            HierarchyListReceiver.REFRESH_HIERARCHY));
                }
            });
            d.show();
        } else {
            Log.w(TAG, "Ignoring action of type: " + action);
        }
    }

    ClearContentRegistry.ClearContentListener ccl = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            Log.d(TAG, "Deleting mission packages");

            //stop pref listener to avoid toast for invalid prefs/settings
            //Note if/when we decide to leave ATAK running after a zeroize,
            //we will need to handle this differently
            MissionPackagePreferenceListener pl = _component.getPrefListener();
            if (pl != null) {
                pl.dispose();
            }

            //remove from database
            FileInfoPersistanceHelper.instance().clearAll();

            //delete files
            _component.getFileIO().clear();
        }
    };

    private String findMapItem(String mapItemUID) {
        MapItem item = _mapView.getRootGroup()
                .deepFindUID(mapItemUID);
        if (item == null)
            return null;

        // TODO could clean this up by pushing it into IMissionPackageEventHandler
        // Here we check some special cases to include more/expected data rather than default
        // map item that was touched by the user
        // if this is a route checkpoint, include entire route, rather than just this checkpoint
        String itemType = item.getType();
        if (item instanceof PointMapItem
                && "b-m-p-w".equals(itemType)) {
            Log.d(TAG,
                    "Processing check point selected by user via map, looking for parent route... "
                            + mapItemUID);

            Route route = getRouteWithPoint(_mapView,
                    (PointMapItem) item);
            if (route != null) {
                String routeUID = route.getUID();
                if (!FileSystemUtils.isEmpty(routeUID)) {
                    Log.d(TAG, "Using parent route for check point: "
                            + mapItemUID);
                    mapItemUID = routeUID;
                }
            }
        } else if (item.hasMetaValue("shapeUID")) {
            // Use shape UID - all shape markers should have this set
            mapItemUID = item.getMetaString("shapeUID", mapItemUID);
        } else if (item instanceof PointMapItem
                && (itemType.contains("center_")
                        || itemType.equals("shape_marker") || itemType
                                .contains("-c-c"))) {

            // If shapeUID isn't set for some reason rely on shapeName

            // See if this is a center point for a circle/rectangle or a free-form shape,
            // attempt to find the actual shape
            // Assume shape has same "shapeName" and is in "Drawing Objects" map group
            String shapeName = item.getTitle();
            Log.d(TAG, "Processing center point selected by user ("
                    + mapItemUID
                    + ") via map, looking for parent shape: " + shapeName);

            if (!FileSystemUtils.isEmpty(shapeName)) {
                MapGroup group = _mapView.getRootGroup().findMapGroup(
                        "Drawing Objects");
                if (group != null) {
                    // this seems to pick up circle/rectangle
                    MapItem shape = group.findItem(
                            "shapeName", shapeName);
                    if (shape == null) {
                        // and this seems to get the free-form shapes
                        shape = group.findItem("title",
                                shapeName);
                    }

                    if (shape != null) {
                        String shapeUID = shape.getUID();
                        if (!FileSystemUtils.isEmpty(shapeUID)) {
                            Log.d(TAG,
                                    "Using parent shape for center point: "
                                            + mapItemUID);
                            mapItemUID = shapeUID;
                        }
                    }
                }
            }
        }
        return mapItemUID;
    }

    public void promptForNetworkSettings() {
        new ServerListDialog(_mapView).promptNetworkSettings();
    }

    public static Route getRouteWithPoint(MapView mapView, PointMapItem item) {
        if (!(mapView.getContext() instanceof MapActivity)) {
            Log.w(TAG, "Unable to find route without MapActivity");
            return null;
        }

        MapActivity activity = (MapActivity) mapView.getContext();
        MapComponent mc = activity.getMapComponent(RouteMapComponent.class);
        if (!(mc instanceof RouteMapComponent)) {
            Log.w(TAG, "Unable to find route without RouteMapComponent");
            return null;
        }

        RouteMapComponent routeComponent = (RouteMapComponent) mc;
        return routeComponent.getRouteMapReceiver().getRouteWithPoint(item);
    }

    private void showMissionPackageView(String... paths) {
        MissionPackageMapOverlay.navigateTo(paths);
    }

    private void showLogView() {
        // drop down solely created for the purposes of managing the log list view
        DropDownReceiver llr = new DropDownReceiver(_mapView) {
            @Override
            public void onReceive(Context c, Intent i) {
            }

            @Override
            public void disposeImpl() {
            }
        };
        llr.showDropDown(getLogListView(), DropDownReceiver.HALF_WIDTH,
                DropDownReceiver.FULL_HEIGHT, DropDownReceiver.FULL_WIDTH,
                DropDownReceiver.HALF_HEIGHT, this);
    }

    public void showQueryResultsView(List<MissionPackageQueryResult> results,
            String serverConnectString) {
        if (FileSystemUtils.isEmpty(results)) {
            Log.d(TAG, "Cannot switch to query results View - No results");
            toast(R.string.no_mission_packages_available);
        } else {
            // drop down solely created for the purposes of managing the query list view
            DropDownReceiver llr = new DropDownReceiver(_mapView) {
                @Override
                public void onReceive(Context c, Intent i) {
                }

                @Override
                public void disposeImpl() {
                }

                @Override
                protected boolean onBackButtonPressed() {
                    return _queryListView != null && _queryListView
                            .onBackButtonPressed();
                }
            };
            llr.showDropDown(getQueryListView(results, serverConnectString),
                    0.6, DropDownReceiver.FULL_HEIGHT,
                    DropDownReceiver.FULL_WIDTH,
                    DropDownReceiver.HALF_HEIGHT, false, this);
        }
    }

    private synchronized View getLogListView() {
        //TODO re-inflate every time? or only when orientation changes? watch for duplicate listview headers
        _logListView = (FileTransferLogView) LayoutInflater.from(_context)
                .inflate(R.layout.missionpackage_log_layout, _mapView, false);
        _logListView.refresh();
        FileInfoPersistanceHelper.instance().addFileTransferListener(this);
        return _logListView;
    }

    @Override
    public void onEvent(FileTransferLog log, boolean added) {
        if (_logListView != null)
            _logListView.refresh();
    }

    private synchronized View getQueryListView(
            List<MissionPackageQueryResult> results,
            String serverConnectString) {
        //TODO re-inflate every time? or only when orientation changes? watch for duplicate listview headers
        _queryListView = (MissionPackageQueryResultView) LayoutInflater.from(
                _context).inflate(R.layout.missionpackage_queryresults_layout,
                        _mapView, false);
        _queryListView.refresh(results, serverConnectString);
        return _queryListView;
    }

    MissionPackageDownloader getDownloader() {
        return _downloader;
    }

    public MissionPackageMapComponent getComponent() {
        return _component;
    }

    /**
     * Build out URL to download a FileInfo DB file
     *
     * @param serverPort
     * @param action
     * @param fi
     * @param additionalQueryParameters
     * @return
     * @throws Exception
     */
    public static String getURL(int serverPort, String action, FileInfo fi,
            List<BasicNameValuePair> additionalQueryParameters)
            throws Exception {
        String ip = NetworkUtils.getIP();
        if (ip == null)
            throw new SocketException("Failed to determine IP address");

        Uri.Builder builder = Uri.parse(
                String.format(LocaleUtil.getCurrent(), "http://%s:%d", ip,
                        serverPort))
                .buildUpon();
        builder.path(action);
        builder.appendQueryParameter("hash", fi.sha256sum());
        if (additionalQueryParameters != null
                && additionalQueryParameters.size() > 0) {
            for (BasicNameValuePair pair : additionalQueryParameters)
                builder.appendQueryParameter(pair.getName(), pair.getValue());
        }

        return builder.build().toString();
    }

    /**
     * Find contact by UID
     *
     * @param contactUID
     * @return
     */
    public static Contact getNetworkContact(String contactUID) {
        return Contacts.getInstance().getContactByUuid(contactUID);
    }

    public MissionPackageViewUserState getUserState() {
        return _userState;
    }

    private boolean preprocessMPReceive(final String fileName,
            final String transferName,
            final String sha256hash,
            final String senderCallsign) {

        Log.d(TAG, "New File Transfer received: " +
                transferName + " hash=" + sha256hash +
                " sender=" + senderCallsign + " file=" + fileName);

        // check and see if we already have an exact match
        // Note we currently match on user label and SHA256 (not using package UID)
        AndroidFileInfo fileInfo = FileInfoPersistanceHelper.instance()
                .getFileInfoFromUserLabelHash(transferName,
                        sha256hash,
                        TABLETYPE.SAVED);
        if (fileInfo != null) {
            if (FileSystemUtils.isFile(fileInfo.file())) {
                // Note here we rely on the SHA256 in the DB rather than re-computing from the
                // filesystem (which can be slow for large files)
                Log.d(TAG,
                        "Package " + transferName
                                + " already exists with checksum: "
                                + sha256hash);

                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        String message = _context.getString(
                                R.string.mission_package_from_callsign_already_exists,
                                _context.getString(
                                        R.string.mission_package_name),
                                transferName,
                                senderCallsign);

                        NotificationUtil.getInstance().postNotification(
                                R.drawable.missionpackage_sent,
                                NotificationUtil.BLUE,
                                _context.getString(
                                        R.string.file_transfer_download_skipped),
                                message, message);

                    }
                });

                // File transfer log
                FileInfoPersistanceHelper.instance().insertLog(
                        new FileTransferLog(FileTransferLog.TYPE.RECV,
                                transferName,
                                _context.getString(
                                        R.string.mission_package_name)
                                        + " Received from " + senderCallsign,
                                fileInfo.sizeInBytes()));

                // Tell transfer system we already have this
                // by returning null
                return false;
            } else {
                // File is DB but not on filesystem??? just log it and re-download
                Log.w(TAG, "Package " + transferName
                        + " in DB but not on filesystem with SHA256: "
                        + sha256hash);
            }
        }
        return true;
    }

    private void handleCoTFileTransfer(FileTransfer fileTransfer) {
        if (!preprocessMPReceive(
                fileTransfer.getLocalPath(),
                fileTransfer.getName(), fileTransfer.getSHA256(false),
                fileTransfer.getSenderCallsign()))
            // We already had it
            return;

        // else download using legacy downloader
        _downloader.download(fileTransfer);
    }

    @Override
    public MPReceiver initiateReceive(final String fileName,
            final String transferName,
            final String sha256hash,
            final long expectedByteLength,
            final String senderCallsign) {

        if (!preprocessMPReceive(fileName, transferName,
                sha256hash, senderCallsign))
            // Tell transfer system we already have this
            // by returning null
            return null;

        // else initiate download;  UI code and download
        // setup extrapolated from pre-commo MissionPackageDownloader
        final int nid = curReceiveNotificationId++;

        final String tickerFilename = MissionPackageUtils.abbreviateFilename(
                transferName, 20);

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                NotificationUtil.getInstance().postNotification(
                        nid,
                        R.drawable.missionpackage_icon,
                        NotificationUtil.WHITE,
                        _context.getString(
                                R.string.mission_package_download_started),
                        _context.getString(
                                R.string.mission_package_downloading_from,
                                tickerFilename, senderCallsign),
                        _context.getString(
                                R.string.mission_package_downloading_from,
                                tickerFilename, senderCallsign));
            }
        });

        return new CommsMPReceiver(transferName, senderCallsign,
                sha256hash, expectedByteLength, nid,
                tickerFilename);
    }

    public int getWebServerPort() {
        return MissionPackagePreferenceListener.getInt(_prefs,
                WebServer.SERVER_PORT_KEY,
                WebServer.DEFAULT_SERVER_PORT);
    }

    public int getSecureWebServerPort() {
        return MissionPackagePreferenceListener.getInt(_prefs,
                WebServer.SECURE_SERVER_PORT_KEY,
                WebServer.DEFAULT_SECURE_SERVER_PORT);
    }

    public long getLowThresholdInBytes() {
        return (long) ((float) getNogoThresholdInBytes() * (1F / 3F));
    }

    public long getHighThresholdInBytes() {
        return (long) ((float) getNogoThresholdInBytes() * (2F / 3F));
    }

    public long getNogoThresholdInBytes() {
        return MissionPackagePreferenceListener.getInt(_prefs,
                MissionPackagePreferenceListener.filesharingSizeThresholdNoGo,
                DEFAULT_FILESIZE_THRESHOLD_NOGO_MB) * 1024L * 1024;
    }

    public long getTransferTimeoutMS() {
        return MissionPackagePreferenceListener
                .getInt(
                        _prefs,
                        MissionPackagePreferenceListener.filesharingTransferTimeoutSecs,
                        DEFAULT_TRANSFER_TIMEOUT_SECS)
                * 1000L;
    }

    public long getConnectionTimeoutMS() {
        return MissionPackagePreferenceListener
                .getInt(
                        _prefs,
                        MissionPackagePreferenceListener.filesharingConnectionTimeoutSecs,
                        DEFAULT_CONNECTION_TIMEOUT_SECS)
                * 1000L;
    }

    public static String getSender(FileTransfer fileTransfer) {
        //TODO migrate to use CotMapComponent.getInstance().getServerCallsign()
        return getSenderCallsign(
                fileTransfer.getSenderCallsign(),
                fileTransfer.getSenderUID());
    }

    public static String getSenderCallsign(String callsign, String uid) {
        //TODO migrate to use CotMapComponent.getInstance().getServerCallsign()

        // see if sender included his callsign
        String sender = callsign;
        if (!FileSystemUtils.isEmpty(sender))
            return sender;

        sender = uid;

        // attempt to get callsign from contact list
        Contact contact = getNetworkContact(sender);
        if (contact != null)
            sender = contact.getName();

        return sender;
    }

    /**
     * When the mission package is being migrated over to the new directory structure,
     * the directory watcher will incorrectly try to reimport the file.   This method
     * makes sure that the migration does not cause any additional importing.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
    public static void addFileToSkip(File file) {
        if (file != null && file.exists() && !file.isDirectory()) {
            filesToSkip.add(file);
        }
    }

    @Override
    public void fileUpdateCallback(File file, OPERATION op,
            long newestFileTime) {

        File fileToSkip = null;

        for (File skip : filesToSkip) {
            if (fileToSkip == null && skip.getName().equals(file.getName())) {
                fileToSkip = skip;
            }
        }
        if (fileToSkip != null) {
            Log.d(TAG, "skipping : " + file.getName());
            filesToSkip.remove(fileToSkip);
            return;
        }

        // Currently the DirectoryWatcher only watches for files manually placed, as we insert in
        // DB manually for newly created and downloaded/received files
        // Files received over network go directly to add() below
        Log.d(TAG,
                "fileUpdateCallback " + op.toString() + " "
                        + file.getAbsolutePath());
        try {
            switch (op) {
                case ADD: {
                    // kick off background to unzip and then add to ui via receiver.add(contents,
                    // userName)
                    new ExtractMissionPackageTask(file, this, null).execute();
                }
                    break;
                default:
                case REMOVE: {
                    uiRemove(file);
                }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to process fileUpdateCallback: " + file, e);
        }
    }

    private void uiRemove(final File file) {
        try {
            ((Activity) _context)
                    .runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MissionPackageMapOverlay overlay = MissionPackageMapOverlay
                                    .getOverlay();
                            if (overlay != null
                                    && !IOProviderFactory.exists(file))
                                overlay.remove(file, false);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "uiRemove failure", e);
        }
    }

    private void uiAdd(final MissionPackageManifest contents,
            final String userName) {
        try {
            ((Activity) _context)
                    .runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MissionPackageMapOverlay overlay = MissionPackageMapOverlay
                                    .getOverlay();
                            if (overlay == null)
                                return;
                            if (overlay.contains(contents)) {
                                // see if already listed. e.g. user import a large MP and then open MPT
                                // right away
                                // it will be added to list, and then when MP is finished being extracted,
                                // the
                                // ExtractMissionPackageTask will add it to the list...
                                Log.d(TAG, "Skipping UI list add of "
                                        + contents.toString());
                                return;
                            }

                            overlay.add(contents, userName);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "uiAdd failure", e);
        }
    }

    public void add(MissionPackageManifest contents, String userName) {
        uiAdd(contents, userName);
    }

    /**
     * Prompts user for send options (Note, does not save as does the MissionPackageListGroup
     * version in MissionPackageListAdapter)
     *
     *  @param manifest
     * @param netContacts
     * @param cb
     */
    private void send(final MissionPackageManifest manifest,
            final Contact[] netContacts, final boolean bSendOnly,
            final MissionPackageBaseTask.Callback cb) {

        // save if not saved, and save user has confirmed...
        if (manifest.isEmpty()) {
            toast(R.string.mission_package_cannot_send_empty_package);
            Log.d(TAG, "Cannot send empty package "
                    + manifest);
            return;
        }

        if (!FileSystemUtils.isEmpty(netContacts)) {
            _component.getFileIO().send(manifest, netContacts, cb);
            return;
        }

        SendDialog.Builder b = new SendDialog.Builder(_mapView);
        b.setName(manifest.getName());
        b.setIcon(R.drawable.missionpackage_icon);
        b.setMissionPackage(manifest);
        b.setMissionPackageCallback(cb);
        b.show();
    }

    /**
     * Note does not save prior to sending, as does the MissionPackageListGroup version
     * in MissionPackageListAdapter
     *
     * @param manifest
     * @param serverConnectString
     */
    private void upload(final MissionPackageManifest manifest,
            final String serverConnectString) {
        //deploy zip to temp folder, then post to server
        MissionPackageBaseTask.Callback callback = new MissionPackageBaseTask.Callback() {
            @Override
            public void onMissionPackageTaskComplete(
                    MissionPackageBaseTask task, boolean success) {

                if (task instanceof CompressionTask && success) {
                    //no-op
                } else if (task instanceof CopyTask && success) {
                    File file = ((CopyTask) task).getDestination();
                    Log.d(TAG,
                            "Deployed and sending to server: "
                                    + file.getAbsolutePath());
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            MissionPackageReceiver.MISSIONPACKAGE_POST)
                                    .putExtra("manifest", manifest)
                                    .putExtra("serverConnectString",
                                            serverConnectString)
                                    .putExtra("filepath",
                                            file.getAbsolutePath()));
                } else {
                    //TODO toast?
                    Log.w(TAG, "Failed to deploy and send to server: "
                            + task.getManifest().getPath());
                }
            }
        };

        //deploy package to "transfer" folder, then post to server
        _component.getFileIO().send(manifest, callback);
    }

    private void toast(int strId, Object... args) {
        Toast.makeText(_context, _context.getString(strId, args),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    private class CommsMPReceiver implements MPReceiver {
        private final String transferName;
        private final String senderCallsign;
        private final String expectedSha256;
        private final long expectedByteLen;

        // For notifications/progress updates
        private final int notifierId;
        private final String tickerFilename;
        private final DownloadProgressTracker progressTracker;
        private final Notification.Builder builder;
        private final NotificationManager notifyManager;

        private final File tempFile;

        public CommsMPReceiver(String transferName,
                String senderCallsign,
                String expectedSha256,
                long expectedByteLen,
                int notifierId, String tickerFilename) {
            this.notifierId = notifierId;
            this.tickerFilename = tickerFilename;
            this.transferName = transferName;
            this.senderCallsign = senderCallsign;
            this.expectedSha256 = expectedSha256;
            this.expectedByteLen = expectedByteLen;

            // setup progress notifications
            notifyManager = (NotificationManager) _context.getSystemService(
                    Context.NOTIFICATION_SERVICE);

            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder = new Notification.Builder(_context);
            } else {
                builder = new Notification.Builder(_context,
                        "com.atakmap.app.def");
            }
            builder.setContentTitle(
                    _context.getString(R.string.mission_package_download))
                    .setContentText(
                            _context.getString(R.string.mission_package_sending,
                                    senderCallsign, tickerFilename))
                    .setSmallIcon(R.drawable.missionpackage_sent);

            // Create temp file
            tempFile = new File(
                    MissionPackageFileIO.getMissionPackageIncomingDownloadPath(
                            FileSystemUtils.getRoot().getAbsolutePath()),
                    UUID.randomUUID().toString());

            builder.setProgress(100, 1, false);

            // We don't know total length, yet.
            progressTracker = new DownloadProgressTracker(expectedByteLen);

        }

        @Override
        public void receiveProgress(long bytesTransferred,
                long totalBytesExpected,
                int attemptNum, int maxAttempts) {
            if (totalBytesExpected == 0)
                totalBytesExpected = this.expectedByteLen;
            long currentTime = System.currentTimeMillis();
            if (progressTracker.contentReceived(bytesTransferred,
                    totalBytesExpected, currentTime)) {
                String message = _context.getString(
                        R.string.mission_package_sending2,
                        senderCallsign, tickerFilename,
                        progressTracker.getCurrentProgress(),
                        MathUtils.GetLengthString(totalBytesExpected),
                        MathUtils.GetDownloadSpeedString(progressTracker
                                .getAverageSpeed()),
                        MathUtils.GetTimeRemainingString(progressTracker
                                .getTimeRemaining()));
                builder.setProgress(100,
                        progressTracker.getCurrentProgress(), false);
                builder.setContentText(message);
                notifyManager
                        .notify(notifierId,
                                builder.build());
                Log.d(TAG, message);
                // start a new block
                progressTracker.notified(currentTime);
            }
        }

        /**
         * Verify this is a valid file transfer
         * @return Error message if file transfer invalid, null if valid
         */
        private String verifyFileTransfer() {
            // Now verify we got download correctly
            if (!FileSystemUtils.isFile(tempFile))
                return "Failed to download data";

            if (!HashingUtils.verify(tempFile, this.expectedByteLen,
                    this.expectedSha256))
                return "Size or MD5 mismatch";

            Log.d(TAG, "File Transfer downloaded and verified");
            return null;
        }

        /**
         * This postprocessing taken from legacy GetFileTransferOperation 
         */
        private MissionPackageManifest postProcessTempFileGFTO(
                MissionPackageManifest manifest) throws Exception {

            // update notification
            String message = _context.getString(
                    R.string.mission_package_processing,
                    tickerFilename, senderCallsign);
            builder.setProgress(100, 99, false);
            builder.setContentText(message);
            if (notifyManager != null)
                notifyManager.notify(notifierId,
                        builder.build());

            MissionPackageConfiguration.ImportInstructions inst = manifest
                    .getConfiguration().getImportInstructions();
            Log.d(TAG,
                    "Processing: " + transferName + " with instructions: "
                            + inst.toString());
            switch (inst) {
                case ImportDelete: {
                    //extract, import, delete and return updated/localized manifest
                    manifest = MissionPackageExtractorFactory
                            .Extract(_context, tempFile,
                                    FileSystemUtils.getRoot(),
                                    true);
                    Log.d(TAG,
                            "Auto imported/deleted Package: "
                                    + tempFile.getAbsolutePath());
                    FileSystemUtils.deleteFile(tempFile);
                }
                    break;
                case ImportNoDelete: {
                    //extract, import, update DB, and return updated/localized manifest
                    manifest = MissionPackageExtractorFactory
                            .Extract(_context, tempFile,
                                    FileSystemUtils.getRoot(),
                                    true);
                    if (manifest == null)
                        throw new Exception("Failed to extract " + _context
                                .getString(R.string.mission_package_name));

                    // now we are unzipped and processed, add db entry
                    // create DB entry so Directory Watcher will ignore, then update after file write is
                    // check for name clash and rename file before creating .zip...
                    // complete. Rename .zip as necessary, but we do not update name in
                    // contents/manifest
                    // b/c then MD5 won't match the sender's. For consistency we do not update DB
                    // "user label" column
                    String path = MissionPackageFileIO
                            .getMissionPackagePath(FileSystemUtils.getRoot()
                                    .getAbsolutePath());
                    String filename = MissionPackageUtils.getUniqueFilename(
                            path, transferName);
                    if (!filename.equals(transferName + ".zip")) {
                        // TODO or would we rather overwrite if name/UID match?
                        Log.d(TAG,
                                "Name "
                                        + transferName
                                        + " is already taken, renaming incoming Package file: "
                                        + filename);
                    }

                    File savedMissionPackage = new File(path, filename);
                    FileInfoPersistanceHelper db = FileInfoPersistanceHelper
                            .instance();
                    AndroidFileInfo fileInfo = db.getFileInfoFromFilename(
                            savedMissionPackage,
                            TABLETYPE.SAVED);
                    if (fileInfo == null) {
                        // dont create SHA256 until after we write out file
                        fileInfo = new AndroidFileInfo("",
                                savedMissionPackage,
                                MIMETypeMapper
                                        .GetContentType(savedMissionPackage),
                                manifest.toXml(true));
                        db.insertOrReplace(fileInfo, TABLETYPE.SAVED);
                    }

                    // move (or copy) file to permanent location
                    if (!FileSystemUtils.renameTo(tempFile,
                            savedMissionPackage)) {
                        progressTracker.error();
                        throw new Exception("Failed to save " + _context
                                .getString(R.string.mission_package_name)
                                + " Error 1");
                    }

                    // now update DB entry with label, sender, size, MD5, etc so user can view, resend
                    // etc via UI
                    if (!FileSystemUtils.isFile(savedMissionPackage)) {
                        progressTracker.error();
                        throw new Exception("Failed to save " + _context
                                .getString(R.string.mission_package_name)
                                + " Error 2");
                    }

                    // TODO what if zip failed, but .zip existed from a previous compression task? may
                    // need to check retVal?
                    // TODO ensure at least one file was successfully zipped...
                    fileInfo.setUserName(senderCallsign);
                    fileInfo.setUserLabel(transferName);
                    // TODO is this checked dynamically or cached when File is created?
                    fileInfo.setSizeInBytes((int) IOProviderFactory
                            .length(savedMissionPackage));

                    fileInfo.setUpdateTime(IOProviderFactory
                            .lastModified(savedMissionPackage));

                    // file size and hash was verified above, so lets use that rather than re-compute
                    if (FileSystemUtils.isEmpty(expectedSha256)) {
                        Log.w(TAG, "Recomputing SHA256...");
                        fileInfo.computeSha256sum();
                    } else {
                        fileInfo.setSha256sum(expectedSha256);
                    }

                    db.update(fileInfo, TABLETYPE.SAVED);
                    manifest.setPath(savedMissionPackage.getAbsolutePath());

                    Log.d(TAG,
                            "Received Package has been saved: "
                                    + savedMissionPackage.getAbsolutePath());
                }
                    break;
                case NoImportDelete: {
                    //extract, do not import, delete, and return updated/localized manifest
                    Log.d(TAG,
                            "Auto (no import) deleted Package: "
                                    + tempFile.getAbsolutePath());
                    manifest = MissionPackageExtractorFactory
                            .Extract(_context, tempFile,
                                    FileSystemUtils.getRoot(),
                                    false);
                    FileSystemUtils.deleteFile(tempFile);
                }
                    break;
                case NoImportNoDelete: {
                    //extract, do not import, do not delete, and return updated/localized manifest
                    Log.d(TAG,
                            "Did not import or delete Package: "
                                    + tempFile.getAbsolutePath());
                    manifest = MissionPackageExtractorFactory
                            .Extract(_context, tempFile,
                                    FileSystemUtils.getRoot(),
                                    false);
                }
                    break;
            }

            return manifest;
        }

        private void postProcessTempFileDL(int attempt,
                MissionPackageManifest manifest) {
            // ftr/initialRequest = the initial request that was sent out

            if (manifest == null || !manifest.isValid()) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to parse request manifest");
                NotificationUtil.getInstance().postNotification(
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

            // handle single file or Mission Package
            NameValuePair p;
            int filesFailed = 0;
            File downloadedFile = new File(manifest.getPath());
            if (!inst.isDelete()) {
                // all file contents should exist
                Log.d(TAG, "Package received contents: " + manifest);
                for (MissionPackageContent content : manifest.getFiles()) {
                    if (content.isIgnore())
                        continue; // don't validate b/c extractor skips it

                    p = content.getParameter(
                            MissionPackageContent.PARAMETER_LOCALPATH);
                    if (p == null || !p.isValid()
                            || !FileSystemUtils.isFile(p.getValue())) {
                        Log.e(TAG,
                                "File Transfer Download Failed - Failed to extract MP file: "
                                        + manifest.getName() + ", "
                                        + (p == null ? ""
                                                : p.getValue()));
                        filesFailed++;
                    }
                }

                // now add to UI
                MissionPackageReceiver.this.add(manifest, senderCallsign);
            }

            // log it
            FileInfoPersistanceHelper.instance().insertLog(
                    new FileTransferLog(FileTransferLog.TYPE.RECV, manifest
                            .getName(),
                            _context.getString(
                                    R.string.mission_package_name)
                                    + " Received from " + senderCallsign,
                            expectedByteLen));

            if (filesFailed > 0) {
                NotificationUtil.getInstance().postNotification(notifierId,
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.file_transfer_download_failed),
                        _context.getString(
                                R.string.failed_to_extract_mission_package,
                                _context.getString(
                                        R.string.mission_package_name),
                                manifest.getName()),
                        _context.getString(
                                R.string.failed_to_extract_mission_package,
                                _context.getString(
                                        R.string.mission_package_name),
                                manifest.getName()));
                return;
            }

            // downloaded file transfer successfully...
            Log.d(TAG,
                    "File Transfer Download Complete - Successfully downloaded file: "
                            + transferName + " from " + senderCallsign
                            + " to "
                            + downloadedFile.getAbsolutePath()
                            + ", number attempts: "
                            + attempt);
            String tickerFilename = MissionPackageUtils.abbreviateFilename(
                    transferName, 20);

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
                    notifierId,
                    R.drawable.missionpackage_sent, NotificationUtil.GREEN,
                    _context.getString(
                            R.string.file_transfer_download_complete),
                    _context.getString(R.string.mission_package_sent_message,
                            senderCallsign, tickerFilename),
                    notificationIntent, true);

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
                        senderCallsign);
                onReceiveIntent
                        .putExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                manifest);
                onReceiveIntent.putExtra(
                        MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                        notifierId);
                AtakBroadcast.getInstance().sendBroadcast(onReceiveIntent);
            }

        }

        /**
         * Parse the temp file as a CoT event
         * @return CoT event object if successful or null if failed
         */
        private CotEvent readCotEvent() {
            try {
                byte[] data = FileSystemUtils.read(tempFile);
                String xml = new String(data, FileSystemUtils.UTF8_CHARSET);
                return CotEvent.parse(xml);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read " + tempFile + " as CoT message", e);
                return null;
            }
        }

        /**
         * Attempt to import CoT event and handle notifications
         * @param event CoT event
         */
        private void postProcessCotEvent(CotEvent event) {
            String name = CotUtils.getCallsign(event);
            if (FileSystemUtils.isEmpty(name))
                name = transferName;

            Bundle extras = new Bundle();
            extras.putString("from", "MissionPackage");
            extras.putString("fromClass",
                    MissionPackageReceiver.class.getName());
            extras.putBoolean("visible", true);
            ImportResult res = CotMapComponent.getInstance()
                    .processCotEvent(event, extras);
            if (res != ImportResult.SUCCESS) {
                // Import failed
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.importmgr_failed_import, name),
                        null, null);
                return;
            }
            // Import successful
            Intent focus = new Intent(CoordOverlayMapReceiver.SHOW_DETAILS);
            GeoPoint point = event.getGeoPoint();
            if (point != null)
                focus.putExtra("point", point);
            focus.putExtra("uid", event.getUID());
            NotificationUtil.getInstance().postNotification(
                    notifierId,
                    R.drawable.missionpackage_sent, NotificationUtil.GREEN,
                    _context.getString(
                            R.string.importmgr_finished_import, name),
                    null,
                    focus, true);
        }

        // Post-processing code on the file, assembled more or less as-is from
        // legacy MissionPackageDownloader, GetFileTransferOperation,
        // NetworkOperation, and a myriad of DataDroid glue classes
        private void postProcessTempFile(final int attempt) throws Exception {
            String errMsg = verifyFileTransfer();
            if (errMsg != null)
                throw new Exception(errMsg);

            // Check if the content is a ZIP or CoT message
            if (!FileSystemUtils.isZip(tempFile)) {
                CotEvent event = readCotEvent();
                if (event != null && event.isValid()) {
                    postProcessCotEvent(event);
                    NotificationUtil.getInstance()
                            .clearNotification(notifierId);
                    return;
                }
            }

            MissionPackageManifest manifest = MissionPackageExtractorFactory
                    .GetManifest(tempFile);
            if (manifest == null || !manifest.isValid()) {
                throw new Exception("Unable to extract package manifest");
            }

            // Let external applications handle Mission Package download first
            // TODO: No sender UID from Commo?
            FileTransfer ft = new FileTransfer(transferName, null,
                    senderCallsign, manifest.getUID(), null, null,
                    expectedByteLen, expectedSha256);
            List<MissionPackageDownloadHandler> handlers = getDownloadHandlers();
            for (MissionPackageDownloadHandler h : handlers) {
                if (h.onMissionPackageDownload(ft, manifest)) {
                    NotificationUtil.getInstance()
                            .clearNotification(notifierId);
                    return;
                }
            }

            // Proceed with Mission Package post processing
            final MissionPackageManifest m = postProcessTempFileGFTO(manifest);
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    postProcessTempFileDL(attempt, m);
                }
            });
        }

        @Override
        public void receiveComplete(final boolean success,
                final String failReason,
                final int attempt) {
            try {
                if (!success)
                    throw new Exception(failReason);

                postProcessTempFile(attempt);
            } catch (final Exception e) {

                FileSystemUtils.deleteFile(tempFile);

                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        /*
                         * This block from legacy MissionPackageDownloader's
                         * onRequestConnectionError(), which handled exceptions
                         * from the GFTOperation
                         */
                        String error = e.getMessage();
                        if (FileSystemUtils.isEmpty(error))
                            error = _context
                                    .getString(R.string.notification_text30);
                        Log.e(TAG, error);
                        Log.e(TAG,
                                "Mission Package Download Failed - "
                                        + error +
                                        " for transfer: " + transferName
                                        + " from "
                                        + senderCallsign);
                        NotificationUtil.getInstance().postNotification(
                                notifierId,
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.mission_package_download_failed),
                                _context.getString(
                                        R.string.mission_package_download_from_sender_failed_after_attempts,
                                        tickerFilename, senderCallsign,
                                        attempt, error),
                                _context.getString(
                                        R.string.mission_package_download_from_sender_failed_after_attempts,
                                        tickerFilename, senderCallsign,
                                        attempt, error));
                    }
                });

            }

        }

        @Override
        public File getDestinationFile() {
            return tempFile;
        }

        @Override
        public void attemptFailed(String reason, int attemptNum,
                int maxAttempts) {
            // update notification
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notifierId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.mission_package_download_failing),
                            _context.getString(
                                    R.string.mission_package_download_from_sender_has_failed_attempts,
                                    tickerFilename, senderCallsign,
                                    attemptNum, maxAttempts),
                            _context.getString(
                                    R.string.mission_package_download_from_sender_has_failed_attempts,
                                    tickerFilename, senderCallsign,
                                    attemptNum, maxAttempts));
        }
    }

    private List<MissionPackageDownloadHandler> getDownloadHandlers() {
        synchronized (_dlHandlers) {
            return new ArrayList<>(_dlHandlers);
        }
    }

    public void addDownloadHandler(MissionPackageDownloadHandler h) {
        synchronized (_dlHandlers) {
            _dlHandlers.add(h);
        }
    }

    public void removeDownloadHandler(MissionPackageDownloadHandler h) {
        synchronized (_dlHandlers) {
            _dlHandlers.remove(h);
        }
    }
}
