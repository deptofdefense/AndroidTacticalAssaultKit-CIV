
package com.atakmap.android.importfiles.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.importfiles.resource.ChildResource;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.resource.RemoteResources;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
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
        implements ATAKActivity.OnShutDownListener {

    private final static String TAG = "ImportManagerMapOverlay";

    private static final int ORDER = 8;

    private final ImportExportMapComponent _component;
    private final MapView _view;
    private final Context _context;
    private final SharedPreferences _prefs;
    private boolean _firstLoad = true;
    private Persister _serializer = new Persister();
    private final Map<String, RemoteResource> _resources = new HashMap<>();

    public ImportManagerMapOverlay(MapView view,
            ImportExportMapComponent component) {
        _component = component;
        _view = view;
        _context = view.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
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

    public class RemoteResourcesOverlayListModel extends
            AbstractHierarchyListItem2 implements Search, Delete,
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
                RemoteResourceHierarchyListItem item = new RemoteResourceHierarchyListItem(
                        res);
                if (this.filter.accept(item))
                    filtered.add(item);
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
                Log.d(TAG, "No remote resources to search");
                return retval;
            }
            for (HierarchyListItem item : children) {
                if (!(item instanceof RemoteResourceHierarchyListItem))
                    continue;
                RemoteResourceHierarchyListItem ri = (RemoteResourceHierarchyListItem) item;
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

    /**
     * HierarchyListItem packages which are being tracked
     * Partially based on MapItemHierarchyListItem
     */
    public class RemoteResourceHierarchyListItem extends
            AbstractChildlessListItem implements Delete, View.OnClickListener {

        private static final String TAG = "RemoteResourceHierarchyListItem";
        private final RemoteResource _resource;
        private final URIContentHandler _handler;

        RemoteResourceHierarchyListItem(RemoteResource resource) {
            _resource = resource;
            this.asyncRefresh = true;
            setLocalData("showLocation", false);

            String path = _resource.getLocalPath();
            if (!FileSystemUtils.isEmpty(path)) {
                _handler = URIContentManager.getInstance().getHandler(
                        new File(FileSystemUtils
                                .sanitizeWithSpacesAndSlashes(path)));
            } else
                _handler = null;
        }

        public boolean isValid() {
            return _resource != null && _resource.isValid();
        }

        @Override
        public String getTitle() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid title");
                return _context.getString(R.string.importmgr_remote_resource);
            }

            return _resource.getName();
        }

        @Override
        public String getDescription() {
            return isValid() ? _resource.getType() : super.getDescription();
        }

        @Override
        public Drawable getIconDrawable() {
            if (!isValid())
                return null;
            String localPath = _resource.getLocalPath();
            int icon = !FileSystemUtils.isEmpty(localPath)
                    && IOProviderFactory.exists(new File(localPath))
                            ? R.drawable.importmgr_status_green
                            : R.drawable.importmgr_status_red;
            return _context.getDrawable(icon);
        }

        @Override
        public Object getUserObject() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid user object");
                return null;
            }

            return _resource;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ExtraHolder();
                v = LayoutInflater.from(_context).inflate(
                        R.layout.importmgr_resource, parent, false);
                h.download = v.findViewById(
                        R.id.importmgr_resource_btnDownloadRefresh);
                h.edit = v.findViewById(
                        R.id.importmgr_resource_btnEdit);
                h.share = v.findViewById(
                        R.id.importmgr_resource_btnShare);
                h.delete = v.findViewById(
                        R.id.importmgr_resource_btnDelete);
                v.setTag(h);
            }
            h.download.setOnClickListener(this);
            h.edit.setOnClickListener(this);
            h.share.setOnClickListener(this);
            h.delete.setOnClickListener(this);
            return v;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();

            // Download resource
            if (id == R.id.importmgr_resource_btnDownloadRefresh) {
                boolean download = _resource.getRefreshSeconds() < 1;
                String buttonText = _context.getString(
                        download ? R.string.download : R.string.stream);
                String message = _context.getString(download
                        ? R.string.importmgr_download_remote_resource_to_local_device
                        : R.string.importmgr_stream_remote_resource_to_local_device,
                        _resource.getName());
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.verify_download);
                b.setMessage(message);
                b.setPositiveButton(buttonText,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Log.d(TAG, "Downloading resource: "
                                        + _resource.toString());
                                _component.download(_resource);
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
            }

            // Edit resource
            else if (id == R.id.importmgr_resource_btnEdit) {
                new AddEditResource(_view).edit(_resource);
            }

            // Share resource
            else if (id == R.id.importmgr_resource_btnShare) {
                Log.v(TAG, "Sending remote resource CoT");
                String callsign = _view.getDeviceCallsign();
                CotEvent event = _resource.toCot(callsign,
                        CotMapComponent.getLastPoint(_view, _prefs));
                File tmp = FileSystemUtils
                        .getItem(FileSystemUtils.TMP_DIRECTORY);
                if (event == null || !event.isValid()
                        || !IOProviderFactory.exists(tmp)
                                && !IOProviderFactory.mkdirs(tmp)) {
                    Log.w(TAG, "Faild to send Remote Resource CoT");
                    Toast.makeText(_context,
                            R.string.importmgr_failed_to_send_resource,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                File cotFile = new File(tmp, FileSystemUtils
                        .sanitizeFilename(getTitle() + ".cot"));
                try {
                    FileSystemUtils.write(
                            IOProviderFactory.getOutputStream(cotFile),
                            event.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write remote resource CoT", e);
                    Toast.makeText(_context,
                            R.string.importmgr_failed_to_send_resource,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Create temp manifest with the proper name
                MissionPackageManifest manifest = MissionPackageApi
                        .CreateTempManifest(getTitle(), true, true, null);
                manifest.addFile(cotFile, null);

                SendDialog.Builder b = new SendDialog.Builder(_view);
                b.setName(getTitle());
                b.setIcon(getIconDrawable());
                b.setMissionPackage(manifest);
                b.show();
            }

            // Delete resource
            else if (id == R.id.importmgr_resource_btnDelete) {
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.verify_delete);
                b.setMessage(_context.getString(
                        R.string.importmgr_delete_local_content_only_or_remove_resource_config_also,
                        _resource.getName()));
                b.setNeutralButton(R.string.importmgr_local_content_only,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                ImportManagerMapOverlay.this.delete(_resource,
                                        true);
                            }
                        });
                b.setPositiveButton(
                        R.string.importmgr_content_and_configuration,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                ImportManagerMapOverlay.this.delete(_resource,
                                        false);
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
            }
        }

        @Override
        public String getUID() {
            if (_resource == null || !_resource.isValid()) {
                Log.w(TAG, "Skipping invalid UID");
                return null;
            }
            return _resource.getUrl();
        }

        /**************************************************************************/

        @Override
        public boolean delete() {
            ImportManagerMapOverlay.this.delete(_resource, false);
            return true;
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (clazz.isInstance(_handler)
                    && _handler.isActionSupported(clazz))
                return clazz.cast(_handler);
            return super.getAction(clazz);
        }

        public String getURL() {
            if (!isValid())
                return "";
            return _resource.getUrl();
        }

        public String getType() {
            if (!isValid())
                return "";
            return _resource.getType();
        }
    }

    private static class ExtraHolder {
        ImageButton download, edit, share, delete;
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
                Log.d(TAG, "Beginning auto refresh: " + resource.toString());
                // check auto-refresh setting
                _component.download(resource);
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
     * Prevents double adding, causes adapter to be redrawn
     */
    public boolean addResource(RemoteResource resource, boolean update) {
        if (resource == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return false;
        }

        if (!resource.isValid()) {
            Log.w(TAG, "Skipping invalid resource: " + resource.toString());
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

        flush();
        refresh();
        // Log.d(TAG, "Adding resource to UI: " + resource.toString());
        return true;
    }

    public boolean addResource(RemoteResource resource) {
        return addResource(resource, true);
    }

    public void delete(RemoteResource resource, boolean bContentOnly) {
        Log.d(TAG,
                "Deleting resource"
                        + (bContentOnly ? ": " : " and configuration:")
                        + resource.toString());

        boolean stopAutoRefresh = resource.getRefreshSeconds() > 0
                && resource.isKML();

        deleteResourceContent(resource);

        // if KML being auto-refreshed, turn it off so file is not re-created
        if (stopAutoRefresh) {
            Intent intent = new Intent();
            intent.setAction(ImportExportMapComponent.KML_NETWORK_LINK_REFRESH);
            intent.putExtra("kml_networklink_filename", resource.getName());
            intent.putExtra("kml_networklink_stop", true);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        if (!bContentOnly) {
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
            if (resFile.getName().endsWith(".zip") && resFile.getParentFile()
                    .getName().equals("datapackage")) {
                MissionPackageFileIO.deletePackage(resFile.getAbsolutePath(),
                        _view.getRootGroup());
                return;
            } else
                FileSystemUtils.deleteFile(resFile);
        } else
            Log.d(TAG, "Skipping delete of missing file for resource: "
                    + resource.toString());

        for (ChildResource child : resource.getChildren()) {
            if (FileSystemUtils.isFile(child.getLocalPath())) {
                FileSystemUtils.deleteFile(new File(child.getLocalPath()));
            } else {
                Log.d(TAG,
                        "Skipping delete of missing file for child resource: "
                                + child.toString());
            }
        }

        // clear child resources
        resource.clearChildren();

        ImportReceiver.remove(Uri.fromFile(resFile), resource.getType(),
                MIMETypeMapper.GetContentType(resFile));
    }

    public void replace(RemoteResource resource,
            RemoteResource newrr) {
        if (resource == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return;
        }
        if (!resource.isValid()) {
            Log.w(TAG, "Skipping replacement resource: " + resource.toString());
            return;
        }

        if (newrr == null) {
            Log.d(TAG, "Tried to add NULL resource.  Ignoring!");
            return;
        }
        if (!newrr.isValid()) {
            Log.w(TAG, "Skipping replacing resource: " + newrr.toString());
            return;
        }

        if (_resources.containsKey(resource.getName())) {
            // found the old one, lets insert new one
            _resources.remove(resource.getName());
            _resources.put(newrr.getName(), newrr);

            refresh();
            Log.d(TAG, "Replacing resource: " + resource.toString() + " with: "
                    + newrr.toString());
        } else {
            Log.w(TAG, "Failed to replace resource: " + resource.toString());
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
