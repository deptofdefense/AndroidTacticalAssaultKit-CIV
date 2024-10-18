
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Toast;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentListener;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importfiles.sort.ImportAPKSort;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportResolver.SortFlags;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Action;
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
import com.atakmap.android.missionpackage.event.MissionPackageEventProcessor;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
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
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
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

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public static final FilenameFilter FeatureSetFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String fn) {
            MIMEType mime = ResourceFile.getMIMETypeForFile(fn);
            return mime == MIMEType.KML || mime == MIMEType.KMZ
                    || mime == MIMEType.GPX || mime == MIMEType.SHP
                    || mime == MIMEType.GML
                    || mime == MIMEType.SHPZ || mime == MIMEType.LPT
                    || mime == MIMEType.DRW || mime == MIMEType.TIF
                    || mime == MIMEType.TIFF;
        }
    };

    private final MapView _view;
    private final Context _context;
    private final SharedPreferences _prefs;
    private final MissionPackageMapComponent _component;
    private final MissionPackageReceiver _receiver;
    private final MissionPackageViewUserState _userState;
    private final View _header;
    private final MissionPackageEventProcessor _eventProcessor;
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
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        this._component = component;
        _receiver = component.getReceiver();
        _userState = _receiver.getUserState();
        this._header = LayoutInflater.from(_context)
                .inflate(R.layout.missionpackage_overlay_header, _view, false);
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
        if (!FileSystemUtils.isEmpty(paths)) {
            for (String uid : paths) {
                if (uid != null)
                    overlayPaths.add(uid);
            }
        }
        Intent om = new Intent(HierarchyListReceiver.MANAGE_HIERARCHY);
        om.putStringArrayListExtra("list_item_paths", overlayPaths);
        om.putExtra("isRootList", true);
        AtakBroadcast.getInstance().sendBroadcast(om);
    }

    @Override
    public String getIdentifier() {
        return MissionPackageMapOverlay.class.getName();
    }

    public MissionPackageMapComponent getComponent() {
        return _component;
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

    View getHeaderView() {
        return _header;
    }

    void saveAll(MissionPackageBaseTask afterSaveTask) {
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

    boolean hasOutstandingChanges() {
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
            Log.d(TAG, "No Map Items to add to group " + group);
            return;
        }

        Log.d(TAG, "Adding " + mapItemUIDArray.length + " map items to "
                + group);

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
            importFile(group, file, true);
        }
        requestRedraw();
    }

    public void addFiles(MissionPackageListGroup group, String... files) {
        addFiles(group, false, files);
    }

    /**
     * Import a file into ATAK that resides within a data package
     * @param group Data package group
     * @param file The file to import
     * @param autoImport True if this file is being auto-imported as part of
     *                   an extraction task or after adding it
     */
    void importFile(final MissionPackageListGroup group, final File file,
            boolean autoImport) {
        // File does not exist
        if (!FileSystemUtils.isFile(file))
            return;

        // Don't import regular images
        if (ImageDropDownReceiver.ImageFileFilter.accept(null, file.getName()))
            return;

        // Check if this file has a content handler before importing
        // If it does then there shouldn't be any reason to re-import
        URIContentHandler h = URIContentManager.getInstance().getHandler(file);
        if (h == null) {

            if (group == null)
                return;

            // Find this content in the existing package
            MissionPackageContent content = null;
            List<MissionPackageContent> files = group.getManifest().getFiles();
            for (MissionPackageContent f : files) {
                String path = f.getParameterValue(
                        MissionPackageContent.PARAMETER_LOCALPATH);
                if (FileSystemUtils.isEquals(path, file.getAbsolutePath())) {
                    content = f;
                    break;
                }
            }

            // Import using the set content type parameter
            String contentType = null;
            boolean hidden = false;
            if (content != null) {
                contentType = content.getParameterValue(
                        MissionPackageContent.PARAMETER_CONTENT_TYPE);
                hidden = FileSystemUtils.isEquals(content.getParameterValue(
                        MissionPackageContent.PARAMETER_VISIBLE), "false");
            }

            // Sort flags based on manifest parameters
            Set<SortFlags> flags = hidden
                    ? Collections.singleton(SortFlags.HIDE_FILE)
                    : Collections.emptySet();

            final List<ImportResolver> matchingSorters = new ArrayList<>();
            List<ImportResolver> sorters = ImportFilesTask.GetSorters(_context,
                    true, false, true, false);
            for (ImportResolver sorter : sorters) {

                // Ignore APK installer - does not produce expected behavior
                // when adding to a data package
                if (autoImport && sorter instanceof ImportAPKSort)
                    continue;

                if (sorter.match(file)) {
                    Pair<String, String> p = sorter.getContentMIME();
                    if (p != null && contentType != null
                            && contentType.equals(p.first)) {
                        // If the content type matches then import right here
                        sorter.beginImport(file, flags);
                        return;
                    }
                    matchingSorters.add(sorter);
                }
            }

            // Allow sorters to filter out other sorters
            final List<ImportResolver> lir = new ArrayList<>(matchingSorters);
            for (ImportResolver ir : lir) {
                if (matchingSorters.contains(ir))
                    ir.filterFoundResolvers(matchingSorters, file);
            }

            // Unsupported file
            if (matchingSorters.isEmpty())
                return;

            // Only one matching sorter left - use that one
            if (matchingSorters.size() == 1) {
                importFile(content, file, matchingSorters.get(0));
                return;
            }

            final MissionPackageContent fContent = content;
            TileButtonDialog d = new TileButtonDialog(_view);
            d.setTitle(R.string.importmgr_select_desired_import_method,
                    file.getName());
            for (ImportResolver sorter : matchingSorters)
                d.addButton(sorter.getIcon(), sorter.getDisplayableName());
            d.setOnClickListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    if (w < 0 || w >= matchingSorters.size())
                        return;
                    importFile(fContent, file, matchingSorters.get(w));
                }
            });
            d.show(true);
        }
    }

    private void importFile(MissionPackageContent content, File file,
            ImportResolver resolver) {
        if (content != null) {
            Pair<String, String> p = resolver.getContentMIME();
            if (p != null && p.first != null)
                content.setParameter(
                        MissionPackageContent.PARAMETER_CONTENT_TYPE, p.first);
        }
        resolver.beginImport(file);
    }

    void remove(MissionPackageListGroup group, boolean bToast) {
        if (group != null) {
            Log.d(TAG, "Removing package: " + group.getManifest().toString());
            // TODO move to async task?
            // Now remove any local files that were unzipped
            String filesDir = MissionPackageFileIO
                    .getMissionPackageFilesPath(FileSystemUtils.getRoot()
                            .getAbsolutePath());
            File dir = new File(filesDir, group.getManifest().getUID());
            if (IOProviderFactory.exists(dir))
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

        Log.d(TAG, "Adding " + group);
        MissionPackageHierarchyListItem item = new MissionPackageHierarchyListItem(
                _view, this, group);
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
            MissionPackageManifest manifest = task.getManifest();
            if (manifest == null) {
                Log.w(TAG,
                        "Unable to find contents to rebase for task: " + task);
                return;
            }

            MissionPackageListGroup group = getGroup(manifest.getUID());
            if (group == null) {
                Log.w(TAG, "Unable to find contents group to rebase for task: "
                        + task);
                return;
            }

            // if task was saved rebase it
            if (task instanceof CompressionTask) {
                // reset the baseline for determining if MP has been modified
                // Mission Package was saved, rebase UI group so we know its not been modified since
                // last save
                group.rebase();
            } else if (task instanceof ExtractFileTask)
                importFile(group, ((ExtractFileTask) task).getExtractedFile(),
                        true);
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
            AbstractHierarchyListItem2
            implements Search, Visibility2, GroupDelete,
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
            return _context.getDrawable(hasOutstandingChanges()
                    ? R.drawable.ic_missionpackage_modified
                    : R.drawable.ic_menu_missionpackage);
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
                            if (!IOProviderFactory.exists(f1)
                                    || (IOProviderFactory.exists(f2) &&
                                            IOProviderFactory.lastModified(
                                                    f1) < IOProviderFactory
                                                            .lastModified(
                                                                    f2))) {
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
                                _view, MissionPackageMapOverlay.this, group));
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
            setViz(_header.findViewById(R.id.delete), View.GONE);

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
        public boolean isSupported(Class<?> target) {
            return Folder.class.equals(target)
                    || KMZFolder.class.equals(target)
                    || GPXExportWrapper.class.equals(target)
                    || OGRFeatureExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters)
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

    Folder getFolder(List<HierarchyListItem> items)
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

    GPXExportWrapper getGPX(List<HierarchyListItem> items)
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

    OGRFeatureExportWrapper getOGR(String name,
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

    void promptAddItems(final MissionPackageListGroup group) {

        View v = LayoutInflater.from(_context)
                .inflate(R.layout.include_attachment, null);
        final CheckBox attCb = v.findViewById(R.id.include_attachment);
        attCb.setChecked(_userState.isIncludeAttachments());

        TileButtonDialog d = new TileButtonDialog(_view);
        d.setTitle(R.string.select_items);
        d.setCustomView(v);
        d.addButton(R.drawable.select_from_map, R.string.map_select);
        d.addButton(R.drawable.ic_menu_import_file, R.string.file_select);
        d.addButton(R.drawable.select_from_overlay, R.string.overlay_title);

        final List<URIContentProvider> providers = URIContentManager
                .getInstance().getProviders("Data Package");
        for (URIContentProvider provider : providers)
            d.addButton(provider.getIcon(), provider.getName());

        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which < 0)
                    return;

                _userState.setIncludeAttachments(attCb.isChecked());

                // Select from map
                if (which == 0)
                    startMapSelectTool(_context, group != null
                            ? group.getManifest()
                            : null);

                // Select from file browser
                else if (which == 1)
                    addFiles(group);

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
                else if (which - 3 < providers.size()) {
                    URIContentProvider provider = providers.get(which - 3);
                    provider.addContent("Data Package", new Bundle(),
                            new URIContentProvider.Callback() {
                                @Override
                                public void onAddContent(URIContentProvider p,
                                        List<String> uris) {
                                    addURIs(group, uris);
                                }
                            });
                }
            }
        });

        d.show(true);
    }

    private void addURIs(MissionPackageListGroup group, List<String> uris) {
        MissionPackageExportWrapper w = new MissionPackageExportWrapper();
        for (String uri : uris) {
            MapItem mi = URIHelper.getMapItem(_view, uri);
            File f = URIHelper.getFile(uri);
            if (mi != null)
                w.addUID(mi.getUID());
            else if (f != null)
                w.addFile(f);
            else
                Log.w(TAG, "Ignoring unsupported URI: " + uri);
        }
        MissionPackageExportMarshal marshal = new MissionPackageExportMarshal(
                _context, _userState.isIncludeAttachments());
        if (group != null)
            marshal.setMissionPackageUID(group.getManifest().getUID());
        try {
            List<Exportable> exports = new ArrayList<>();
            exports.add(w);
            marshal.execute(exports);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add exportables", e);
        }
    }

    private void addFiles(final MissionPackageListGroup group) {
        final ImportManagerFileBrowser importView = ImportManagerFileBrowser
                .inflate(_view);

        importView.setTitle(R.string.select_files_to_import);
        importView.setStartDirectory(
                ATAKUtilities.getStartDirectory(_view.getContext()));

        importView.setExtensionTypes(new String[] {
                "*"
        });
        importView.allowDirectorySelect(false);
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

                    _prefs.edit().putString("lastDirectory",
                            importView.getCurrentPath())
                            .apply();
                }
            }
        });
        final AlertDialog alert = b.create();

        // Show the dialog
        alert.show();

        AlertDialogHelper.adjustWidth(alert, 0.90d);
    }

    void deletePackage(MissionPackageListGroup group, boolean toast,
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
                && IOProviderFactory
                        .length(src) > SMALLMISSIONPACKAGE_SIZE_INBYTES)
            new DeleteFileTask(mpm, _receiver, null).execute();
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
                        CoordinateFormat cf = CoordinateFormat.find(
                                _prefs.getString("coord_display_pref", _context
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
                                    _receiver, item.getContent(),
                                    MissionPackageMapOverlay.this).execute();
                        else
                            new ExtractMapItemTask(group.getManifest(),
                                    _receiver, item.getContent(),
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

    static void startMapSelectTool(Context context,
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
