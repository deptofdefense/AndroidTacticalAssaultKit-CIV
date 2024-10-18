
package com.atakmap.android.features;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorWindow;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.features.FeatureDataStorePathUtils.PathEntry;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Send;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicPointStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.math.MathUtils;
import com.atakmap.spatial.file.MvtSpatialDb;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;

public class FeatureSetHierarchyListItem extends AbstractHierarchyListItem2
        implements View.OnClickListener, View.OnLongClickListener,
        Visibility, Search, Delete, Export, FeatureEdit {

    private static final String TAG = "FeatureSetHierarchyListItem";

    public final static int MAX_DISTANCE_SORT = 2000;
    private final static int MAX_WINDOW_SIZE = 500;
    private final static int WINDOW_READ_SIZE = 100;

    private final static int NUM_WINDOW_COLUMNS = 9;

    private final static int WINDOW_COL_FID = 0;
    private final static int WINDOW_COL_IS_POINT = 1;
    private final static int WINDOW_COL_MIN_X = 2;
    private final static int WINDOW_COL_MIN_Y = 3;
    private final static int WINDOW_COL_MAX_X = 4;
    private final static int WINDOW_COL_MAX_Y = 5;
    private final static int WINDOW_COL_NAME = 6;
    private final static int WINDOW_COL_ICON = 7;
    private final static int WINDOW_COL_COLOR = 8;

    private final Context context;
    private final FeatureDataStore2 spatialDb;

    private final String title;
    private final String uid;
    private Sort order;
    private int featureChildCount;
    private final String contentType;
    private final String mimeType;
    private final String iconUri;

    private CursorWindow window;

    private final PathEntry entry;
    private int descendantCount;
    private final String path;

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore spatialDb, FeatureSet group,
            Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {

        this(context, Adapters.adapt(spatialDb), group,
                getTitle(group.getName()), order,
                listener,
                contentType, mimeType, iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb, FeatureSet group,
            Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {

        this(context, spatialDb, group, getTitle(group.getName()), order,
                listener,
                contentType, mimeType, iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore spatialDb, FeatureSet group,
            String title, Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {
        this(context, Adapters.adapt(spatialDb), group, title, order, listener,
                contentType, mimeType, iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb, FeatureSet group,
            String title, Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {

        this(context,
                spatialDb,
                new PathEntry(group.getId(), group.getName()),
                title,
                title,
                order,
                listener,
                contentType,
                mimeType,
                iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore spatialDb, FeatureSet group,
            String title, HierarchyListFilter filter, BaseAdapter listener,
            String contentType,
            String mimeType, String iconUri) {
        this(context, Adapters.adapt(spatialDb), group, title, filter, listener,
                contentType, mimeType, iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb, FeatureSet group,
            String title, HierarchyListFilter filter, BaseAdapter listener,
            String contentType,
            String mimeType, String iconUri) {

        this(context,
                spatialDb,
                new PathEntry(group.getId(), group.getName()),
                title,
                title,
                (filter != null) ? filter.sort : new SortAlphabet(),
                listener,
                contentType,
                mimeType,
                iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb,
            PathEntry path,
            HierarchyListFilter filter, BaseAdapter listener,
            String contentType,
            String mimeType, String iconUri) {

        this(context,
                spatialDb,
                path,
                (filter != null) ? filter.sort : new SortAlphabet(),
                listener,
                contentType,
                mimeType,
                iconUri);
    }

    public FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb,
            PathEntry path,
            Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {

        this(context,
                spatialDb,
                path,
                path.folder,
                path.folder,
                order,
                listener,
                contentType,
                mimeType,
                iconUri);
    }

    private FeatureSetHierarchyListItem(Context context,
            FeatureDataStore2 spatialDb,
            PathEntry entry,
            String title,
            String path,
            Sort order, BaseAdapter listener, String contentType,
            String mimeType, String iconUri) {

        this.context = context;
        this.spatialDb = spatialDb;
        this.entry = entry;
        this.title = getTitle(title);
        this.uid = getUID(entry);
        this.path = path;
        this.order = order;
        this.listener = listener;
        this.contentType = contentType;
        this.mimeType = mimeType;
        this.iconUri = iconUri;

        this.featureChildCount = -1;
        this.window = new CursorWindow(null);
        this.window.setNumColumns(NUM_WINDOW_COLUMNS);
        this.window.setStartPosition(0);
        this.asyncRefresh = false;
    }

    private void prepareQueryParams(
            FeatureDataStore.FeatureSetQueryParameters params) {
        if (this.entry.fsid != FeatureDataStore.FEATURESET_ID_NONE
                && this.entry.childFsids.isEmpty()) {
            params.ids = Collections.singleton(this.entry.fsid);
        } else {
            params.names = new ArrayList<>(2);
            params.names.add(this.path);
            params.names.add(this.path + "/%");
            params.ids = this.entry.childFsids;
            if (this.entry.fsid != FeatureDataStore.FEATURESET_ID_NONE) {
                params.ids = new HashSet<>(params.ids);
                params.ids.add(this.entry.fsid);
            }
        }
    }

    private void prepareQueryParams(FeatureQueryParameters params) {
        if (this.entry.fsid != FeatureDataStore.FEATURESET_ID_NONE
                && this.entry.childFsids.isEmpty()) {
            params.featureSetIds = Collections.singleton(this.entry.fsid);
        } else {
            params.featureSets = new ArrayList<>(2);
            params.featureSets.add(this.path);
            params.featureSets.add(this.path + "/%");
            params.featureSetIds = this.entry.childFsids;
            if (this.entry.fsid != FeatureDataStore.FEATURESET_ID_NONE) {
                params.featureSetIds = new HashSet<>(params.featureSetIds);
                params.featureSetIds.add(this.entry.fsid);
            }
        }
    }

    /**
     * Check if the style is editable for this database
     * @return True if editable
     */
    private boolean isStyleEditable() {
        return MathUtils.hasBits(spatialDb.getModificationFlags(),
                FeatureDataStore.MODIFY_FEATURE_STYLE);
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getUID() {
        return this.uid;
    }

    @Override
    public String getIconUri() {
        return this.iconUri;
    }

    @Override
    public int getChildCount() {
        if (this.featureChildCount == -1) {
            FeatureQueryParameters params = new FeatureQueryParameters();
            params.featureSetIds = Collections.singleton(this.entry.fsid);

            try {
                this.featureChildCount = this.spatialDb
                        .queryFeaturesCount(Adapters.adapt(params, null));
            } catch (DataStoreException e) {
                return entry.children.size();
            }
        }
        return this.entry.children.size() + this.featureChildCount;
    }

    @Override
    public int getDescendantCount() {
        if (this.descendantCount == -1) {
            FeatureQueryParameters params = new FeatureQueryParameters();
            params.featureSets = Collections.singleton(this.path + "/%");
            params.featureSetIds = this.entry.childFsids;

            try {
                this.descendantCount = this.spatialDb
                        .queryFeaturesCount(Adapters.adapt(params, null));
            } catch (DataStoreException dse) {
                return this.featureChildCount;
            }
            // initialize 'featureChildCount'
            this.getChildCount();
        }
        return this.featureChildCount + this.descendantCount;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        final int featureSetChildCount = this.entry.children.size();
        if (index < featureSetChildCount) {
            // XXX - ew
            int i = 0;
            for (PathEntry child : this.entry.children.values()) {
                if (i == index)
                    return createChildSet(child);
                i++;
            }

            throw new IllegalStateException();
        }

        // subtract off any nested groups
        index -= featureSetChildCount;

        int limit = this.window.getStartPosition()
                + this.window.getNumRows();
        // check if the index is included within the current window
        if (index < this.window.getStartPosition() || index >= limit) {

            if (index < this.window.getStartPosition() - WINDOW_READ_SIZE
                    || index > limit + WINDOW_READ_SIZE) {
                this.window.clear();
                this.window.setNumColumns(NUM_WINDOW_COLUMNS);
                this.window.setStartPosition(index);

                // the index is outside of the read size, reset the limit to
                // force a read starting from the index
                limit = this.window.getStartPosition()
                        + this.window.getNumRows();
            }

            if (index < this.window.getStartPosition()) {
                // read from the index up to the current window into a new
                // window
                CursorWindow tmp = new CursorWindow(null);
                tmp.setNumColumns(NUM_WINDOW_COLUMNS);
                tmp.setStartPosition(index);
                this.fillWindow(tmp, index, this.window.getStartPosition()
                        - index);

                int row = tmp.getStartPosition() + tmp.getNumRows();
                if (row != this.window.getStartPosition()) {
                    // XXX -
                    throw new IllegalStateException();
                }

                // append the current window's content onto the newly read
                // window
                transfer(
                        this.window,
                        tmp,
                        NUM_WINDOW_COLUMNS,
                        tmp.getStartPosition() + tmp.getNumRows(),
                        Math.min(this.window.getNumRows(), MAX_WINDOW_SIZE
                                - tmp.getNumRows()));

                // flip our window reference
                this.window.clear();
                this.window = tmp;
            } else {
                // append to the current window
                this.fillWindow(this.window, limit, WINDOW_READ_SIZE);
            }

            if (this.window.getNumRows() > MAX_WINDOW_SIZE) {

                // trim the window
                CursorWindow tmp = new CursorWindow(null);
                tmp.setNumColumns(NUM_WINDOW_COLUMNS);
                tmp.setStartPosition(MathUtils.clamp(
                        index - MAX_WINDOW_SIZE / 2,
                        this.window.getStartPosition(),
                        this.window.getStartPosition() +
                                this.window.getNumRows() - MAX_WINDOW_SIZE));

                transfer(this.window, tmp, NUM_WINDOW_COLUMNS,
                        tmp.getStartPosition(),
                        MAX_WINDOW_SIZE);

                this.window.clear();
                this.window = tmp;
            }
        }

        FeatureHierarchyListItem item = null;
        try {
            item = new FeatureHierarchyListItem(
                    this.spatialDb,
                    this.window.getLong(index, WINDOW_COL_FID),
                    this.window.getString(index, WINDOW_COL_NAME),
                    this.window.getString(index, WINDOW_COL_ICON),
                    this.window.getInt(index, WINDOW_COL_COLOR),
                    this.window.getInt(index, WINDOW_COL_IS_POINT) != 0,
                    this.window.getDouble(index, WINDOW_COL_MIN_X),
                    this.window.getDouble(index, WINDOW_COL_MIN_Y),
                    this.window.getDouble(index, WINDOW_COL_MAX_X),
                    this.window.getDouble(index, WINDOW_COL_MAX_Y));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read child at index " + index);
        }
        return item;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Visibility.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Search.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Delete.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Export.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(FeatureEdit.class)) {
            return isStyleEditable() ? clazz.cast(this) : null;
        } else {
            return null;
        }
    }

    @Override
    public Object getUserObject() {
        return entry.fsid;
    }

    @Override
    public View getExtraView(View row, ViewGroup parent) {
        final File file = this.getGroupFile();
        if (file == null)
            return null;

        // Get/create view holder
        FeatureExtraHolder h = FeatureExtraHolder.get(row, parent);
        if (h == null)
            return null;

        URIContentHandler fh = getHandler(file);

        h.pan.setVisibility(fh != null && fh.isActionSupported(GoTo.class)
                ? View.VISIBLE
                : View.GONE);

        h.edit.setVisibility(!isStyleEditable() || this.contentType == null
                || this.contentType.equals(MvtSpatialDb.MVT_CONTENT_TYPE)
                        ? View.GONE
                        : View.VISIBLE);

        h.send.setVisibility(this.contentType != null
                && (fh != null && fh.isActionSupported(Send.class)
                        || FileSystemUtils.isFile(file)) ? View.VISIBLE
                                : View.GONE);

        h.pan.setOnClickListener(this);
        h.edit.setOnClickListener(this);
        h.send.setOnClickListener(this);

        return h.root;
    }

    @Override
    protected void refreshImpl() {
        // Call this on UI thread so the window is synced up correctly
        Sort s = this.filter.sort;
        if (order != s) {
            if (!(s instanceof SortDistanceFrom)
                    || featureChildCount >= MAX_DISTANCE_SORT)
                s = new SortAlphabet();
            order = s;
            window.clear();
            window.setNumColumns(NUM_WINDOW_COLUMNS);

            notifyListener();
        }
    }

    @Override
    public boolean isGetChildrenSupported() {
        return false;
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    /**
     * Find a child feature or feature set given its ID
     * @param uid UID to search for
     * @return Child or null if not found
     */
    @Override
    public HierarchyListItem findChild(String uid) {

        // Check for <folder name>:<uuid> format
        int slashIdx = uid.lastIndexOf(':');
        if (slashIdx != -1) {

            // Find feature set folder based on name
            String folderName = uid.substring(0, slashIdx);
            for (PathEntry child : entry.children.values()) {
                if (folderName.equals(child.folder)) {

                    // Then compare UID
                    if (uid.equals(getUID(child)))
                        return createChildSet(child);
                }
            }

            return null;
        }

        // Feature ID or feature set ID
        try {
            long id = Long.parseLong(uid);
            if (entry.childFsids.contains(id)) {
                // Find child feature set based on ID
                for (PathEntry child : entry.children.values()) {
                    if (child.fsid == id)
                        return createChildSet(child);
                }
            } else {
                // Assume this is a feature ID - query for it
                FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
                params.limit = 1;
                params.featureSetFilter = new FeatureSetQueryParameters();
                params.featureSetFilter.limit = 1;
                params.featureSetFilter.ids = Collections.singleton(entry.fsid);
                params.ids = Collections.singleton(id);
                try (FeatureCursor c = spatialDb.queryFeatures(params)) {
                    if (c.moveToNext())
                        return new FeatureHierarchyListItem(spatialDb, c.get());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to find child in " + getUID() + ": " + uid);
        }
        return null;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        prepareQueryParams(params);
        params.ignoredFields = FeatureQueryParameters.FIELD_ATTRIBUTES;
        if (terms.length() >= 2)
            terms = "%" + terms + "%";
        else
            terms = terms + "%";
        params.featureNames = Collections.singleton(terms);

        Set<HierarchyListItem> retval = new HashSet<>();
        FeatureCursor result = null;
        try {
            result = this.spatialDb.queryFeatures(Adapters.adapt(params, null));
            while (result.moveToNext()) {
                retval.add(new FeatureHierarchyListItem(
                        this.spatialDb,
                        result.get()));
            }
        } catch (DataStoreException dse) {
            Log.e(TAG,
                    "error occurred querying datastore: " + spatialDb.getUri());
        } finally {
            if (result != null)
                result.close();
        }
        return retval;
    }

    /**************************************************************************/
    // View On Item Long Click Listener

    @Override
    public void onClick(View v) {
        final File file = this.getGroupFile();
        if (file == null)
            return;

        int id = v.getId();

        URIContentHandler handler = getHandler(file);

        // Pan to file
        if (id == R.id.panButton && handler != null
                && handler.isActionSupported(GoTo.class)) {
            ((GoTo) handler).goTo(false);
        }

        // Send file
        else if (id == R.id.sendButton) {
            if (this.contentType == null)
                return;

            MapView mv = MapView.getMapView();
            if (mv == null)
                return;

            if (handler != null && handler.isActionSupported(Send.class))
                ((Send) handler).promptSend();
            else
                new SendDialog.Builder(mv)
                        .addFile(file, contentType)
                        .show();
        }

        // Edit features
        else if (id == R.id.editButton) {
            if (!isStyleEditable())
                return;

            // 500 feature limit
            if (getDescendantCount() > 500) {
                Toast.makeText(context,
                        R.string.bulk_feature_edit_limit_msg,
                        Toast.LENGTH_LONG).show();
                return;
            }

            // collect the fsids from the current list item or any children it may have
            long[] idArray;
            if (this.entry.fsid == 0) {
                int i = 0;
                idArray = new long[this.entry.childFsids.size()];
                for (long fsid : this.entry.childFsids)
                    idArray[i++] = fsid;
            } else
                idArray = new long[] {
                        this.entry.fsid
                };
            new FeatureEditDropdownReceiver(MapView.getMapView(), spatialDb)
                    .show(this.title, idArray);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (this.contentType == null)
            return false;

        final File fileToSend = this.getGroupFile();
        if (fileToSend == null) {
            Log.w(TAG, "Unable to send file");
            return false;
        }

        return ExportFileMarshal.sendFile(this.context, this.contentType,
                fileToSend, true, null);
    }

    private FeatureSetHierarchyListItem createChildSet(PathEntry child) {
        return new FeatureSetHierarchyListItem(context, spatialDb,
                child, child.folder, this.path + "/" + child.folder,
                order, listener, contentType, mimeType,
                ATAKUtilities.getResourceUri(R.drawable.import_folder_icon));
    }

    private URIContentHandler getHandler(File groupFile) {
        String cType = contentType;
        if (cType != null)
            cType = cType.toUpperCase(LocaleUtil.getCurrent());
        return URIContentManager.getInstance().getHandler(groupFile, cType);
    }

    private File getGroupFile() {

        // Get the associated feature set
        FeatureSet group = null;
        if (this.entry.fsid != FeatureDataStore.FEATURESET_ID_NONE
                && this.entry.childFsids.isEmpty()) {
            try {
                group = Utils.getFeatureSet(spatialDb, this.entry.fsid);
            } catch (DataStoreException dse) {
                Log.e(TAG, "error occurred querying datastore: "
                        + spatialDb.getUri());
            }
        } else {
            FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
            prepareQueryParams(params);

            // XXX - delete all???
            params.limit = 1;

            FeatureSetCursor result = null;
            try {
                result = this.spatialDb
                        .queryFeatureSets(Adapters.adapt(params, null));
                if (!result.moveToNext())
                    return null;
                group = result.get();
            } catch (DataStoreException dse) {
                Log.e(TAG, "error occurred querying datastore: "
                        + spatialDb.getUri());
            } finally {
                if (result != null)
                    result.close();
            }
        }
        if (group == null)
            return null;

        // Get the file for this feature set
        return Utils.getSourceFile(this.spatialDb, group);
    }

    /**************************************************************************/

    @Override
    public boolean setVisible(boolean visible) {
        if (this.entry.fsid != 0 && this.entry.childFsids.isEmpty()) {
            try {
                this.spatialDb.setFeatureSetVisible(this.entry.fsid, visible);
            } catch (DataStoreException dse) {
                Log.e(TAG, "error occurred querying datastore: "
                        + spatialDb.getUri());
            }
        } else {
            FeatureDataStore.FeatureSetQueryParameters params = new FeatureDataStore.FeatureSetQueryParameters();
            prepareQueryParams(params);

            try {
                this.spatialDb.setFeatureSetsVisible(
                        Adapters.adapt(params, null), visible);
            } catch (DataStoreException dse) {
                Log.e(TAG, "error occurred querying datastore: "
                        + spatialDb.getUri());
            }
        }
        return true;
    }

    @Override
    public boolean isVisible() {
        if (this.entry.fsid != 0 && this.entry.childFsids.isEmpty()) {
            try {
                return Utils.isFeatureSetVisible(this.spatialDb,
                        this.entry.fsid);
            } catch (DataStoreException dse) {
                return false;
            }
        } else {
            FeatureQueryParameters params = new FeatureQueryParameters();
            prepareQueryParams(params);
            params.visibleOnly = true;
            params.limit = 1;

            try {
                return (this.spatialDb
                        .queryFeaturesCount(Adapters.adapt(params, null)) > 0);
            } catch (DataStoreException dse) {
                Log.e(TAG, "error occurred querying datastore: "
                        + spatialDb.getUri());
                return false;
            }
        }
    }

    @Override
    public boolean delete() {
        File file = getGroupFile();
        if (file == null) {
            Log.w(TAG, "Failed to find group file to delete");
            return false;
        }

        Log.d(TAG,
                "Delete: " + this.title + ", "
                        + file.getAbsolutePath());
        Intent deleteIntent = new Intent();
        deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
        if (this.contentType != null)
            deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    this.contentType);
        if (this.mimeType != null)
            deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    this.mimeType);
        deleteIntent.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(file)
                .toString());
        AtakBroadcast.getInstance().sendBroadcast(deleteIntent);

        return true;
    }

    @Override
    @NonNull
    public FeatureDataStore2 getFeatureDatabase() {
        return spatialDb;
    }

    @Override
    @NonNull
    public FeatureDataStore2.FeatureQueryParameters getFeatureQueryParams() {
        // Query parameters for all features in this set
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.featureSetFilter = new FeatureSetQueryParameters();
        if (entry.fsid != FeatureDataStore.FEATURESET_ID_NONE) {
            // This set has a defined feature ID
            params.featureSetFilter.ids = Collections
                    .singleton(this.entry.fsid);
        } else {
            // This set is a folder
            params.featureSetFilter.names = new HashSet<>(2);
            params.featureSetFilter.names.add(this.path);
            params.featureSetFilter.names.add(this.path + "/%");
            params.featureSetFilter.ids = new HashSet<>(this.entry.childFsids);
        }
        return params;
    }

    private void fillWindow(CursorWindow resultsWindow, int off, int num) {
        FeatureQueryParameters params = new FeatureQueryParameters();

        FeatureCursor result = null;
        try {
            // XXX - avoid distance sort if there are a lot of children
            if ((this.order instanceof SortDistanceFrom)
                    && this.featureChildCount < MAX_DISTANCE_SORT) {
                final SortDistanceFrom distanceFrom = (SortDistanceFrom) order;

                params.order = Collections.singleton(
                        new FeatureQueryParameters.Distance(
                                distanceFrom.location));
            } else { // default to alphabetic sort
                params.order = Collections.singleton(
                        FeatureQueryParameters.FeatureName.INSTANCE);
            }

            params.featureSetIds = Collections.singleton(this.entry.fsid);
            params.ignoredFields = FeatureQueryParameters.FIELD_ATTRIBUTES;
            params.limit = num;
            params.offset = off;

            result = this.spatialDb.queryFeatures(Adapters.adapt(params, null));
            int row = resultsWindow.getStartPosition()
                    + resultsWindow.getNumRows();
            Feature feature;
            Geometry geom;
            Envelope mbb;
            while (result.moveToNext()) {
                if (!resultsWindow.allocRow())
                    return;

                feature = result.get();
                geom = feature.getGeometry();
                if (geom != null) {
                    mbb = geom.getEnvelope();

                    resultsWindow.putLong(feature.getId(), row, WINDOW_COL_FID);

                    // Item name
                    final String name = feature.getName();
                    if (name != null)
                        resultsWindow.putString(name, row,
                                WINDOW_COL_NAME);
                    else
                        resultsWindow.putNull(row, WINDOW_COL_NAME);

                    // Marker icon
                    Style style = feature.getStyle();
                    if (style == null)
                        style = geom instanceof Point
                                ? new BasicPointStyle(-1, 0)
                                : new BasicStrokeStyle(-1, 0);
                    final String iconUri = FeatureHierarchyListItem
                            .iconUriFromStyle(style);
                    if (iconUri != null)
                        resultsWindow.putString(iconUri, row, WINDOW_COL_ICON);
                    else
                        resultsWindow.putNull(row, WINDOW_COL_ICON);

                    // Marker color
                    resultsWindow.putLong(FeatureHierarchyListItem
                            .colorFromStyle(style), row, WINDOW_COL_COLOR);

                    // Location and bounds
                    resultsWindow.putLong(((geom instanceof Point) ? 1 : 0),
                            row, WINDOW_COL_IS_POINT);
                    resultsWindow.putDouble(mbb.minX, row, WINDOW_COL_MIN_X);
                    resultsWindow.putDouble(mbb.minY, row, WINDOW_COL_MIN_Y);
                    resultsWindow.putDouble(mbb.maxX, row, WINDOW_COL_MAX_X);
                    resultsWindow.putDouble(mbb.maxY, row, WINDOW_COL_MAX_Y);
                }
                row++;

            }
        } catch (Exception e) {
            Log.d(TAG, "bad database access: ", e);

        } finally {
            if (result != null)
                result.close();
        }
    }

    /**************************************************************************/

    private static void transfer(CursorWindow src, CursorWindow dst,
            int numColumns, int row,
            int count) {
        for (int i = 0; i < count; i++) {
            if (!dst.allocRow())
                throw new OutOfMemoryError();

            for (int j = 0; j < numColumns; j++) {
                switch (src.getType(row, j)) {
                    case Cursor.FIELD_TYPE_BLOB:
                        dst.putBlob(src.getBlob(row, j), row, j);
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        dst.putDouble(src.getDouble(row, j), row, j);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        dst.putLong(src.getLong(row, j), row, j);
                        break;
                    case Cursor.FIELD_TYPE_NULL:
                        dst.putNull(row, j);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        dst.putString(src.getString(row, j), row, j);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
            row++;
        }
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {

        if (MissionPackageExportWrapper.class.equals(target)) {
            return toMissionPackage();
        }

        return null;
    }

    private MissionPackageExportWrapper toMissionPackage() {
        File file = getGroupFile();
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "No file found");
            return null;
        }

        return new MissionPackageExportWrapper(false, file.getAbsolutePath());
    }

    /**************************************************************************/

    private static String getTitle(String groupName) {
        if (groupName == null)
            return null;
        final int pathIdx = groupName.lastIndexOf('/');
        if (pathIdx < 0)
            return groupName;
        else
            return groupName.substring(pathIdx);
    }

    /**
     * Generate a UUID for a feature set path entry using the folder name
     * and feature set ID (or child IDs)
     * @param entry Path entry
     * @return UUID string or feature set ID
     */
    private static String getUID(PathEntry entry) {
        // Just use feature set ID
        if (entry.fsid != FeatureDataStore.FEATURESET_ID_NONE)
            return String.valueOf(entry.fsid);

        // Format: <folder name>:<UUID from child feature set IDs>
        // Since 2 different sets with the same folder name can exist
        StringBuilder uid = new StringBuilder(entry.folder);
        if (!entry.childFsids.isEmpty()) {
            uid.append(':');
            StringBuilder sb = new StringBuilder();
            for (long fsid : entry.childFsids)
                sb.append(fsid).append(",");
            uid.append(UUID.nameUUIDFromBytes(sb.toString().getBytes(
                    FileSystemUtils.UTF8_CHARSET)));
        }
        return uid.toString();
    }
}
