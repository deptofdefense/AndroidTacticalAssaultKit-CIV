
package com.atakmap.android.elev.dt2;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.map.layer.opengl.GLLayerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Dt2OutlineMapOverlay extends AbstractMapOverlay2 implements
        Dt2FileWatcher.Listener {

    private static final String TAG = "Dt2OutlineOverlay";
    private static final String PREF_KEY = "dted_outlines_visible";
    private static final String CHAR_DEG = "\u00B0";

    private final MapView _mapView;
    private final Context _context;
    private final AtakPreferences _prefs;
    private final Dt2FileWatcher _dtedWatcher;
    private final GLDt2OutlineOverlay.Instance _outlineOverlay;
    private final Set<String> _ignorePaths = new HashSet<>();

    private ListModel _listModel;

    public Dt2OutlineMapOverlay(MapView mapView) {
        this(mapView, createDtedWatcher(mapView));
    }

    public Dt2OutlineMapOverlay(MapView mapView, Dt2FileWatcher dtedWatcher) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = new AtakPreferences(mapView);
        _mapView.getMapOverlayManager().addOverlay(this);

        _dtedWatcher = dtedWatcher;
        _dtedWatcher.addListener(this);

        GLLayerFactory.register(GLDt2OutlineOverlay.SPI);
        _outlineOverlay = new GLDt2OutlineOverlay.Instance(mapView);
        _outlineOverlay.setVisible(_prefs.get(PREF_KEY, false));
        _mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, _outlineOverlay);
    }

    public void dispose() {
        _mapView.getMapOverlayManager().removeOverlay(this);
        _dtedWatcher.removeListener(this);
        _dtedWatcher.dispose();
        _mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS,
                _outlineOverlay);
        GLLayerFactory.unregister(GLDt2OutlineOverlay.SPI);
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter filter) {
        if (_listModel == null)
            _listModel = new ListModel();
        _listModel.syncRefresh(adapter, filter);
        return _listModel;
    }

    @Override
    public String getIdentifier() {
        return "ElevationOutlines";
    }

    @Override
    public String getName() {
        return _context.getString(R.string.elevation_data);
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
    public void onDtedFilesUpdated() {
        if (_listModel != null)
            _listModel.requestRefresh();
    }

    private static int getValue(String str) {
        char h = Character.toUpperCase(str.charAt(0));
        int lastDot = str.indexOf('.');
        if (lastDot == -1)
            lastDot = str.length();
        int v = Integer.parseInt(str.substring(1, lastDot));
        if (h == 'W' || h == 'S')
            v = -v;
        return v;
    }

    private static Dt2FileWatcher createDtedWatcher(MapView mapView) {
        // Initialize DTED watcher
        List<File> rootDirs = Arrays.asList(FileSystemUtils.getItems("DTED"));
        Dt2FileWatcher dtedWatcher = new Dt2FileWatcher(mapView, rootDirs);
        dtedWatcher.start();
        return dtedWatcher;
    }

    private final Comparator<HierarchyListItem> COMP_LIST = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(HierarchyListItem o1, HierarchyListItem o2) {
            if (o1 instanceof FileItem && o2 instanceof FileItem) {
                FileItem f1 = (FileItem) o1;
                FileItem f2 = (FileItem) o2;
                int latComp = Integer.compare(f1._latitude, f2._latitude);
                if (latComp != 0)
                    return latComp;
                return Integer.compare(f1._level, f2._level);
            } else if (o1 instanceof DirectoryItem
                    && o2 instanceof DirectoryItem) {
                DirectoryItem l1 = (DirectoryItem) o1;
                DirectoryItem l2 = (DirectoryItem) o2;
                return Integer.compare(l1._longitude, l2._longitude);
            }
            return 0;
        }
    };

    private class ListModel extends AbstractHierarchyListItem2 implements
            FOVFilter.Filterable, Visibility, Delete, MapItemUser {

        private int _childCount;

        ListModel() {
            this.reusable = this.asyncRefresh = true;
        }

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public String getDescription() {
            return getChildCount() + " " + _context.getString(R.string.tiles);
        }

        @Override
        public int getChildCount() {
            return _childCount;
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_overlay_dted);
        }

        @Override
        public int getPreferredListIndex() {
            return 97;
        }

        @Override
        public Object getUserObject() {
            return null;
        }

        @Override
        protected void refreshImpl() {
            _childCount = _dtedWatcher.getFileCount();
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (int level = 0; level < 4; level++) {
                DirectoryItem item = new DirectoryItem(level, "DTED", -1,
                        listener);
                if (this.filter.accept(item)) {
                    item.syncRefresh(listener, filter);
                    filtered.add(item);
                }
            }
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public void requestRefresh() {
            super.requestRefresh();
        }

        @Override
        public boolean isVisible() {
            return _outlineOverlay.isVisible();
        }

        @Override
        public boolean setVisible(boolean visible) {
            if (visible != isVisible()) {
                _outlineOverlay.setVisible(visible);
                _prefs.set(PREF_KEY, visible);
                return true;
            }
            return false;
        }

        @Override
        public boolean delete() {
            // Not allowed to delete the entire DTED listing
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(_context,
                            R.string.delete_elevation_data_warning,
                            Toast.LENGTH_LONG).show();
                }
            });
            return true;
        }

        @Override
        public boolean accept(FOVFilter.MapState fov) {
            int minLng = (int) Math.floor(fov.westBound);
            int maxLng = (int) Math.floor(fov.eastBound);
            int minLat = (int) Math.floor(fov.southBound);
            int maxLat = (int) Math.floor(fov.northBound);
            BitSet coverage = _dtedWatcher.getFullCoverage();
            _childCount = getFilteredCount(coverage, minLat, minLng,
                    maxLat, maxLng);
            return _childCount > 0;
        }

        @Override
        public MapItem getMapItem() {
            // HACK - Avoids accepting this list in MapItemFilter
            return null;
        }
    }

    private class DirectoryItem extends AbstractHierarchyListItem2 implements
            FOVFilter.Filterable, Delete, MapItemUser {

        private final String _path;
        private final boolean _root;
        private final String _title;
        private final int _level;
        private final int _longitude;
        private GeoBounds _bounds;
        private int _childCount;

        DirectoryItem(int level, String path, int lng, BaseAdapter listener) {
            _level = level;
            _path = path;
            _longitude = lng;
            this.listener = listener;
            this.reusable = this.asyncRefresh = true;
            _root = _path.equals("DTED");
            if (_root)
                _title = "DTED" + _level;
            else {
                _title = Math.abs(lng) + CHAR_DEG + (lng < 0 ? " W" : " E");
                _bounds = new GeoBounds(-90, lng, 90, lng + 1);
                _bounds.setWrap180(true);
            }
            _childCount = _dtedWatcher.getFileCount(_level, _path);
        }

        @Override
        public String getTitle() {
            return _title;
        }

        @Override
        public String getDescription() {
            return _childCount + " " + _context.getString(R.string.tiles);
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(_title.contains("DTED")
                    ? R.drawable.ic_dted
                    : R.drawable.ic_overlay_dted);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public int getChildCount() {
            return _childCount;
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Object getUserObject() {
            return _path;
        }

        @Override
        protected void refreshImpl() {
            List<String> files = _dtedWatcher.getFiles(_level, _path);
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (String f : files) {
                if (_ignorePaths.contains(f))
                    continue;
                int value;
                try {
                    value = getValue(f.substring(f.lastIndexOf('/') + 1));
                } catch (Exception e) {
                    Log.w(TAG, "Invalid file path: " + f + " Ignoring...", e);
                    _ignorePaths.add(f);
                    continue;
                }
                HierarchyListItem item;
                if (_root)
                    item = new DirectoryItem(_level, f, value, listener);
                else
                    item = new FileItem(this, f, value);
                if (this.filter.accept(item))
                    filtered.add(item);
            }
            sortItems(filtered);
            updateChildren(filtered);
        }

        @Override
        public List<Sort> getSorts() {
            List<Sort> sorts = new ArrayList<>();
            sorts.add(new ComparatorSort(COMP_LIST,
                    _context.getString(R.string.dted),
                    ATAKUtilities.getResourceUri(_context,
                            R.drawable.ic_dted)));
            return sorts;
        }

        @Override
        public boolean accept(FOVFilter.MapState fov) {
            int minLng = (int) Math.floor(fov.westBound);
            int maxLng = (int) Math.floor(fov.eastBound);
            int minLat = (int) Math.floor(fov.southBound);
            int maxLat = (int) Math.floor(fov.northBound);
            BitSet coverage = _dtedWatcher.getCoverage(_level);
            _childCount = 0;
            if (_root) {
                _childCount = getFilteredCount(coverage, minLat, minLng,
                        maxLat, maxLng);
            } else if (_longitude >= minLng && _longitude <= maxLng) {
                _childCount = getFilteredCount(coverage, minLat, _longitude,
                        maxLat, _longitude);
            }
            return _childCount > 0;
        }

        @Override
        public boolean delete() {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    promptDelete();
                }
            });
            return true;
        }

        private void promptDelete() {
            View v = LayoutInflater.from(_context).inflate(
                    R.layout.delete_elevation_data_dialog, _mapView, false);
            TextView msg = v.findViewById(R.id.msg);
            msg.setText(_context.getString(
                    R.string.are_you_sure_delete_elevation, getTitle()));
            final Switch switch1 = v.findViewById(R.id.toggleSwitch1);
            final Switch switch2 = v.findViewById(R.id.toggleSwitch2);
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.are_you_sure);
            b.setView(v);
            b.setPositiveButton(R.string.delete, null);
            b.setNegativeButton(R.string.cancel, null);
            final AlertDialog d = b.show();
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!switch1.isChecked() || !switch2.isChecked()) {
                                Toast.makeText(_context,
                                        R.string.delete_elevation_please_lock,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            d.dismiss();
                            new DeleteDTEDTask(_mapView, _level, _path)
                                    .execute();
                        }
                    });
        }

        @Override
        public MapItem getMapItem() {
            // HACK - Avoids accepting this list in MapItemFilter
            return null;
        }
    }

    private static int getFilteredCount(BitSet coverage, int minLat, int minLng,
            int maxLat, int maxLng) {
        int childCount = 0;
        for (int lat = minLat; lat <= maxLat; lat++) {
            for (int lng = minLng; lng <= maxLng; lng++) {
                if (coverage.get(Dt2FileWatcher.getCoverageIndex(lat, lng)))
                    childCount++;
            }
        }
        return childCount;
    }

    private class FileItem extends AbstractChildlessListItem
            implements FOVFilter.Filterable, GoTo, Delete {

        private final DirectoryItem _parent;
        private final String _name, _path;
        private String _title, _type;
        private final GeoBounds _bounds;
        private final int _latitude;
        private final int _level;

        FileItem(DirectoryItem parent, String path, int latitude) {
            _parent = parent;
            _path = path;
            _latitude = latitude;

            _name = path.substring(path.lastIndexOf('/') + 1);
            _level = Integer.parseInt(_name.substring(
                    _name.lastIndexOf('.') + 3));
            GeoBounds bounds = parent._bounds;
            _bounds = new GeoBounds(_latitude, bounds.getWest(),
                    _latitude + 1, bounds.getEast());
            _bounds.setWrap180(true);
        }

        @Override
        public String getTitle() {
            if (_title == null)
                _title = Math.abs(_latitude) + CHAR_DEG
                        + (_latitude < 0 ? " S " : " N ")
                        + _parent.getTitle();
            return _title;
        }

        @Override
        public String getDescription() {
            if (_type == null) {
                _type = _name.substring(_name.lastIndexOf('.') + 1)
                        .toUpperCase().replace("DT", "DTED");
            }
            return _type;
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_overlay_dted);
        }

        @Override
        public Object getUserObject() {
            return _path;
        }

        @Override
        public boolean accept(FOVFilter.MapState fov) {
            return fov.intersects(_bounds);
        }

        @Override
        public boolean goTo(boolean select) {
            ATAKUtilities.scaleToFit(_mapView, _bounds, _mapView.getWidth(),
                    _mapView.getHeight());
            return false;
        }

        @Override
        public boolean delete() {
            return Dt2FileWatcher.getInstance().delete(_level, _path);
        }
    }

    // Asynchronous task for bulk deletion of DTED data
    private static class DeleteDTEDTask extends AsyncTask<Void, Object, Void>
            implements DialogInterface.OnCancelListener {

        private final ProgressDialog _progDialog;
        private final int _level;
        private final String _path;
        private final String _refreshMsg;

        DeleteDTEDTask(MapView mapView, int level, String path) {
            _level = level;
            _path = path;

            String dtedType = "DTED";
            if (_level != -1)
                dtedType += _level;
            Context ctx = mapView.getContext();
            _progDialog = new ProgressDialog(ctx);
            _progDialog.setTitle(
                    ctx.getString(R.string.deleting_dted_files, dtedType));
            _progDialog.setMessage(" ");
            _progDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            _progDialog.setCanceledOnTouchOutside(false);
            _progDialog.setOnCancelListener(this);
            _progDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    ctx.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _progDialog.cancel();
                        }
                    });

            // Save this string for later so AS doesn't complain about Context leaks
            _refreshMsg = ctx.getString(R.string.refreshing);
        }

        @Override
        protected void onPreExecute() {
            _progDialog.show();
        }

        @Override
        protected Void doInBackground(Void... v) {

            Dt2FileFilter dtedFilter = new Dt2FileFilter(_level);
            List<File> roots = Dt2FileWatcher.getInstance().getRootDirs();

            String path = _path;
            if (path.startsWith("DTED"))
                path = path.substring(4);
            for (File root : roots) {
                File dir = new File(root, path);
                delete(dir, dtedFilter);
            }

            if (isCancelled())
                return null;

            // Force rescan
            publishProgress(_refreshMsg, 1, 1);
            Dt2FileWatcher.getInstance().scan();

            return null;
        }

        private void delete(File dir, Dt2FileFilter filter) {
            File[] files = IOProviderFactory.listFiles(dir, filter);
            if (files == null)
                return;
            int progress = 0, total = files.length;
            publishProgress(FileSystemUtils.prettyPrint(dir), 0, total);
            for (File f : files) {
                if (isCancelled())
                    return;
                if (IOProviderFactory.isDirectory(f))
                    delete(f, filter);
                else {
                    FileSystemUtils.delete(f);
                    publishProgress(null, ++progress, total);
                }
            }

            // Remove empty directory
            files = IOProviderFactory.listFiles(dir);
            if (files != null && files.length == 0)
                FileSystemUtils.deleteDirectory(dir, false);
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            if (values[0] != null)
                _progDialog.setMessage((String) values[0]);
            _progDialog.setProgress((int) values[1]);
            _progDialog.setMax((int) values[2]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            _progDialog.dismiss();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            cancel(false);
        }
    }
}
