
package com.atakmap.android.rubbersheet.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Sub-groups for rubber sheets that are part of the same source file
 */
public class SheetGroupListItem extends AbstractHierarchyListItem2
        implements View.OnClickListener, Visibility2, Delete, Search {

    private final MapView _mapView;
    private final Context _context;
    private final File _file;
    private final List<AbstractSheetHierarchyListItem> _sheets;

    public SheetGroupListItem(MapView mapView, File file) {
        this.asyncRefresh = true;
        this.reusable = true;
        _mapView = mapView;
        _context = mapView.getContext();
        _file = file;
        _sheets = new ArrayList<>();
    }

    @Override
    public String getTitle() {
        return _file.getName();
    }

    @Override
    public Drawable getIconDrawable() {
        return _mapView.getResources().getDrawable(R.drawable.ic_folder);
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public File getUserObject() {
        return _file;
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
        h.delete.setOnClickListener(this);
        return h.root;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.delete)
            promptRemove();
    }

    private void promptRemove() {
        final List<AbstractSheetHierarchyListItem> sheets = getSheets();
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.are_you_sure);
        b.setMessage(_context.getString(R.string.remove_rs_models_msg,
                sheets.size(), getTitle()));
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (AbstractSheetHierarchyListItem item : sheets)
                            item.delete();
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    public void syncRefresh(BaseAdapter listener, HierarchyListFilter filter,
            List<AbstractSheetHierarchyListItem> sheets) {
        synchronized (_sheets) {
            _sheets.clear();
            _sheets.addAll(sheets);
        }
        syncRefresh(listener, filter);
    }

    private List<AbstractSheetHierarchyListItem> getSheets() {
        List<AbstractSheetHierarchyListItem> items;
        synchronized (_sheets) {
            items = new ArrayList<>(_sheets);
        }
        return items;
    }

    @Override
    protected void refreshImpl() {
        List<HierarchyListItem> filtered = new ArrayList<>();
        List<AbstractSheetHierarchyListItem> items = getSheets();
        for (AbstractSheetHierarchyListItem item : items) {
            if (this.filter.accept(item))
                filtered.add(item);
        }
        sortItems(filtered);
        updateChildren(filtered);
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }
}
