
package com.atakmap.spatial.file;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ImageButton;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.items.MapGroupHierarchyListItem;
import com.atakmap.android.hierarchy.items.NonExportableMapItemHierarchyListItem;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.app.R;
import com.atakmap.content.CatalogDatabase;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Objects;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

public class FileDatabaseMapGroupHierarchyListItem extends
        MapGroupHierarchyListItem implements View.OnClickListener,
        View.OnLongClickListener {

    private static final String TAG = "FileDatabaseMapGroupHierarchyListItem";

    private final FileDatabase database;
    private final boolean exportable;

    public FileDatabaseMapGroupHierarchyListItem(
            FileDatabaseMapGroupHierarchyListItem parent, MapView mapView,
            MapGroup group, MapGroup.MapItemsCallback itemFilter,
            HierarchyListFilter filter, BaseAdapter listener,
            FileDatabase database,
            boolean exportable) {
        super(parent, mapView, group, itemFilter, filter, listener);
        this.database = database;
        this.exportable = exportable;
    }

    @Override
    protected HierarchyListItem createChild(MapGroup group) {
        //map groups (e.g. files) are exportable if the parent is
        return new FileDatabaseMapGroupHierarchyListItem(this, this.mapView,
                group, this.itemFilter, this.filter,
                this.listener, database, this.exportable);
    }

    /**
     * Child items/features are not individually exportable
     */
    @Override
    protected HierarchyListItem createChild(MapItem item) {
        return new NonExportableMapItemHierarchyListItem(this.mapView, item);
    }

    /**************************************************************************/
    // View On Item Long Click Listener

    @Override
    public View getExtraView() {
        final String path = getFilePathToSend();
        if (FileSystemUtils.isEmpty(path))
            return null;

        LayoutInflater inflater = LayoutInflater.from(mapView.getContext());
        View view = inflater.inflate(R.layout.feature_set_extra, null);

        ImageButton panBtn = view.findViewById(R.id.panButton);
        ImageButton sendBtn = view.findViewById(R.id.sendButton);

        panBtn.setOnClickListener(this);
        sendBtn.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        final String path = getFilePathToSend();
        if (FileSystemUtils.isEmpty(path))
            return;

        File file = new File(path);

        int id = v.getId();

        if (id == R.id.panButton) {
            URIContentHandler handler = URIContentManager.getInstance()
                    .getHandler(file);
            if (handler != null && handler.isActionSupported(GoTo.class))
                ((GoTo) handler).goTo(false);
        } else if (id == R.id.sendButton) {
            new SendDialog.Builder(mapView)
                    .addFile(file)
                    .show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        String filepath = getFilePathToSend();
        if (isSendable() && filepath != null) {
            return sendFile(filepath);
        }
        return false;
    }

    private boolean sendFile(String filepath) {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return false;
        new SendDialog.Builder(mapView)
                .addFile(new File(FileSystemUtils
                        .sanitizeWithSpacesAndSlashes(filepath)))
                .show();
        return true;
    }

    // XXX - legacy send implementation -- logic needs to be moved into
    // applicable overlays as they know how to send themselves
    private boolean isSendable() {
        if (this.group == null || this.group.getParentGroup() == null)
            return false;
        return (Objects.equals(this.group.getParentGroup().getFriendlyName(),
                "DRW")
                || Objects.equals(this.group.getParentGroup().getFriendlyName(),
                        "LPT"));
    }

    public String getFilePathToSend() {
        if (isSendable()) {
            // Find file path from MapGroup's meta-data fields
            String filenameFromMapGroup = this.group.getMetaString("FILEPATH",
                    null);
            // @see FileSpatialDb
            Log.d("MapGroupHierarchyListItem",
                    "Filename from MapGroup meta-data: "
                            + filenameFromMapGroup);

            // Only do the following HACK if we couldn't get the file path
            // from the map group's meta-data
            if (filenameFromMapGroup == null) {
                String filename = new File(FileSystemUtils
                        .sanitizeWithSpacesAndSlashes(getTitle())).getName();
                final File f = FILE_TYPE_TO_DIRECTORY_MAP.get(
                        getFileExtention(filename));
                if (f != null) {
                    return f.getAbsolutePath()
                            + File.separatorChar + filename;
                }
            }

            // Otherwise, if we got the filepath from the MapGroup's
            // meta-data...
            return FileSystemUtils
                    .sanitizeWithSpacesAndSlashes(filenameFromMapGroup);
        }
        return null;
    }

    // (KYLE) This is an EXACT copy of the File to Dir map from
    // SharedFileListener,
    // but I can't get access to it because of this package structure.
    // We should really think about condensing these so we don't need so many
    // copies!
    private static final String ATAK_FILE_PATH = FileSystemUtils.getRoot()
            .getPath();
    private static final Map<String, File> FILE_TYPE_TO_DIRECTORY_MAP = new HashMap<>();

    static {
        FILE_TYPE_TO_DIRECTORY_MAP
                .put("kml", new File(ATAK_FILE_PATH
                        + FileSystemUtils.OVERLAYS_DIRECTORY
                        + File.separatorChar));
        FILE_TYPE_TO_DIRECTORY_MAP
                .put("kmz", new File(ATAK_FILE_PATH
                        + FileSystemUtils.OVERLAYS_DIRECTORY
                        + File.separatorChar));
        FILE_TYPE_TO_DIRECTORY_MAP
                .put("drw", new File(ATAK_FILE_PATH
                        + FileSystemUtils.OVERLAYS_DIRECTORY
                        + File.separatorChar));
        FILE_TYPE_TO_DIRECTORY_MAP
                .put("lpt", new File(ATAK_FILE_PATH
                        + FileSystemUtils.OVERLAYS_DIRECTORY
                        + File.separatorChar));
    }

    private static String getFileExtention(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1).trim()
                .toLowerCase(LocaleUtil.getCurrent());
    }

    /**************************************************************************/
    // Delete

    @Override
    public boolean delete() {
        String filepath = getFilePathToSend();
        if (filepath != null) {
            //single file to delete
            File file = new File(filepath);
            Log.d(TAG,
                    "Delete: " + this.group.getFriendlyName() + ", "
                            + file.getAbsolutePath());
            Intent deleteIntent = new Intent();
            deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
            deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    database.getContentType());
            deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    database.getFileMimeType());
            deleteIntent.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(file)
                    .toString());
            AtakBroadcast.getInstance().sendBroadcast(deleteIntent);
            return true;
        } else if (this.group.getFriendlyName().equals(
                database.getContentType())) {
            ArrayList<String> children = getChildFiles();
            if (children == null || children.size() < 1) {
                Log.d(TAG, "No " + this.group.getFriendlyName()
                        + " children to delete");
                return true;
            }

            Log.d(TAG, "Deleting " + this.group.getFriendlyName()
                    + " children count: " + children.size());
            Intent deleteIntent = new Intent();
            deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
            deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    database.getContentType());
            deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    database.getFileMimeType());
            deleteIntent.putExtra(ImportReceiver.EXTRA_URI_LIST, children);
            AtakBroadcast.getInstance().sendBroadcast(deleteIntent);
            return true;
        } else {
            Log.w(TAG, "Failed to find group file to delete");
            return super.delete();
        }
    }

    /**
     * Get list of child files (match appName for this contentSource)
     * @return the list of files
     */
    private ArrayList<String> getChildFiles() {
        ArrayList<String> paths = new ArrayList<>();

        CatalogDatabase.CatalogCursor result = null;
        try {
            result = database.queryCatalog(database.getContentType());
            while (result.moveToNext()) {
                if (result != null
                        && !FileSystemUtils.isEmpty(result.getPath()))
                    paths.add(result.getPath());
                else
                    Log.w(TAG, "Failed to to catalog path");
            }
        } finally {
            if (result != null)
                result.close();
        }

        return paths;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        if (!this.exportable)
            return false;

        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters) {

        if (group == null || !isSupported(target)) {
            //nothing to export
            return null;
        }

        if (MissionPackageExportWrapper.class.equals(target)) {
            return toMissionPackage();
        }

        return null;
    }

    private MissionPackageExportWrapper toMissionPackage() {
        if (!this.exportable)
            return null;

        String filepath = getFilePathToSend();
        if (filepath != null) {
            //single file to delete
            File file = new File(filepath);
            Log.d(TAG,
                    "Export: " + this.group.getFriendlyName() + ", "
                            + file.getAbsolutePath());
            return new MissionPackageExportWrapper(false,
                    file.getAbsolutePath());
        } else if (this.group.getFriendlyName().equals(
                database.getContentType())) {
            ArrayList<String> children = getChildFiles();
            if (children == null || children.size() < 1) {
                Log.d(TAG, "No " + this.group.getFriendlyName()
                        + " children to export");
                return null;
            }

            Log.d(TAG, "Exporting " + this.group.getFriendlyName()
                    + " children count: " + children.size());
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();
            f.getFilepaths().addAll(children);
            return f;
        } else {
            Log.w(TAG, "Failed to find group file to export");
            return null;
        }
    }
}
