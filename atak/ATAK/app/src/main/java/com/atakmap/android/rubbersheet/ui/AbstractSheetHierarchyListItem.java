
package com.atakmap.android.rubbersheet.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.rubbersheet.data.export.ExportFileTask;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.io.File;

/**
 * List item representing a survey
 */
abstract class AbstractSheetHierarchyListItem extends AbstractHierarchyListItem2
        implements Visibility2, GoTo, MapItemUser, ILocation, Delete,
        View.OnClickListener, ExportFileTask.Callback {

    private static final String TAG = "AbstractSheetHierarchyListItem";

    protected final MapView _mapView;
    protected final Context _context;
    protected final AbstractSheet _item;
    private final GeoPoint _point = GeoPoint.createMutable();
    private final CoordinateFormat _coordFmt;

    AbstractSheetHierarchyListItem(MapView mapView, AbstractSheet item) {
        _mapView = mapView;
        _context = mapView.getContext();
        _item = item;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        _coordFmt = CoordinateFormat.find(prefs.getString(
                "coord_display_pref",
                CoordinateFormat.MGRS.toString()));
        setLocalData("showLocation", false); // Takes up too much space
    }

    @Override
    public String getTitle() {
        return _item.getTitle();
    }

    @Override
    public String getDescription() {
        // Show load progress
        LoadState ls = _item.getLoadState();
        if (ls == LoadState.LOADING)
            return _context.getString(R.string.loading_percent,
                    _item.getLoadProgress());

        // Show location
        getPoint(_point);
        return CoordinateFormatUtilities.formatToString(_point, _coordFmt);
    }

    @Override
    public String getUID() {
        return _item.getUID();
    }

    @Override
    public boolean isMultiSelectSupported() {
        return false;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    public String getIconUri() {
        return ATAKUtilities.getIconUri(_item);
    }

    @Override
    public int getIconColor() {
        return _item.getStrokeColor();
    }

    @Override
    public Object getUserObject() {
        return _item;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        SheetExtraHolder h = v != null && v.getTag() instanceof SheetExtraHolder
                ? (SheetExtraHolder) v.getTag()
                : null;
        if (h == null)
            h = new SheetExtraHolder(_mapView, parent);
        h.failed.setVisibility(View.GONE);
        h.loader.setVisibility(View.GONE);
        h.export.setVisibility(View.GONE);
        LoadState ls = _item.getLoadState();
        if (ls == LoadState.LOADING)
            h.loader.setVisibility(View.VISIBLE);
        else if (ls == LoadState.FAILED)
            h.failed.setVisibility(View.VISIBLE);
        else
            h.export.setVisibility(View.VISIBLE);
        h.export.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        return h.root;
    }

    @Override
    protected void refreshImpl() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean hideIfEmpty() {
        return false;
    }

    /**************************************************************************/

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.export)
            promptExport();
        else if (id == R.id.delete)
            promptRemove();
    }

    @Override
    public GeoPoint getPoint(GeoPoint point) {
        GeoPointMetaData center = _item.getCenter();
        if (point != null && point.isMutable()) {
            point.set(center.get());
            return point;
        }
        return center.get();
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        return _item.getBounds(bounds);
    }

    @Override
    public MapItem getMapItem() {
        return _item;
    }

    @Override
    public boolean goTo(boolean select) {
        return MapTouchController.goTo(_item, select);
    }

    @Override
    public boolean setVisible(boolean visible) {
        // Top-level visibility control only affects the survey and obstacles
        _item.setVisible(visible);
        return true;
    }

    @Override
    public int getVisibility() {
        return _item.getVisible() ? VISIBLE : INVISIBLE;
    }

    @Override
    public boolean delete() {
        return _item.removeFromGroup();
    }

    protected abstract void promptExport();

    protected void promptRemove() {
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.are_you_sure));
        b.setMessage(_context.getString(R.string.remove_rs_msg, getTitle()));
        b.setPositiveButton(_context.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        delete();
                    }
                });
        b.setNegativeButton(_context.getString(R.string.cancel), null);
        b.show();
    }

    @Override
    public void onExportFinished(AbstractSheet item, final File file) {
        if (!FileSystemUtils.isFile(file))
            return;

        View v = LayoutInflater.from(_context).inflate(
                R.layout.rs_export_finished_dialog, _mapView, false);
        TextView msg = v.findViewById(R.id.export_msg);
        TextView path = v.findViewById(R.id.export_path);
        TextView size = v.findViewById(R.id.export_size);

        msg.setText(_context.getString(R.string.export_finished_message1,
                item.getTitle()));
        path.setText(FileSystemUtils.prettyPrint(file));
        size.setText(MathUtils.GetLengthString(IOProviderFactory.length(file)));

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.export_finished_title));
        b.setView(v);
        b.setPositiveButton(_context.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Toggle off the layer and import the file into ATAK
                        _item.setVisible(false);
                        Intent i = new Intent(
                                ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION);
                        i.putExtra("filepath", file.getAbsolutePath());
                        i.putExtra("promptOnMultipleMatch", false);
                        i.putExtra("importInPlace", true);
                        AtakBroadcast.getInstance().sendBroadcast(i);
                    }
                });
        b.setNegativeButton(_context.getString(R.string.cancel), null);
        b.show();
    }
}
