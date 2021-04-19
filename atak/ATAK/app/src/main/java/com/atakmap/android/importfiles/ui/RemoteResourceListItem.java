package com.atakmap.android.importfiles.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class RemoteResourceListItem extends AbstractHierarchyListItem2
        implements Delete, View.OnClickListener {

    private static final String TAG = "RemoteResourceListItem";

    private final MapView _view;
    private final Context _context;
    private final SharedPreferences _prefs;
    private final ImportManagerMapOverlay _overlay;
    private final RemoteResource _resource;

    RemoteResourceListItem(MapView view, ImportManagerMapOverlay overlay,
            RemoteResource resource) {
        _view = view;
        _context = view.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _overlay = overlay;
        _resource = resource;
        this.asyncRefresh = true;
        setLocalData("showLocation", false);
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
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public boolean isChildSupported() {
        return _resource.hasChildren();
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    @Override
    public String getDescription() {
        String type = _resource.getType();
        if (type == null)
            type = "OTHER";
        int count = getChildCount();
        if (count > 0)
            return _context.getString(R.string.kml_link_desc, type, count);
        else
            return type;
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
                ? (ExtraHolder) v.getTag() : null;
        if (h == null) {
            h = new ExtraHolder();
            v = LayoutInflater.from(_context).inflate(
                    R.layout.importmgr_resource, parent, false);
            h.download = v.findViewById(
                    R.id.importmgr_resource_btnDownloadRefresh);
            h.edit = v.findViewById(R.id.importmgr_resource_btnEdit);
            h.share = v.findViewById(R.id.importmgr_resource_btnShare);
            h.delete = v.findViewById(R.id.importmgr_resource_btnDelete);
            v.setTag(h);
        }
        h.download.setOnClickListener(this);
        h.edit.setOnClickListener(this);
        h.share.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        return v;
    }

    private static class ExtraHolder {
        ImageButton download, edit, share, delete;
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
                            ImportExportMapComponent.getInstance()
                                    .download(_resource, true);
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
            try(OutputStream os = IOProviderFactory.
                    getOutputStream(cotFile)) {
                FileSystemUtils.write(os, event.toString());
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
                            _overlay.delete(_resource, true);
                        }
                    });
            b.setPositiveButton(
                    R.string.importmgr_content_and_configuration,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _overlay.delete(_resource, false);
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

    @Override
    protected void refreshImpl() {
        List<RemoteResource> resources = _resource.getChildren();

        // Filter
        List<HierarchyListItem> filtered = new ArrayList<>();
        for (RemoteResource res : resources) {
            RemoteResourceListItem item = new RemoteResourceListItem(_view,
                    _overlay, res);
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

    /**************************************************************************/

    @Override
    public boolean delete() {
        _overlay.delete(_resource, false);
        return true;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        URIContentHandler handler = getHandler();
        if (clazz.isInstance(handler) && handler.isActionSupported(clazz))
            return clazz.cast(handler);
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

    private URIContentHandler getHandler() {
        String path = _resource.getLocalPath();
        if (!FileSystemUtils.isEmpty(path))
            return URIContentManager.getInstance().getHandler(
                    new File(FileSystemUtils
                            .sanitizeWithSpacesAndSlashes(path)));
        return null;
    }
}
