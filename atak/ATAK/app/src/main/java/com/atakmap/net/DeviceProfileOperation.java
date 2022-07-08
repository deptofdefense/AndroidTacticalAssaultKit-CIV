
package com.atakmap.net;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.file.MissionPackageExtractorFactory;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class DeviceProfileOperation extends HTTPOperation {

    private static final String TAG = "DeviceProfileOperation";

    public static final String PARAM_PROFILE_REQUEST = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_REQEUST";

    /**
     * Replaced with PARAM_PROFILE_REQUEST.
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", removeAt = "4.7", forRemoval = true)
    public static final String PARAM_PROFILE_REQEUST = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_REQEUST";

    /**
     * Note, only set if DeviceProfileRequest.getAutoImportProfile() is false
     */
    public static final String PARAM_PROFILE_OUTPUT_FILE = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_OUTPUT_FILE";

    public static final String PARAM_PROFILE_CONTENT_TYPE = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_CONTENT_TYPE";

    /**
     * Name of outpu parameter which will contain Last-Modified. Currently only populated for tool/file
     * requests which have new content (returned HTTP 200)
     */
    public static final String PARAM_PROFILE_OUTPUT_LAST_MODIFIED = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_OUTPUT_LAST_MODIFIED";

    public static final String PARAM_PROFILE_OUTPUT_HTTP_STATUS = com.atakmap.net.DeviceProfileOperation.class
            .getName()
            + ".PARAM_PROFILE_OUTPUT_HTTP_STATUS";

    /**
     * Name of response header indicating time last modified
     */
    public static final String HEADER_RESPONSE_LAST_MODIFIED = "Last-Modified";
    public static final String HEADER_REQUEST_IF_MODIFIED_SINCE = "If-Modified-Since";

    private static final int NOTIFICATION_ID = TAG.hashCode();

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        TakHttpClient httpClient = null;
        try {
            // Get request data
            DeviceProfileRequest profileRequest = (DeviceProfileRequest) request
                    .getParcelable(
                            DeviceProfileOperation.PARAM_PROFILE_REQUEST);
            if (profileRequest == null) {
                throw new DataException("Unable to serialize profile request");
            }

            String path;
            SslNetCotPort.Type portType;
            AtakAuthenticationCredentials credentials = null;

            String clientUid = MapView.getDeviceUid();

            if (profileRequest.getOnEnrollment()) {
                //api/tls allows basic auth (no client cert) on port 8446
                path = "/api/tls/profile/enrollment?clientUid=" + clientUid;
                portType = SslNetCotPort.Type.CERT_ENROLLMENT;
            } else if (profileRequest.getOnConnect()) {
                path = "/api/device/profile/connection?syncSecago=";
                path += profileRequest.getSyncSecago();
                path += "&clientUid=" + clientUid;
                portType = SslNetCotPort.Type.SECURE;
            } else if (profileRequest.hasFilepath()) {
                //api/tls allows basic auth (no client cert) on port 8446
                portType = SslNetCotPort.Type.CERT_ENROLLMENT;
                path = "/api/tls/profile/tool/";
                path += profileRequest.getTool();

                List<String> filepaths = profileRequest.getFilepaths();
                boolean bFirst = true;
                StringBuilder pathBuilder = new StringBuilder(path);
                for (int i = 0; i < filepaths.size(); i++) {
                    String cur = filepaths.get(i);
                    if (FileSystemUtils.isEmpty(cur)) {
                        Log.w(TAG, "Invalid filepath: " + i);
                        continue;
                    }

                    if (bFirst) {
                        pathBuilder.append("/file?relativePath=");
                        bFirst = false;
                    } else {
                        pathBuilder.append("&relativePath=");
                    }

                    //require leading slash
                    if (!cur.startsWith("/")) {
                        pathBuilder.append("/");
                    }

                    pathBuilder.append(cur);
                }
                path = pathBuilder.toString();

                //                try {
                //                   path = URLEncoder.encode(path, FileSystemUtils.UTF8_CHARSET);
                //                } catch (UnsupportedEncodingException uee) {
                //                   Log.d(TAG, "unable to encode: " + path);
                //                }
                path = path.replaceAll(" ", "%20");

                path += "&clientUid=" + clientUid;
                if (profileRequest.hasSyncSecago()) {
                    path += "&syncSecago=" + profileRequest.getSyncSecago();
                }
            } else if (profileRequest.hasTool()) {
                path = "/api/device/profile/tool/";
                path += profileRequest.getTool();
                path += "?clientUid=" + clientUid;
                if (profileRequest.hasSyncSecago()) {
                    path += "&syncSecago=" + profileRequest.getSyncSecago();
                }

                portType = SslNetCotPort.Type.SECURE;
            } else {
                throw new DataException("Invalid profile request!");
            }

            if (profileRequest.hasCredentials()) {
                credentials = new AtakAuthenticationCredentials();
                credentials.username = profileRequest.getUsername();
                credentials.password = profileRequest.getPassword();
            }

            String baseUrl = profileRequest.getServer()
                    + SslNetCotPort.getServerApiPath(portType);

            if (!baseUrl.startsWith("http"))
                baseUrl = "https://" + baseUrl;

            SSLSocketFactory sslSocketFactory = CertificateManager
                    .getSockFactory(
                            false, baseUrl,
                            profileRequest.isAllowAllHostnames());
            httpClient = new TakHttpClient(baseUrl, sslSocketFactory);
            HttpGet httpget = new HttpGet(baseUrl + path);

            if (profileRequest.hasIfModifiedSince()) {
                //Note, TAK Server only returns files which have been modified since this date. If
                //multiple paths are specified, only those which have been modified since this date
                //are returned
                Log.d(TAG, "Request " + HEADER_REQUEST_IF_MODIFIED_SINCE + ": "
                        + profileRequest.getIfModifiedSince());
                httpget.addHeader(HEADER_REQUEST_IF_MODIFIED_SINCE,
                        profileRequest.getIfModifiedSince());
            }

            Log.d(TAG, "Getting profile: " + httpget.getRequestLine());

            TakHttpResponse response;
            if (credentials != null) {
                response = httpClient.execute(httpget, credentials);
            } else {
                response = httpClient.execute(httpget);
            }

            Bundle output = new Bundle();
            output.putParcelable(PARAM_PROFILE_REQUEST, profileRequest);

            if (response.isOk()) {
                //content available, process it now
                Context useContext = DeviceProfileClient.getInstance()
                        .getContext() != null
                                ? DeviceProfileClient.getInstance().getContext()
                                : MapView.getMapView().getContext();
                processResults(useContext, profileRequest, response, output,
                        profileRequest.getAutoImportProfile());
            } else if (response.isStatus(HttpStatus.SC_NO_CONTENT)) {
                //no content available, nothing to process, but not an error
                Log.d(TAG, "HttpStatus.SC_NO_CONTENT "
                        + response.getReasonPhrase());
            } else if (response.isStatus(HttpStatus.SC_NOT_MODIFIED)) {
                //content not updated since specified time, nothing to process, but not an error
                Log.d(TAG, "HttpStatus.SC_NOT_MODIFIED "
                        + response.getReasonPhrase());
            } else {
                throw new ConnectionException(
                        "Failed to get profile: " + profileRequest.getType(),
                        response.getStatusCode());
            }

            output.putInt(PARAM_PROFILE_OUTPUT_HTTP_STATUS,
                    response.getStatusCode());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "DeviceProfileRequest failed", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (ConnectionException e) {
            Log.e(TAG, "DeviceProfileRequest failed2", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "DeviceProfileRequest failed3", e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (httpClient != null)
                    httpClient.shutdown();
            } catch (Exception e) {
                Log.d(TAG, "Failed to shutdown the client", e);
            }
        }
    }

    private void processResults(Context context,
            DeviceProfileRequest profileRequest,
            TakHttpResponse response, Bundle output, boolean autoImportProfile)
            throws IOException {

        //write response to file system (avoid entire file in RAM)
        HttpEntity resEntity = response.getEntity();
        File downloaded = new File(
                MissionPackageFileIO.getMissionPackageIncomingDownloadPath(
                        FileSystemUtils.getRoot().getAbsolutePath()),
                UUID.randomUUID().toString());

        Log.d(TAG, "processResults: " + downloaded.getAbsolutePath());

        //setup progress notification
        Notification.Builder builder = null;
        DownloadProgressTracker progressTracker = null;
        long contentLength = response.getContentLength();
        //TODO not getting content length for large PDF doc
        String title;
        String messageTitle = null;
        int notifId = NOTIFICATION_ID;

        if (profileRequest.isDisplayNotification() && contentLength > 0) {
            String url = response.getRequestUrl();
            if (FileSystemUtils.isEmpty(url))
                url = profileRequest.toString();

            Log.d(TAG, "Displaying progress for: " + url);

            if (profileRequest.hasTool()) {
                title = context.getString(R.string.profile_downloading_content,
                        profileRequest.getTool());
            } else {
                title = context.getString(R.string.profile_downloading_content,
                        "Profile");
            }

            if (profileRequest.hasOutputPath()) {
                messageTitle = context.getString(
                        R.string.profile_downloading_content,
                        new File(profileRequest.getOutputPath()).getName());
            } else {
                messageTitle = context.getString(
                        R.string.profile_downloading_content,
                        (profileRequest.getFilepaths().size() + " files"));
            }

            //get a unique notification ID
            notifId = NotificationUtil.getInstance().reserveNotifyId();

            NotificationUtil.getInstance().postNotification(notifId,
                    ATAKConstants.getIconId(), title, messageTitle,
                    messageTitle);
            builder = NotificationUtil.getInstance()
                    .getNotificationBuilder(notifId);
            builder.setProgress(100, 1, false);

            progressTracker = new DownloadProgressTracker(contentLength);
            progressTracker.setCurrentLength(0);
        }

        int len;
        byte[] buf = new byte[8192];
        try (OutputStream fos = IOProviderFactory.getOutputStream(downloaded);
                InputStream in = resEntity.getContent()) {
            while ((len = in.read(buf)) > 0) {
                fos.write(buf, 0, len);

                // see if we should update progress notification based on progress or time since
                // last update
                long currentTime = System.currentTimeMillis();
                if (progressTracker != null
                        && progressTracker.contentReceived(len, currentTime)) {
                    String message = String
                            .format(LocaleUtil.getCurrent(),
                                    context.getString(
                                            R.string.profile_downloading_proogress),
                                    messageTitle,
                                    progressTracker
                                            .getCurrentProgress(),
                                    MathUtils.GetLengthString(contentLength),
                                    MathUtils
                                            .GetDownloadSpeedString(
                                                    progressTracker
                                                            .getAverageSpeed()),
                                    MathUtils
                                            .GetTimeRemainingString(
                                                    progressTracker
                                                            .getTimeRemaining()));
                    if (builder != null) {
                        builder.setProgress(100,
                                progressTracker.getCurrentProgress(), false);
                        builder.setContentText(message);
                        NotificationUtil.getInstance().postNotification(notifId,
                                builder.build(), true);
                    }
                    Log.d(TAG, message);
                    // start a new block
                    progressTracker.notified(currentTime);
                }
            } // end read loop
        }

        //more than one file was requested, unzip and return path to that dir
        if (!FileSystemUtils.isFile(downloaded)) {
            Log.w(TAG, "Cannot extract invalid file: "
                    + downloaded.getAbsolutePath());
            if (progressTracker != null)
                progressTracker.error();
            throw new IOException(
                    "Profile data not found: " + downloaded.getAbsolutePath());
        }

        //see if last modified header was specified on the response
        Header lastMod = response.getHeader(HEADER_RESPONSE_LAST_MODIFIED);
        if (lastMod != null) {
            //Note TAK Server returns last mod time for newest file
            output.putString(PARAM_PROFILE_OUTPUT_LAST_MODIFIED,
                    lastMod.getValue());
            Log.d(TAG,
                    "Response last modified: " + lastMod.getValue());
        }

        if (builder != null && profileRequest.isDisplayNotification()) {
            // update notification
            String message = context.getString(
                    R.string.profile_processing,
                    messageTitle);
            builder.setProgress(100, 99, false);
            builder.setContentText(message);
            NotificationUtil.getInstance().postNotification(notifId,
                    builder.build(), true);
        }

        //first see if we should import as mission package
        if (autoImportProfile) {
            //extract, import, delete profile Mission Package
            Log.d(TAG,
                    "Auto importing/deleting Package: "
                            + downloaded.getAbsolutePath());
            MissionPackageManifest manifest = MissionPackageExtractorFactory
                    .Extract(context, downloaded, FileSystemUtils.getRoot(),
                            true);
            FileSystemUtils.deleteFile(downloaded);

            if (manifest != null && manifest.isValid()) {
                Log.d(TAG, "Imported: " + manifest);
            }
        } else {
            Log.d(TAG, "Not importing/deleting Package: "
                    + downloaded.getAbsolutePath());

            //If we got back a zip, then extract it now
            String contentType = response.getContentType();
            if (!profileRequest.isDoNotUnzip() && FileSystemUtils
                    .isEquals(contentType, ResourceFile.MIMEType.ZIP.MIME)) {
                Log.d(TAG,
                        "Unzipping response: " + downloaded.getAbsolutePath());

                //see if we should leave in temp folder, or place it at direction of the request
                File contentFolder = new File(downloaded.getParent(),
                        downloaded.getName() + "_content");
                if (profileRequest.hasOutputPath()) {
                    contentFolder = new File(profileRequest.getOutputPath());
                    Log.d(TAG, "Output folder: "
                            + contentFolder.getAbsolutePath());
                }

                if (!IOProviderFactory.exists(contentFolder)
                        && !IOProviderFactory.mkdirs(contentFolder)) {
                    Log.w(TAG, "Cannot create dir "
                            + contentFolder.getAbsolutePath());
                }

                FileSystemUtils.unzip(downloaded, contentFolder, true);

                //delete temp file, and return path to unzipped dir
                FileSystemUtils.deleteFile(downloaded);

                //set output path of the downloaded file
                output.putString(PARAM_PROFILE_CONTENT_TYPE, contentType);
                output.putString(PARAM_PROFILE_OUTPUT_FILE,
                        contentFolder.getAbsolutePath());
            } else {
                Log.d(TAG, "Not unzipping response type: " + contentType);

                File outputFile = downloaded;
                if (profileRequest.hasOutputPath()) {
                    outputFile = new File(profileRequest.getOutputPath());
                    Log.d(TAG, "Output file: " + outputFile.getAbsolutePath());

                    File parent = outputFile.getParentFile();
                    if (parent == null) {
                        Log.e(TAG, "outputfile does not have a parent: "
                                + outputFile.getAbsolutePath());
                        return;
                    }

                    if (!IOProviderFactory.exists(parent)
                            && !IOProviderFactory.mkdirs(parent)) {
                        Log.w(TAG, "Cannot create dir "
                                + parent.getAbsolutePath());
                    }

                    if (!FileSystemUtils.renameTo(downloaded, outputFile)) {
                        Log.w(TAG, "rename failed copy: "
                                + outputFile.getAbsolutePath());
                        outputFile = downloaded;
                    }
                }

                //set output path of the downloaded file
                output.putString(PARAM_PROFILE_CONTENT_TYPE, contentType);
                output.putString(PARAM_PROFILE_OUTPUT_FILE,
                        outputFile.getAbsolutePath());
            }

            Log.d(TAG, "Processing complete: " + notifId);
            NotificationUtil.getInstance().clearNotification(notifId);
        }
    }
}
