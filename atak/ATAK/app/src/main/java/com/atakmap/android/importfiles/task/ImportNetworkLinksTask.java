
package com.atakmap.android.importfiles.task;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.sort.ImportGRGSort;
import com.atakmap.android.importfiles.sort.ImportKMLSort;
import com.atakmap.android.importfiles.sort.ImportKMZSort;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportResolver.SortFlags;
import com.atakmap.android.importfiles.ui.ImportManagerDropdown;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.NetworkLink;

import org.simpleframework.xml.Serializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final boolean _showNotifications;
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
            RemoteResource resource, int notificationId,
            boolean showNotifications) {
        _context = context;
        _notificationId = notificationId;
        _showNotifications = showNotifications;
        _serializer = serializer;
        _resource = resource;
    }

    @Override
    protected Result doInBackground(File... params) {
        Thread.currentThread().setName("ImportNetworkLinksTask");

        if (FileSystemUtils.isEmpty(params)) {
            Log.w(TAG, "No files to import...");
            return new Result(_context.getString(R.string.no_file_specified));
        }

        // just take first file for now
        File dir = params[0];

        // get sorters (just KML/Z)
        // Also include GRG sorter because GRGs use the KMZ extension
        List<ImportResolver> sorters = new ArrayList<>();
        sorters.add(new ImportGRGSort(_context, true, false, false));
        sorters.add(new ImportKMZSort(_context, true, false, false, false));
        sorters.add(new ImportKMLSort(_context, true, false, false));

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
        if (FileSystemUtils.isEmpty(downloads)) {
            Log.w(TAG,
                    "Remote KML Download Failed - No KML files downloaded: "
                            + dir.getAbsolutePath());
            return new Result(
                    _context.getString(
                            R.string.importmgr_remote_kml_download_failed_no_files_downloaded));
        }

        // loop all downloaded file
        int sortedCount = 0;
        Set<SortFlags> flags = new HashSet<>();
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
                                sorter
                                        + ", Unable to determine destination path for: "
                                        + file.getAbsolutePath());
                        continue;
                    }

                    RemoteResource res;
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
                        res = _resource;
                        Log.d(TAG, "Found match on updated resource: "
                                + _resource);
                    } else {
                        // otherwise this is a child resource from a NetworkLink
                        res = _resource.findChildByPath(file.getAbsolutePath());
                    }

                    if (res == null) {
                        Log.e(TAG, "Unknown resource with name: " + name);
                        continue;
                    }

                    String ext = sorter.getExt();
                    if (FileSystemUtils.isEmpty(ext))
                        ext = FileSystemUtils.getExtension(destPath, true,
                                false);
                    if (ext.startsWith("."))
                        ext = ext.substring(1)
                                .toUpperCase(LocaleUtil.getCurrent());

                    res.setType(ext);
                    res.setLocalPath(destPath.getAbsolutePath());
                    res.setMd5(newMD5);
                    res.setLastRefreshed(IOProviderFactory.lastModified(file));

                    if (IOProviderFactory.exists(destPath)) {
                        String existingMD5 = HashingUtils.md5sum(destPath);
                        if (existingMD5 != null && existingMD5.equals(newMD5)) {
                            Log.d(TAG,
                                    sorter
                                            + ", File has not been updated, discarding: "
                                            + file.getAbsolutePath()
                                            + " based on MD5: " + newMD5);
                            // now delete file rather than move
                            if (!IOProviderFactory.delete(file,
                                    IOProvider.SECURE_DELETE))
                                Log.w(TAG,
                                        sorter
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
                    if (sorter.beginImport(file, flags)) {
                        sortedCount++;
                        Log.d(TAG,
                                sorter + ", Sorted: "
                                        + file.getAbsolutePath()
                                        + " to " + destPath.getAbsolutePath());

                        // setup refresh interval for NetworkLinks, if specified by user
                        if (_resource.getRefreshSeconds() > 0) {
                            beginNetworkLinkRefresh(destPath, _context);
                        }

                        // XXX - Currently no reason to import using multiple
                        // sorters here - only produces buggy behavior
                        break;
                    } else
                        Log.w(TAG,
                                sorter
                                        + ", Matched, but did not sort: "
                                        + file.getAbsolutePath());
                } // end if sorter match was found
            } // end sorter loop
        } // end download file loop

        // delete UID folder if all went well. If files left over, they will cleaned up by
        // DirectoryCleanup task/timer
        downloads = IOProviderFactory.listFiles(dir);
        if (FileSystemUtils.isEmpty(downloads)) {
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
            if (_showNotifications)
                NotificationUtil.getInstance().postNotification(
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
            if (_showNotifications)
                NotificationUtil.getInstance().postNotification(
                        _notificationId,
                        R.drawable.ic_kml_file_notification_icon,
                        NotificationUtil.GREEN,
                        _context.getString(
                                R.string.importmgr_remote_kml_download_complete),
                        _context.getString(
                                R.string.importmgr_download_complete_importing_files,
                                result.fileCount),
                        _context.getString(
                                R.string.importmgr_download_complete_importing_files,
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
                                + _resource);
                Intent intent = new Intent();
                intent.setAction(
                        ImportExportMapComponent.KML_NETWORK_LINK_REFRESH);
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

            if (_showNotifications)
                NotificationUtil.getInstance().postNotification(
                        _notificationId,
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.importmgr_remote_kml_download_cancelled),
                        error, error);
        }
    }

    private static void beginNetworkLinkRefresh(File file, Context context) {

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

        try (InputStream is = IOProviderFactory.getInputStream(fileToParse)) {
            beginNetworkLinkRefresh(is, file.getName());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing KML file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * @param is XML Input stream to parse
     * @param filename (_not_ file path)
     */
    private static void beginNetworkLinkRefresh(InputStream is,
            final String filename) {
        Log.d(TAG, "Processing KML Network Links for: " + filename);
        KMLUtil.parseNetworkLinks(is, new FeatureHandler<NetworkLink>() {
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

                ImportExportMapComponent.getInstance().refreshNetworkLink(
                        url, childFilename, intervalSeconds, false);
                return false;
            }
        });
    }
}
