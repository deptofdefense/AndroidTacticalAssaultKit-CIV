
package com.atakmap.spatial.file;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Send;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem;
import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MvtFileListItem extends AbstractHierarchyListItem
        implements View.OnClickListener, View.OnLongClickListener,
        Visibility, Search, Delete, Export {

    private static final String TAG = "MvtFileListItem";

    private final Context context;
    private final File file;
    private final List<HierarchyListItem> featureSets;

    public MvtFileListItem(Context context,
            File file, List<HierarchyListItem> featureSets) {

        this.context = context;
        this.file = file;
        this.featureSets = new ArrayList<>(featureSets);
    }

    @Override
    public String getTitle() {
        return this.file.getName();
    }

    @Override
    public String getIconUri() {
        return MvtSpatialDb.ICON_PATH;
    }

    @Override
    public int getChildCount() {
        return this.featureSets.size();
    }

    @Override
    public int getDescendantCount() {
        return this.getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        if (index >= 0 && index < this.featureSets.size())
            return this.featureSets.get(index);
        return null;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Visibility.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Search.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Delete.class)) {
            return clazz.cast(this);
        } else if (clazz.equals(Export.class)) {
            return clazz.cast(this);
        } else {
            return null;
        }
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView() {
        if (file == null)
            return null;

        URIContentHandler h = URIContentManager.getInstance().getHandler(file);

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.feature_set_extra, null);

        ImageButton panBtn = view.findViewById(R.id.panButton);
        panBtn.setVisibility(h != null && h.isActionSupported(GoTo.class)
                ? View.VISIBLE
                : View.GONE);

        ImageButton sendBtn = view.findViewById(R.id.sendButton);
        sendBtn.setVisibility(h != null && h.isActionSupported(Send.class)
                || FileSystemUtils.isFile(file) ? View.VISIBLE : View.GONE);

        ImageButton editBtn = view.findViewById(R.id.editButton);
        editBtn.setVisibility(View.GONE);

        panBtn.setOnClickListener(this);
        sendBtn.setOnClickListener(this);

        return view;
    }

    @Override
    public Sort refresh(final Sort sort) {
        return sort;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> retval = new HashSet<>();
        for (HierarchyListItem child : featureSets) {
            Search s = child.getAction(Search.class);
            if (s != null)
                retval.addAll(s.find(terms));
        }
        return retval;
    }

    /**************************************************************************/
    // View On Item Long Click Listener

    @Override
    public void onClick(View v) {
        if (file == null)
            return;

        int id = v.getId();

        URIContentHandler handler = URIContentManager.getInstance()
                .getHandler(file);

        // Pan to file
        if (id == R.id.panButton && handler != null
                && handler.isActionSupported(GoTo.class)) {
            ((GoTo) handler).goTo(false);
        }

        // Send file
        else if (id == R.id.sendButton) {
            MapView mv = MapView.getMapView();
            if (mv == null)
                return;
            if (handler != null && handler.isActionSupported(Send.class))
                ((Send) handler).promptSend();
            else
                new SendDialog.Builder(mv)
                        .addFile(file, MvtSpatialDb.MVT_CONTENT_TYPE)
                        .show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        final File fileToSend = this.file;
        if (fileToSend == null) {
            Log.w(TAG, "Unable to send file");
            return false;
        }

        return ExportFileMarshal.sendFile(this.context,
                MvtSpatialDb.MVT_CONTENT_TYPE,
                fileToSend, true, null);
    }

    /**************************************************************************/

    @Override
    public boolean setVisible(boolean visible) {
        for (HierarchyListItem child : featureSets) {
            Visibility2 vis2 = child.getAction(Visibility2.class);
            if (vis2 != null) {
                vis2.setVisible(visible);
                continue;
            }
            Visibility vis = child.getAction(Visibility.class);
            if (vis != null) {
                vis.setVisible(visible);
                continue;
            }
        }
        return true;
    }

    @Override
    public boolean isVisible() {
        boolean visible = featureSets.isEmpty();
        for (HierarchyListItem child : featureSets) {
            Visibility2 vis2 = child.getAction(Visibility2.class);
            if (vis2 != null) {
                visible |= vis2.isVisible();
                continue;
            }
            Visibility vis = child.getAction(Visibility.class);
            if (vis != null) {
                visible |= vis.isVisible();
                continue;
            }
        }
        return visible;
    }

    @Override
    public boolean delete() {
        if (file == null) {
            Log.w(TAG, "Failed to find group file to delete");
            return false;
        }

        Log.d(TAG,
                "Delete: " + this.file.getName() + ", "
                        + file.getAbsolutePath());
        Intent deleteIntent = new Intent();
        deleteIntent.setAction(ImportExportMapComponent.ACTION_DELETE_DATA);
        deleteIntent.putExtra(ImportReceiver.EXTRA_CONTENT,
                MvtSpatialDb.MVT_CONTENT_TYPE);
        deleteIntent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                MvtSpatialDb.MVT_FILE_MIME_TYPE);
        deleteIntent.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(file)
                .toString());
        AtakBroadcast.getInstance().sendBroadcast(deleteIntent);

        return true;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {

        if (MissionPackageExportWrapper.class.equals(target)) {
            return toMissionPackage();
        }

        return null;
    }

    private MissionPackageExportWrapper toMissionPackage() {
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "No file found");
            return null;
        }

        return new MissionPackageExportWrapper(false, file.getAbsolutePath());
    }
}
