
package com.atakmap.android.importexport.send;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentSender;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.event.MissionPackageShapefileHandler;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Send dialog that can be used for, map items, files, and Mission Packages
 */
public class SendDialog {

    private static final String TAG = "SendDialog";

    private final MapView _mapView;
    private final Context _context;
    private final String _name;
    private final Drawable _icon;
    private final URIContentSender.Callback _callback;

    private String _uri;
    private MissionPackageManifest _manifest;
    private MissionPackageBaseTask.Callback _mpCallback;
    private File _file;

    private SendDialog(MapView mapView, Drawable icon, String name,
            URIContentSender.Callback cb) {
        _mapView = mapView;
        _context = mapView.getContext();
        _icon = icon;
        _name = name;
        _callback = cb;
    }

    private SendDialog(MapView mapView, Drawable icon,
            MissionPackageManifest mpm, MissionPackageBaseTask.Callback mpCb,
            URIContentSender.Callback cb) {
        this(mapView, icon, mpm.getName(), cb);
        _manifest = mpm;
        _mpCallback = mpCb;
    }

    private SendDialog(MapView mapView, Drawable icon, String name, File file,
            URIContentSender.Callback cb) {
        this(mapView, icon, name != null ? name : file.getName(), cb);
        _file = file;
    }

    private SendDialog(MapView mapView, Drawable icon, String name,
            String uri, URIContentSender.Callback cb) {
        this(mapView, icon, name, cb);
        _uri = uri;
    }

    public void show() {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return;

        // Determine whether we're sending a single file or a Mission Package
        String uri = _uri;
        if (_manifest != null && _manifest.isValid())
            uri = URIHelper.getURI(_manifest);
        else if (_file != null)
            uri = URIHelper.getURI(_file);
        show(_name, uri);
    }

    private void show(String name, final String uri) {
        final List<URIContentSender> senders = URIContentManager.getInstance()
                .getSenders(uri);

        // Move "Choose App..." function to the bottom always
        URIContentSender tps = null;
        for (URIContentSender s : senders) {
            if (s instanceof ThirdPartySender) {
                tps = s;
                break;
            }
        }
        if (tps != null) {
            senders.remove(tps);
            senders.add(tps);
        }

        TileButtonDialog d = new TileButtonDialog(_mapView);
        if (_icon != null)
            d.setIcon(_icon);
        for (URIContentSender s : senders)
            d.addButton(s.getIcon(), s.getName());
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which >= 0 && which < senders.size())
                    send(uri, senders.get(which));
            }
        });
        d.show(_context.getString(R.string.send) + " " + name, null, true);
    }

    private void send(String uri, URIContentSender s) {

        Log.d(TAG, "sending: " + uri + " " + s);
        if (s == null)
            return;
        if (_manifest != null && s instanceof MissionPackageSender)
            ((MissionPackageSender) s).sendMissionPackage(
                    _manifest, _mpCallback, _callback);
        else
            s.sendContent(uri, _callback);
    }

    public static class Builder {

        private final MapView _mapView;

        private String _name;
        private Drawable _icon;
        private String _uri;
        private URIContentSender.Callback _callback;

        // Mission Package specific
        private final Set<ResourceFile> _files = new HashSet<>();
        private final Set<String> _items = new HashSet<>();
        private MissionPackageManifest _manifest;
        private boolean _importOnReceive = true;
        private boolean _deleteOnReceive = true;
        private String _onReceiveAction;
        private MissionPackageBaseTask.Callback _mpCallback;

        public Builder(MapView mapView) {
            _mapView = mapView;
        }

        public Builder setName(String name) {
            _name = name;
            return this;
        }

        public Builder setIcon(Drawable icon) {
            _icon = icon;
            return this;
        }

        public Builder setIcon(int iconId) {
            _icon = _mapView.getContext().getDrawable(iconId);
            return this;
        }

        public Builder setURI(String uri) {
            _uri = uri;
            return this;
        }

        public Builder setMissionPackage(MissionPackageManifest mpm) {
            _manifest = mpm;
            return this;
        }

        public Builder setMissionPackageCallback(
                MissionPackageBaseTask.Callback cb) {
            _mpCallback = cb;
            return this;
        }

        public Builder setImportOnReceive(boolean importOnReceive) {
            _importOnReceive = importOnReceive;
            return this;
        }

        public Builder setDeleteOnReceive(boolean deleteOnReceive) {
            _deleteOnReceive = deleteOnReceive;
            return this;
        }

        public Builder setOnReceiveAction(String intentAction) {
            _onReceiveAction = intentAction;
            return this;
        }

        public Builder setCallback(URIContentSender.Callback callback) {
            _callback = callback;
            return this;
        }

        public Builder addFile(ResourceFile file) {
            _files.add(file);
            return this;
        }

        public Builder addFile(File file, String contentType) {
            return addFile(new ResourceFile(file.getAbsolutePath(),
                    contentType));
        }

        public Builder addFile(File file) {
            return addFile(file, null);
        }

        public Builder addMapItem(String uid) {
            _items.add(uid);
            return this;
        }

        public Builder addMapItem(MapItem item) {
            return addMapItem(item.getUID());
        }

        public SendDialog build() {

            // If there's a URI specified then ignore everything else
            if (_uri != null)
                return new SendDialog(_mapView, _icon, _name, _uri, _callback);

            // Check if we're only attempting to send a single file
            File singleFile = null;
            if (_manifest == null && _files.size() == 1 && _items.isEmpty()) {
                ResourceFile rf = _files.iterator().next();
                if (rf != null)
                    singleFile = new File(rf.getFilePath());
            }

            // Create manifest and set import instructions
            if (_manifest == null && singleFile == null)
                _manifest = MissionPackageApi.CreateTempManifest(_name,
                        _importOnReceive, _deleteOnReceive, _onReceiveAction);

            // Add files and map items
            if (_manifest != null) {
                for (ResourceFile rf : _files)
                    addFile(_manifest, rf);
                for (String uid : _items)
                    _manifest.addMapItem(uid);
            }

            if (_manifest == null) {
                return new SendDialog(_mapView, _icon, _name, singleFile,
                        _callback);
            } else
                return new SendDialog(_mapView, _icon, _manifest, _mpCallback,
                        _callback);
        }

        public SendDialog show() {
            SendDialog d = build();
            d.show();
            return d;
        }

        private void addFile(MissionPackageManifest mpm, ResourceFile rf) {
            String path = rf.getFilePath();
            if (FileSystemUtils.isEmpty(path))
                return;

            File f = new File(path);
            if (!IOProviderFactory.exists(f) || !IOProviderFactory.isFile(f))
                return;

            // Special case for SHP file
            boolean shp = ShapefileSpatialDb.SHP_CONTENT_TYPE.equalsIgnoreCase(
                    rf.getContentType())
                    && f.getName().toLowerCase(
                            LocaleUtil.getCurrent()).endsWith(
                                    ShapefileSpatialDb.SHP_TYPE);
            if (shp && !MissionPackageShapefileHandler.add(
                    _mapView.getContext(), mpm, f)) {
                Log.w(TAG, "Unable to add shapefile: " + f);
            } else if (!mpm.addFile(f, null))
                Log.w(TAG, "Unable to add file: " + f);
        }
    }
}
