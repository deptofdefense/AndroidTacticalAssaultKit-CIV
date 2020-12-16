
package com.atakmap.android.importfiles.task;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.resource.ChildResource;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.sort.ImportKMLSort;
import com.atakmap.android.importfiles.sort.ImportKMZSort;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.ui.ImportManagerDropdown;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.NetworkLink;

import org.simpleframework.xml.Serializer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Background task to import one or more KML files that were downloaded via NetworkLinks. Sends
 * intents to start interval based refreshing of the NetworkLinks
 * 
 * 
 */
public class ImportNetworkLinksTask extends
        AsyncTask<File, File, ImportNetworkLinksTask.Result> {

    private static final String TAG = "ImportNetworkLinksTask";

    private final Context _context;
    private final int _notificationId;
    private final Serializer _serializer;
    private final RemoteResource _resource;

    /**
     * Result of import attempt
     * 
     * 
     */
    static class Result {
        final File dir;
        int fileCount;
        final boolean success;
        final String error;

        Result(File f, int fc) {
            dir = f;
            fileCount = fc;
            success = true;
            error = null;
        }

        Result(String e) {
            dir = null;
            success = false;
            error = e;
        }
    }

    public ImportNetworkLinksTask(Serializer serializer, Context context,
            RemoteResource resource,
            int notificationId) {
        _context = context;
        _notificationId = notificationId;
        _serializer = serializer;
        _resource = resource;
    }

    @Override
    protected Result doInBackground(File... params) {
        Thread.currentThread().setName("ImportNetworkLinksTask");

        if (params == null || params.length < 1) {
            Log.w(TAG, "No files to import...");
            return new Result(_context.getString(R.string.no_file_specified));
        }

        // just take first file for now
        File dir = params[0];

        // get sorters (just KML/Z)
        List<ImportResolver> sorters = new ArrayList<>();
        sorters.add(new ImportKMLSort(_context, false, false, false));
        sorters.add(new ImportKMZSort(_context, false, false, false, false));

        return sort(dir, sorters);
    }

    private ImportNetworkLinksTask.Result sort(File dir,
            List<ImportResolver> sorters) {
        // move all files (the current 'childRequests' and any ancestors that
        // were already downloaded, move them into ATAK kml folder

        if (!IOProviderFactory.exists(dir)
                || !IOProviderFactory.isDirectory(dir)) {
            Log.w(TAG,
                    "KML download folder is not a directory: "
                            + dir.getAbsolutePath());
            return new Result(
                    String.format(
                            _context.getString(
                                    R.string.importmgr_kml_download_folder_not_a_directory),
                            dir.getName()));
        }

        File[] downloads = IOProviderFactory.listFiles(dir);
        if (downloads == null || downloads.length < 1) {
            Log.w(TAG,
                    "Remote KML Download Failed - No KML files downloaded: "
                            + dir.getAbsolutePath());
            return new Result(
                    _context.getString(
                            R.string.importmgr_remote_kml_download_failed_no_files_downloaded));
        }

        // loop all downloaded file
        int sortedCount = 0;
        for (File file : downloads) {
            // use KML or KMZ to validate and import to proper location
            Log.d(TAG, "Importing Network Link: " + file.getAbsolutePath());
            for (ImportResolver sorter : sorters) {
                // see if this sorter can handle the current file
                if (sorter.match(file)) {
                    // check if we will be overwriting an existing file
                    File destPath = sorter.getDestinationPath(file);
                    if (destPath == null) {
                        Log.w(TAG,
                                sorter.toString()
                                        + ", Unable to determine destination path for: "
                                        + file.getAbsolutePath());
                        continue;
                    }

                    String newMD5 = HashingUtils.md5sum(file);

                    // look for name match pull updated info for the resource
                    // cover case of resource name matches file name (with extension) and
                    // resource name does not include extension
                    String name = FileSystemUtils
                            .stripExtension(file.getName());
                    if (name.equalsIgnoreCase(_resource.getName())
                            || file.getName().equalsIgnoreCase(
                                    _resource.getName())) {
                        // Could be KML or KMZ but we dont update type, just leave as KML as "KML"
                        // has special handling by the UI code
                        _resource.setLocalPath(destPath.getAbsolutePath());
                        _resource.setMd5(newMD5);
                        _resource.setLastRefreshed(
                                IOProviderFactory.lastModified(file));
                        Log.d(TAG, "Found match on updated resource: "
                                + _resource.toString());
                    } else {
                        // otherwise this is a child resource from a NetworkLink
                        ChildResource child = new ChildResource();
                        child.setName(name);
                        child.setLocalPath(destPath.getAbsolutePath());
                        // child.setMd5(newMD5);
                        _resource.addChild(child);
                        Log.d(TAG,
                                "Adding child resource: " + child.toString());
                    }

                    if (IOProviderFactory.exists(destPath)) {
                        String existingMD5 = HashingUtils.md5sum(destPath);
                        if (existingMD5 != null && existingMD5.equals(newMD5)) {
                            Log.d(TAG,
                                    sorter.toString()
                                            + ", File has not been updated, discarding: "
                                            + file.getAbsolutePath()
                                            + " based on MD5: " + newMD5);
                            // now delete file rather than move
                            if (!IOProviderFactory.delete(file,
                                    IOProvider.SECURE_DELETE))
                                Log.w(TAG,
                                        sorter.toString()
                                                + ", Failed to delete un-updated file: "
                                                + file.getAbsolutePath());

                            continue;
                        } else {
                            Log.d(TAG,
                                    "Overwriting existing file with updates: "
                                            + destPath.getAbsolutePath());
                        }
                    }

                    // now attempt to sort (i.e. move the file to proper location)
                    if (sorter.beginImport(file)) {
                        sortedCount++;
                        Log.d(TAG,
                                sorter.toString() + ", Sorted: "
                                        + file.getAbsolutePath()
                                        + " to " + destPath.getAbsolutePath());

                        // setup refresh interval for NetworkLinks, if specified by user
                        if (_resource.getRefreshSeconds() > 0) {
                            ImportNetworkLinksTask.beginNetworkLinkRefresh(
                                    destPath, _serializer,
                                    _context);
                        }
                    } else
                        Log.w(TAG,
                                sorter.toString()
                                        + ", Matched, but did not sort: "
                                        + file.getAbsolutePath());
                } // end if sorter match was found
            } // end sorter loop
        } // end download file loop

        // delete UID folder if all went well. If files left over, they will cleaned up by
        // DirectoryCleanup task/timer
        downloads = IOProviderFactory.listFiles(dir);
        if (downloads == null || downloads.length < 1) {
            FileSystemUtils.delete(dir);
        } else
            Log.w(TAG,
                    "Skipping delete of non empty dir" + dir.getAbsolutePath());

        return new Result(dir, sortedCount);
    }

    @Override
    protected void onPostExecute(ImportNetworkLinksTask.Result result) {

        if (result == null) {
            Log.e(TAG, "Failed to import Remote KML");

            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.importmgr_remote_kml_import_failed),
                            _context.getString(R.string.failed_to_import),
                            _context.getString(R.string.failed_to_import));
            return;
        }

        if (result.success) {
            // Toast.makeText(_context, "Finished downloading Remote KML. Importing...",
            // Toast.LENGTH_LONG).show();
            Log.d(TAG, "Finished importing: " + result.dir);

            // notify user
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notificationId,
                            R.drawable.ic_kml_file_notification_icon,
                            NotificationUtil.GREEN,
                            _context.getString(
                                    R.string.importmgr_remote_kml_download_complete),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_download_complete_importing_files),
                                    result.fileCount),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_download_complete_importing_files),
                                    result.fileCount));

            // send an intent so adapter can update details about the local cache of the remote
            // resource
            Intent updateIntent = new Intent();
            updateIntent.setAction(ImportManagerDropdown.UPDATE_RESOURCE);
            updateIntent.putExtra("resource", _resource);
            AtakBroadcast.getInstance().sendBroadcast(updateIntent);

            // schedule refresh timer for top level resource
            if (_resource.getRefreshSeconds() > 0) {
                Log.d(TAG,
                        "Scheduling refresh task for top level resource: "
                                + _resource.toString());
                Intent intent = new Intent();
                intent.setAction(
                        "com.atakmap.android.importfiles.KML_NETWORK_LINK");
                intent.putExtra("kml_networklink_url", _resource.getUrl());
                intent.putExtra("kml_networklink_filename",
                        _resource.getName());
                intent.putExtra("kml_networklink_intervalseconds",
                        _resource.getRefreshSeconds());
                intent.putExtra("kml_networklink_stop", false);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }

        } else {
            String error = result.error;
            if (error == null || error.length() < 1)
                error = _context
                        .getString(
                                R.string.importmgr_failed_to_import_remote_kml_error_unknown);

            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.importmgr_remote_kml_download_cancelled),
                            error, error);
        }
    }

    private static void beginNetworkLinkRefresh(File file,
            Serializer serializer, Context context) {

        // Extract KML from KMZ if necessary
        File fileToParse = file;
        try {
            File tempKML = KMLUtil.getKmlFileFromKmzFile(file,
                    context.getCacheDir());
            if (tempKML != null) {
                Log.d(TAG,
                        "Extracting KMZ downloaded file: "
                                + file.getAbsolutePath());
                fileToParse = tempKML;
            } else {
                Log.d(TAG, "Network Link is not KMZ, processing KML...");
            }
        } catch (IOException e1) {
            Log.d(TAG, "Network Link is not KMZ, processing KML...", e1);
        }

        // TODO for performance do not need to parse entire document? just need list of NetworkLinks

        FileInputStream fis = null;
        try {
            // Open file in non-strict mode to ignore folks' non-standard or deprecated KML
            Kml kml = serializer.read(Kml.class, new BufferedInputStream(
                    fis = IOProviderFactory.getInputStream(
                            fileToParse)),
                    false);

            if (kml == null) {
                Log.e(TAG,
                        "Unable to parse KML file: " + file.getAbsolutePath());
                return;
            }

            beginNetworkLinkRefresh(kml, file.getName(), context);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing KML file: " + file.getAbsolutePath(), e);

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                    Log.e(TAG, "error closing stream");
                }
            }
        }

    }

    /**
     * @param kml
     * @param filename (_not_ file path)
     */
    private static void beginNetworkLinkRefresh(Kml kml, final String filename,
            final Context context) {

        Log.d(TAG, "Processing KML Network Links for: " + filename);

        KMLUtil.deepFeatures(kml, new FeatureHandler<NetworkLink>() {

            @Override
            public boolean process(NetworkLink link) {
                if (link == null || link.getLink() == null
                        || link.getLink().getHref() == null) {
                    Log.w(TAG, "Network Link has no URL specified");
                    return false;
                }

                String url = KMLUtil.getURL(link.getLink());
                if (FileSystemUtils.isEmpty(url)) {
                    Log.e(TAG, "Unsupported NetworkLink URL: : " + url);
                    return false;
                }

                if (link.getLink().getRefreshMode() == null
                        || !link.getLink().getRefreshMode()
                                .equals("onInterval")) {
                    Log.d(TAG, "Network Link refreshMode not supported: "
                            + link.getLink().getRefreshMode());
                    return false;
                }

                String childFilename = KMLUtil.getNetworkLinkName(
                        FileSystemUtils.stripExtension(filename), link);
                long intervalSeconds = KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS;
                if (link.getLink().getRefreshInterval() != null
                        && link.getLink().getRefreshInterval()
                                .longValue() >= KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS) {
                    // TODO truncating from Float to long here... yet KML XSD calls for double...
                    intervalSeconds = link.getLink().getRefreshInterval()
                            .longValue();
                } else
                    Log.w(TAG,
                            "Using default Network Link refreshInterval instead of: "
                                    + link.getLink().getRefreshInterval());

                Intent intent = new Intent();
                intent.setAction(
                        ImportExportMapComponent.KML_NETWORK_LINK_REFRESH);
                intent.putExtra("kml_networklink_url", url);
                intent.putExtra("kml_networklink_filename", childFilename);
                intent.putExtra("kml_networklink_intervalseconds",
                        intervalSeconds);
                intent.putExtra("kml_networklink_stop", false);
                if (context != null) {
                    Log.d(TAG, "Scheduling NetworkLink refresh: " + url);
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                } else
                    Log.w(TAG,
                            "Context not ready, unable to schedule NetworkLink refresh: "
                                    + url);
                return false;
            }
        }, NetworkLink.class);
    }
}
