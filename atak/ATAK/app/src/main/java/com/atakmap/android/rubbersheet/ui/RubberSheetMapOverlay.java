
package com.atakmap.android.rubbersheet.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.rubbersheet.data.RubberImageData;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.create.AbstractCreationTask;
import com.atakmap.android.rubbersheet.data.create.CreateRubberImageTask;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rubber Sheet overlay listing
 * Used to manage rubber sheet items
 */
public class RubberSheetMapOverlay extends AbstractMapOverlay2 implements
        MapGroup.OnItemListChangedListener,
        AbstractCreationTask.Callback, AbstractSheet.OnLoadListener {

    private static final String TAG = "RubberSheetMapOverlay";

    private static final int ORDER = 6;

    public static final Set<String> EXTS = new HashSet<>();

    static {
        EXTS.addAll(RubberImageData.EXTS);
        EXTS.addAll(RubberModelData.EXTS);
    }

    private final MapView _mapView;
    private final Context _context;
    private final RubberSheetMapGroup _group;
    private final Map<File, List<AbstractSheet>> _fileMap = new HashMap<>();

    private boolean _vizSupported = false;
    private RubberSheetListModel _listModel;
    private HierarchyListAdapter _om;

    public RubberSheetMapOverlay(MapView view, RubberSheetMapGroup group) {
        _mapView = view;
        _context = view.getContext();
        _group = group;
        _group.addOnItemListChangedListener(this);
        _mapView.getMapOverlayManager().addOverlay(this);
    }

    public void dispose() {
        _group.removeOnItemListChangedListener(this);
        _mapView.getMapOverlayManager().removeOverlay(this);
    }

    public String getIdentifier() {
        return _group.getFriendlyName();
    }

    @Override
    public String getName() {
        return _group.getFriendlyName();
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
            long capabilities, HierarchyListFilter prefFilter) {
        if (adapter instanceof HierarchyListAdapter)
            _om = (HierarchyListAdapter) adapter;
        if (_listModel == null)
            _listModel = new RubberSheetListModel();
        _listModel.refresh(adapter, prefFilter);
        return _listModel;
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        if (item instanceof AbstractSheet) {
            AbstractSheet sheet = (AbstractSheet) item;
            sheet.addLoadListener(this);

            File file = sheet.getFile();
            synchronized (_fileMap) {
                List<AbstractSheet> sheets = _fileMap.get(file);
                if (sheets == null)
                    _fileMap.put(file, sheets = new ArrayList<>());
                sheets.add(sheet);
            }

            refresh();
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item instanceof AbstractSheet) {
            AbstractSheet sheet = (AbstractSheet) item;
            sheet.removeLoadListener(this);

            File file = sheet.getFile();
            synchronized (_fileMap) {
                List<AbstractSheet> sheets = _fileMap.get(file);
                if (sheets != null) {
                    sheets.remove(sheet);
                    if (sheets.isEmpty())
                        _fileMap.remove(file);
                }
            }

            refresh();
        }
    }

    @Override
    public void onLoadStateChanged(AbstractSheet sheet, LoadState ls) {
        refresh();
    }

    @Override
    public void onLoadProgress(AbstractSheet sheet, int progress) {
        if (_om != null && _om.isActive())
            _om.notifyDataSetChanged();
    }

    private void refresh() {
        if (_om != null && _om.isActive())
            _om.refreshList();
    }

    private class RubberSheetListModel extends AbstractHierarchyListItem2
            implements HierarchyListStateListener, Search, Visibility2, Delete,
            View.OnClickListener {

        private final static String TAG = "RubberSheetListModel";

        private final View _header;
        private final Map<File, SheetGroupListItem> _groupMap;

        private RubberSheetListModel() {
            this.asyncRefresh = true;
            _groupMap = new HashMap<>();
            _header = LayoutInflater.from(_context).inflate(
                    R.layout.rs_list_header, _mapView, false);
            _header.findViewById(R.id.import_file).setOnClickListener(this);
        }

        @Override
        public String getTitle() {
            return _group.getFriendlyName();
        }

        @Override
        public String getIconUri() {
            return "android.resource://" + _context.getPackageName()
                    + "/" + R.drawable.ic_rubber_sheet;
        }

        public int getPreferredListIndex() {
            return ORDER;
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Object getUserObject() {
            return RubberSheetListModel.this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public View getHeaderView() {
            return _header;
        }

        @Override
        public boolean onOpenList(HierarchyListAdapter om) {
            HintDialogHelper.showHint(_context,
                    _context.getString(R.string.app_name),
                    "This tool allows you to create resizable imagery and 3D model overlays which can be exported to KMZ or OBJ respectively.\n\n"
                            +
                            "Tap the (+) button at the top of the list to create a new \"rubber sheet\" from a locally-stored image or model.\n\n",
                    "rubbersheet.listhint");
            return false;
        }

        @Override
        public void refreshImpl() {
            List<HierarchyListItem> filtered = new ArrayList<>();
            boolean vizSupported = false;

            Map<File, List<AbstractSheet>> fMap;
            synchronized (_fileMap) {
                fMap = new HashMap<>(_fileMap);
            }
            Map<File, SheetGroupListItem> gMap;
            synchronized (_groupMap) {
                gMap = new HashMap<>();
                for (Map.Entry<File, List<AbstractSheet>> e : fMap.entrySet()) {
                    File file = e.getKey();
                    SheetGroupListItem group = _groupMap.get(file);
                    if (group == null)
                        gMap.put(file, new SheetGroupListItem(_mapView, file));
                    else
                        gMap.put(file, group);
                }
                _groupMap.clear();
                _groupMap.putAll(gMap);
            }

            for (Map.Entry<File, List<AbstractSheet>> e : fMap.entrySet()) {
                List<AbstractSheet> sheets = e.getValue();
                SheetGroupListItem group = gMap.get(e.getKey());
                List<AbstractSheetHierarchyListItem> groupItems = new ArrayList<>();
                boolean addGroup = sheets.size() > 1;
                for (AbstractSheet sheet : sheets) {
                    AbstractSheetHierarchyListItem item;
                    if (sheet instanceof RubberImage)
                        item = new RubberImageHierarchyListItem(_mapView,
                                (RubberImage) sheet);
                    else if (sheet instanceof RubberModel)
                        item = new RubberModelHierarchyListItem(_mapView,
                                (RubberModel) sheet);
                    else
                        continue;
                    if (this.filter.accept(item)) {
                        if (addGroup)
                            groupItems.add(item);
                        else
                            filtered.add(item);
                        vizSupported = true;
                    }
                }
                if (addGroup)
                    filtered.add(group);
                group.syncRefresh(this.listener, this.filter, groupItems);
            }

            // Sort
            sortItems(filtered);

            // Update
            _vizSupported = vizSupported;
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
            disposeChildren();
        }

        @Override
        protected void disposeChildren() {
            synchronized (this.children) {
                this.children.clear();
            }
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public boolean isMultiSelectSupported() {
            return true;
        }

        /**
         * ******************************************************************
         */

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.import_file)
                openFileBrowser();
        }
    }

    private void importFile(final File f) {
        if (RubberImageData.isSupported(f))
            new CreateRubberImageTask(_mapView, new RubberImageData(f),
                    false, this).execute();
        else
            new RubberModelData.SupportTask(_mapView, f, this).execute();
    }

    @Override
    public void onFinished(AbstractCreationTask task,
            List<AbstractSheet> sheets) {
        if (!sheets.isEmpty()) {
            Envelope.Builder eb = new Envelope.Builder();
            MutableGeoBounds m = new MutableGeoBounds(0, 0, 0, 0);
            double maxAlt = -Double.MAX_VALUE;
            for (AbstractSheet s : sheets) {
                if (s.getGroup() == null)
                    _group.add(s);
                s.getBounds(m);
                GeoPoint p = s.getCenterPoint();
                if (p.isAltitudeValid())
                    maxAlt = Math.max(maxAlt, p.getAltitude() + s.getHeight());
                eb.add(m.getEast(), m.getNorth());
                eb.add(m.getWest(), m.getSouth());
            }
            Envelope e = eb.build();
            m.set(e.minY, e.minX, e.maxY, e.maxX);
            if (!task.isBackground())
                ATAKUtilities.scaleToFit(_mapView, m, maxAlt,
                        _mapView.getWidth(), _mapView.getHeight());
        } else {
            Toast.makeText(_context, task.getFailMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openFileBrowser() {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        final String lastDirectory = prefs.getString("lastDirectory",
                FileSystemUtils.getRoot().getAbsolutePath());

        final ImportManagerFileBrowser v = ImportManagerFileBrowser
                .inflate(_mapView);
        v.setMultiSelect(false);
        v.setStartDirectory(lastDirectory);
        v.setExtensionTypes(EXTS.toArray(new String[0]));

        AlertDialog.Builder adb = new AlertDialog.Builder(_context);
        adb.setView(v);
        adb.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File file = v.getReturnFile();
                        if (!FileSystemUtils.isFile(file)) {
                            Log.d(TAG, "No file selected for import");
                            return;
                        }
                        prefs.edit().putString("lastDirectory",
                                v.getCurrentPath()).apply();
                        importFile(file);
                    }
                });
        final AlertDialog alert = adb.create();
        v.setAlertDialog(alert);
        alert.show();
    }
}
