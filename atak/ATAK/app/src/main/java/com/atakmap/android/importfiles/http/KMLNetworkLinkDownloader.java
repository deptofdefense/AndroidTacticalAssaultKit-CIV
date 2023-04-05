
package com.atakmap.android.importfiles.http;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.annotations.FortifyFinding;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.net.AtakAuthenticationHandlerHTTP;
import javax.net.ssl.TrustManager;
import com.atakmap.net.CertificateManager;

import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.FileOutputStream;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetFilesOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.task.ImportNetworkLinksTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.SpatialDbContentSource;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLMatcher;
import com.atakmap.spatial.kml.KMLUtil;
import com.atakmap.util.zip.IoUtils;
import com.ekito.simpleKML.model.Link;
import com.ekito.simpleKML.model.NetworkLink;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import org.apache.http.client.methods.HttpGet;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP download support for remote KML import. Supports nested NetworkLinks Leverages Android
 * Service to offload async HTTP requests
 *
 */
public class KMLNetworkLinkDownloader extends NetworkLinkDownloader {

    protected static final String TAG = "KMLNetworkLinkDownloader";

    /**
     * Cache for performance
     */
    private Serializer _serializer;

    /**
     * ctor
     * 
     * @param context the context to be used for downloading kml network links.  Must be an
     *                activity context.
     */
    public KMLNetworkLinkDownloader(Context context) {
        super(context, 84000);
        if (!(context instanceof Activity)) {
            throw new IllegalArgumentException(
                    "KMLNetworkLinkRefresh requires Activity context");
        }

        // XXX - unused due to SimpleKML unreliability (ATAK-7343)
        _serializer = new Persister(new KMLMatcher());
    }

    /**
     * Download specified file asynchronously
     * 
     * @param resource the remote resource to download.
     */
    @Override
    public void download(RemoteResource resource, boolean showNotifications) {

        if (FileSystemUtils.isEmpty(resource.getUrl())) {
            Log.e(TAG, "Unable to determine URL");
            if (showNotifications)
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_kml_download_failed,
                        R.string.importmgr_unable_to_determine_url);
            return;
        }

        if (FileSystemUtils.isEmpty(resource.getName())) {
            Log.e(TAG, "Unable to determine KML filename label");
            if (showNotifications)
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_kml_download_failed,
                        R.string.importmgr_unable_to_determine_kml_filename);
            return;
        }

        // all files (this and child Network Links) are stored in temp dir named based on UID
        // The UID should be based on the name + URL instead of randomly generated
        // or else this will create tons of duplicate directories when auto-refresh is on
        String uid = UUID.nameUUIDFromBytes((resource.getName()
                + resource.getUrl()).getBytes(FileSystemUtils.UTF8_CHARSET))
                .toString();
        File tmpDir = new File(
                FileSystemUtils.getItem(FileSystemUtils.TMP_DIRECTORY), uid);
        if (IOProviderFactory.exists(tmpDir))
            FileSystemUtils.deleteDirectory(tmpDir, true);
        else if (!IOProviderFactory.mkdirs(tmpDir)) {
            Log.w(TAG,
                    "Failed to create directories: "
                            + tmpDir.getAbsolutePath());
        }
        RemoteResourceRequest request = new RemoteResourceRequest(resource,
                resource.getName(), tmpDir.getAbsolutePath(),
                getNotificationId(), showNotifications);

        List<GetFileRequest> requests = new ArrayList<>();
        requests.add(request);
        download(new RemoteResourcesRequest(resource, uid, requests,
                request.getNotificationId(), showNotifications));
    }

    /**
     * Download specified files and parse each one, iterate to download any child NetworkLinks
     * Somewhat recursive with all Network Links for all files in a request being downloaded
     * together and then parsed together. If any of those files in turn have child Network Links,
     * all will be processed together, and so on
     * 
     * @param request the request to use.
     */
    private void download(final RemoteResourcesRequest request) {

        if (request == null || !request.isValid()) {
            Log.e(TAG, "Cannot download invalid request");
            return;
        }

        // notify user
        Log.d(TAG, "Import KML Network Link download request created for: "
                + request);

        if (request.showNotifications())
            postNotification(request.getNotificationId(),
                    R.drawable.ic_kml_file_notification_icon,
                    getString(R.string.importmgr_remote_kml_download_started),
                    getString(R.string.importmgr_downloading_count_files,
                            request.getCount()));

        // Kick off async HTTP request to get file
        ndl(request);
    }

    private void ndl(final RemoteResourcesRequest requests) {
        Thread t = new Thread(TAG) {
            @Override
            public void run() {
                final String resourceUrl = requests.getResource().getUrl();
                _downloading.add(resourceUrl);
                Log.d(TAG, "start download: " + resourceUrl);
                try {
                    for (GetFileRequest r : requests.getRequests()) {
                        if (!(r instanceof RemoteResourceRequest))
                            continue;

                        RemoteResourceRequest request = (RemoteResourceRequest) r;

                        InputStream input;

                        final String urlStr = r.getUrl();
                        URI uri = new URI(urlStr);
                        String host = uri.getHost();

                        boolean hostIsTakServer = false;

                        TAKServer[] servers = TAKServerListener.getInstance()
                                .getConnectedServers();
                        if (servers != null) {
                            for (TAKServer server : servers) {
                                NetConnectString netConnectString = NetConnectString
                                        .fromString(
                                                server.getConnectString());
                                if (netConnectString.getHost()
                                        .equalsIgnoreCase(host)) {
                                    hostIsTakServer = true;
                                    break;
                                }
                            }
                        }

                        if (hostIsTakServer &&
                                urlStr.toLowerCase(LocaleUtil.getCurrent())
                                        .startsWith("https")
                                &&
                                uri.getPort() == 8443) {

                            String baseUrl = "https://" + uri.getHost() + ":"
                                    + uri.getPort();
                            TakHttpClient client = TakHttpClient
                                    .GetHttpClient(baseUrl);
                            HttpGet httpget = new HttpGet(urlStr);
                            TakHttpResponse response = client.execute(httpget);

                            input = response.getEntity().getContent();
                        } else {
                            URL url = new URL(request.getUrl());
                            URLConnection conn = url.openConnection();
                            conn.setRequestProperty("User-Agent", "TAK");
                            conn.setUseCaches(true);
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(30000);
                            setAcceptAllVerifier(conn);

                            conn = processRedirect(conn);

                            // support authenticated connections
                            if (conn instanceof HttpURLConnection) {
                                AtakAuthenticationHandlerHTTP.Connection connection;
                                connection = AtakAuthenticationHandlerHTTP
                                        .makeAuthenticatedConnection(
                                                (HttpURLConnection) conn, 3);
                                conn = connection.conn;
                                input = connection.stream;
                            } else {
                                conn.connect();
                                input = conn.getInputStream();
                            }
                        }

                        File fout = new File(request.getDir(),
                                request.getFileName());

                        try (FileOutputStream fos = IOProviderFactory
                                .getOutputStream(fout)) {
                            FileSystemUtils.copy(input, fos);
                            Log.d(TAG, "success: " + request.getDir() + "/"
                                    + request.getFileName());
                        } catch (Exception e) {
                            Log.d(TAG, "failure: " + request.getFileName());
                            onRequestConnectionError(new Request(
                                    NetworkOperationManager.REQUEST_TYPE_GET_FILES),
                                    new RequestManager.ConnectionError(900,
                                            "unable to write download"));
                        } finally {
                            IoUtils.close(input);
                        }

                    }
                    Bundle b = new Bundle();
                    b.putParcelable(GetFilesOperation.PARAM_GETFILES, requests);
                    onRequestFinished(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILES), b);

                } catch (Exception e) {
                    Log.e(TAG, "error occurred", e);
                    onRequestConnectionError(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILES),
                            new RequestManager.ConnectionError(-1,
                                    "unable to download network source"));

                } finally {
                    Log.d(TAG, "end download: " + resourceUrl);
                    _downloading.remove(resourceUrl);
                }
            }
        };
        t.start();

        //HTTPRequestManager.from(_context).execute(
        //        request.createGetFileRequests(), this);
    }

    /**
     * Given an existing connection, process a redirect.
     * @param conn the connection
     * @return the redirected connection
     * @throws IOException interactions with the connection failed.
     */
    @FortifyFinding(finding = "Server-Side Request Forgery", rational = "FortifyFinding flags this as a Server-Side Request Forgery but the URL containing the KML file has rediected us to a new site. This protection should be part of the network management / firewall and is too broad scoped for the application.")
    private URLConnection processRedirect(URLConnection conn)
            throws IOException {
        URLConnection retval = conn;
        if (conn instanceof HttpURLConnection) {
            boolean redirect = false;

            retval = duplicate((HttpURLConnection) conn);

            int status = ((HttpURLConnection) conn)
                    .getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER)
                    redirect = true;
            }

            if (redirect) {

                // get redirect url from "location" header field
                final String newUrl = conn.getHeaderField("Location");

                // get the cookie if need, for login
                //String cookies = conn
                //        .getHeaderField("Set-Cookie");

                // open the new connnection again
                if (newUrl != null &&
                        (newUrl.startsWith("http://")
                                || newUrl.startsWith("https://"))) {
                    boolean useCaches = conn.getUseCaches();
                    String userAgent = conn.getRequestProperty("User-Agent");
                    int connectionTimeout = conn.getConnectTimeout();
                    int readTimeout = conn.getReadTimeout();

                    // server side forgery described in FortifyAPI annotation
                    retval = new URL(newUrl)
                            .openConnection();
                    retval.setRequestProperty("User-Agent", userAgent);
                    retval.setUseCaches(useCaches);
                    retval.setConnectTimeout(connectionTimeout);
                    retval.setReadTimeout(readTimeout);
                    setAcceptAllVerifier(retval);
                    Log.d(TAG, "Redirect to URL : " + newUrl);
                } else {
                    Log.e(TAG, "invalid redirect (not allowing): " + newUrl);
                }
            }
        }

        return retval;
    }

    private static void setAcceptAllVerifier(URLConnection connection) {
        if (connection instanceof javax.net.ssl.HttpsURLConnection) {
            try {
                javax.net.ssl.SSLContext sc = CertificateManager
                        .createSSLContext(new TrustManager[] {
                                CertificateManager.SelfSignedAcceptingTrustManager
                });
                ((javax.net.ssl.HttpsURLConnection) connection)
                        .setSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception ignored) {
            }
        }
    }

    private static HttpURLConnection duplicate(HttpURLConnection conn)
            throws IOException {
        HttpURLConnection retval = (HttpURLConnection) conn.getURL()
                .openConnection();
        retval.setRequestProperty("User-Agent", "TAK");

        final String xcommonsitename = conn
                .getRequestProperty("x-common-site-name");
        if (xcommonsitename != null) {
            retval.setRequestProperty("x-common-site-name", xcommonsitename);
        }
        retval.setUseCaches(conn.getUseCaches());
        retval.setConnectTimeout(conn.getConnectTimeout());
        retval.setReadTimeout(conn.getReadTimeout());
        setAcceptAllVerifier(retval);
        return retval;
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        // HTTP response received successfully
        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_FILES) {
            if (resultData == null) {
                Log.e(TAG,
                        "Remote KML Download Failed - Unable to obtain results");
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_kml_download_failed,
                        R.string.importmgr_unable_to_obtain_results);
                return;
            }

            // the initial request that was sent out
            final RemoteResourcesRequest initialRequest = resultData
                    .getParcelable(GetFilesOperation.PARAM_GETFILES);
            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG,
                        "Remote KML Download Failed - Unable to parse request");
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_kml_download_failed,
                        R.string.importmgr_unable_to_parse_request);
                return;
            }

            // loop all requested files
            final RemoteResourcesRequest childRequests = new RemoteResourcesRequest(
                    initialRequest.getResource(),
                    initialRequest.getUID(),
                    new ArrayList<GetFileRequest>(),
                    initialRequest.getNotificationId(),
                    initialRequest.showNotifications());
            Log.d(TAG, "Parsing child requests size: "
                    + initialRequest.getRequests().size());
            for (final GetFileRequest curRequest : initialRequest
                    .getRequests()) {
                File downloadedFile = new File(curRequest.getDir(),
                        curRequest.getFileName());
                if (!FileSystemUtils.isFile(downloadedFile)) {
                    Log.e(TAG,
                            "Remote KML Download Failed - Failed to create local file: "
                                    + downloadedFile);
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(
                                    R.string.importmgr_failed_to_create_local_file_filename,
                                    curRequest.getFileName()));
                    continue;
                    //return;
                }

                // Attempt to set file extension, user specified this is KML/Z
                String filename = curRequest.getFileName().toLowerCase(
                        LocaleUtil.getCurrent());
                if (!filename.endsWith(".kml") && !filename.endsWith(".kmz")) {
                    if (FileSystemUtils.isZip(new File(downloadedFile
                            .getParent(), filename))) {
                        filename += ".kmz";
                    } else {
                        filename += ".kml";
                    }

                    Log.d(TAG, curRequest.getFileName() + " set filename: "
                            + filename);
                    File renamed = new File(downloadedFile.getParent(),
                            filename);
                    if (!FileSystemUtils.renameTo(downloadedFile, renamed)) {
                        Log.e(TAG,
                                "Remote KML Download Failed - Failed to rename local file: "
                                        + renamed.getAbsolutePath());
                        postNotification(initialRequest,
                                R.drawable.ic_network_error_notification_icon,
                                getString(
                                        R.string.importmgr_remote_kml_download_failed),
                                getString(
                                        R.string.importmgr_failed_to_create_local_file_filename,
                                        curRequest.getFileName()));
                        return;
                    }

                    downloadedFile = renamed;
                    curRequest.setFileName(filename);

                }

                if (!FileSystemUtils.isFile(downloadedFile)) {
                    Log.e(TAG,
                            "Remote KML Download Failed - Failed to create local file: "
                                    + downloadedFile);
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(
                                    R.string.importmgr_failed_to_create_local_file_filename,
                                    curRequest.getFileName()));
                    return;
                }

                // Extract KML from KMZ if necessary
                try {
                    File tempKML = KMLUtil
                            .getKmlFileFromKmzFile(downloadedFile,
                                    _context.getCacheDir());
                    if (tempKML != null) {
                        Log.d(TAG, "Extracting KMZ downloaded file: "
                                + curRequest);
                        downloadedFile = tempKML;
                    } else {
                        Log.d(TAG,
                                "Network Link is not KMZ, processing KML...");
                    }
                } catch (IOException e1) {
                    Log.d(TAG,
                            "Network Link is not KMZ, processing KML...",
                            e1);
                }

                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(downloadedFile)) {
                    Log.d(TAG, "Parsing downloaded file: " + downloadedFile);
                    childRequests.getRequests().addAll(
                            processLinks(initialRequest, curRequest, fis));
                } catch (Exception e) {
                    Log.e(TAG,
                            "Remote KML Download Failed - Unable to de-serialize KML",
                            e);
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(R.string.importmgr_invalid_kml,
                                    curRequest.getFileName()));
                    return;
                }

            } // end request loop

            // see if we have more Network Links to download
            if (childRequests.getCount() > 0) {
                // psuedo-recurse
                download(childRequests);
            } else {
                Log.d(TAG, "Finished downloading Network Links for: "
                        + initialRequest);

                // all data is on file system, now move them into KML folder to be imported
                // we are storing all in the same temp dir, so just grab off first one
                File tempFolder = new File(initialRequest.getRequests()
                        .get(0).getDir());

                new ImportNetworkLinksTask(_serializer, _context,
                        initialRequest.getResource(),
                        initialRequest.getNotificationId(),
                        initialRequest.showNotifications())
                                .execute(tempFolder);
            }
        } else {
            Log.w(TAG,
                    "Unhandled request response: " + request.getRequestType());
        }
    }

    private List<RemoteResourceRequest> processLinks(
            final RemoteResourcesRequest initialRequest,
            final GetFileRequest curRequest, InputStream is) {
        final RemoteResource resource = initialRequest.getResource();
        final List<RemoteResourceRequest> ret = new ArrayList<>();
        KMLUtil.parseNetworkLinks(is, new FeatureHandler<NetworkLink>() {
            @Override
            public boolean process(NetworkLink link) {
                if (link == null || link.getLink() == null
                        || link.getLink().getHref() == null) {
                    Log.w(TAG,
                            "Remote KML Download Failed - KML has invalid Link");
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(R.string.importmgr_kml_has_invalid_link,
                                    curRequest.getFileName()));
                    return false;
                }

                Link l = link.getLink();
                String url = KMLUtil.getURL(l);
                if (FileSystemUtils.isEmpty(url)) {
                    Log.e(TAG, "Unsupported NetworkLink URL: : " + url);
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(
                                    R.string.importmgr_only_http_s_network_links_supported,
                                    curRequest.getFileName()));
                    return false;
                }

                String filename = curRequest.getFileName();
                if (FileSystemUtils.isEmpty(filename)) {
                    Log.e(TAG,
                            "Unable to determine local filename for Network Link: "
                                    + url);
                    postNotification(initialRequest,
                            R.drawable.ic_network_error_notification_icon,
                            getString(
                                    R.string.importmgr_remote_kml_download_failed),
                            getString(
                                    R.string.importmgr_unable_to_determine_filename));
                    return false;
                }

                // get label for this Network Link

                Log.d(TAG, "download: " + link);
                filename = filename + "-" + l.getHref()
                        .replaceAll(":", "")
                        .replaceAll("/", "-");
                Log.d(TAG, "to: " + filename);

                RemoteResource child = resource.findChildByURL(url);
                if (child == null)
                    resource.addChild(child = new RemoteResource());
                child.setUrl(url);
                child.setName(link.getName());
                child.setTypeFromURL(url);
                child.setRefreshSeconds(l.getRefreshInterval().longValue());
                child.setLocalPath(new File(curRequest.getDir(), filename));

                // filename = KMLUtil.getNetworkLinkName(
                //         filename, link) + (count++);
                ret.add(new RemoteResourceRequest(child, filename,
                        curRequest.getDir(),
                        initialRequest.getNotificationId(),
                        initialRequest.showNotifications()));
                return false;
            }
        });
        return ret;
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "Remote KML Download Failed - Request Connection Error: "
                + detail);
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                getString(R.string.importmgr_remote_kml_download_failed),
                getString(R.string.importmgr_check_your_url, detail));
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "Remote KML Download Failed - Request Data Error");
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                R.string.importmgr_remote_kml_download_failed,
                R.string.importmgr_request_data_error);
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "Remote KML Download Failed - Request Custom Error");
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                R.string.importmgr_remote_kml_download_failed,
                R.string.importmgr_request_custom_error);
    }
}
