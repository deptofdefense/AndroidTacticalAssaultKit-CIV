
package com.atakmap.android.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.atakmap.android.bluetooth.BluetoothDevicesConfig;
import com.atakmap.android.data.DataMgmtReceiver;
import com.atakmap.android.favorites.FavoriteListAdapter;
import com.atakmap.android.importfiles.ui.ImportManagerView;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.video.VideoBrowserDropDownReceiver;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.R;
import com.atakmap.app.preferences.GeocoderPreferenceFragment;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 *
 * TODO for now just shuffles directories during startup. Could be extended to do other tasks,
 * delegate control to components/plugins to upgrade, only run upgrade once when a version change
 * is detected. Would need to solve issues with timing of upgrade wrt to component initialization
 * e.g. migrate existing directories prior to init of default dirs/data by the components
 */
public class AppVersionUpgrade {

    private static final String TAG = "AppVersionUpgrade";

    public static boolean OVERLAYS_MIGRATED = false;

    /**
     * Provides a central place to run all directory shuffles for components
     */
    public synchronized static void onUpgrade(Context context) {
        Log.d(TAG, "Migrating directories");
        FileSystemUtils.init();

        long start = android.os.SystemClock.elapsedRealtime();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        final String version = prefs.getString("document_version", "");
        final boolean redeploy = !version.equals(BuildConfig.REDEPLOY_VERSION);
        Log.d(TAG, "force redeploying the documentation: " + redeploy);

        File userGuideFile = new File(FileSystemUtils.getRoot(),
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar + "docs"
                        + File.separatorChar + "ATAK_User_Guide.pdf");
        if (!userGuideFile.exists() || redeploy) {
            File pFile = userGuideFile.getParentFile();
            if (pFile == null || !pFile.mkdirs())
                Log.e(TAG, "could not make the user guide directory");

            try (FileOutputStream pdfStream = new FileOutputStream(
                    userGuideFile)) {
                FileSystemUtils.copyFromAssets(
                        context.getApplicationContext(),
                        "support/docs/ATAK_User_Guide.pdf",
                        pdfStream);
            } catch (IOException e) {
                Log.d(TAG, "could not copy ATAK_User_Guide.pdf to "
                        + userGuideFile.getAbsolutePath(), e);
            }
        }

        // for internation builds these files will be empty
        File docFile = FileSystemUtils.getItem(
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar + "docs"
                        + File.separatorChar + "ATAK_User_Guide.pdf");
        if (docFile.length() == 0)
            if (docFile.delete())
                Log.d(TAG, "could not delete the empty document file");

        docFile = FileSystemUtils.getItem(
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar + "docs"
                        + File.separatorChar + "ATAK_Change_Log.pdf");
        if (docFile.length() == 0)
            if (docFile.delete())
                Log.d(TAG, "unable to delete empty document file");

        try {
            File f = FileSystemUtils.getItem("support/support.inf");
            String s = FileSystemUtils.copyStreamToString(
                    new FileInputStream(f),
                    true,
                    FileSystemUtils.UTF8_CHARSET);
            if (s.contains("atakmap.com") || s.contains("takmaps.com")) {
                Log.d(TAG, "removing outdated support.inf file");
                if (f.delete())
                    Log.d(TAG, "unable to delete outdated support.inf file");
            }

        } catch (java.io.IOException ignored) {
        }

        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null)
            fp.deployDocumentation();

        if (!prefs.getBoolean("wms_deployed", false) || redeploy) {
            // make sure the WMS sources actually are redeployed
            prefs.edit().putBoolean("wms_deployed", false).apply();
            _removeWMSSource("mapquest-map.xml");
            _removeWMSSource("mapquest-sat.xml");
            _removeWMSSource("DigitalGlobe-G-EGD_MostAesthetic.xmle");
            _removeWMSSource("DigitalGlobe-G-EGD_MostCurrent.xmle");
            _removeWMSSource("DigitalGlobe-G-EGD_LeastClouds.xmle");

            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NOAA_RNC.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "USGSImageryOnly.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "USGSImageryTopo.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-DRG-Auto.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-DRG-Mosaic.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-FAA-Sectionals.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-FAA-TACs.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-FAA-WACs.xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-NAIP-(CONUS).xml");
            _copyWMSSource(context.getApplicationContext(), prefs,
                    "NRL-OpenStreetMap.xml");

            if (fp != null)
                fp.deployWMSPointers();

            final String active = prefs.getString("lastViewedLayer.active",
                    null);
            if (active == null) {
                // ATAK-14138 - Default to OpenStreetMap
                SharedPreferences.Editor e = prefs.edit();
                e.putString("lastViewedLayer.active", "Mobile");
                e.putString("MobileLayerSelectionAdapter.selected",
                        "NRL-OpenStreetMap");
                e.apply();
            }

            prefs.edit().putBoolean("wms_deployed", true).apply();
        }

        //if 3.5 and below users had a custom update server, then automatically use it as a "remote repo" in 3.6
        String updateUrl = prefs.getString("atakUpdateServerUrl",
                context.getString(R.string.atakUpdateServerUrlDefault));
        if (redeploy && !FileSystemUtils.isEquals(
                context.getString(R.string.atakUpdateServerUrlDefault),
                updateUrl)) {

            //3.6 disallows HTTP for Over the Air update, so fix up URLs
            if (!FileSystemUtils.isEmpty(updateUrl)) {
                try {
                    Uri uri = Uri.parse(updateUrl);
                    if (uri != null) {
                        String scheme = uri.getScheme();
                        if (FileSystemUtils.isEquals(scheme, "http")) {
                            Uri.Builder builder = uri.buildUpon();

                            Log.d(TAG,
                                    "Switching Update Server URL from HTTP to HTTPs: "
                                            + updateUrl);
                            builder.scheme("https");

                            int port = uri.getPort();
                            if (port == 80) {
                                String auth = uri.getAuthority();
                                if (auth != null) {
                                    auth = auth.replace(":80", ":443");
                                    Log.d(TAG,
                                            "Switching Update Server port from 80 to 443: "
                                                    + auth);
                                    builder.encodedAuthority(auth);
                                }
                            } else if (port == SslNetCotPort
                                    .getServerApiPort(
                                            SslNetCotPort.Type.UNSECURE)) {
                                String auth = uri.getAuthority();
                                if (auth != null) {
                                    auth = auth
                                            .replace(
                                                    (":" + SslNetCotPort
                                                            .getServerApiPort(
                                                                    SslNetCotPort.Type.UNSECURE)),
                                                    (":" + SslNetCotPort
                                                            .getServerApiPort(
                                                                    SslNetCotPort.Type.SECURE)));
                                    Log.d(TAG,
                                            "Switching Update Server port from "
                                                    +
                                                    SslNetCotPort
                                                            .getServerApiPort(
                                                                    SslNetCotPort.Type.UNSECURE)
                                                    +
                                                    " to "
                                                    +
                                                    SslNetCotPort
                                                            .getServerApiPort(
                                                                    SslNetCotPort.Type.SECURE)
                                                    + auth);
                                    builder.encodedAuthority(auth);
                                }
                            }

                            updateUrl = builder.build().toString();
                            prefs.edit()
                                    .putString("atakUpdateServerUrl", updateUrl)
                                    .apply();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to fixup updateUrl", e);
                }
            }

            Log.d(TAG, "auto enable remote repo URL: " + updateUrl);
            prefs.edit().putBoolean("appMgmtEnableUpdateServer", true).apply();
        }

        if (shuffleDirsLayers()) {
            Log.d(TAG, "shuffleDirsLayers success");
        }

        //TODO this came from WktMapComponents.shuffleDirs() which was previously runson a background thread
        if (shuffleDirsOverlays()) {
            Log.d(TAG, "shuffleDirsOverlays success");
            OVERLAYS_MIGRATED = true;
        }

        if (shuffleDir(
                FileSystemUtils.getItem("logs"),
                FileSystemUtils.getItem(FileSystemUtils.SUPPORT_DIRECTORY
                        + File.separatorChar + "logs"))) {
            Log.d(TAG, "shuffle Logs success");
        }

        // attempt to migrate REC_IMG
        if (shuffleDir(FileSystemUtils.getItem("REC_IMG"),
                FileSystemUtils.getItem("attachments"))) {
            Log.d(TAG, "shuffle attachments success");
        }

        //move from top level folder to tools folder
        if (shuffleDir(FileSystemUtils.getItem("bluetooth"),
                FileSystemUtils.getItem(BluetoothDevicesConfig.DIRNAME))) {
            Log.d(TAG, "shuffle bluetooth success");
        }

        File legacyDir = FileSystemUtils
                .getItem(FileSystemUtils.TOOL_DATA_DIRECTORY
                        + File.separatorChar + "missionpackage");

        File[] legacyFiles = IOProviderFactory.listFiles(legacyDir);
        if (legacyFiles != null) {
            for (File f : legacyFiles) {
                MissionPackageReceiver.addFileToSkip(f);
            }
        }

        if (shuffleDir(legacyDir,
                FileSystemUtils.getItem(FileSystemUtils.TOOL_DATA_DIRECTORY
                        + File.separatorChar + "datapackage"))) {
            Log.d(TAG, "shuffle datapackage success, removing missionpackage");
            FileSystemUtils
                    .deleteDirectory(
                            new File(FileSystemUtils.TOOL_DATA_DIRECTORY
                                    + File.separatorChar + "missionpackage"),
                            false);
        }

        if (shuffleDir(FileSystemUtils.getItem("Favorites"),
                FileSystemUtils.getItem(FavoriteListAdapter.DIRNAME))) {
            Log.d(TAG, "shuffle Favorites success");
        }
        if (shuffleDir(FileSystemUtils.getItem("JumpMaster"),
                FileSystemUtils.getItem(FileSystemUtils.TOOL_DATA_DIRECTORY
                        + File.separatorChar + "jumpmaster"))) {
            Log.d(TAG, "shuffle JumpMaster success");
        }
        if (shuffleDir(FileSystemUtils.getItem(context
                .getString(R.string.mission_package_folder)),
                FileSystemUtils.getItem(FileSystemUtils.TOOL_DATA_DIRECTORY
                        + File.separatorChar
                        + context
                                .getString(R.string.mission_package_folder)))) {
            Log.d(TAG, "shuffleLogs success");
        }

        FileSystemUtils.migrate(ImportManagerView.FILENAME,
                ImportManagerView.XML_FILEPATH);
        if (shuffleDir(FileSystemUtils.getItem("videos"),
                FileSystemUtils
                        .getItem(VideoBrowserDropDownReceiver.VIDEO_DIRNAME))) {
            Log.d(TAG, "shuffle videos success");
        }
        if (shuffleDir(FileSystemUtils.getItem("videosnaps"),
                FileSystemUtils
                        .getItem(
                                VideoBrowserDropDownReceiver.SNAPSHOT_DIRNAME))) {
            Log.d(TAG, "shuffle videosnaps success");
        }
        if (shuffleDir(FileSystemUtils.getItem("address"),
                FileSystemUtils
                        .getItem(GeocoderPreferenceFragment.ADDRESS_DIR))) {
            Log.d(TAG, "shuffle address success");
        }
        if (shuffleDir(FileSystemUtils.getItem("apks"),
                FileSystemUtils.getItem(AppMgmtUtils.APK_DIR))) {
            Log.d(TAG, "shuffle apks success");
        }

        File dir = FileSystemUtils.getItem(AppMgmtUtils.APK_DIR);
        if (IOProviderFactory.exists(dir)
                && IOProviderFactory.isDirectory(dir)) {
            File[] list = IOProviderFactory.listFiles(dir,
                    AppMgmtUtils.APK_FilenameFilter);
            if (list != null) {
                File destDir = FileSystemUtils
                        .getItem(
                                BundledProductProvider.LOCAL_BUNDLED_REPO_PATH);
                if (!IOProviderFactory.mkdirs(destDir)) {
                    Log.d(TAG, "could not make: " + destDir);
                }
                for (File aList : list) {
                    File dest = new File(destDir, aList.getName());
                    if (FileSystemUtils.renameTo(aList, dest)) {
                        Log.d(TAG,
                                "shuffle bundled apks success: "
                                        + dest.getAbsolutePath());
                    }
                }
            }

            // write .nomedia file so these icons don't show up in the gallery
            File nomedia = new File(dir, ".nomedia");
            IOProviderFactory.createNewFile(nomedia);

        }

        //In support of refactoring done in ATAK 3.8
        File geofences = FileSystemUtils.getItem(
                "Databases" + File.separatorChar + "geofence.sqlite");
        if (IOProviderFactory.exists(geofences)) {
            //TODO import existing geofences under new method
            FileSystemUtils.deleteFile(geofences);
        }

        if (shuffleDir(FileSystemUtils.getItem("prefs"),
                FileSystemUtils.getItem(PreferenceControl.DIRNAME))) {
            Log.d(TAG, "shuffle prefssuccess");
        }
        if (shuffleDir(FileSystemUtils.getItem("GDAL"),
                FileSystemUtils.getItem(MapView.GDAL_DIRNAME))) {
            Log.d(TAG, "shuffle GDAL success");
        }

        //delete legacy paths
        FileSystemUtils.deleteDirectory(FileSystemUtils.getItem("license"),
                false);
        FileSystemUtils.deleteDirectory(FileSystemUtils.getItem("docs"), false);
        FileSystemUtils.deleteDirectory(FileSystemUtils.getItem("TrackLogs"),
                false);
        FileSystemUtils.deleteFile(FileSystemUtils.getItem("README.txt"));
        FileSystemUtils.deleteFile(FileSystemUtils.getItem("support.inf"));
        FileSystemUtils.deleteFile(FileSystemUtils.getItem("cot"));
        FileSystemUtils.deleteFile(FileSystemUtils.getItem("tmp"));
        DataMgmtReceiver.deleteDirs(new String[] {
                "prefs", "TrackLogs", "kml", "shp", "gpx", "drw", "lpt",
                "Favorites", "isrv", "Intel",
                "JumpMaster", "logs", "Snapshots", "videos",
                "videosnaps", "address", "bluetooth"
        }, false);
        DataMgmtReceiver.deleteFiles(new String[] {
                "import_links.xml", "video_links.xml"
        });

        prefs.edit().putString("document_version", BuildConfig.REDEPLOY_VERSION)
                .apply();

        Log.d(TAG,
                "Migration complete in seconds: "
                        + (android.os.SystemClock.elapsedRealtime() - start)
                                / 1000D);

    }

    public static void migrate(String oldFile, String newFile) {
        FileSystemUtils.migrate(oldFile, newFile);
    }

    private static boolean shuffleDirsLayers() {
        Log.d(TAG, "shuffleDirsLayers");

        final String[] legacyNativeDirs = new String[] {
                "mrsid", "native", "pfps",
        };
        final String[] legacyMobileDirs = new String[] {
                "layers", "mobac",
        };

        final String[] mountPoints = FileSystemUtils
                .findMountPoints();

        boolean changed = false;

        File legacyDir;
        File imageryDir;
        File imageryMobileDir;
        File[] contents;
        boolean allMoved;
        boolean moved;
        for (String mountPoint : mountPoints) {
            imageryDir = new File(mountPoint, "imagery");
            if (!IOProviderFactory.exists(imageryDir)) {
                if (!IOProviderFactory.mkdirs(imageryDir))
                    Log.e(TAG, "Error creating directories");
            }
            for (String legacyNativeDir : legacyNativeDirs) {
                legacyDir = new File(mountPoint, legacyNativeDir);
                Log.i(TAG, "Shuffling " + legacyDir);
                if (!IOProviderFactory.exists(legacyDir)
                        || !IOProviderFactory.isDirectory(legacyDir))
                    continue;

                contents = IOProviderFactory.listFiles(legacyDir);
                allMoved = true;
                if (contents != null) {
                    for (File content : contents) {
                        moved = IOProviderFactory.renameTo(content,
                                new File(imageryDir,
                                        content.getName()));
                        changed |= moved;
                        allMoved &= moved;
                        Log.i(TAG, "Try move " + content + " -> "
                                + new File(imageryDir, content.getName())
                                + " " + moved);
                    }
                }

                if (allMoved) {
                    FileSystemUtils.delete(legacyDir);
                }
            }

            imageryMobileDir = new File(mountPoint, "imagery/mobile");
            if (!IOProviderFactory.exists(imageryMobileDir)) {

                if (!IOProviderFactory.mkdirs(imageryMobileDir)) {
                    Log.w(TAG, "Error creating directory: " + imageryMobileDir);
                }
            }
            for (String legacyMobileDir : legacyMobileDirs) {
                legacyDir = new File(mountPoint, legacyMobileDir);
                Log.i(TAG, "Shuffling " + legacyDir);
                if (!IOProviderFactory.exists(legacyDir)
                        || !IOProviderFactory.isDirectory(legacyDir))
                    continue;

                contents = IOProviderFactory.listFiles(legacyDir);
                allMoved = true;
                if (contents != null) {
                    for (File content : contents) {
                        if (content.getName()
                                .toLowerCase(LocaleUtil.getCurrent())
                                .endsWith(".kmz"))
                            moved = IOProviderFactory.renameTo(content,
                                    new File(imageryDir,
                                            content.getName()));
                        else
                            moved = IOProviderFactory.renameTo(content,
                                    new File(
                                            imageryMobileDir,
                                            content.getName()));
                        changed |= moved;
                        allMoved &= moved;
                        Log.i(TAG, "Try move " + content + " -> "
                                + new File(imageryDir, content.getName())
                                + " " + moved);
                    }
                }

                if (allMoved) {
                    FileSystemUtils.delete(legacyDir);
                }
            }
        }

        return changed;
    }

    // move the contents of the legacy type dirs into
    // 'atak/overlays'
    private static boolean shuffleDirsOverlays() {
        Log.d(TAG, "shuffleDirsOverlays");

        final String[] legacyDirs = new String[] {
                "drw", "gpx", "kml", "lpt", "shp"
        };

        final String[] mountPoints = FileSystemUtils
                .findMountPoints();

        boolean changed = false;

        File legacyDir;
        File overlaysDir;
        File[] contents;
        boolean allMoved;
        boolean moved;
        for (String mountPoint : mountPoints) {
            overlaysDir = new File(mountPoint, "overlays");
            for (String legacyDir1 : legacyDirs) {
                legacyDir = new File(mountPoint, legacyDir1);
                Log.i(TAG, "Shuffling " + legacyDir);
                if (!IOProviderFactory.exists(legacyDir)
                        || !IOProviderFactory.isDirectory(legacyDir))
                    continue;

                contents = IOProviderFactory.listFiles(legacyDir);
                allMoved = true;
                if (contents != null) {
                    for (File content : contents) {
                        moved = IOProviderFactory.renameTo(content,
                                new File(overlaysDir,
                                        content.getName()));
                        changed |= moved;
                        allMoved &= moved;
                        Log.i(TAG, "Try move " + content + " -> "
                                + new File(overlaysDir, content.getName())
                                + " " + moved);
                    }
                }

                if (allMoved)
                    FileSystemUtils.delete(legacyDir);
            }
        }

        return changed;
    }

    /**
     * Move logs/* into support/logs/*
     * @return true if the shuffle occurred correctly.
     */
    private static boolean shuffleDir(final File legacyDir, final File newDir) {
        boolean changed = false;
        boolean allMoved;
        boolean moved;

        Log.i(TAG, "Shuffling " + legacyDir + ", to " + newDir);
        if (!IOProviderFactory.exists(legacyDir)
                || !IOProviderFactory.isDirectory(legacyDir))
            return false;

        if (!IOProviderFactory.exists(newDir)) {
            boolean r = IOProviderFactory.mkdirs(newDir);
            if (!r)
                Log.d(TAG, "could not wrap: " + newDir);
        }

        File[] contents = IOProviderFactory.listFiles(legacyDir);
        allMoved = true;
        if (contents != null) {
            for (File content : contents) {
                moved = IOProviderFactory.renameTo(content, new File(newDir,
                        content.getName()));
                changed |= moved;
                allMoved &= moved;
                Log.i(TAG, "Try move " + content + " -> "
                        + new File(newDir, content.getName())
                        + " " + moved);
            }
        }

        if (allMoved)
            FileSystemUtils.delete(legacyDir);

        return changed;
    }

    /**
     * If the given file has not already been copied to the device, copy the file.
     * TODO - if a part of a source changes, we'll have to add versions the xml
     *        files to ensure the lastest file has been copied to the device
     */
    private static void _copyWMSSource(Context context,
            SharedPreferences prefs, String file) {

        //if the file has not already been written to device attempt to copy it
        File dst = FileSystemUtils.getItem("imagery/mobile/mapsources/" + file);
        if (!IOProviderFactory.exists(dst)
                || (!prefs.getBoolean("wms_deployed", false))) {
            Log.d(TAG, "redeploy: " + file);
            if (FileSystemUtils.copyFromAssetsToStorageFile(
                    context, "wms/" + file,
                    "imagery/mobile/mapsources/" + file, true)) {
            }
        }
    }

    private static void _removeWMSSource(final String file) {
        File dst = FileSystemUtils.getItem("imagery/mobile/mapsources/" + file);
        FileSystemUtils.delete(dst);
    }
}
