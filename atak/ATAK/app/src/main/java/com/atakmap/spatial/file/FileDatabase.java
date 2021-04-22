
package com.atakmap.spatial.file;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.content.CatalogCurrency;
import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.content.CatalogDatabase;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.database.CursorIface;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.spatial.wkt.WktGeometry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @deprecated Transitioned to Map Engine Features API
 * Replaced by {@link FalconViewSpatialDb}
 */
@Deprecated
@DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
public abstract class FileDatabase extends CatalogDatabase implements
        CatalogCurrency, Importer {

    private static final String TAG = "FileDatabase";

    public static final int FILE_ICON_ID = R.drawable.ic_geojson_file_notification_icon;
    protected static final File DATABASE_FILE = FileSystemUtils
            .getItem("Databases/files.sqlite3");

    final protected Context context;
    final protected MapView view;
    final protected SharedPreferences prefs;
    final protected FileContentResolver contentResolver;

    protected MapGroup rootGroup = null;
    protected MapOverlay overlay;

    public FileDatabase(final File file, final Context context,
            final MapView view) {
        // XXX -
        super(IOProviderFactory.createDatabase(file),
                new CatalogCurrencyRegistry());

        this.currencyRegistry.register(this);

        this.context = context;
        this.view = view;

        prefs = context.getSharedPreferences("file_databases",
                Context.MODE_PRIVATE);

        // Content resolver
        String ext = getContentType().toLowerCase(LocaleUtil.getCurrent());
        this.contentResolver = new FileContentResolver(Collections
                .singleton(ext));
        URIContentManager.getInstance().registerResolver(
                this.contentResolver);

        // XXX - validate then add new files
        addExistingFilesToDb();

        // Remove any files that ARE in the DB but ARE NOT on the FS
        validateCatalog();

        // Create MapGroup        
        createRootGroup();
    }

    @Override
    public void close() {
        URIContentManager.getInstance().unregisterResolver(
                this.contentResolver);
        this.contentResolver.dispose();

        if (overlay != null) {
            view.getMapOverlayManager().removeFilesOverlay(overlay);
            overlay = null;
        }
        if (rootGroup != null) {
            rootGroup.clearGroups();
            rootGroup.clearItems();
            rootGroup = null;
        }
        super.close();
    }

    public boolean processFile(File f) {
        if (!this.accept(f))
            return false;
        if (this.checkCatalogEntryExists(f))
            return true;
        try {
            ImportResult result = this.importData(Uri.fromFile(f),
                    this.getFileMimeType(), null);
            switch (result) {
                case SUCCESS:
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public abstract boolean accept(File file);

    public abstract File getFileDirectory();

    protected abstract String getFileMimeType();

    protected abstract String getIconPath();

    protected abstract void processFile(File file, MapGroup fileGrp);

    public final MapGroup getRootGroup() {
        return rootGroup;
    }

    public final int getIconId() {
        return FILE_ICON_ID;
    }

    protected final void addToDbFromFile(File file) {
        if (!checkCatalogEntryExists(file)) {
            addCatalogEntry(file, this);
        }
    }

    protected final int getIdFromFile(String path) {
        CursorIface result = null;
        long catalogId;
        try {
            result = this.database.query("SELECT " + COLUMN_CATALOG_ID
                    + " FROM "
                    + TABLE_CATALOG + " WHERE " + COLUMN_CATALOG_PATH + " = ?",
                    new String[] {
                            path
                    });
            if (!result.moveToNext())
                return -1;
            catalogId = result.getLong(0);
        } finally {
            if (result != null)
                result.close();
        }

        return (int) catalogId;
    }

    protected final boolean checkCatalogEntryExists(File derivedFrom) {
        CatalogCursor result = null;
        try {
            result = this.queryCatalog(derivedFrom);
            return result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public final void validateCatalog() {
        this.validateCatalog(getName());
    }

    @Override
    public final String getName() {
        return getContentType();
    }

    @Override
    public final int getAppVersion() {
        return 0;
    }

    @Override
    public final byte[] getAppData(File derivedFrom) {
        ByteBuffer retval = ByteBuffer.wrap(new byte[17]);
        retval.order(ByteOrder.BIG_ENDIAN);
        retval.put(IOProviderFactory.isFile(derivedFrom) ? (byte) 0x01
                : (byte) 0x00);
        retval.putLong(FileSystemUtils.getFileSize(derivedFrom));
        retval.putLong(FileSystemUtils.getLastModified(derivedFrom));
        return retval.array();
    }

    @Override
    public final boolean isValidApp(File f, int appVersion, byte[] appData) {
        if (appVersion != this.getAppVersion())
            return false;

        ByteBuffer data = ByteBuffer.wrap(appData);
        data.order(ByteOrder.BIG_ENDIAN);
        if ((data.get() == 0x01) != IOProviderFactory.isFile(f))
            return false;
        if (data.getLong() != FileSystemUtils.getFileSize(f))
            return false;
        if (data.getLong() != FileSystemUtils.getLastModified(f))
            return false;
        return true;
    }

    protected final Set<File> addExistingFilesToDb() {
        File fileDir = getFileDirectory();

        // add the files that are ALREADY in the FS to the DB
        Set<File> filesAdded = new HashSet<>(
                addExistingFilesToDbInDir(fileDir));

        // now add files that are in any external storage...
        for (File externalStorageDir : getATAKMountPoints()) {
            if (externalStorageDir != null
                    && !externalStorageDir.equals(FileSystemUtils.getRoot())) {
                Log.d(TAG,
                        "Got Mount Point: "
                                + externalStorageDir.getAbsolutePath());
                File geospatialDirInExternalStorage = replaceRootDir(fileDir,
                        externalStorageDir);
                Log.d(TAG,
                        "Checking for geospatial files in: "
                                + geospatialDirInExternalStorage
                                        .getAbsolutePath());
                if (geospatialDirInExternalStorage != null &&
                        IOProviderFactory
                                .isDirectory(geospatialDirInExternalStorage)) {
                    List<File> geospatialFileList = addExistingFilesToDbInDir(
                            geospatialDirInExternalStorage);
                    Log.d(TAG, "Found files: " + geospatialFileList + " in: "
                            + geospatialDirInExternalStorage.getAbsolutePath());
                    filesAdded.addAll(geospatialFileList);
                }
            }
        }

        return filesAdded;
    }

    protected static File replaceRootDir(File fileDir,
            File externalStorageDir) {
        String androidSdCard = FileSystemUtils.getRoot().getAbsolutePath();
        return new File(fileDir.getAbsolutePath().replaceFirst(androidSdCard,
                externalStorageDir.getAbsolutePath()));
    }

    protected static Set<File> getATAKMountPoints() {
        Set<File> ret = new HashSet<>();
        String[] rootDirs = FileSystemUtils.findMountPoints();
        if (rootDirs != null) {
            for (String rootDir : rootDirs) {
                ret.add(new File(rootDir));
            }
        }
        return ret;
    }

    protected final List<File> addExistingFilesToDbInDir(File fileDir) {
        List<File> filesAdded = new LinkedList<>();
        try {
            if (fileDir != null
                    && IOProviderFactory.listFiles(fileDir) != null) {
                for (File fileToRead : IOProviderFactory.listFiles(fileDir)) {
                    if (fileToRead != null
                            && IOProviderFactory.isFile(fileToRead)
                            && IOProviderFactory.canRead(fileToRead)
                            && accept(fileToRead)) {
                        try {
                            addToDbFromFile(fileToRead);
                            filesAdded.add(fileToRead);
                        } catch (java.lang.Exception e) {
                            Log.w(TAG,
                                    "Problem adding spatial file "
                                            + fileToRead.getAbsolutePath()
                                            + " to Spatial DB",
                                    e);
                        }
                    }
                }
            } else {
                if (fileDir != null) {
                    Log.w(TAG,
                            "Folder "
                                    + fileDir
                                    + " does not exist... not parsing any files for that file type");
                } else {
                    Log.w(TAG,
                            "Could not retreive directory to list geospatial files");
                }
            }
        } catch (java.lang.Exception e) {
            Log.d(TAG, "Couldn't access file list for geospatial files");
        }
        return filesAdded;
    }

    /**** Turn this section into a MapItemsDatabase? ****/

    protected final void createRootGroup() { // possible constructor
        rootGroup = new DefaultMapGroup(getContentType());
        rootGroup.setMetaString("iconUri", getIconPath());
        // rootGroup.setMetaString("overlay", "wkt");
        rootGroup.setMetaBoolean("ignoreOffscreen", true);
        rootGroup.setVisible(false);
        overlay = new FileDatabaseMapGroupOverlay(view, rootGroup, this);
        view.getMapOverlayManager().addFilesOverlay(overlay);

        // add all cataloged items
        CatalogDatabase.CatalogCursor result = null;
        try {
            result = this.queryCatalog();
            while (result.moveToNext()) {
                if (result.getAppName().equals(this.getName())) {
                    createGroup(rootGroup, result);
                }
            }
        } finally {
            if (result != null)
                result.close();
        }
    }

    private void createGroup(MapGroup rootGroup,
            CatalogDatabase.CatalogCursor result) {
        createGroup(rootGroup, result.getPath());
    }

    private void createGroup(MapGroup rootGroup, String filepath) {
        final File file = new File(
                FileSystemUtils.sanitizeWithSpacesAndSlashes(filepath));
        MapGroup fileGrp = MapGroup.findMapGroup(rootGroup, file.getName());
        if (fileGrp == null) {
            fileGrp = new DefaultMapGroup(file.getName());
            fileGrp.setMetaString("FILEPATH", file.getAbsolutePath());

        }

        fileGrp.setMetaString("iconUri", getIconPath());

        /**
         * Need to revisit in the next release, should keep a better handle on what is visible and not inside 
         * the file.  That would mean computing a checksum, importing the file.  Then in future iterations 
         * use the database unless the checksum changes.  then reimport.
         */
        Log.d(TAG,
                "Processing " + getContentType() + " file: " + file.getName());
        boolean enabled = prefs.getBoolean("overlay." + file.getName(), true);
        fileGrp.setVisible(enabled);
        prefs.edit()
                .putBoolean("overlay." + file.getName(), fileGrp.getVisible())
                .apply();
        if (enabled)
            rootGroup.setVisible(true);

        processFile(file, fileGrp);

        rootGroup.addGroup(fileGrp);

        fileGrp.addOnVisibleChangedListener(
                new MapGroup.OnVisibleChangedListener() {
                    @Override
                    public void onGroupVisibleChanged(final MapGroup group) {
                        Log.d(TAG,
                                "toggling visibility: "
                                        + group.getFriendlyName()
                                        + " is " + group.getVisible());
                        prefs.edit()
                                .putBoolean(
                                        "overlay." + group.getFriendlyName(),
                                        group.getVisible())
                                .apply();
                    }
                });

    }

    protected final void createMapItem(
            FeatureDataSource.FeatureDefinition feature,
            MapGroup fileGrp) {
        if (feature != null) {
            List<? extends WktGeometry> geometries = WktGeometry
                    .parse((String) feature.rawGeom);
            if (geometries != null) {
                List<MapItem> mapItems = new LinkedList<>();
                FeatureStyle style = new FeatureStyle();
                for (WktGeometry geom : geometries) {
                    if (geom != null) {
                        geom.setName(feature.name);
                        // Stored as KML styles in the DB
                        if (feature.rawStyle != null) {
                            style.clear();
                            FeatureStyleParser.parse((String) feature.rawStyle,
                                    style);
                            if (style.hasStyle())
                                geom.setStyle(style);
                        }
                        mapItems.clear();
                        geom.toMapItems(mapItems);
                        for (MapItem mapitem : mapItems) {
                            this.addMapItem(mapitem, fileGrp);
                        }
                    }
                }
            }
        }
    }

    protected final void addMapItem(MapItem mapitem, MapGroup fileGrp) {
        fileGrp.addItem(mapitem);
        //String itemTitle = MapItem.getUniqueMapItemName(mapitem);
        mapitem.setVisible(fileGrp.getVisible());
    }

    /**************************************************************************/
    // Importer

    @Override
    public final Set<String> getSupportedMIMETypes() {
        return Collections.singleton(this.getFileMimeType());
    }

    @Override
    public final ImportResult importData(InputStream source, String mime,
            Bundle bundle) {
        return ImportResult.FAILURE;
    }

    @Override
    public final ImportResult importData(Uri uri, String mime, Bundle b) {
        if (uri.getScheme() != null && !uri.getScheme().equals("file"))
            return ImportResult.FAILURE;

        boolean showMapNotifications = b != null && b.getBoolean(
                ImportReceiver.EXTRA_SHOW_NOTIFICATIONS);
        boolean zoomToFile = b != null && b.getBoolean(
                ImportReceiver.EXTRA_ZOOM_TO_FILE);

        Log.d(TAG, "importData: " + mime + ", " + uri);
        File file;
        try {
            file = new File(FileSystemUtils.validityScan(uri.getPath()));
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return ImportResult.FAILURE;
        }

        final int notificationId = NotificationUtil.getInstance()
                .reserveNotifyId();
        if (showMapNotifications) {
            NotificationUtil.getInstance().postNotification(
                    notificationId,
                    NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                    NotificationUtil.BLUE,
                    "Starting Import: " + file.getName(), null, null, false);
        }

        //remove existing data if it exists
        boolean bDelete = false;
        CatalogDatabase.CatalogCursor result = null;
        try {
            result = this.queryCatalog(file);
            if (result.moveToNext()) {
                bDelete = true;
            }
        } finally {
            if (result != null)
                result.close();
        }

        if (bDelete) {
            Log.d(TAG, "Removing existing file: " + file.getAbsolutePath());
            deleteFile(file, true);
        }

        //now add data
        addToDbFromFile(file);
        createGroup(rootGroup, uri.getPath());

        Intent i = new Intent(ImportExportMapComponent.ZOOM_TO_FILE_ACTION);
        i.putExtra("filepath", file.getAbsolutePath());
        if (showMapNotifications) {
            NotificationUtil.getInstance().postNotification(
                    notificationId,
                    NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                    NotificationUtil.GREEN,
                    "Finished Import: " + file.getName(), null, i, true);

        } else {
            NotificationUtil.getInstance().clearNotification(notificationId);

            NotificationUtil.getInstance().postNotification(
                    SpatialDbContentSource.getNotificationId(),
                    getIconId(), NotificationUtil.WHITE,
                    "Imported " + getContentType() + " file ",
                    " Imported: " + file.getName(),
                    "Imported " + getContentType() + " file: "
                            + file.getName(),
                    i, true);
        }

        if (zoomToFile)
            AtakBroadcast.getInstance().sendBroadcast(i);

        return ImportResult.SUCCESS;
    }

    @Override
    public final boolean deleteData(Uri uri, String mime) {

        File file;
        try {
            file = new File(FileSystemUtils.validityScan(uri.getPath()));
        } catch (IOException ioe) {
            Log.d(TAG, "invalid file", ioe);
            return false;
        }
        deleteFile(file, false);
        return true;
    }

    protected void deleteFile(File file, boolean replacing) {
        //remove from UI
        MapGroup fileGroup = findMapGroup(file.getAbsolutePath());
        if (fileGroup != null) {
            fileGroup.clearItems();
            fileGroup.clearGroups();
            if (this.rootGroup != null)
                this.rootGroup.removeGroup(fileGroup);
        }

        //remove from DB
        this.deleteCatalog(file);

        // Remove handler
        if (!replacing)
            this.contentResolver.removeHandler(file);
    }

    /**
     * Find the map group for the specified file
     * 
     * @param filepath
     * @return
     */
    private MapGroup findMapGroup(String filepath) {
        if (this.rootGroup == null || FileSystemUtils.isEmpty(filepath))
            return null;

        for (MapGroup fileGroup : this.rootGroup.getChildGroups()) {
            String curFilepath = fileGroup.getMetaString("FILEPATH", "");
            if (FileSystemUtils.isEquals(filepath, curFilepath))
                return fileGroup;
        }

        return null;
    }
}
