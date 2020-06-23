
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import android.widget.TextView;
import android.graphics.Point;
import android.view.WindowManager;
import android.view.Display;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentListener;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import android.widget.Button;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.MapItemSelectTool;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.event.MissionPackageEventProcessor;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.DeleteFileTask;
import com.atakmap.android.missionpackage.file.task.ExtractFileTask;
import com.atakmap.android.missionpackage.file.task.ExtractMapItemTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.routes.Route;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import java.util.Map;
import java.util.Set;

/**
 * Mission Package overlay listing
 * Used to manage Mission Packages; to replace Mission Package Tool
 */
public class MissionPackageMapOverlay extends AbstractMapOverlay2 implements
        MissionPackageBaseTask.Callback, URIContentListener {

    private final static String TAG = "MissionPackageMapOverlay";

    private static final int ORDER = 4;
    private static final int SMALLMISSIONPACKAGE_SIZE_INBYTES = 3 * 1024 * 1024;

    public static final FilenameFilter FeatureSetFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String fn) {
            MIMEType mime = ResourceFile.getMIMETypeForFile(fn);
            return mime == MIMEType.KML || mime == MIMEType.KMZ
                    || mime == MIMEType.GPX || mime == MIMEType.SHP
                    || mime == MIMEType.SHPZ || mime == MIMEType.LPT
                    || mime == MIMEType.DRW || mime == MIMEType.TIF
                    || mime == MIMEType.TIFF;
        }
    };

    private final MapView _view;
    private final Context _context;
    private final MissionPackageMapComponent _component;
    private final View _header;
    private final MissionPackageEventProcessor _eventProcessor;
    private final MissionPackageDrawable _modDrawable;
    private AlertDialog _unsavedDialog;
    private MissionPackageOverlayListModel _listModel;
    private HierarchyListAdapter _om;
    private boolean _vizSupported = false;
    private boolean _disposed = false;

    private final Map<String, MissionPackageHierarchyListItem> _items = new HashMap<>();
    private final List<MissionPackageListGroup> _groups = new ArrayList<>();

    public MissionPackageMapOverlay(MapView view,
            MissionPackageMapComponent component) {
        this._view = view;
        this._context = view.getContext();
        this._component = component;
        this._header = LayoutInflater.from(_context)
                .inflate(R.layout.missionpackage_overlay_header, _view, false);
        this._modDrawable = new MissionPackageDrawable(_context);
        _eventProcessor = new MissionPackageEventProcessor(_context,
                _view.getRootGroup());
        URIContentManager.getInstance().registerListener(this);
    }

    public void dispose() {
        URIContentManager.getInstance().unregisterListener(this);
        _disposed = true;
    }

    public static MissionPackageMapOverlay getOverlay() {
        MapView mv = MapView.getMapView();
        return mv != null ? (MissionPackageMapOverlay) mv
                .getMapOverlayManager()
                .getOverlay(MissionPackageMapOverlay.class.getName()) : null;
    }

    /**
     * Navigate to a mission package
     * @param paths Path array to navigate to (null for root list)
     */
    public static void navigateTo(String... paths) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        ArrayList<String> overlayPaths = new ArrayList<>();
        overlayPaths.add(mv.getContext()
                .getString(R.string.mission_package_name_plural));
        if (!FileSystemUtils.isEmpty(paths))
            overlayPaths.addAll(Arrays.asList(paths));
        Intent om = new Intent(HierarchyListReceiver.MANAGE_HIERARCHY);
        om.putStringArrayListExtra("list_item_paths", overlayPaths);
        om.putExtra("refresh", true); // Refresh if possible, otherwise open OM
        om.putExtra("isRootList", true);
        AtakBroadcast.getInstance().sendBroadcast(om);
    }

    @Override
    public String getIdentifier() {
        return MissionPackageMapOverlay.class.getName();
    }

    private List<MissionPackageHierarchyListItem> getItems() {
        synchronized (_items) {
            return new ArrayList<>(
                    _items.values());
        }
    }

    private Map<String, MissionPackageHierarchyListItem> getItemMap() {
        synchronized (_items) {
            return new HashMap<>(_items);
        }
    }

    public List<MissionPackageListGroup> getGroups() {
        List<MissionPackageListGroup> groups = new ArrayList<>();
        List<MissionPackageHierarchyListItem> items = getItems();
        for (MissionPackageHierarchyListItem item : items) {
            MissionPackageListGroup g = item.getGroup();
            if (g != null)
                groups.add(g);
        }
        return groups;
    }

    public MissionPackageListGroup getGroup(String uid) {
        synchronized (_items) {
            MissionPackageHierarchyListItem item = _items.get(uid);
            if (item != null)
                return item.getGroup();
        }
        return null;
    }

    private void saveAll(MissionPackageBaseTask afterSaveTask) {
        Log.d(TAG, "Saving all modified packages...");

        MissionPackageBaseTask previousTask;
        MissionPackageBaseTask lastTask = afterSaveTask;

        // TODO update progress percentages...

        // chain tasks to run one after the other... the first one we process will be the
        // last one to execute
        // save all that are modified
        List<MissionPackageListGroup> groups = getGroups();
        for (MissionPackageListGroup g : groups) {
            if (g.isModified()) {
                previousTask = lastTask;
                // when each task completes, callback will refresh UI list and rebase that package
                lastTask = new CompressionTask(g.getManifest(), _component
                        .getReceiver(), true, previousTask, this, false);
            }
        }

        if (lastTask != null) {
            if (lastTask instanceof CompressionTask)
                Log.d(TAG, "Saving " + ((CompressionTask) lastTask)
                        .getChainedTaskCount() + " packages");
            // kick off the chain
            lastTask.execute();
        }
        requestRedraw();
    }

    private boolean hasOutstandingChanges() {
        List<MissionPackageListGroup> groups = getGroups();
        for (MissionPackageListGroup g : groups) {
            if (g.isModified())
                return true;
        }
        return false;
    }

    /**
     * Show file transfer logs
     */
    private void showLogView() {
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                MissionPackageReceiver.MISSIONPACKAGE_LOG));
    }

    public void addMapItems(String groupId, boolean includeAttachments,
            String... mapItemUIDArray) {
        MissionPackageListGroup group = getGroup(groupId);
        if (group == null) {
            Log.w(TAG, "Unable to find group " + groupId + " to add map items");
            toast(R.string.unable_to_add_map_items_to_mission_package);
            return;
        }

        if (mapItemUIDArray == null || mapItemUIDArray.length < 1) {
            Log.d(TAG, "No Map Items to add to group " + group.toString());
            return;
        }

        Log.d(TAG, "Adding " + mapItemUIDArray.length + " map items to "
                + group.toString());

        group.addMapItems(_view.getRootGroup(), mapItemUIDArray);

        // optionally, include map item attachments
        int totalAttachments = 0;
        if (includeAttachments) {
            for (String uid : mapItemUIDArray) {
                //check for special case of route checkpoints with attached images
                MapItem item = _view.getRootGroup().deepFindItem("uid", uid);
                if (item instanceof Route) {
                    Route route = (Route) item;
                    // find all checkpoints
                    for (int i = 0; i < route.getNumPoints(); i++) {
                        PointMapItem marker = route.getMarker(i);
                        if (marker == null)
                            continue;

                        String cpUID = marker.getUID();
                        if (FileSystemUtils.isEmpty(cpUID))
                            continue;

                        List<File> attachments = AttachmentManager
                                .getAttachments(cpUID);
                        if (!attachments.isEmpty()) {
                            for (File attachment : attachments)
                                group.addFile(attachment, cpUID);

                            totalAttachments += attachments.size();
                            Log.d(TAG, "Attaching " + attachments.size()
                                    + " files for checkpoint: " + cpUID);
                        }
                    } //end checkpoint loop
                } else {
                    //not a route, just check for attachments
                    List<File> attachments = AttachmentManager
                            .getAttachments(uid);
                    if (!attachments.isEmpty()) {
                        for (File attachment : attachments)
                            group.addFile(attachment, uid);

                        totalAttachments += attachments.size();
                        Log.d(TAG, "Attaching " + attachments.size()
                                + " files for: " + uid);
                    }
                }
            }
        }
        if (totalAttachments > 0)
            toast(R.string.mission_package_added_attachments_for_map_items,
                    totalAttachments);
        requestRedraw();
    }

    public void addFiles(MissionPackageListGroup group, boolean autoImport,
            String... files) {
        if (group == null) {
            // Prompt user to create new or add to existing MP
            List<Exportable> ex = new ArrayList<>();
            for (String path : files)
                ex.add(new MissionPackageExportWrapper(false,
                        path));
            try {
                new MissionPackageExportMarshal(_context)
                        .execute(ex);
            } catch (Exception e) {
                Log.e(TAG, "Failed to export map items to MP",
                        e);
                toast(R.string.failed_export);
            }
            return;
        }
        // loop each file
        for (String path : files) {
            if (!FileSystemUtils.isFile(path))
                continue;
            File file = new File(path);
            if (FileSystemUtils.isEquals(path, group.getManifest().getPath())) {
                toast(R.string.mission_package_add_to_self, file.getName());
                continue;
            }
            if (!_eventProcessor.add(group, file) || !autoImport)
                continue;
            importFile(file, true);
        }
        requestRedraw();
    }

    public void addFiles(MissionPackageListGroup group, String... files) {
        addFiles(group, false, files);
    }

    private void importFile(File file, boolean checkIfFeature) {
        if (!FileSystemUtils.isFile(file))
            return;
        if (!checkIfFeature || FeatureSetFilter.accept(null, file.getName())) {
            // Check if this file has a content handler before importing
            // If it does then there shouldn't be any reason to re-import
            URIContentHandler h = URIContentManager.getInstance()
                    .getHandler(file);
            if (h == null) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION)
                                .putExtra("filepath", file.getAbsolutePath())
                                .putExtra("promptOnMultipleMatch", true)
                                .putExtra("importInPlace", true));
            }
        }
    }

    void importFile(File file) {
        importFile(file, false);
    }

    private void remove(MissionPackageListGroup group, boolean bToast) {
        if (group != null) {
            Log.d(TAG, "Removing package: " + group.getManifest().toString());
            // TODO move to async task?
            // Now remove any local files that were unzipped
            String filesDir = MissionPackageFileIO
                    .getMissionPackageFilesPath(FileSystemUtils.getRoot()
                            .getAbsolutePath());
            File dir = new File(filesDir, group.getManifest().getUID());
            if (dir.exists())
                FileSystemUtils.deleteDirectory(dir, false);
            if (bToast)
                toast(R.string.deleting_mission_package);
        } else {
            Log.w(TAG, "Failed to remove package from null group");
        }
        requestRedraw();
    }

    public void remove(String filePath, boolean bToast) {
        List<MissionPackageListGroup> groups = getGroups();
        for (MissionPackageListGroup g : groups) {
            if (g.getManifest().getPath().equals(filePath)) {
                remove(g, bToast);
                break;
            }
        }
    }

    public void remove(File f, boolean bToast) {
        remove(f.getAbsolutePath(), bToast);
    }

    public boolean contains(MissionPackageManifest contents) {
        List<MissionPackageListGroup> groups = getGroups();
        for (MissionPackageListGroup g : groups) {
            if (g.getManifest().getPath().equals(contents.getPath()))
                return true;
        }
        return false;
    }

    public void add(MissionPackageManifest contents, String userName) {
        add(MissionPackageManifestAdapter.adapt(contents, userName,
                _view.getRootGroup()));
    }

    public boolean add(MissionPackageListGroup group) {
        if (group == null) {
            Log.w(TAG, "Unable to add empty group");
            return false;
        }

        Log.d(TAG, "Adding " + group.toString());
        MissionPackageHierarchyListItem item = new MissionPackageHierarchyListItem(
                group);
        boolean updated;
        synchronized (_items) {
            updated = _items.containsKey(item.getUID());
            _items.put(item.getUID(), item);
        }
        synchronized (_groups) {
            _groups.add(group);
        }
        toast(updated ? R.string.mission_package_updated
                : R.string.mission_package_added, item.getTitle());
        requestRedraw();
        return true;
    }

    void toast(final String str) {
        if (!_disposed) {
            _view.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(_context, str, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    void toast(int strId, Object... args) {
        toast(_context.getString(strId, args));
    }

    private void requestRedraw() {
        if (_om != null && _om.isActive())
            _om.refreshList();
    }

    @Override
    public void onContentImported(URIContentHandler handler) {
        requestRedraw();
    }

    @Override
    public void onContentDeleted(URIContentHandler handler) {
        requestRedraw();
    }

    @Override
    public void onContentChanged(URIContentHandler handler) {
        requestRedraw();
    }

    @Override
    public void onMissionPackageTaskComplete(MissionPackageBaseTask task,
            boolean success) {
        Log.d(TAG, "on package TaskComplete: " + success + ", "
                + (task == null ? "null" : task.toString()));

        if (success) {
            // if task was saved rebase it
            if (task instanceof CompressionTask) {
                // reset the baseline for determining if MP has been modified
                CompressionTask ctask = (CompressionTask) task;
                MissionPackageManifest c = ctask.getManifest();
                if (c == null) {
                    Log.w(TAG, "Unable to find contents to rebase for task: "
                            + task.toString());
                    return;
                }

                // Mission Package was saved, rebase UI group so we know its not been modified since
                // last save
                MissionPackageListGroup group = getGroup(c.getUID());
                if (group != null)
                    group.rebase();
                else
                    Log.w(TAG,
                            "Unable to find contents group to rebase for task: "
                                    + ctask.toString());
            } else if (task instanceof ExtractFileTask)
                importFile(((ExtractFileTask) task).getExtractedFile(), true);
            requestRedraw();
        }
    }

    @Override
    public String getName() {
        return _context.getString(R.string.mission_package_name_plural);
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter filter) {
        if (adapter instanceof HierarchyListAdapter)
            _om = (HierarchyListAdapter) adapter;
        if (_listModel == null)
            _listModel = new MissionPackageOverlayListModel();
        _listModel.refresh(adapter, filter);
        return _listModel;
    }

    private boolean setViz(View v, int viz) {
        if (v.getVisibility() != viz) {
            v.setVisibility(viz);
            return true;
        }
        return false;
    }

    private class MissionPackageOverlayListModel extends
            AbstractHierarchyListItem2 implements Search, Visibility2, Delete,
            Export, View.OnClickListener {

        private final static String TAG = "MissionPackageOverlayListModel";

        private MissionPackageOverlayListModel() {
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        public String getTitle() {
            return MissionPackageMapOverlay.this.getName();
        }

        @Override
        public Drawable getIconDrawable() {
            return hasOutstandingChanges() ? _modDrawable
                    : _context
                            .getDrawable(R.drawable.ic_menu_missionpackage);
        }

        @Override
        public int getPreferredListIndex() {
            return ORDER;
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public String getAssociationKey() {
            return "missionpackagePreference";
        }

        @Override
        public Object getUserObject() {
            return MissionPackageMapOverlay.this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public void refreshImpl() {
            List<HierarchyListItem> filtered = new ArrayList<>();
            try {
                // Filter
                long startTime = SystemClock.elapsedRealtime();
                List<MissionPackageListGroup> groups = MissionPackageUtils
                        .getUiPackages(_view.getRootGroup());
                synchronized (_groups) {
                    _groups.clear();
                    _groups.addAll(groups);
                }
                Log.d(TAG, "getUiPackages took "
                        + (SystemClock.elapsedRealtime() - startTime) + "ms");

                // Keep any modified packages
                boolean vizSupported = false;
                Map<String, MissionPackageHierarchyListItem> itemMap = getItemMap();
                List<String> uids = new ArrayList<>();
                List<String> uidsToRemove = new ArrayList<>();
                for (MissionPackageListGroup group : groups) {
                    if (group == null || !group.isValid())
                        continue;
                    String uid = group.getManifest().getUID();
                    MissionPackageHierarchyListItem existing = itemMap.get(uid);
                    if (uids.contains(uid)) {
                        if (existing != null && existing.getGroup() != null) {
                            // Duplicate package detected
                            File f1 = new File(group.getManifest().getPath());
                            File f2 = new File(existing.getGroup()
                                    .getManifest()
                                    .getPath());
                            if (!f1.exists() || (f2.exists() &&
                                    f1.lastModified() < f2.lastModified())) {
                                // Don't replace existing package
                                Log.d(TAG, "Skipping older package: " + f1);
                                continue;
                            }
                        }
                    } else
                        uids.add(uid);
                    MissionPackageListGroup existingGroup;
                    if (existing == null
                            || (existingGroup = existing.getGroup()) == null
                            || !existingGroup.isModified())
                        itemMap.put(uid, new MissionPackageHierarchyListItem(
                                group));
                }
                for (MissionPackageHierarchyListItem item : itemMap.values()) {
                    MissionPackageListGroup g = item.getGroup();
                    if (g == null)
                        continue;
                    String uid = g.getManifest().getUID();
                    if (!uids.contains(uid)) {
                        uidsToRemove.add(uid);
                        continue;
                    }
                    item.syncRefresh(this.listener, this.filter);
                    filtered.add(item);
                    if (!vizSupported
                            && item.getAction(Visibility.class) != null)
                        vizSupported = true;
                }
                // In case some packages were deleted
                for (String uid : uidsToRemove)
                    itemMap.remove(uid);

                // Sort
                sortItems(filtered);

                // Update groups
                if (!isDisposed()) {
                    synchronized (_items) {
                        _items.clear();
                        _items.putAll(itemMap);
                    }
                }

                _vizSupported = vizSupported;
            } catch (Throwable e) {
                Log.e(TAG, "Failed to refreshImpl", e);
            }

            // Update
            updateChildren(filtered);
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if ((clazz.equals(Visibility.class)
                    || clazz.equals(Visibility2.class)) && !_vizSupported)
                return null;
            return super.getAction(clazz);
        }

        @Override
        public void dispose() {
            super.dispose();
            if (_unsavedDialog == null && hasOutstandingChanges()) {
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.mission_package_name_plural);
                b.setMessage(R.string.discard_unsaved);
                b.setNegativeButton(R.string.yes, null);
                b.setPositiveButton(R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                saveAll(null);
                            }
                        });
                _unsavedDialog = b.create();
                _unsavedDialog.show();
                _unsavedDialog.setOnDismissListener(
                        new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                _unsavedDialog = null;
                                synchronized (_items) {
                                    _items.clear();
                                }
                                synchronized (_groups) {
                                    _groups.clear();
                                }
                            }
                        });
            }
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public View getHeaderView() {
            View dl = _header.findViewById(R.id.download);
            dl.setOnClickListener(this);
            setViz(dl, View.VISIBLE);

            View ftl = _header.findViewById(R.id.changes);
            ftl.setOnClickListener(this);
            setViz(ftl, View.VISIBLE);

            View create = _header.findViewById(R.id.create);
            create.setOnClickListener(this);
            setViz(create, View.VISIBLE);

            View save = _header.findViewById(R.id.save);
            save.setOnClickListener(this);
            setViz(save, hasOutstandingChanges() ? View.VISIBLE : View.GONE);

            setViz(_header.findViewById(R.id.edit), View.GONE);

            return _header;
        }

        /**
         * ******************************************************************
         */

        @Override
        public void onClick(View v) {
            int i = v.getId();
            if (i == R.id.download) {
                if (_component.checkFileSharingEnabled())
                    promptMissionsQuery();

            } else if (i == R.id.changes) {
                if (_component.checkFileSharingEnabled())
                    showLogView();

            } else if (i == R.id.create) {
                promptAddItems(null);

            } else if (i == R.id.save) {
                saveAll(null);

            }
        }

        @Override
        public boolean setVisible(boolean visible) {
            List<Visibility> actions = getChildActions(Visibility.class);
            boolean ret = !actions.isEmpty();
            for (Visibility viz : actions)
                ret &= viz.setVisible(visible);
            return ret;
        }

        @Override
        public boolean delete() {
            List<Delete> actions = getChildActions(Delete.class);
            boolean ret = !actions.isEmpty();
            for (Delete del : actions)
                ret &= del.delete();
            return ret;
        }

        // Search
        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<String> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();
            terms = terms.toLowerCase(LocaleUtil.getCurrent());

            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children)) {
                Log.d(TAG, "No packages to search");
                return retval;
            }
            for (HierarchyListItem item : children) {
                if (!(item instanceof MissionPackageHierarchyListItem))
                    continue;
                MissionPackageHierarchyListItem list = (MissionPackageHierarchyListItem) item;
                MissionPackageListGroup att = list.getGroup();
                if (att == null || !att.isValid())
                    continue;

                MissionPackageManifest manifest = att.getManifest();
                String uid = manifest.getUID();
                if (found.contains(uid))
                    continue;

                String name = manifest.getName().toLowerCase(
                        LocaleUtil.getCurrent());
                String user = att.getUserName().toLowerCase(
                        LocaleUtil.getCurrent());

                // Search name, user name, and contents
                if (name.contains(terms) || user.contains(terms)
                        || !list.find(terms).isEmpty()) {
                    retval.add(item);
                    found.add(uid);
                }
            }
            return retval;
        }

        @Override
        public boolean isSupported(Class target) {
            return Folder.class.equals(target)
                    || KMZFolder.class.equals(target)
                    || GPXExportWrapper.class.equals(target)
                    || OGRFeatureExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class target, ExportFilters filters)
                throws FormatNotSupportedException {
            List<HierarchyListItem> items = getChildren();
            if (Folder.class.equals(target))
                return getFolder(items);
            else if (KMZFolder.class.equals(target))
                return new KMZFolder(getFolder(items));
            else if (GPXExportWrapper.class.equals(target))
                return getGPX(items);
            else if (OGRFeatureExportWrapper.class.equals(target))
                return getOGR(getTitle(), getChildren());
            return null;
        }
    }

    private Folder getFolder(List<HierarchyListItem> items)
            throws FormatNotSupportedException {
        Folder folder = new Folder();
        List<Feature> features = new ArrayList<>();
        folder.setFeatureList(features);
        for (HierarchyListItem item : items) {
            if (item instanceof Exportable && ((Exportable) item)
                    .isSupported(Folder.class)) {
                Folder sub = (Folder) ((Exportable) item)
                        .toObjectOf(Folder.class, null);
                features.add(sub);
            }
        }
        return folder;
    }

    private GPXExportWrapper getGPX(List<HierarchyListItem> items)
            throws FormatNotSupportedException {
        GPXExportWrapper wrapper = new GPXExportWrapper();
        for (HierarchyListItem item : items) {
            if (item instanceof Exportable && ((Exportable) item)
                    .isSupported(GPXExportWrapper.class)) {
                GPXExportWrapper gpx = (GPXExportWrapper) ((Exportable) item)
                        .toObjectOf(GPXExportWrapper.class, null);
                wrapper.add(gpx);
            }
        }
        return wrapper;
    }

    private OGRFeatureExportWrapper getOGR(String name,
            List<HierarchyListItem> items) throws FormatNotSupportedException {
        OGRFeatureExportWrapper wrapper = new OGRFeatureExportWrapper(name);
        for (HierarchyListItem item : items) {
            if (item instanceof Exportable && ((Exportable) item)
                    .isSupported(OGRFeatureExportWrapper.class)) {
                OGRFeatureExportWrapper ogr = (OGRFeatureExportWrapper) ((Exportable) item)
                        .toObjectOf(
                                OGRFeatureExportWrapper.class, null);
                wrapper.addGeometries(ogr);
            }
        }
        return wrapper;
    }

    private void savePackage(final MissionPackageListGroup group) {
        Log.d(TAG, "Saving modified package "
                + group.getManifest().getName());
        group.saveAndSend(_component, false, this, null);
    }

    /**
     * Prompt the user to query missions from a specific TAK server
     */
    private void promptMissionsQuery() {
        //check if server is available prior to deploying
        TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        new ServerListDialog(_view).show(_context.getString(
                R.string.select_server), servers,
                new ServerListDialog.Callback() {
                    @Override
                    public void onSelected(TAKServer server) {
                        if (server == null) {
                            Log.d(TAG, "No configured server selected");
                            return;
                        }
                        String serverConnectString = server
                                .getConnectString();
                        if (!FileSystemUtils.isEmpty(serverConnectString)) {
                            //initiate query of packages available on the server
                            Intent intent = new Intent(
                                    MissionPackageReceiver.MISSIONPACKAGE_QUERY);
                            intent.putExtra("serverConnectString",
                                    serverConnectString);
                            AtakBroadcast.getInstance().sendBroadcast(
                                    intent);
                        } else
                            Log.d(TAG, "No configured server selected (2)");
                    }
                });
    }

    private void promptAddItems(final MissionPackageListGroup group) {
        Resources r = _view.getResources();
        TileButtonDialog d = new TileButtonDialog(_view);
        d.addButton(r.getDrawable(R.drawable.select_from_map),
                r.getString(R.string.map_select));
        d.addButton(r.getDrawable(R.drawable.ic_menu_import_file),
                r.getString(R.string.file_select));
        d.addButton(r.getDrawable(R.drawable.select_from_overlay),
                r.getString(R.string.overlay_title));

        final List<URIContentProvider> providers = URIContentManager
                .getInstance().getProviders("Data Package");
        for (URIContentProvider provider : providers)
            d.addButton(provider.getIcon(), provider.getName());

        d.show(R.string.select_items, R.string.choose_selection_method);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Select from map
                if (which == 0)
                    startMapSelectTool(_context, group != null
                            ? group.getManifest()
                            : null);

                // Select from file browser
                else if (which == 1)
                    addFiles(null);

                // Select from OM
                else if (which == 2) {
                    Intent i = new Intent(
                            HierarchyListReceiver.MANAGE_HIERARCHY);
                    i.putExtra("hier_userselect_handler",
                            HierarchyListUserMissionPackage.class.getName());
                    i.putExtra("hier_usertag", group != null
                            ? group.getManifest().getUID()
                            : null);
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }

                // Content providers
                else if (which >= 3 && which - 3 < providers.size()) {
                    URIContentProvider provider = providers.get(which - 3);
                    provider.addContent("Data Package", new Bundle(),
                            new URIContentProvider.Callback() {
                                @Override
                                public void onAddContent(URIContentProvider p,
                                        List<String> uris) {
                                    List<String> paths = new ArrayList<>(
                                            uris.size());
                                    for (String uri : uris) {
                                        File f = URIHelper.getFile(uri);
                                        if (f != null)
                                            paths.add(f.getAbsolutePath());
                                    }
                                    addFiles(group, true,
                                            paths.toArray(new String[0]));
                                }
                            });
                }
            }
        });
    }

    private void addFiles(final MissionPackageListGroup group) {
        final ImportManagerFileBrowser importView = ImportManagerFileBrowser
                .inflate(_view);

        importView.setTitle(R.string.select_files_to_import);
        importView.setStartDirectory(_component.getReceiver()
                .getUserState()
                .getLastImportDirectory());

        importView.setExtensionTypes(new String[] {
                "*"
        });
        AlertDialog.Builder b = new AlertDialog.Builder(_view.getContext());
        b.setView(importView);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User has selected items and touched OK. Import the data.
                List<File> sFiles = importView.getSelectedFiles();

                if (sFiles.size() == 0) {
                    Toast.makeText(_view.getContext(),
                            R.string.no_import_files,
                            Toast.LENGTH_SHORT).show();
                } else {
                    String[] selectedFiles = new String[sFiles.size()];
                    for (int i = 0; i < sFiles.size(); ++i)
                        selectedFiles[i] = sFiles.get(i).toString();

                    Log.d(TAG, "Selected " + selectedFiles.length + " files");
                    // Add files to current MP
                    addFiles(group, true, selectedFiles);
                    _component.getReceiver().getUserState()
                            .setLastImportDirectory(
                                    importView.getCurrentPath());
                }

            }
        });
        final AlertDialog alert = b.create();
        // Find the current width of the window, we will use this in a minute to determine how large
        // to make the dialog.
        WindowManager wm = (WindowManager) _view.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point p = new Point();
        display.getSize(p);

        // Show the dialog
        alert.show();

        // Copy over the attributes from the displayed window and then set the width
        // to be 70% of the total window width
        Window w = alert.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = Math.min((int) (p.x * .90), 2160);
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(lp);
        }
    }

    /**
     * HierarchyListItem packages which are being tracked
     * Partially based on MapItemHierarchyListItem
     */
    public class MissionPackageHierarchyListItem extends
            AbstractHierarchyListItem2 implements Visibility2, Search,
            Delete, Export, View.OnClickListener {

        private static final String TAG = "MissionPackageHierarchyListItem";
        private final MissionPackageListGroup _group;
        private boolean _vizSupported = false;

        MissionPackageHierarchyListItem(MissionPackageListGroup group) {
            _group = group;
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        public String getTitle() {
            if (_group == null || !_group.isValid()) {
                Log.w(TAG, "Skipping invalid title");
                return _context.getString(
                        R.string.mission_package_name);
            }

            return _group.getManifest().getName();
        }

        @Override
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            String user = MissionPackageUtils.abbreviateFilename(
                    _group.getUserName(), 15);
            if (!FileSystemUtils.isEmpty(user))
                sb.append(user).append(", ");
            int childCount = getChildCount();
            if (childCount == 1)
                sb.append(_context.getString(R.string.single_item));
            else
                sb.append(_context.getString(R.string.items, childCount));
            return sb.toString();
        }

        @Override
        public int getChildCount() {
            if (_group == null || !_group.isValid()) {
                Log.w(TAG, "Skipping invalid child count");
                return 0;
            }

            return super.getChildCount();
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public boolean isChildSupported() {
            return true;
        }

        @Override
        public Drawable getIconDrawable() {
            return _group.isModified() ? _modDrawable
                    : _context
                            .getDrawable(R.drawable.ic_menu_missionpackage);
        }

        @Override
        public String getAssociationKey() {
            return "missionpackagePreference";
        }

        private MissionPackageListGroup getGroup() {
            if (_group == null || !_group.isValid()) {
                Log.w(TAG, "Skipping invalid user object");
                return null;
            }

            return _group;
        }

        @Override
        public Object getUserObject() {
            return getGroup();
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ExtraHolder();
                v = LayoutInflater.from(_context).inflate(
                        R.layout.missionpackage_overlay_manifestitem,
                        parent, false);
                h.size = v.findViewById(
                        R.id.missionpackage_overlay_manifestitem_size);
                h.save = v.findViewById(R.id.save);
                h.send = v.findViewById(R.id.send);
                h.delete = v.findViewById(R.id.delete);
                v.setTag(h);
            }
            boolean multiSelect = this.listener instanceof HierarchyListAdapter
                    && ((HierarchyListAdapter) this.listener)
                            .getSelectHandler() != null;

            long totalSize = _group.getManifest().getTotalSize();
            h.size.setText(MathUtils.GetLengthString(totalSize));

            int warning = getSendWarningColor(totalSize,
                    _component.getReceiver().getHighThresholdInBytes(),
                    _component.getReceiver().getLowThresholdInBytes());

            switch (warning) {
                case Color.RED:
                    h.size.setTextColor(0xFFFF6666);
                    h.size.setTypeface(null, Typeface.BOLD);
                    break;
                case Color.YELLOW:
                    h.size.setTextColor(0xFFFFFF66);
                    h.size.setTypeface(null, Typeface.NORMAL);
                    break;
                default:
                    h.size.setTextColor(Color.LTGRAY);
                    h.size.setTypeface(null, Typeface.NORMAL);
            }

            h.save.setOnClickListener(this);
            h.save.setVisibility(_group.isModified()
                    ? View.VISIBLE
                    : View.GONE);

            h.send.setOnClickListener(this);
            h.send.setVisibility(FileSystemUtils.isEmpty(_group.getItems())
                    ? View.GONE
                    : View.VISIBLE);

            h.delete.setOnClickListener(this);
            h.delete.setVisibility(View.VISIBLE);

            if (multiSelect) {
                h.save.setVisibility(View.GONE);
                h.send.setVisibility(View.GONE);
                h.delete.setVisibility(View.GONE);
            }
            return v;
        }

        @Override
        public void refreshImpl() {
            // Filter
            boolean vizSupported = false;
            List<HierarchyListItem> filtered = new ArrayList<>();
            if (_group != null && _group.isValid()) {
                for (MissionPackageListItem child : _group.getItems()) {
                    HierarchyListItem listItem = null;
                    if (child instanceof MissionPackageListFileItem)
                        listItem = new MissionPackageFileHierarchyListItem(
                                MissionPackageMapOverlay.this, _view,
                                this.listener, _group,
                                (MissionPackageListFileItem) child);
                    else if (child instanceof MissionPackageListMapItem)
                        listItem = new MissionPackageMapItemHierarchyListItem(
                                MissionPackageMapOverlay.this, _view,
                                this.listener, _group,
                                (MissionPackageListMapItem) child);
                    if (listItem != null && this.filter.accept(listItem)) {
                        filtered.add(listItem);
                        if (!vizSupported && listItem.getAction(
                                Visibility.class) != null)
                            vizSupported = true;
                    }
                }
            }
            _vizSupported = vizSupported;

            // Sort
            sortItems(filtered);

            // Update
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            // Only hide empty Mission Packages if a non-default filter is active
            return this.filter != null && !this.filter.isDefaultFilter();
        }

        @Override
        public String getUID() {
            if (_group == null || !_group.isValid()) {
                Log.w(TAG, "Skipping invalid UID");
                return null;
            }

            return _group.getManifest().getUID();
        }

        /**************************************************************************/

        @Override
        public void onClick(View v) {
            int i = v.getId();
            if (i == R.id.edit) {
                edit();

            } else if (i == R.id.save) {
                savePackage(_group);

            } else if (i == R.id.send) {
                if (_component.checkFileSharingEnabled())
                    sendConfirm();

            } else if (i == R.id.delete) {
                deleteConfirm();

            }
        }

        @Override
        public View getHeaderView() {
            View edit = _header.findViewById(R.id.edit);
            edit.setOnClickListener(this);
            setViz(edit, View.VISIBLE);

            View save = _header.findViewById(R.id.save);
            save.setOnClickListener(this);
            setViz(save, _group.isModified() ? View.VISIBLE
                    : View.GONE);

            setViz(_header.findViewById(R.id.download),
                    View.GONE);
            setViz(_header.findViewById(R.id.create),
                    View.GONE);
            setViz(_header.findViewById(R.id.changes),
                    View.GONE);

            return _header;
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if ((clazz.equals(Visibility.class)
                    || clazz.equals(Visibility2.class)) && !_vizSupported)
                return null;
            return super.getAction(clazz);
        }

        @Override
        public boolean isSupported(Class target) {
            return Folder.class.equals(target)
                    || KMZFolder.class.equals(target)
                    || GPXExportWrapper.class.equals(target)
                    || OGRFeatureExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class target, ExportFilters filters)
                throws FormatNotSupportedException {
            List<HierarchyListItem> items = getChildren();
            if (Folder.class.equals(target))
                return getFolder(items);
            else if (KMZFolder.class.equals(target))
                return new KMZFolder(getFolder(items));
            else if (GPXExportWrapper.class.equals(target))
                return getGPX(items);
            else if (OGRFeatureExportWrapper.class.equals(target))
                return getOGR(getTitle(), getChildren());
            return null;
        }

        @Override
        public boolean delete() {
            List<Delete> actions = getChildActions(Delete.class);
            boolean ret = !actions.isEmpty();
            for (Delete del : actions)
                ret &= del.delete();
            if (_group != null && _group.getItems().isEmpty())
                deletePackage(_group, false, true);
            return ret;
        }

        @Override
        public boolean setVisible(boolean visible) {
            if (_group == null || !_group.isValid()) {
                Log.w(TAG, "Skipping invalid setVisible");
                return false;
            }
            boolean ret = false;
            List<Visibility> vizActions = getChildActions(Visibility.class);
            for (Visibility v : vizActions)
                ret |= v.setVisible(visible);
            return ret;
        }

        /**
         * ******************************************************************
         */
        // Search
        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<String> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Search
                        && ((Search) item).find(terms) != null
                        && !found.contains(item.getUID())) {
                    retval.add(item);
                    found.add(item.getUID());
                }
            }
            return retval;
        }

        private EditText editName, editRemarks;
        private TextView txtContent;
        private CheckBox chkSelectViaMapView, chkIncludeAttachments;
        private Button addMapItem, addFile;

        private void edit() {
            Log.d(TAG, "Editing: " + _group.toString());

            Context ctx = _context;
            View view = LayoutInflater.from(ctx).inflate(
                    R.layout.missionpackage_edit, _view, false);

            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setCancelable(false);
            b.setPositiveButton(R.string.done, null);
            b.setNegativeButton(R.string.cancel, null);
            b.setView(view);
            final AlertDialog editDialog = b.create();
            editDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = editDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Validate inputs (e.g. package name)
                            if (validateInput()) {
                                // otherwise just refresh the adapter
                                editDialog.dismiss();
                                requestRedraw();
                            }
                        }
                    });
                }
            });

            // allow only alphanumeric, space, underscore (this will be a filename)
            editName = view
                    .findViewById(R.id.mission_package_edit_name);
            editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editName.setFilters(new InputFilter[] {
                    MissionPackageExportMarshal.NAME_FILTER
            });
            editName.setText(_group.getManifest().getName());

            editRemarks = view
                    .findViewById(R.id.mission_package_edit_remarks);
            editRemarks.setText(_group.getManifest().getRemarks());

            txtContent = view
                    .findViewById(R.id.mission_package_edit_content);
            if (_group.getManifest().isEmpty())
                txtContent.setVisibility(TextView.INVISIBLE);
            else
                txtContent.setText(ctx.getString(
                        R.string.mission_package_edit_content_text,
                        _group.getManifest().getMapItemCount(),
                        _group.getManifest().getFileCount()));

            // set up check boxes
            chkSelectViaMapView = view
                    .findViewById(R.id.missionpackage_select_chkMapItemMapView);
            chkIncludeAttachments = view
                    .findViewById(
                            R.id.missionpackage_select_chkMapItemAttachments);

            MissionPackageViewUserState userState = _component.getReceiver()
                    .getUserState();
            chkIncludeAttachments.setChecked(userState.isIncludeAttachments());
            chkSelectViaMapView.setChecked(userState.isLastMapItemViaMapView());

            // setup buttons
            addMapItem = view
                    .findViewById(R.id.btnMissionPackageSelectMapItem);
            addMapItem
                    .setOnClickListener(
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // button only displayed for isUpdate
                                    // test if anything changed. Only show dialog if info was changed.
                                    if (!validateInput()) {
                                        Log.d(TAG,
                                                "Cannot Add Map Items until package edits are valid");
                                        return;
                                    }
                                    editDialog.dismiss();
                                    selectMapItems(
                                            chkSelectViaMapView.isChecked());
                                }
                            });

            addFile = view
                    .findViewById(R.id.btnMissionPackageSelectExternalFile);
            addFile.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!validateInput()) {
                        Log.d(TAG,
                                "Cannot Add Files until package edits are valid");
                        return;
                    }
                    editDialog.dismiss();
                    addFiles(_group);
                }
            });

            // display the dialog
            editDialog.show();
        }

        /**
         * test the fields for valid input If valid, set them on the Manifest working copy
         * @return - true if all fields are valid
         */
        private boolean validateInput() {
            if (editName == null)
                return false;
            Context ctx = _context;
            String name = editName.getText().toString().trim();
            if (FileSystemUtils.isEmpty(name)) {
                Log.d(TAG, "User must specify Name");
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(ctx.getString(R.string.name_missing));
                b.setMessage(ctx
                        .getString(R.string.mission_package_specify_name));
                b.setPositiveButton(ctx.getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                editName.requestFocus();
                            }
                        });
                b.show();
                return false;
            }

            // see if name has changed and be sure unique
            if (!name.equals(_group.getManifest().getName())) {

                // see if set back to original name
                if (name.equals(_group.getBaseline().getName())) {
                    Log.d(TAG, "Updated to original name: " + name);
                    _group.getManifest().setName(name);
                } else {

                    // be sure name is unique
                    String temp = MissionPackageUtils.getUniqueName(ctx, name);
                    if (!FileSystemUtils.isEquals(name, temp)) {
                        name = temp;
                        Log.d(TAG, "Updated to unique name: " + name);
                        editName.setText(name);
                    }

                    // now update the manifest working copy
                    _group.getManifest().setName(name);
                }
            }

            String remarks = editRemarks.getText().toString().trim();
            if (!FileSystemUtils.isEquals(remarks, _group.getManifest()
                    .getRemarks())) {
                _group.getManifest().setRemarks(remarks);
            }

            // save state out for next edit
            MissionPackageViewUserState userState = _component.getReceiver()
                    .getUserState();
            userState.setIncludeAttachments(chkIncludeAttachments.isChecked());
            userState.setLastMapItemViaMapView(chkSelectViaMapView.isChecked());

            return true;
        }

        private void selectMapItems(boolean bMapView) {
            if (!bMapView) {
                Log.d(TAG, "Selecting Map Items...");
                // display map item hierarchy to get user selections
                Intent hierIntent = new Intent();
                hierIntent
                        .setAction(HierarchyListReceiver.MANAGE_HIERARCHY);
                hierIntent.putExtra("hier_userselect_handler",
                        HierarchyListUserMissionPackage.class.getName());
                // set tag so on return we can associate with proper Mission Package
                hierIntent.putExtra("hier_usertag", getUID());
                AtakBroadcast.getInstance().sendBroadcast(hierIntent);
                return;
            }

            // ensure all changes are saved, so changes are not lost
            if (hasOutstandingChanges()) {
                Log.d(TAG,
                        "Saving all changes prior to selecting map items...");
                saveAll(new SaveAndSelectMapItemTask(_context,
                        _group.getManifest(), _component.getReceiver()));
            } else {
                // launch select tool
                startMapSelectTool(_context, _group.getManifest());
            }
        }

        private void deleteConfirm() {
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setMessage(
                    R.string.mission_package_delete_package_and_remove_package_contents);
            b.setNegativeButton(R.string.cancel, null);
            b.setPositiveButton(R.string.remove_contents,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            AlertDialog.Builder b = new AlertDialog.Builder(
                                    _context);
                            b.setTitle(R.string.confirm_delete);
                            b.setMessage(
                                    R.string.mission_package_contents_will_be_removed_from_device);
                            b.setNegativeButton(R.string.cancel, null);
                            b.setPositiveButton(R.string.remove_contents,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int whichButton) {
                                            Log.d(TAG, "Removing contents: "
                                                    + _group.toString());
                                            _group.removeContents();
                                            delete(true);
                                        }
                                    });
                            b.show();
                        }
                    });
            b.setNeutralButton(R.string.leave_contents,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            dialog.dismiss();
                            Log.d(TAG,
                                    "Deleting package: " + _group.toString());
                            delete(true);
                        }
                    });
            b.show();
        }

        private void delete(boolean singleDelete) {
            if (!singleDelete) {
                delete();
                return;
            }
            remove(_group, false);
            deletePackage(_group, true, true);
        }

        private void sendConfirm() {
            // check if empty
            if (_group == null || _group.getManifest().isEmpty()) {
                Log.e(TAG, "Unable to send empty contents");
                toast(R.string.mission_package_cannot_send_empty_package);
                return;
            }

            // check if over "NoGo"
            MissionPackageReceiver receiver = _component.getReceiver();
            long totalSizeInBytes = _group.getManifest().getTotalSize(),
                    highSize = receiver
                            .getHighThresholdInBytes(),
                    lowSize = receiver
                            .getLowThresholdInBytes(),
                    nogoSize = receiver
                            .getNogoThresholdInBytes();
            if (totalSizeInBytes > nogoSize) {
                String message = _context.getString(
                        R.string.mission_package_cannot_send_package_above_size)
                        + MathUtils.GetLengthString(nogoSize);
                Log.e(TAG, message);
                toast(message);
                return;
            }

            // no need to confirm send if size is "Green"
            if (totalSizeInBytes < lowSize) {
                MissionPackageApi.Send(_context, _group.getManifest(),
                        null, null, true);
                return;
            }

            View v = LayoutInflater.from(_context).inflate(
                    R.layout.missionpackage_send, _view, false);

            String title, warning;
            if (totalSizeInBytes < lowSize) {
                title = _context.getString(R.string.mission_package_name);
                warning = "";
            } else if (totalSizeInBytes < highSize) {
                title = _context.getString(R.string.large_mission_package);
                warning = _context.getString(
                        R.string.mission_package_large_file_size_transfer_warning);
            } else {
                title = _context.getString(R.string.very_large_mission_package);
                warning = _context.getString(
                        R.string.mission_package_very_large_file_size_transfer_warning);
            }

            Log.d(TAG, "Showing send dialog for package: "
                    + _group.getManifest().toString());

            TextView nameText = v
                    .findViewById(R.id.missionpackage_send_txtName);
            nameText.setText(MissionPackageUtils.abbreviateFilename(
                    _group.getManifest().getName(), 40));

            TextView sizeText = v
                    .findViewById(R.id.missionpackage_send_txtTotalSize);
            sizeText.setText(_context.getString(R.string.mission_package_size,
                    MathUtils.GetLengthString(totalSizeInBytes)));

            TextView textWarning = v
                    .findViewById(R.id.missionpackage_send_txtSizeWarning);
            textWarning.setText(warning);
            if (totalSizeInBytes < lowSize)
                textWarning.setVisibility(TextView.GONE);
            else {
                textWarning.setVisibility(TextView.VISIBLE);
                textWarning.setTextColor(getSendWarningColor(
                        totalSizeInBytes, highSize, lowSize));
            }

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(_context.getString(R.string.mission_package_confirm,
                    title));
            b.setView(v);
            b.setPositiveButton(R.string.send,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int i) {
                            MissionPackageApi.Send(_context,
                                    _group.getManifest(),
                                    null, null, true);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    private static class ExtraHolder {
        TextView size;
        ImageButton save, send, delete;
    }

    private void deletePackage(MissionPackageListGroup group, boolean toast,
            boolean deleteDupes) {
        boolean onUI = Thread.currentThread() == Looper.getMainLooper()
                .getThread();
        if (deleteDupes) {
            List<MissionPackageListGroup> groups;
            synchronized (_groups) {
                groups = new ArrayList<>(_groups);
            }
            // Find all duplicate MPs (matching UIDs) and delete
            List<MissionPackageListGroup> removed = new ArrayList<>();
            for (MissionPackageListGroup gr : groups) {
                if (gr.getManifest() != null && gr.getManifest().getUID()
                        .equals(group.getManifest().getUID())) {
                    removed.add(gr);
                    deletePackage(gr, false, false);
                }
            }
            synchronized (_groups) {
                _groups.removeAll(removed);
            }
            if (toast)
                toast(R.string.deleting_mission_package);
            return;
        }

        // Delete entire package
        MissionPackageManifest mpm = group.getManifest();
        File src = new File(mpm.getPath());
        Log.d(TAG, "Deleting " + mpm.getName() + "@" + src);
        // see if its worth the overhead of an async task
        if (onUI && FileSystemUtils.isFile(src)
                && src.length() > SMALLMISSIONPACKAGE_SIZE_INBYTES)
            new DeleteFileTask(mpm, _component.getReceiver(),
                    null).execute();
        else {
            if (toast)
                toast(R.string.deleting_mission_package);
            File file = FileSystemUtils.moveToTemp(_context, src);
            FileSystemUtils.deleteFile(file);
        }
    }

    void promptExtractContent(final MissionPackageListGroup group,
            final MissionPackageListItem item) {
        String name = item.getname();
        long size = item.getContent().getSize();
        if (size > 0)
            name += " (" + MathUtils.GetLengthString(size) + ")";
        String message = _context
                .getString(R.string.mission_package_extract_from_package, name);
        if (item instanceof MissionPackageListMapItem) {
            // Extract CoT and display metadata
            String how = null, type = null, point = null, callsign = null,
                    uid = ((MissionPackageListMapItem) item)
                            .getUID();
            String cotXml = MissionPackageExtractor.ExtractCoT(_context,
                    new File(group.getManifest().getPath()), item.getContent(),
                    false);
            if (!FileSystemUtils.isEmpty(cotXml)) {
                CotEvent event = CotEvent.parse(cotXml);
                if (event != null && event.isValid()) {
                    how = event.getHow();
                    type = event.getType();
                    callsign = CotUtils.getCallsign(event);
                    CotPoint cp = event.getCotPoint();
                    if (cp != null) {
                        SharedPreferences sp = PreferenceManager
                                .getDefaultSharedPreferences(_context);
                        CoordinateFormat cf = CoordinateFormat.find(
                                sp.getString("coord_display_pref", _context
                                        .getString(
                                                R.string.coord_display_pref_default)));
                        if (cf != null)
                            point = CoordinateFormatUtilities.formatToString(
                                    new GeoPoint(cp.getLat(), cp.getLon(),
                                            GeoPoint.UNKNOWN,
                                            0, 0),
                                    cf);
                    }
                } else
                    Log.e(TAG,
                            "Unable to parse extracted CoT Event: " + cotXml);
            }
            message = _context.getString(
                    R.string.mission_package_extract_item_from_package,
                    (FileSystemUtils.isEmpty(callsign)
                            ? _context.getString(R.string.item_lower)
                            : callsign),
                    callsign, uid, point, type, how);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(item.isFile() ? R.string.mission_package_extract_file
                : R.string.extract_item);
        b.setMessage(message);
        b.setCancelable(false);
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int id) {
                        if (item.isFile())
                            new ExtractFileTask(group.getManifest(),
                                    _component.getReceiver(),
                                    item.getContent(),
                                    MissionPackageMapOverlay.this).execute();
                        else
                            new ExtractMapItemTask(group.getManifest(),
                                    _component.getReceiver(),
                                    item.getContent(),
                                    MissionPackageMapOverlay.this).execute();
                    }
                });
        b.setNegativeButton(R.string.no, null);
        b.show();
    }

    void promptRemoveContent(final MissionPackageListGroup group,
            final MissionPackageListItem item) {

        String msg = _context.getString(R.string.remove_from_mission_package,
                item.getname(),
                _context.getString(R.string.mission_package_name),
                group.getManifest().getName());

        View v = LayoutInflater.from(_context).inflate(
                R.layout.missionpackage_remove_content, _view, false);

        final CheckBox fromMap = v.findViewById(R.id.remove_map);
        final CheckBox fromPkg = v.findViewById(R.id.remove_package);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.confirm_discard);
        b.setMessage(msg);
        b.setView(v);
        b.setCancelable(false);
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int id) {
                        if (fromPkg.isChecked()) {
                            group.removeItem(item);
                            toast(R.string.mission_package_removing_item_from_package);
                        }
                        if (fromMap.isChecked())
                            item.removeContent();
                        requestRedraw();
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private static class SaveAndSelectMapItemTask extends
            MissionPackageBaseTask {

        public final static String TAG = "SaveAndSelectMapItemTask";

        private final Context _context;

        private SaveAndSelectMapItemTask(Context ctx,
                MissionPackageManifest contents,
                MissionPackageReceiver receiver) {
            super(contents, receiver, true, null);
            _context = ctx;
        }

        @Override
        public String getProgressDialogMessage() {
            return _context.getString(R.string.compressing_please_wait);
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            Thread.currentThread().setName("SaveAndSelectMapItemTask");

            // work to be performed by background thread
            Log.d(TAG, "Executing: " + toString());

            // launch select tool
            startMapSelectTool(_context, _manifest);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // work to be performed by UI thread after work is complete
            Log.d(TAG, "onPostExecute");

            // close the progress dialog
            if (_progressDialog != null) {
                // no follow on task.. we are all down
                _progressDialog.dismiss();
                _progressDialog = null;
            }
        }
    }

    private static class MissionPackageDrawable extends Drawable {

        private final Context _context;
        private final Bitmap _mpIcon;
        private final Paint _paint = new Paint();

        MissionPackageDrawable(Context ctx) {
            _context = ctx;
            _mpIcon = ATAKUtilities.getUriBitmap(ATAKUtilities.getResourceUri(
                    R.drawable.ic_menu_missionpackage));
            _paint.setTextSize(18 * _context.getResources()
                    .getDisplayMetrics().density);
            _paint.setFakeBoldText(true);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            float s, scaledWidth, scaledHeight;
            Matrix m = new Matrix();
            if (_mpIcon != null && !_mpIcon.isRecycled()) {
                // Base icon
                s = Math.min(width / _mpIcon.getWidth(),
                        height / _mpIcon.getHeight());
                scaledWidth = _mpIcon.getWidth() * s;
                scaledHeight = _mpIcon.getHeight() * s;
                m.postScale(s, s);
                m.postTranslate((width - scaledWidth) / 2,
                        (height - scaledHeight) / 2);
                _paint.setColor(Color.WHITE);
                canvas.drawBitmap(_mpIcon, m, _paint);
            }
            _paint.setColor(Color.RED);
            canvas.drawText("*", 0, _paint.getTextSize(), _paint);
        }

        @Override
        public void setAlpha(int alpha) {
            // do nothing
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // do nothing
        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    }

    private static int getSendWarningColor(long totalSizeInBytes,
            long high, long low) {
        if (totalSizeInBytes < low)
            return Color.GREEN;
        else if (totalSizeInBytes < high)
            return Color.YELLOW;
        return Color.RED;
    }

    private static void startMapSelectTool(Context context,
            MissionPackageManifest manifest) {
        Intent callback = new Intent(
                MissionPackageReceiver.MISSIONPACKAGE_MAPSELECT);
        if (manifest != null)
            callback.putExtra("MissionPackageUID", manifest.getUID());

        Intent i = new Intent(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        i.putExtra("tool", MapItemSelectTool.TOOL_NAME);
        i.putExtra("title", context.getString(R.string.add_to)
                + (manifest != null ? manifest.getName()
                        : context.getString(R.string.mission_package_name)));
        i.putExtra("disallowKeys", new String[]
        // Disallow TAK user, emergency markers, and non-CoT items
        {
                "atakRoleType", "emergency", "nevercot"
        });
        i.putExtra("disallowTypes", new String[]
        // Disallow SPIs and self marker
        {
                "b-m-p-s-p-i", "self"
        });
        i.putExtra("callback", callback);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }
}
