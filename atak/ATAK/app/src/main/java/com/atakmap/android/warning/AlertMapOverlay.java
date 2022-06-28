
package com.atakmap.android.warning;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.warning.WarningComponent.Alert;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds generic/other alerts to Overlay Manager
 */
class AlertMapOverlay extends AbstractMapOverlay2 implements Delete {

    private static final String TAG = "AlertMapOverlay";

    private final Context _context;
    private final String _groupName;

    private AlertHierarchyListModel _listModel;

    AlertMapOverlay(MapView mapView, String groupName) {
        _context = mapView.getContext();
        _groupName = groupName;
    }

    @Override
    public String getIdentifier() {
        return TAG + "/" + _groupName;
    }

    @Override
    public String getName() {
        return _groupName;
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
        if (_listModel == null)
            _listModel = new AlertHierarchyListModel(adapter, prefFilter);
        else
            _listModel.syncRefresh(adapter, prefFilter);
        return _listModel;
    }

    @Override
    public boolean delete() {
        return _listModel != null && _listModel.delete();
    }

    private class AlertHierarchyListModel extends
            AbstractHierarchyListItem2
            implements Search, Delete, View.OnClickListener {

        AlertHierarchyListModel(BaseAdapter listener,
                HierarchyListFilter filter) {
            this.asyncRefresh = true;
            syncRefresh(listener, filter);
        }

        @Override
        public String getTitle() {
            return AlertMapOverlay.this.getName();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.nav_alert);
        }

        @Override
        public int getDescendantCount() {
            return getChildCount();
        }

        @Override
        public Object getUserObject() {
            return null;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ExtraHolder();
                v = h.delete = (ImageButton) LayoutInflater.from(_context)
                        .inflate(R.layout.delete_button, parent, false);
                h.delete.setOnClickListener(this);
                v.setTag(h);
            }
            return v;
        }

        @Override
        protected void refreshImpl() {
            List<Alert> alerts = WarningComponent.getAlerts(_groupName);

            // Filter
            List<HierarchyListItem> filtered = new ArrayList<>();
            for (Alert alert : alerts) {
                if (alert != null) {
                    AlertListItem item = new AlertListItem(alert);
                    if (this.filter.accept(item))
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

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = terms.toLowerCase(LocaleUtil.getCurrent());
            Set<HierarchyListItem> ret = new HashSet<>();
            for (HierarchyListItem item : getChildren()) {
                if (item instanceof AlertListItem) {
                    AlertListItem ali = (AlertListItem) item;
                    Alert alert = ali.getUserObject();
                    String msg = alert.getMessage();
                    String group = alert.getAlertGroupName();
                    if (msg != null && msg.toLowerCase(LocaleUtil.getCurrent())
                            .contains(terms))
                        ret.add(item);
                    else if (group != null && group.toLowerCase(
                            LocaleUtil.getCurrent()).contains(terms))
                        ret.add(item);
                }
            }
            return ret;
        }

        @Override
        public void onClick(View v) {
            promptDelete();
        }

        private void promptDelete() {
            final AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.dismiss_alerts);
            b.setMessage(_context.getString(
                    R.string.geofence_quick_dismiss_inquiry, _groupName,
                    WarningComponent.getAlerts(_groupName).size()));
            b.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            WarningComponent.removeAlerts(WarningComponent
                                    .getAlerts(_groupName));
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    /**
     * The list item used for each alert
     */
    private class AlertListItem extends AbstractChildlessListItem
            implements ItemClick, Delete, View.OnClickListener {

        private final Alert _alert;

        AlertListItem(Alert alert) {
            _alert = alert;
        }

        @Override
        public String getTitle() {
            return _alert.getMessage();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.nav_alert);
        }

        @Override
        public Alert getUserObject() {
            return _alert;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) v.getTag()
                    : null;
            if (h == null) {
                h = new ExtraHolder();
                v = h.delete = (ImageButton) LayoutInflater.from(_context)
                        .inflate(R.layout.delete_button, parent, false);
                v.setTag(h);
            }
            h.delete.setOnClickListener(this);
            return v;
        }

        @Override
        public void onClick(View v) {
            promptDelete();
        }

        @Override
        public boolean onClick() {
            _alert.onClick();
            return true;
        }

        @Override
        public boolean onLongClick() {
            return false;
        }

        @Override
        public boolean delete() {
            WarningComponent.removeAlert(_alert);
            return true;
        }

        private void promptDelete() {
            final AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.dismiss_alert);
            b.setMessage(R.string.dismiss_alert_confirmation);
            b.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            WarningComponent.removeAlert(_alert);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    private static class ExtraHolder {
        ImageButton delete;
    }
}
