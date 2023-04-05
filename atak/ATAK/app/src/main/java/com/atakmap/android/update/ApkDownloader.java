
package com.atakmap.android.update;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.util.Base64;
import android.text.Editable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import android.content.DialogInterface;
import android.content.Context;
import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.http.rest.BasicUserCredentials;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.update.http.ApkFileRequest;
import com.atakmap.android.update.http.GetApkOperation;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import com.foxykeep.datadroid.requestmanager.RequestManager.RequestListener;

import java.io.File;

/**
 * HTTP download support APKs. Leverages Android Service to offload async HTTP
 * requests
 * 
 * 
 */
public class ApkDownloader implements RequestListener {

    protected static final String TAG = "ApkDownloader";

    private MapView _mapView;

    public static final int notificationId = 86789;
    //public static final int pluginNotificationId = 86709;

    //Auto Update Requests
    public static final int REQUEST_TYPE_GET_APK;

    /**
     * Most recent request was silent, or not. Indicates whether
     * to report update/check errors to user
     */
    private boolean _bSilent;

    static {
        REQUEST_TYPE_GET_APK = NetworkOperationManager.register(
                "com.atakmap.android.update.http.GetApkOperation",
                new com.atakmap.android.update.http.GetApkOperation());
    }

    /**
     * ctor
     *  @param mapView
     */
    public ApkDownloader(MapView mapView) {
        _mapView = mapView;

    }

    public synchronized void dispose() {
        _mapView = null;
    }

    /**
     * Download specified file asynchronously
     * 
     * @param apkUrl the url for the apk to download
     * @param packageName the package name
     * @param filename the filename
     * @param hash the hash to verify against.
     * @param bInstall install after a succesfull download.
     */
    public void downloadAPK(final String apkUrl, final String packageName,
            final String filename,
            final String hash, final boolean bInstall) {

        //once user has indicated desire to interact, we now report errors
        this._bSilent = false;

        //Destination needs to be outside of the IO Abstraction
        File apkDir = FileSystemUtils
                .getItem(RemoteProductProvider.REMOTE_REPO_CACHE_PATH);
        if (!apkDir.mkdirs())
            Log.d(TAG, "could not wrap: " + apkDir);

        //send request w/out credentials initially
        ApkFileRequest request = new ApkFileRequest(packageName, apkUrl,
                filename,
                apkDir.getAbsolutePath(), notificationId, bInstall, false,
                hash, getBasicUserCredentials(apkUrl));

        downloadAPK(request);
    }

    private final static Object lock = new Object();
    private static boolean downloading;

    private void downloadAPK(final ApkFileRequest request) {
        // notify user

        synchronized (lock) {
            if (downloading) {
                // if the AppMgmtActivity is showing use the activity context, otherwise use the other context
                Context c = AppMgmtActivity.getActivityContext();
                if (c == null)
                    c = _mapView.getContext();
                Toast.makeText(c, "download already in progress",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            downloading = true;
        }

        NotificationUtil.getInstance().postNotification(
                notificationId,
                R.drawable.ic_menu_apps, NotificationUtil.BLUE,
                "Downloading "
                        + _mapView.getContext().getString(R.string.app_name)
                        + " updates...",
                "Downloading: " + request.getFileName(),
                "Downloading: " + request.getFileName(), null, false);

        // Kick off async HTTP request to get config from update server
        try {
            HTTPRequestManager.from(_mapView.getContext()).execute(
                    request.createGetFileRequest(), this);
        } catch (IllegalArgumentException iae) {
            synchronized (lock) {
                downloading = false;
            }
        }

        Log.d(TAG, "Downloading APK " + request);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        Log.d(TAG, "onRequestFinished");

        // HTTP response received successfully
        if (request.getRequestType() == ApkDownloader.REQUEST_TYPE_GET_APK) {
            synchronized (lock) {
                downloading = false;
            }

            if (resultData == null) {
                Log.e(TAG,
                        "Update Download Failed - Unable to obtain results");
                if (!_bSilent) {
                    NotificationUtil.getInstance().postNotification(
                            notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _mapView.getContext().getString(
                                    R.string.app_name)
                                    + " Update Failed",
                            "Update server not available",
                            "Update server not available");
                }
                return;
            }

            // the initial request that was sent out
            final ApkFileRequest initialRequest = resultData
                    .getParcelable(GetApkOperation.PARAM_GETAPKFILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG, "Update Failed - Unable to parse request");
                if (!_bSilent) {
                    NotificationUtil.getInstance().postNotification(
                            notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _mapView.getContext().getString(
                                    R.string.app_name)
                                    + " Update Failed",
                            "Unable to parse request",
                            "Unable to parse request");
                }
                return;
            }

            if (resultData
                    .containsKey(GetApkOperation.PARAM_GETCREDENTIALS)
                    &&
                    resultData
                            .getBoolean(GetApkOperation.PARAM_GETCREDENTIALS)) {
                promptForCredentials(_mapView.getContext(), initialRequest,
                        new Runnable() {
                            @Override
                            public void run() {
                                downloadAPK(initialRequest);
                            }
                        });
                return;
            }

            Log.d(TAG, "Downloaded: " + initialRequest);
            NotificationUtil.getInstance().clearNotification(
                    initialRequest.getNotificationId());
            if (initialRequest.isInstall()) {
                //attempt install
                if (!AppMgmtUtils.install(_mapView.getContext(),
                        initialRequest.getFile())) {
                    Log.w(TAG,
                            "installProduct failed: "
                                    + initialRequest.getPackageName());
                }
            }
        } else {
            synchronized (lock) {
                Log.e(TAG,
                        "unknown request.getRequestType() encountered, setting download flag to false",
                        new Exception());
                downloading = false;

            }
        }
    }

    private static String trimUrl(String updateUrl) {
        // hack to allow for a base updateUrl to be used when storing credentials
        if (updateUrl == null) {
            return null;
        } else if (updateUrl.endsWith(".infz")) {
            updateUrl = updateUrl.substring(0, updateUrl.lastIndexOf("/"));
        } else if (updateUrl.endsWith(".inf")) {
            updateUrl = updateUrl.substring(0, updateUrl.lastIndexOf("/"));
        } else if (updateUrl.endsWith(".apk")) {
            updateUrl = updateUrl.substring(0, updateUrl.lastIndexOf("/"));
        } else if (updateUrl.endsWith(".png")) {
            updateUrl = updateUrl.substring(0, updateUrl.lastIndexOf("/"));
        }
        return updateUrl;
    }

    static BasicUserCredentials getBasicUserCredentials(String updateUrl) {

        updateUrl = trimUrl(updateUrl);

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        updateUrl);

        if (credentials != null
                && !FileSystemUtils.isEmpty(credentials.username)) {
            String base64 = credentials.username + ":" + credentials.password;
            try {
                base64 = Base64.encodeToString(
                        base64.getBytes(FileSystemUtils.UTF8_CHARSET),
                        Base64.NO_WRAP);
                return new BasicUserCredentials(base64);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static void promptForCredentials(final Context context,
            final GetFileRequest request, final Runnable r) {
        Log.d(TAG, "Prompting user credentials for: " + request.toString());

        //layout borrowed from AtakAuthenticationHandler, but not using
        //that DB to cache credentials
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.login_dialog, null);

        final EditText uidText = dialogView
                .findViewById(R.id.txt_name);
        final EditText pwdText = dialogView
                .findViewById(R.id.password);

        // if the AppMgmtActivity is showing use the activity context, otherwise use the other context
        Context c = AppMgmtActivity.getActivityContext();
        if (c == null)
            c = context;

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(c);
        dialogBuilder.setTitle(R.string.login_update_server);
        dialogBuilder.setView(dialogView)
                .setPositiveButton("Login",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String username = uidText.getText().toString();
                                String base64 = username + ":"
                                        + pwdText.getText().toString();
                                base64 = Base64.encodeToString(
                                        base64.getBytes(
                                                FileSystemUtils.UTF8_CHARSET),
                                        Base64.NO_WRAP);
                                BasicUserCredentials creds = new BasicUserCredentials(
                                        base64);
                                request.setCredentials(creds);

                                AtakAuthenticationDatabase
                                        .saveCredentials(
                                                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                                trimUrl(request.getUrl()),
                                                username, pwdText.getText()
                                                        .toString(),
                                                true);
                                Log.d(TAG,
                                        "Re-executing APK request with credentials");
                                r.run();

                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                NotificationUtil.getInstance()
                                        .clearNotification(notificationId);
                            }
                        });

        final Dialog loginDialog = dialogBuilder.create();

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        request.getUrl());

        final CheckBox checkBox = dialogView
                .findViewById(R.id.password_checkbox);
        checkBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean isChecked) {
                        if (isChecked) {
                            pwdText.setTransformationMethod(
                                    HideReturnsTransformationMethod
                                            .getInstance());
                        } else {
                            pwdText.setTransformationMethod(
                                    PasswordTransformationMethod.getInstance());
                        }
                    }
                });

        if (credentials != null) {
            if (!FileSystemUtils.isEmpty(credentials.username))
                uidText.setText(credentials.username);
            if (!FileSystemUtils.isEmpty(credentials.password))
                pwdText.setText(credentials.password);

            if (!FileSystemUtils.isEmpty(credentials.password)) {
                checkBox.setEnabled(false);
                pwdText.addTextChangedListener(new AfterTextChangedWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s != null && s.length() == 0) {
                            checkBox.setEnabled(true);
                            pwdText.removeTextChangedListener(this);
                        }
                    }
                });
            } else {
                checkBox.setEnabled(true);
            }
        }

        loginDialog.show();
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        final String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "Download Failed - Connection Data Error: " + detail);
        synchronized (lock) {
            downloading = false;
        }
        if (!_bSilent) {
            NotificationUtil.getInstance().postNotification(
                    notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _mapView.getContext().getString(R.string.app_name)
                            + " " + _mapView.getContext().getString(
                                    R.string.update_failed),
                    _mapView.getContext()
                            .getString(R.string.unable_to_download)
                            + ":"
                            + detail,
                    _mapView.getContext()
                            .getString(R.string.unable_to_download)
                            + ":"
                            + detail);
        }
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "Download Failed - Request Data Error");
        synchronized (lock) {
            downloading = false;
        }
        if (!_bSilent) {
            NotificationUtil.getInstance().postNotification(
                    notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _mapView.getContext().getString(R.string.app_name)
                            + " " + _mapView.getContext().getString(
                                    R.string.update_failed),
                    _mapView.getContext()
                            .getString(R.string.unable_to_download),
                    _mapView.getContext()
                            .getString(R.string.unable_to_download));
        }
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "Download Failed - Request Custom Error");
        synchronized (lock) {
            downloading = false;
        }
        if (!_bSilent) {
            NotificationUtil.getInstance().postNotification(
                    notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    _mapView.getContext().getString(R.string.app_name)
                            + " " + _mapView.getContext().getString(
                                    R.string.update_failed),
                    _mapView.getContext()
                            .getString(R.string.unable_to_download),
                    _mapView.getContext()
                            .getString(R.string.unable_to_download));
        }
    }
}
