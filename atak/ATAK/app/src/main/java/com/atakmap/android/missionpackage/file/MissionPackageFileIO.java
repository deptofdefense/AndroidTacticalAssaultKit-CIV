
package com.atakmap.android.missionpackage.file;

import android.content.Context;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.filesharing.android.service.DirectoryWatcher;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.os.FileObserver;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.CopyAndSendTask;
import com.atakmap.android.missionpackage.file.task.CopyTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask.Callback;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.android.missionpackage.ui.MissionPackageListItem;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

/**
 * File IO support for Mission Package Tool
 * 
 * 
 */
public class MissionPackageFileIO {

    public static final String TAG = "MissionPackageFileIO";

    private static String MissionPackageFolder = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "datapackage";

    /**
     * Path of fileshare directory to store imports and received files
     */
    private final String _primaryMissionPackagePath;

    /**
     * File share directory watchers to import .zips manually copied into missionpackage folder
     */
    private final List<DirectoryWatcher> _directoryWatchers;

    /**
     * Cleans up temp files
     */
    private DirectoryCleanup _directoryCleanup;

    private final Context _context;
    private final MissionPackageReceiver _receiver;

    public MissionPackageFileIO(MissionPackageReceiver receiver,
            Context context) {
        _context = context;
        _receiver = receiver;

        _primaryMissionPackagePath = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY + File.separatorChar
                        + _context.getString(R.string.mission_package_folder))
                .getAbsolutePath();
        _directoryWatchers = new ArrayList<>();
        MissionPackageFolder = FileSystemUtils.TOOL_DATA_DIRECTORY
                + File.separatorChar + context
                        .getString(R.string.mission_package_folder);
    }

    public String getMissionPackagePath() {
        return _primaryMissionPackagePath;
    }

    public String getMissionPackageFilesPath() {
        return _primaryMissionPackagePath + File.separatorChar + "files";
    }

    public static String getMissionPackagePath(String atakMapDataPath) {
        return atakMapDataPath + File.separatorChar + MissionPackageFolder;
    }

    public static String getMissionPackageFilesPath(String atakMapDataPath) {
        return atakMapDataPath + File.separatorChar + MissionPackageFolder
                + File.separatorChar
                + "files";
    }

    public String getMissionPackageTransferPath() {
        return _primaryMissionPackagePath + File.separatorChar + "transfer";
    }

    public static String getMissionPackageTransferPath(String atakMapDataPath) {
        return atakMapDataPath + File.separatorChar + MissionPackageFolder
                + File.separatorChar
                + "transfer";
    }

    public String getMissionPackageIncomingDownloadPath() {
        return _primaryMissionPackagePath + File.separatorChar + "incoming";
    }

    public static String getMissionPackageIncomingDownloadPath(
            String atakMapDataPath) {
        return atakMapDataPath + File.separatorChar + MissionPackageFolder
                + File.separatorChar
                + "incoming";
    }

    /**
     * Begin watching for file changes
     */
    public void enableFileWatching() {

        disableFileWatching();

        // periodically clean up temp zip and download directories
        _directoryCleanup = new DirectoryCleanup();

        // initialize db
        FileInfoPersistanceHelper db = FileInfoPersistanceHelper
                .initialize(_context);

        Set<String> mounts = getFileShareMountPoints();
        for (String mount : mounts) {
            watchDirectory(mount);
        }

        // now purge out any stale db entries, e.g. files deleted since listening last stopped.
        db.purge();
    }

    private void watchDirectory(String missionPackagePath) {

        Log.d(TAG, "Package Directory: " + missionPackagePath);

        // be sure all sub-dirs exists and setup directory watchers
        try {
            // watch missionPackageDir, auto-import any .zips found there (e.g. received or manually
            // placed), no HTTP serving, no auto-cleanup
            // check if already in db, if not add it. DB entry to point to .zip file on disk
            File dir = new File(missionPackagePath);
            if (!IOProviderFactory.exists(dir)) {
                Log.d(TAG,
                        "Creating Package directory: " + dir.getAbsolutePath());
                if (!IOProviderFactory.mkdirs(dir))
                    Log.e(TAG,
                            "Failed to create Package directory: "
                                    + dir.getAbsolutePath());
            }

            boolean found = false;
            for (DirectoryWatcher dw : _directoryWatchers) {
                if (dw.getPath().equalsIgnoreCase(dir.getAbsolutePath())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                Log.d(TAG,
                        "Adding new Package Directory Watcher: "
                                + dir.getAbsolutePath());
                DirectoryWatcher missionPackageSavedWatcher = new MissionPackageDirectoryWatcher(
                        TABLETYPE.SAVED, dir.getAbsolutePath(), _receiver);
                missionPackageSavedWatcher.startWatching();
                _directoryWatchers.add(missionPackageSavedWatcher);
            }

            // Note missionPackageDir/files, no directory watching, no auto cleanup
            dir = new File(missionPackagePath + File.separatorChar + "files");
            if (!IOProviderFactory.exists(dir)) {
                Log.d(TAG,
                        "Creating Package Files directory: "
                                + dir.getAbsolutePath());
                if (!IOProviderFactory.mkdirs(dir))
                    Log.e(TAG, "Failed to create Package Files directory: "
                            + dir.getAbsolutePath());
            }

            // missionPackageDir/incoming, no watch, auto clean up (even though .zips deleted after
            // unzipped and verified)
            dir = new File(
                    missionPackagePath + File.separatorChar + "incoming");
            if (!IOProviderFactory.exists(dir)) {
                Log.d(TAG,
                        "Creating Package Incoming directory: "
                                + dir.getAbsolutePath());
                if (!IOProviderFactory.mkdirs(dir))
                    Log.e(TAG,
                            "Failed to create Package Incoming directory: "
                                    + dir.getAbsolutePath());
            }
            _directoryCleanup.add(dir.getAbsolutePath());

            // missionPackageDir/transfer, no watch, and served up via HTTP, auto clean up
            dir = new File(
                    missionPackagePath + File.separatorChar + "transfer");
            if (!IOProviderFactory.exists(dir)) {
                Log.d(TAG,
                        "Creating Package Transfer directory: "
                                + dir.getAbsolutePath());
                if (!IOProviderFactory.mkdirs(dir))
                    Log.e(TAG,
                            "Failed to create Package Transfer directory: "
                                    + dir.getAbsolutePath());
            }
            _directoryCleanup.add(dir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error creating directories", e);
        }
    }

    private boolean hasFileshareWatchers() {
        return _directoryWatchers != null && _directoryWatchers.size() > 0;
    }

    /**
     * Stop watching for file changes
     */
    public void disableFileWatching() {

        // stop watching directory
        if (hasFileshareWatchers()) {
            for (FileObserver dw : _directoryWatchers) {
                dw.stopWatching();
            }

            _directoryWatchers.clear();
        }

        // periodically clean up temp zip and download directories
        if (_directoryCleanup != null) {
            _directoryCleanup.shutdown();
            _directoryCleanup = null;
        }
    }

    public static Set<String> getFileShareMountPoints() {
        Set<String> ret = new HashSet<>();
        String[] rootDirs = FileSystemUtils.findMountPoints();
        if (rootDirs != null) {
            for (String rootDir : rootDirs) {
                ret.add(rootDir + File.separatorChar + MissionPackageFolder);
            }
        }
        return ret;
    }

    /**
     * Watch for .zips and set username based on local callsign
     * 
     * 
     */
    static class MissionPackageDirectoryWatcher extends DirectoryWatcher {

        private final MissionPackageReceiver _receiver;

        public MissionPackageDirectoryWatcher(TABLETYPE dt, String path,
                MissionPackageReceiver receiver) {
            super(dt, path, new FileFilter() {

                @Override
                public boolean accept(File pathname) {
                    String file = pathname.getName()
                            .toLowerCase(LocaleUtil.getCurrent());
                    return file.endsWith(".zip") || file.endsWith(".dpk");
                }
            }, new MIMETypeMapper(), receiver, false, true);
            // need to set receiver after super() but b/f files are processed...
            _receiver = receiver;
            processDirectory();
        }

        @Override
        public String getUserName() {
            if (_receiver != null && _receiver.getMapView() != null)
                return _receiver.getMapView().getDeviceCallsign();
            else {
                Log.w(TAG, "Map View not valid");
                return super.getUserName();
            }
        }
    }

    /**
     * Save the Mission Package via an asynchronous task. Provide callback when complete
     * 
     * @param contents Mission Package manifest
     * @param persist True to persist the package to the local file database
     * @param callback Task callback
     */
    public void save(MissionPackageManifest contents, boolean persist,
            Callback callback) {

        // delete old .zip if this has been renamed...
        if (FileSystemUtils.isFile(contents.getLastSavedPath())
                && !contents.getLastSavedPath().equals(contents.getPath())) {
            Log.d(TAG,
                    "Deleting previous Package: "
                            + contents.getLastSavedPath());
            try {
                FileSystemUtils.deleteFile(new File(FileSystemUtils
                        .validityScan(contents.getLastSavedPath())));
            } catch (IOException ioe) {
                Log.w(TAG, "could not delete: " + contents.getLastSavedPath());
            }
        }

        // now save it out via async task and progress dialog
        CompressionTask task = new CompressionTask(contents, _receiver, true,
                null, callback, false);
        task.setPersist(persist);
        task.execute();
        contents.setLastSavedPath(contents.getPath());
    }

    /**
     * Saves and persists the Mission Package via an asynchronous task.
     * Provide callback when complete
     *
     * @param contents
     * @param callback
     */
    public void save(MissionPackageManifest contents, Callback callback) {
        save(contents, true, callback);
    }

    /**
     * Copy/deploy the Mission Package to the built in Web Server and then launch Contact List to
     * send the package to another ATAK user. Callback once ContactList has been launched
     *
     * @param contents
     * @param netContacts
     * @param callback
     */
    public void send(MissionPackageManifest contents,
            Contact[] netContacts,
            Callback callback) {

        new CopyAndSendTask(contents, netContacts, _receiver, callback)
                .execute();
    }

    /**
     * Copy/deploy the Mission Package to the built in Web Server and then launch Contact List to
     * send the package to another ATAK user. Callback once ContactList has been launched
     *
     * @param contents
     * @param toUIDs
     * @param callback
     */
    public void send(MissionPackageManifest contents,
            String[] toUIDs,
            Callback callback) {

        send(contents,
                Contacts.getInstance().getIndividualContactsByUuid(
                        Arrays.asList(toUIDs)),
                callback);
    }

    /**
     * Copy/deploy the Mission Package to the built in Web Server and
     * then invoke callback
     *
     * @param contents
     * @param callback
     */
    public void send(MissionPackageManifest contents, Callback callback) {

        new CopyTask(contents, _receiver, callback).execute();
    }

    /**
     * Save Mission Package and then Copy/deploy the Mission Package to the built in Web Server and
     * then launch Contact List to send the package to another ATAK user
     *
     * @param contents
     * @param callback invoked once compression and again once Contact List has been launched
     * @param bSenderDeleteUponError
     * @param toUIDs
     */
    public void saveAndSendUIDs(MissionPackageManifest contents,
            Callback callback,
            boolean bSenderDeleteUponError, String[] toUIDs) {
        saveAndSend(
                contents,
                callback,
                bSenderDeleteUponError,
                toUIDs == null || toUIDs.length < 1 ? null
                        : Contacts.getInstance().getIndividualContactsByUuid(
                                Arrays.asList(toUIDs)));
    }

    /**
     * Save Mission Package and then Copy/deploy the Mission Package to the built in Web Server and
     * then launch Contact List to send the package to another ATAK user
     * 
     * @param contents
     * @param callback invoked once compression and again once Contact List has been launched
     * @param bSenderDeleteUponError
     * @param netContacts
     */
    public void saveAndSend(MissionPackageManifest contents, Callback callback,
            boolean bSenderDeleteUponError, Contact[] netContacts) {
        // delete old .zip if this has been renamed...
        if (FileSystemUtils.isFile(contents.getLastSavedPath())
                && !contents.getLastSavedPath().equals(contents.getPath())) {
            Log.d(TAG,
                    "Deleting previous Package: "
                            + contents.getLastSavedPath());
            FileSystemUtils.deleteFile(new File(contents.getLastSavedPath()));
        }

        // now save it out via async task and progress dialog
        new CompressionTask(
                contents,
                _receiver,
                true,
                new CopyAndSendTask(contents, netContacts, _receiver, callback),
                callback, bSenderDeleteUponError).execute();
        contents.setLastSavedPath(contents.getPath());
    }

    /**
     * Save Mission Package and then Copy/deploy the Mission Package to the built in Web Server and
     * then invoke callback
     *
     * @param contents
     * @param callback
     */
    public void saveAndSend(MissionPackageManifest contents,
            Callback callback) {
        // delete old .zip if this has been renamed...
        if (FileSystemUtils.isFile(contents.getLastSavedPath())
                && !contents.getLastSavedPath().equals(contents.getPath())) {
            Log.d(TAG,
                    "Deleting previous Package: "
                            + contents.getLastSavedPath());
            FileSystemUtils.deleteFile(new File(contents.getLastSavedPath()));
        }

        // now save it out via async task and progress dialog
        new CompressionTask(
                contents,
                _receiver,
                true,
                new CopyTask(contents, _receiver, callback),
                callback, true).execute();
        contents.setLastSavedPath(contents.getPath());
    }

    public void clear() {
        final String[] mountPoints = FileSystemUtils.findMountPoints();
        for (String mountPoint : mountPoints) {
            //delete all contents
            File dir = new File(mountPoint, MissionPackageFolder);
            if (IOProviderFactory.exists(dir)
                    && IOProviderFactory.isDirectory(dir)) {
                //first move packages out to avoid FileObserver issues with SecureDelete
                File[] packages = IOProviderFactory.listFiles(dir);
                if (packages != null && packages.length > 0) {
                    for (File packageFile : packages) {
                        deletePackageFile(packageFile);
                    }
                }

                //now delete all other files
                FileSystemUtils.deleteDirectory(dir, true);
            }

            //now build back out child dirs
            dir = new File(mountPoint, MissionPackageFolder
                    + File.separatorChar + "files");
            if (!IOProviderFactory.exists(dir))
                if (!IOProviderFactory.mkdirs(dir)) {
                    Log.d(TAG,
                            " Faild to make dir at " + dir.getAbsolutePath());
                }

            dir = new File(mountPoint, MissionPackageFolder
                    + File.separatorChar + "incoming");
            if (!IOProviderFactory.exists(dir))
                if (!IOProviderFactory.mkdirs(dir)) {
                    Log.d(TAG,
                            " Failed to make dir at " + dir.getAbsolutePath());
                }

            dir = new File(mountPoint, MissionPackageFolder
                    + File.separatorChar + "transfer");
            if (!IOProviderFactory.exists(dir))
                if (!IOProviderFactory.mkdirs(dir)) {
                    Log.d(TAG,
                            " Failed to make dir at " + dir.getAbsolutePath());
                }
        }
    }

    /**
     * Proper way to delete mission package file
     * Move to temporary storage and delete there so the file observer
     * doesn't attempt to extract it
     * @param packageZip Package zip file
     */
    public static void deletePackageFile(File packageZip) {
        if (IOProviderFactory.exists(packageZip)
                && IOProviderFactory.isFile(packageZip)) {
            File f = FileSystemUtils.moveToTemp(MapView.getMapView()
                    .getContext(), packageZip);
            FileSystemUtils.deleteFile(f);
        }
    }

    /**
     * Remove mission package and its contents
     * @param path Path to mission package
     * @param rootGroup Root group to start searching for packages
     */
    public static void deletePackage(String path, MapGroup rootGroup) {
        if (rootGroup == null) {
            MapView mv = MapView.getMapView();
            if (mv != null)
                rootGroup = mv.getRootGroup();
        }
        List<MissionPackageListGroup> packages = MissionPackageUtils
                .getUiPackages(rootGroup);
        MissionPackageListGroup group = null;
        for (MissionPackageListGroup p : packages) {
            if (p.getManifest() != null && p.getManifest()
                    .getPath().equals(path)) {
                group = p;
                break;
            }
        }
        if (group != null) {
            // Remove content and zip file
            for (MissionPackageListItem item : group.getItems()) {
                item.removeContent();
            }
            MissionPackageFileIO.deletePackageFile(new File(path));
        } else {
            Log.w(TAG, "Failed to find package with path: " + path);
        }
    }
}
