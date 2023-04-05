
package com.atakmap.android.importexport.send;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentRecipient;
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
import java.util.ArrayList;
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
    private URIContentRecipient.Callback _reCallback;

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

    private void setRecipientCallback(URIContentRecipient.Callback cb) {
        _reCallback = cb;
    }

    public void show() {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled()) {
            if (_manifest != null) { 
                AlertDialog.Builder builder = new AlertDialog.Builder(_context);
                builder.setTitle(R.string.mission_package_file_sharing_disabled);
                builder.setMessage(R.string.would_you_like_to_export_mission_pkg);
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveMissionPackage();
                    }
                });
                builder.show();
            } else { 
                Toast.makeText(_context, R.string.mission_package_file_sharing_disabled,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }


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

        // No senders (very unlikely scenario)
        if (FileSystemUtils.isEmpty(senders)) {
            Toast.makeText(_context, R.string.failed_to_send_file,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // If there's only 1 sender then automatically use it
        if (senders.size() == 1)
            selectSender(uri, senders.get(0));

        TileButtonDialog d = new TileButtonDialog(_mapView);
        if (_icon != null)
            d.setIcon(_icon);
        for (URIContentSender s : senders)
            d.addButton(s.getIcon(), s.getName());

        // add in the ability to save
        if (_manifest != null)
            d.addButton(_context.getDrawable(R.drawable.export_menu_default), _context.getString(R.string.export));

        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which >= 0 && which < senders.size())
                    selectSender(uri, senders.get(which));
                else if (which == senders.size()) {
                    saveMissionPackage();
                }

            }
        });

        d.show(_context.getString(R.string.send) + " " + name, null, true);
    }


    private void saveMissionPackage() {
        if (_manifest == null)
            return;

        final File f = new File(_manifest.getPath());
        MissionPackageMapComponent.getInstance().getFileIO().save(_manifest, new MissionPackageBaseTask.Callback() {
            @Override
            public void onMissionPackageTaskComplete(MissionPackageBaseTask task, boolean success) {
                File newFile = FileSystemUtils.getItem("export/" + f.getName());
                final DialogInterface.OnClickListener docl = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean rename = FileSystemUtils.renameTo(f, newFile);
                        if (rename) {
                            Toast.makeText(_context, "exported: " + FileSystemUtils.prettyPrint(newFile), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(_context, "failed to export: " + FileSystemUtils.prettyPrint(newFile), Toast.LENGTH_SHORT).show();

                        }
                    }
                };
                if (newFile.exists()) {
                    AlertDialog.Builder ad = new AlertDialog.Builder(_context);
                    ad.setTitle(R.string.overwrite);
                    ad.setMessage("Overwrite existing file: " + FileSystemUtils.prettyPrint(newFile));
                    ad.setPositiveButton(R.string.ok, docl);
                    ad.setNegativeButton(R.string.cancel, null);
                    ad.show();
                } else {
                    docl.onClick(null, -1);
                }
            }
        });

    }

    private void selectSender(String uri, URIContentSender s) {
        if (!send(uri, s))
            Toast.makeText(_context, R.string.failed_to_send_file,
                    Toast.LENGTH_LONG).show();
    }

    private boolean send(String uri, URIContentSender s) {

        Log.d(TAG, "sending: " + uri + " " + s);

        if (s == null)
            return false;

        // Prompt to select recipients without sending
        if (_reCallback != null) {
            if (s instanceof URIContentRecipient.Sender)
                ((URIContentRecipient.Sender) s).selectRecipients(uri,
                        _reCallback);
            else
                _reCallback.onSelectRecipients(s, uri, null);
            return true;
        }

        // Send content
        if (_manifest != null && s instanceof MissionPackageSender) {
            // Save time by invoking this method directly
            MissionPackageSender mpms = (MissionPackageSender) s;
            return mpms.sendMissionPackage(_manifest, _mpCallback, _callback);
        } else
            return s.sendContent(uri, _callback);
    }

    /**
     * Used to create a new {@link SendDialog}
     */
    public static class Builder {

        private final MapView _mapView;

        private String _name;
        private Drawable _icon;
        private String _uri;
        private URIContentSender.Callback _callback;
        private URIContentRecipient.Callback _reCallback;

        // Mission Package specific
        private final Set<ResourceFile> _files = new HashSet<>();
        private final Set<String> _items = new HashSet<>();
        private final List<Attachment> _attachments = new ArrayList<>();
        private MissionPackageManifest _manifest;
        private boolean _importOnReceive = true;
        private boolean _deleteOnReceive = true;
        private String _onReceiveAction;
        private MissionPackageBaseTask.Callback _mpCallback;

        public Builder(MapView mapView) {
            _mapView = mapView;
        }

        /**
         * Set the display name for the content being sent
         *
         * @param name Display name
         * @return Builder
         */
        public Builder setName(String name) {
            _name = name;
            return this;
        }

        /**
         * Set the display icon
         *
         * @param icon Icon drawable
         * @return Builder
         */
        public Builder setIcon(Drawable icon) {
            _icon = icon;
            return this;
        }

        /**
         * Set the display icon
         *
         * @param iconId Icon resource ID (app context)
         * @return Builder
         */
        public Builder setIcon(int iconId) {
            _icon = _mapView.getContext().getDrawable(iconId);
            return this;
        }

        /**
         * Set the base content URI (optional)
         *
         * This takes precedence over all other added content, meaning it should
         * only be used by itself (no other calls to {@link #addFile(File)}
         * or {@link #addMapItem(String)}).
         *
         * @param uri Content URI
         * @return Builder
         */
        public Builder setURI(String uri) {
            _uri = uri;
            return this;
        }

        /**
         * Set a callback that's fired once content has been sent
         *
         * @param callback Callback
         * @return Builder
         */
        public Builder setCallback(URIContentSender.Callback callback) {
            _callback = callback;
            return this;
        }

        /**
         * Set a callback that's fired when the user has selected which
         * recipients to send content to
         *
         * Note: Some senders do not support this workflow. In this case the
         * callback will be invoked with a null list.
         *
         * @param callback Recipients selected callback
         * @return Builder
         */
        public Builder setRecipientCallback(
                URIContentRecipient.Callback callback) {
            _reCallback = callback;
            return this;
        }

        /**
         * Set the base data package manifest (optional)
         *
         * Content that is added via other *add calls will automatically be
         * added to this data package.
         *
         * @param mpm Data package
         * @return Builder
         */
        public Builder setMissionPackage(MissionPackageManifest mpm) {
            _manifest = mpm;
            return this;
        }

        /**
         * Set the data package task callback
         *
         * @param cb Callback
         * @return Builder
         */
        public Builder setMissionPackageCallback(
                MissionPackageBaseTask.Callback cb) {
            _mpCallback = cb;
            return this;
        }

        /**
         * Flag that this content should be imported upon being received
         *
         * @param importOnReceive True to import on receive
         * @return Builder
         */
        public Builder setImportOnReceive(boolean importOnReceive) {
            _importOnReceive = importOnReceive;
            return this;
        }

        /**
         * Flag that the data package should be removed upon being received
         *
         * @param deleteOnReceive True to delete on receive
         * @return Builder
         */
        public Builder setDeleteOnReceive(boolean deleteOnReceive) {
            _deleteOnReceive = deleteOnReceive;
            return this;
        }

        /**
         * Set an intent to be fired when the data package is received
         *
         * @param intentAction Intent action
         * @return Builder
         */
        public Builder setOnReceiveAction(String intentAction) {
            _onReceiveAction = intentAction;
            return this;
        }

        /**
         * Add a file with content and mime type
         *
         * @param file Resource file
         * @return Builder
         */
        public Builder addFile(ResourceFile file) {
            _files.add(file);
            return this;
        }

        /**
         * Add a file with content type
         *
         * @param file File
         * @param contentType Importer content type
         * @return Builder
         */
        public Builder addFile(File file, String contentType) {
            return addFile(new ResourceFile(file.getAbsolutePath(),
                    contentType));
        }

        /**
         * Add a file to be sent
         *
         * @param file File
         * @return Builder
         */
        public Builder addFile(File file) {
            return addFile(file, null);
        }

        /**
         * Add a map item UID
         *
         * @param uid Map item UID
         * @return Builder
         */
        public Builder addMapItem(String uid) {
            _items.add(uid);
            return this;
        }

        /**
         * Add a map item to be sent
         *
         * @param item Map item
         * @return Builder
         */
        public Builder addMapItem(MapItem item) {
            return addMapItem(item.getUID());
        }

        /**
         * Add a file attached to a map item
         *
         * @param file Resource file attachment
         * @param item Map item the file is attached to
         * @return Builder
         */
        public Builder addAttachment(ResourceFile file, MapItem item) {
            _attachments.add(new Attachment(file, item));
            return this;
        }

        /**
         * Add a file attached to a map item
         *
         * @param file File attachment
         * @param contentType File content type
         * @param item Map item the file is attached to
         * @return Builder
         */
        public Builder addAttachment(File file, String contentType,
                MapItem item) {
            return addAttachment(new ResourceFile(file.getAbsolutePath(),
                    contentType), item);
        }

        /**
         * Add a file attached to a map item
         *
         * @param file File attachment
         * @param item Map item the file is attached to
         * @return Builder
         */
        public Builder addAttachment(File file, MapItem item) {
            return addAttachment(file, null, item);
        }

        /**
         * Build the send dialog
         *
         * @return New {@link SendDialog}
         */
        public SendDialog build() {
            SendDialog d = buildImpl();
            d.setRecipientCallback(_reCallback);
            return d;
        }

        /**
         * Show the send dialog
         *
         * @return New {@link SendDialog}
         */
        public SendDialog show() {
            SendDialog d = build();
            d.show();
            return d;
        }

        private SendDialog buildImpl() {
            // If there's a URI specified then ignore everything else
            if (_uri != null)
                return new SendDialog(_mapView, _icon, _name, _uri, _callback);

            // Check if we're only attempting to send a single file with
            // no other data package flags or instructions
            File singleFile = null;
            if (_manifest == null && _mpCallback == null && _files.size() == 1
                    && _items.isEmpty() && _attachments.isEmpty()
                    && _onReceiveAction == null) {
                ResourceFile rf = _files.iterator().next();
                if (rf != null)
                    singleFile = new File(rf.getFilePath());
            }

            // Create manifest and set import instructions
            if (_manifest == null && singleFile == null) {
                if (_name == null)
                    _name = _mapView.getContext().getString(
                            R.string.mission_package_name);
                _manifest = MissionPackageApi.CreateTempManifest(_name,
                        _importOnReceive, _deleteOnReceive, _onReceiveAction);
            }

            // Add files and map items
            if (_manifest != null) {
                for (Attachment att : _attachments)
                    addFile(_manifest, att.file, att.item);
                for (ResourceFile rf : _files)
                    addFile(_manifest, rf, null);
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

        private void addFile(MissionPackageManifest mpm, ResourceFile rf,
                MapItem item) {
            String path = rf.getFilePath();
            if (FileSystemUtils.isEmpty(path))
                return;

            File f = new File(path);
            if (!IOProviderFactory.exists(f) || !IOProviderFactory.isFile(f))
                return;

            String itemUID = item != null ? item.getUID() : null;

            // Special case for SHP file
            boolean shp = ShapefileSpatialDb.SHP_CONTENT_TYPE.equalsIgnoreCase(
                    rf.getContentType())
                    && f.getName().toLowerCase(
                            LocaleUtil.getCurrent()).endsWith(
                                    ShapefileSpatialDb.SHP_TYPE);
            if (shp && !MissionPackageShapefileHandler.add(
                    _mapView.getContext(), mpm, f, itemUID)) {
                Log.w(TAG, "Unable to add shapefile: " + f);
            } else if (!mpm.addFile(f, itemUID)) {
                Log.w(TAG, "Unable to add file: " + f);
            }
        }
    }

    private static class Attachment {
        ResourceFile file;
        MapItem item;

        Attachment(ResourceFile file, MapItem item) {
            this.file = file;
            this.item = item;
        }
    }
}
