
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class MissionPackageFileHierarchyListItem extends AbstractChildlessListItem
        implements GoTo, Delete, MapItemUser, Search, ItemClick, Visibility,
        View.OnClickListener {

    private static final String TAG = "MissionPackageFileHierarchyListItem";

    private final MissionPackageMapOverlay _overlay;
    private final MapView _mapView;
    private final Context _context;
    private final MissionPackageListGroup _group;
    private final MissionPackageListFileItem _fileItem;
    private final MissionPackageContent _content;
    private final File _file;
    private final URIContentHandler _handler;
    private final Drawable _icon;

    MissionPackageFileHierarchyListItem(MissionPackageMapOverlay overlay,
            MapView mapView, BaseAdapter listener,
            MissionPackageListGroup group,
            MissionPackageListFileItem manifest) {
        this.listener = listener;
        _overlay = overlay;
        _mapView = mapView;
        _context = mapView.getContext();
        _group = group;
        _fileItem = manifest;

        // Get the local file
        _file = manifest != null && manifest.getPath() != null
                ? new File(FileSystemUtils
                        .sanitizeWithSpacesAndSlashes(manifest.getPath()))
                : null;

        // Get the file handler
        if (_file != null)
            _handler = URIContentManager.getInstance().getHandler(_file);
        else
            _handler = null;

        // Get the icon
        Drawable icon = null;
        if (_handler != null)
            icon = _handler.getIcon();

        _content = _fileItem != null ? _fileItem.getContent() : null;
        if (icon == null && _content != null) {
            String contentType = _content.getParameterValue(
                    MissionPackageContent.PARAMETER_CONTENT_TYPE);
            if (!FileSystemUtils.isEmpty(contentType))
                icon = ATAKUtilities.getContentIcon(contentType);
            else
                icon = ATAKUtilities.getFileIcon(_file);
        }

        _icon = icon;

        this.asyncRefresh = true;
    }

    @Override
    public String getTitle() {
        if (_handler != null)
            return _handler.getTitle();
        if (_fileItem == null) {
            Log.w(TAG, "Skipping invalid title");
            return _context.getString(R.string.file);
        }
        return _fileItem.getname();
    }

    @Override
    public String getDescription() {
        if (_file != null && IOProviderFactory.exists(_file)
                && IOProviderFactory.isFile(_file))
            return MathUtils.GetLengthString(IOProviderFactory.length(_file));
        return null;
    }

    @Override
    public Drawable getIconDrawable() {
        return _icon;
    }

    @Override
    public int getIconColor() {
        return _handler != null ? _handler.getIconColor() : Color.WHITE;
    }

    @Override
    public Object getUserObject() {
        if (_fileItem == null) {
            Log.w(TAG, "Skipping invalid user object");
            return null;
        }

        return _fileItem;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.extract)
            _overlay.promptExtractContent(_group, _fileItem);
        else if (i == R.id.open)
            MIMETypeMapper.openFile(_file, _context);
        else if (i == R.id.delete)
            _overlay.promptRemoveContent(_group, _fileItem);
    }

    @Override
    public boolean onClick() {
        // Prompt to extract content when clicked
        if (!FileSystemUtils.isFile(_file)) {
            _overlay.promptExtractContent(_group, _fileItem);
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongClick() {
        showFileDialog();
        return true;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                ? (ExtraHolder) v.getTag()
                : null;
        if (h == null) {
            h = new ExtraHolder();
            v = LayoutInflater.from(_context).inflate(
                    R.layout.missionpackage_overlay_fileitem, parent, false);
            h.missing = v.findViewById(R.id.not_found);
            h.extract = v.findViewById(R.id.extract);
            h.open = v.findViewById(R.id.open);
            h.delete = v.findViewById(R.id.delete);
            v.setTag(h);
        }
        boolean showExtras = !(listener instanceof HierarchyListAdapter &&
                ((HierarchyListAdapter) listener).getSelectHandler() != null);
        boolean showExtract = showExtras && !FileSystemUtils.isFile(_file);
        boolean showView = MIMETypeMapper.getOpenIntent(_context,
                _file) != null;
        h.missing.setVisibility(showExtract ? View.VISIBLE : View.GONE);
        h.extract.setVisibility(showExtract ? View.VISIBLE : View.GONE);
        h.open.setVisibility(showView ? View.VISIBLE : View.GONE);
        h.delete.setVisibility(showExtras ? View.VISIBLE : View.GONE);
        h.open.setOnClickListener(this);
        h.extract.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        return v;
    }

    private static class ExtraHolder {
        TextView missing;
        ImageButton extract, open, delete;
    }

    @Override
    public String getUID() {
        if (_fileItem == null || _file == null) {
            Log.w(TAG, "Skipping invalid UID");
            return null;
        }

        return HashingUtils.sha256sum(_file.getAbsolutePath());
    }

    @Override
    public boolean delete() {
        if (_fileItem != null) {
            _group.removeFile(_fileItem);
            return true;
        }
        return false;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        if (_fileItem.getname().toLowerCase(LocaleUtil.getCurrent())
                .contains(terms.toLowerCase(LocaleUtil.getCurrent())))
            return new HashSet<>();
        return null;
    }

    @Override
    public boolean goTo(boolean select) {
        // File handler comes first
        if (_handler != null) {
            if (_handler.isActionSupported(GoTo.class))
                return ((GoTo) _handler).goTo(select);
            return false;
        }

        if (_fileItem == null) {
            Log.w(TAG, "Skipping invalid file item");
            return false;
        }

        MapItem item = getMapItem();
        String name = _file.getName();

        // Image
        if (ImageDropDownReceiver.ImageFileFilter.accept(null, name)) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ImageDropDownReceiver.IMAGE_DISPLAY)
                            .putExtra("imageURI",
                                    Uri.fromFile(_file).toString()));
            return false;
        }

        // Check if this file can be imported
        Collection<ImportResolver> resolvers = ImportFilesTask.GetSorters(
                _context, true, false, true, false);
        for (ImportResolver res : resolvers) {
            if (res.match(_file)) {
                _overlay.importFile(_group, _file, false);
                return false;
            }
        }

        // Display marker attachments
        if (item != null)
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(ImageGalleryReceiver.VIEW_ATTACHMENTS)
                            .putExtra("uid", _fileItem.getMarkerUid()));

        // Finally just try to open file
        else
            MIMETypeMapper.openFile(_file, _context);

        return false;
    }

    @Override
    public MapItem getMapItem() {
        if (_fileItem != null) {
            String uid = _fileItem.getMarkerUid();
            return _mapView.getMapItem(uid);
        }
        return null;
    }

    private void showFileDialog() {
        if (_file == null) {
            Log.e(TAG, "Unable to view details for empty file path");
            _overlay.toast(R.string.mission_package_unable_to_access_file_path);
            return;
        }

        View v = LayoutInflater.from(_context)
                .inflate(R.layout.missionpackage_file_detail, _mapView, false);

        if (!FileSystemUtils.isFile(_file)) {
            Log.e(TAG, "Unable to locate file: " + _file);
            _overlay.toast(R.string.mission_package_unable_to_access_file);
            return;
        }

        Log.d(TAG, "Showing details of file: " + _file.getAbsolutePath());

        TextView nameText = v
                .findViewById(R.id.missionpackage_file_detail_txtName);
        nameText.setText(MissionPackageUtils.abbreviateFilename(
                _file.getName(), 40));

        TextView directoryText = v
                .findViewById(R.id.missionpackage_file_detail_txtDirectory);
        directoryText.setText(_file.getParent());

        TextView typeText = v
                .findViewById(R.id.missionpackage_file_detail_txtType);
        typeText.setText(FileSystemUtils.getExtension(_file, true, true));

        TextView sizeText = v
                .findViewById(R.id.missionpackage_file_detail_txtSize);
        sizeText.setText(
                MathUtils.GetLengthString(IOProviderFactory.length(_file)));

        TextView dateText = v
                .findViewById(R.id.missionpackage_file_detail_txtModifiedDate);
        dateText.setText(MissionPackageUtils.getModifiedDate(_file));

        final View md5Layout = v
                .findViewById(R.id.missionpackage_file_detail_md5_layout);
        final TextView md5Text = v
                .findViewById(R.id.missionpackage_file_detail_txtMd5);

        // Compute MD5 asynchronously
        Button computeMD5 = v.findViewById(R.id.compute_md5);
        computeMD5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View computeMD5) {
                computeMD5.setEnabled(false);
                postRefresh(new Runnable() {
                    @Override
                    public void run() {
                        final String md5 = HashingUtils.md5sum(_file);
                        _mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                computeMD5.setVisibility(View.GONE);
                                md5Layout.setVisibility(View.VISIBLE);
                                md5Text.setText(md5);
                            }
                        });
                    }
                });
            }
        });

        final AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.mission_package_file);
        b.setView(v);
        b.setPositiveButton(R.string.ok, null);
        b.show();
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        // Handler actions
        // While we could return the handler directly, we would no longer
        // be able to override any actions from within this class
        if (clazz.equals(Visibility2.class) && clazz.isInstance(_handler)
                && _handler.isActionSupported(clazz)
                && FileSystemUtils.isFile(_file))
            return clazz.cast(_handler);
        return super.getAction(clazz);
    }

    @Override
    public boolean isVisible() {
        return _handler != null && _handler.isActionSupported(Visibility.class)
                && ((Visibility) _handler).isVisible();
    }

    @Override
    public boolean setVisible(boolean visible) {
        if (_handler != null && _handler.isActionSupported(Visibility.class)) {
            if (_content != null)
                _content.setParameter(MissionPackageContent.PARAMETER_VISIBLE,
                        String.valueOf(visible));
            return ((Visibility) _handler).setVisible(visible);
        }
        return false;
    }
}
