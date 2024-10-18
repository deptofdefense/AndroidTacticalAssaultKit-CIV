
package com.atakmap.android.importfiles.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.resource.RemoteResources;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.KMLUtil;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Map overlay for remote resources
 * TODO: This could use a rewrite for 3.13 similar to the video alias rewrite
 *  It's a confusing rat's nest of intents and tasks which could be simplified
 *  All this needs to do is download a file given some resource info,
 *  doesn't need to be so damn complicated...
 */
public class ImportManagerMapOverlay extends AbstractMapOverlay2
        implements ATAKActivity.OnShutDownListener, Delete {

    private final static String TAG = "ImportManagerMapOverlay";

    private static final int ORDER = 8;

    private final ImportExportMapComponent _component;
    private final MapView _view;
    private final Context _context;
    private boolean _firstLoad = true;
    private final Persister _serializer = new Persister();
    private final Map<String, RemoteResource> _resources = new HashMap<>();

    public ImportManagerMapOverlay(MapView view,
            ImportExportMapComponent component) {
        _component = component;
        _view = view;
        _context = view.getContext();
        ((ATAKActivity) _context).addOnShutDownListener(this);
    }

    @Override
    public String getIdentifier() {
        return ImportManagerMapOverlay.class.getName();
    }

    @Override
    public String getName() {
        return _context.getString(R.string.importmgr_remote_resource_plural);
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
        return new RemoteResourcesOverlayListModel(adapter, prefFilter);
    }

    private void refresh() {
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.REFRESH_HIERARCHY));
    }

    @Override
    public boolean delete() {
        // This is implemented simply to get HierarchyListUserDelete to
        // not filter out this overlay
        return false;
    }

    public class RemoteResourcesOverlayListModel extends
            AbstractHierarchyListItem2 implements Search, GroupDelete,
            View.OnClickListener {

        private final static String TAG = "RemoteResourcesOverlayListModel";

        private View _header;

        public RemoteResourcesOverlayListModel(BaseAdapter listener,
                HierarchyListFilter filter) {
            this.asyncRefresh = true;
            refresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return ImportManagerMapOverlay.this.getName();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_menu_import_file);
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
        public Object getUserObject() {
            return ImportManagerMapOverlay.this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public View getHeaderView() {
            if (_header == null) {
                _header = LayoutInflater.from(_context).inflate(
                        R.layout.importmgr_resource_header, _view, false);
                _header.findViewById(R.id.add_resource_button)
                        .setOnClickListener(this);
            }
            return _header;
        }

        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.add_resource_button) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ImportExportMapComponent.USER_IMPORT_FILE_ACTION));
            }
        }

        @Override
        public void refreshImpl() {
            // Filter
            List<HierarchyListItem> filtered = new ArrayList<>();
            List<RemoteResource> resources = getResources();
            for (RemoteResource res : resources) {
                if (res.getParent() != null)
                    continue;
                RemoteResourceListItem item = new RemoteResourceListItem(_view,
                        ImportManagerMapOverlay.this, res);
                if (this.filter.accept(item)) {
                    item.syncRefresh(this.listener, this.filter);
                    filtered.add(item);
                }
            }

            // Sort
            sortItems(filtered);

            // Update
            updateChildren(filtered);
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        // Search
        @Override
        public Set<HierarchyListItem> find(String terms) {
            Set<String> found = new HashSet<>();
            Set<HierarchyListItem> retval = new HashSet<>();
            terms = terms.toLowerCase(LocaleUtil.getCurrent());

            List<HierarchyListItem> children = getChildren();
            if (FileSystemUtils.isEmpty(children)) {
                Log.d(TAG, "No remote resources to search");
                return retval;
            }
            for (HierarchyListItem item : children) {
                if (!(item instanceof RemoteResourceListItem))
                    continue;
                RemoteResourceListItem ri = (RemoteResourceListItem) item;
                if (!ri.isValid()) {
                    Log.w(TAG, "Skipping invalid remote resource");
                    continue;
                }

                if (!found.contains(item.getUID()) &&
                        (find(ri.getTitle(), terms)
                                || find(ri.getURL(), terms)
                                || find(ri.getType(), terms))) {
                    retval.add(ri);
                    found.add(ri.getUID());
                }
            }

            return retval;
        }

        private boolean find(String str, String terms) {
            return str.toLowerCase(LocaleUtil.getCurrent()).contains(terms);
        }
    }

    public List<RemoteResource> getResources() {
        if (_firstLoad) {
            List<RemoteResource> res = findResources();
            synchronized (_resources) {
                for (RemoteResource r : res) {
                    _resources.put(r.getName(), r);
                }
            }
        }
        synchronized (_resources) {
            return new ArrayList<>(_resources.values());
        }
    }

    public RemoteResource getResource(String name) {
        synchronized (_resources) {
            return _resources.get(name);
        }
    }

    public List<RemoteResource> findResources() {
        List<RemoteResource> ret = new ArrayList<>();
        String[] dirs = FileSystemUtils.findMountPoints();
        for (String dir : dirs)
            ret.addAll(findResources(dir));
        _firstLoad = false;
        return ret;
    }

    public List<RemoteResource> findResources(String dir) {
        List<RemoteResource> ret = new ArrayList<>();
        boolean bInternal = isInternal(dir);
        File resourceFile = new File(dir, ImportManagerView.XML_FILEPATH);
        if (!IOProviderFactory.exists(resourceFile)) {
            Log.d(TAG,
                    "Did not find resource file: "
                            + resourceFile.getAbsolutePath());
            return ret;
        }

        RemoteResources resources = RemoteResources.load(
                resourceFile, _serializer);
        if (resources == null) {
            Log.w(TAG,
                    "Failed to load resources: "
                            + resourceFile.getAbsolutePath());
            return ret;
        }

        if (resources.getResources() == null
                || resources.getResources().size() < 1) {
            Log.d(TAG,
                    "Found no configured resources: "
                            + resourceFile.getAbsolutePath());
            return ret;
        }

        // loop all resources
        for (RemoteResource resource : resources.getResources()) {
            if (_firstLoad && resource
                    .getRefreshSeconds() >= KMLUtil.MIN_NETWORKLINK_INTERVAL_SECS) {
                Log.d(TAG, "Beginning auto refresh: " + resource);
                // check auto-refresh setting
                _component.download(resource, false);
            }

            if (bInternal)
                resource.setSource(RemoteResource.Source.LOCAL_STORAGE);
            else
                resource.setSource(RemoteResource.Source.EXTERNAL);

            ret.add(resource);
        }
        return ret;
    }

    /**
     * Flush the current resource cache to disk
     * @param dir The root path to flush to
     * @param onlyIfExists True to flush only if the file exists
     */
    private void flush(String dir, boolean onlyIfExists) {
        boolean bInternal = isInternal(dir);
        File resourceFile = new File(dir, ImportManagerView.XML_FILEPATH);
        if (onlyIfExists && !IOProviderFactory.exists(resourceFile))
            return;

        RemoteResources rs = new RemoteResources();
        List<RemoteResource> resources = getResources();
        for (RemoteResource r : resources) {
            if (bInternal
                    && r.getSource() == RemoteResource.Source.LOCAL_STORAGE)
                rs.add(r);
            else if (!bInternal
                    && r.getSource() == RemoteResource.Source.EXTERNAL)
                rs.add(r);
        }

        rs.save(resourceFile, _serializer);
    }

    private void flush(boolean onlyIfExists) {
        String[] dirs = FileSystemUtils.findMountPoints();
        for (String dir : dirs)
            flush(dir, onlyIfExists);
    }

    public void flush() {
        flush(false);
    }

    @Override
    public void onShutDown() {
        Log.d(TAG, "Cleaning up Remote Resources based on configuration");
        boolean bFlush = false;
        synchronized (_resources) {
            for (RemoteResource resource : _resources.values()) {
                if (resource.isDeleteOnExit()) {
                    deleteResourceContent(resource);
                    bFlush = true;
                }
            }

            // now that resources/children are cleaned up, flush out updated XML
            if (bFlush)
                flush(true);

            _resources.clear();
        }
    }

    /**
     * Bulk add/update resources
     * @param resources List of remote resource definitions
     * @param update True to update existing resources
     * @return True if all resources were added successfully
     */
    public boolean addResources(List<RemoteResource> resources,
            boolean update) {
        boolean ret = true;
        for (RemoteResource res : resources)
            ret &= addResourceNoRefresh(res, update);
        flush();
        refresh();
        return ret;
    }

    /**
     * Prevents double adding, causes adapter to be redrawn
     */
    public boolean addResource(RemoteResource resource, boolean update) {
        if (!addResourceNoRefresh(resource, update))
            return false;

        flush();
        refresh();
        // Log.d(TAG, "Adding resource to UI: " + resource.toString());
        return true;
    }

    public boolean addResource(RemoteResource resource) {
        return addResource(resource, true);
    }

    private boolean addResourceNoRefresh(RemoteResource resource,
            boolean update) {
        if (resource == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return false;
        }

        if (!resource.isValid()) {
            Log.w(TAG, "Skipping invalid resource: " + resource);
            return false;
        }

        synchronized (_resources) {
            if (!FileSystemUtils.isEmpty(resource.getMd5())
                    && !FileSystemUtils.isEmpty(resource.getLocalPath())) {
                // Overwrite duplicate resources with newest path
                for (RemoteResource res : _resources.values()) {
                    if (!FileSystemUtils.isEmpty(res.getMd5())
                            && !FileSystemUtils.isEmpty(res.getLocalPath())
                            && res.getMd5().equals(resource.getMd5())
                            && !res.getLocalPath().equals(
                                    resource.getLocalPath())) {
                        deleteResourceContent(res);
                        res.setLocalPath(resource.getLocalPath());
                        Log.w(TAG, "Overwriting " + res.getLocalPath()
                                + " with " + resource.getLocalPath());
                    }
                }
            }
            boolean overwrite = true;
            if (!update && _resources.containsKey(resource.getName())) {
                // Rename new resource so we don't overwrite
                RemoteResource existing = _resources.get(resource.getName());
                if (existing.isValid()) {
                    if (!resource.getUrl().equals(existing.getUrl())) {
                        resource.setName(ATAKUtilities.getNonDuplicateName(
                                resource.getName(),
                                new ArrayList<>(_resources.keySet()),
                                ".#"));
                    } else {
                        // Only do a soft update if URLs match
                        existing.setRefreshSeconds(
                                resource.getRefreshSeconds());
                        existing.setDeleteOnExit(resource.isDeleteOnExit());
                        overwrite = false;
                    }
                }
            }
            if (overwrite)
                _resources.put(resource.getName(), resource);
        }

        return true;
    }

    public void delete(RemoteResource resource, boolean bContentOnly) {
        Log.d(TAG,
                "Deleting resource"
                        + (bContentOnly ? ": " : " and configuration:")
                        + resource.toString());

        deleteResourceContent(resource);

        // if KML being auto-refreshed, turn it off so file is not re-created
        ImportExportMapComponent.getInstance().refreshNetworkLink(
                resource, true);

        if (!bContentOnly) {
            RemoteResource parent = resource.getParent();
            if (parent != null)
                parent.removeChild(resource);
            // remove from configuration
            synchronized (_resources) {
                _resources.remove(resource.getName());
            }
        }

        flush();
        refresh();
    }

    private void deleteResourceContent(RemoteResource resource) {
        if (FileSystemUtils.isEmpty(resource.getLocalPath()))
            return;

        File resFile = new File(resource.getLocalPath());
        if (FileSystemUtils.isFile(resFile)) {
            if (FileSystemUtils.checkExtension(resFile, "zip")
                    && resFile.getParentFile() != null
                    && resFile.getParentFile().getName()
                            .equals("datapackage")) {
                MissionPackageFileIO.deletePackage(resFile.getAbsolutePath(),
                        _view.getRootGroup());
                return;
            } else
                FileSystemUtils.deleteFile(resFile);
        } else
            Log.d(TAG, "Skipping delete of missing file for resource: "
                    + resource);

        for (RemoteResource child : resource.getChildren())
            deleteResourceContent(child);

        // clear child resources
        resource.clearChildren();

        URIContentHandler h = URIContentManager.getInstance()
                .getHandler(resFile);
        if (h != null)
            h.deleteContent();
        else
            ImportReceiver.remove(Uri.fromFile(resFile), resource.getType(),
                    MIMETypeMapper.GetContentType(resFile));
    }

    public void replace(RemoteResource resource, RemoteResource newrr) {
        if (resource == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return;
        }
        if (!resource.isValid()) {
            Log.w(TAG, "Skipping replacement resource: " + resource);
            return;
        }

        if (newrr == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return;
        }
        if (!newrr.isValid()) {
            Log.w(TAG, "Skipping replacing resource: " + newrr);
            return;
        }

        if (_resources.containsKey(resource.getName())) {
            // found the old one, lets insert new one
            _resources.remove(resource.getName());
            _resources.put(newrr.getName(), newrr);

            refresh();
            Log.d(TAG, "Replacing resource: " + resource + " with: "
                    + newrr);
        } else {
            Log.w(TAG, "Failed to replace resource: " + resource);
        }
    }

    public static boolean isInternal(String dir) {
        return dir.contentEquals(FileSystemUtils.getRoot().getPath());
    }

    public static ImportManagerMapOverlay getOverlay(MapView mapView) {
        MapOverlay o = mapView.getMapOverlayManager().getOverlay(
                ImportManagerMapOverlay.class.getName());
        if (o instanceof ImportManagerMapOverlay)
            return (ImportManagerMapOverlay) o;
        return null;
    }
}
