
package com.atakmap.android.missionpackage.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.toolbars.RangeAndBearingEndpoint;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

class MissionPackageMapItemHierarchyListItem extends
        AbstractChildlessListItem implements Visibility, Delete, GoTo,
        Search, MapItemUser, Export, View.OnClickListener {

    private static final String TAG = "MissionPackageMapItemHierarchyListItem";

    private final static Set<String> SEARCH_FIELDS = new HashSet<>();
    static {
        SEARCH_FIELDS.add("callsign");
        SEARCH_FIELDS.add("title");
        SEARCH_FIELDS.add("shapeName");
    }

    private final MissionPackageMapOverlay _overlay;
    private final MapView _mapView;
    private final Context _context;
    private final MissionPackageListGroup _group;
    private final MissionPackageListMapItem _mapListItem;
    private final MapItem _item;
    private final Shape _shape;
    private Drawable _icon;
    private Integer _iconColor;

    private CoordinateFormat _coordFmt;

    MissionPackageMapItemHierarchyListItem(MissionPackageMapOverlay overlay,
            MapView mapView, BaseAdapter listener,
            MissionPackageListGroup group,
            MissionPackageListMapItem manifest) {
        this.listener = listener;
        _overlay = overlay;
        _mapView = mapView;
        _context = mapView.getContext();
        _group = group;
        _mapListItem = manifest;

        // Find associated map items
        _item = _mapView.getRootGroup().deepFindUID(_mapListItem.getUID());
        MapItem mi = ATAKUtilities.findAssocShape(_item);
        if (mi instanceof RangeAndBearingMapItem
                && !(_item instanceof RangeAndBearingEndpoint))
            mi = _item;
        _shape = mi instanceof Shape ? (Shape) mi : null;

        // Extract CoT for callsign, icon, and point
        // Performed within the refresh thread so don't need to worry about
        // performance too much
        // TODO: Store CoT type and other details in the Data Package manifest
        //  instead of having to reach down into the CoT event after the fact
        String callsign = _mapListItem.getname();
        String uid = _mapListItem.getUID();
        if (_item == null) {
            String cotXml = MissionPackageExtractor.ExtractCoT(_context,
                    new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(
                            group.getManifest().getPath())),
                    _mapListItem.getContent(), false);
            CotEvent event;
            if (!FileSystemUtils.isEmpty(cotXml)
                    && (event = CotEvent.parse(cotXml)) != null
                    && event.isValid()) {
                _mapListItem.settype(event.getType());
                callsign = CotUtils.getCallsign(event);
                CotPoint point = event.getCotPoint();
                if (point != null)
                    _mapListItem.point = point.toGeoPoint();
                _icon = MissionPackageUtils.getIconDrawable(event);
                _iconColor = MissionPackageUtils.getColor(event);
            }
            if (FileSystemUtils.isEquals(callsign, uid))
                callsign = _context
                        .getString(R.string.mission_package_map_item);
            _mapListItem.setname(callsign);
        }
    }

    @Override
    public String getTitle() {
        if (_item != null)
            return ATAKUtilities.getDisplayName(_item);
        if (this._mapListItem == null) {
            Log.w(TAG, "Skipping invalid title");
            return _context.getString(R.string.mission_package_map_item);
        }
        return _mapListItem.getname();
    }

    @Override
    public String getDescription() {
        // Show item coordinate
        if (_coordFmt == null) {
            SharedPreferences sp = PreferenceManager
                    .getDefaultSharedPreferences(_context);
            _coordFmt = CoordinateFormat.find(sp.getString(
                    "coord_display_pref", _context.getString(
                            R.string.coord_display_pref_default)));
        }
        GeoPoint gp = _mapListItem.point;
        if (_item != null) {
            if (_item instanceof PointMapItem) {
                gp = ((PointMapItem) _item).getPoint();
            } else if (_item instanceof AnchoredMapItem) {
                PointMapItem anchor = ((AnchoredMapItem) _item)
                        .getAnchorItem();
                if (anchor != null)
                    gp = anchor.getPoint();
            } else if (_item instanceof Shape) {
                gp = ((Shape) _item).getCenter().get();
            }
        }
        return gp != null ? CoordinateFormatUtilities.formatToString(
                gp, _coordFmt) : null;
    }

    @Override
    public Drawable getIconDrawable() {

        // Get shape icon instead of center marker
        if (_shape != null)
            return _shape.getIconDrawable();

        // Use actual map item icon
        if (_item != null)
            return _item.getIconDrawable();

        // Default icon drawable
        return _icon;
    }

    @Override
    public String getIconUri() {
        // Legacy icon URI
        if (this._mapListItem == null)
            return null;
        Icon ico = _mapListItem.geticon();
        return ico != null ? ico.getImageUri(Icon.STATE_DEFAULT) : null;
    }

    @Override
    public int getIconColor() {
        if (_shape != null)
            return ATAKUtilities.getIconColor(_shape);
        if (_item != null)
            return ATAKUtilities.getIconColor(_item);
        if (_iconColor != null)
            return _iconColor;

        if (this._mapListItem == null || _mapListItem.geticon() == null) {
            Log.w(TAG, "Skipping invalid icon color");
            return super.getIconColor();
        }

        return _mapListItem.geticon().getColor(Icon.STATE_DEFAULT);
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        boolean validItem = _item != null && _item.getGroup() != null;
        if (!validItem) {
            if (clazz.equals(Visibility.class) || clazz.equals(GoTo.class)
                    && _mapListItem.point == null)
                return null;
        }
        return super.getAction(clazz);
    }

    @Override
    public Object getUserObject() {
        if (this._item == null) {
            Log.w(TAG, "Skipping invalid user object");
            return null;
        }

        return this._item;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.extract)
            _overlay.promptExtractContent(_group, _mapListItem);
        else if (i == R.id.delete)
            _overlay.promptRemoveContent(_group, _mapListItem);
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                ? (ExtraHolder) v.getTag()
                : null;
        if (h == null) {
            h = new ExtraHolder();
            v = LayoutInflater.from(_context).inflate(
                    R.layout.missionpackage_overlay_mapitem, parent, false);
            h.missing = v.findViewById(R.id.not_found);
            h.extract = v.findViewById(R.id.extract);
            h.delete = v.findViewById(R.id.delete);
            v.setTag(h);
        }
        boolean showExtras = !(listener instanceof HierarchyListAdapter &&
                ((HierarchyListAdapter) listener).getSelectHandler() != null);
        boolean showExtract = showExtras && (_item == null ||
                _item.getGroup() == null);
        h.missing.setVisibility(showExtract ? View.VISIBLE : View.GONE);
        h.extract.setVisibility(showExtract ? View.VISIBLE : View.GONE);
        h.delete.setVisibility(showExtras ? View.VISIBLE : View.GONE);
        h.extract.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        return v;
    }

    private static class ExtraHolder {
        TextView missing;
        ImageButton extract, delete;
    }

    @Override
    public boolean isVisible() {
        if (this._item == null) {
            Log.w(TAG, "Skipping isVisible");
            return false;
        }

        return this._item.getVisible();
    }

    @Override
    public boolean setVisible(boolean visible) {
        if (this._item == null) {
            Log.w(TAG, "Skipping setVisible");
            return false;
        }
        boolean changed = visible != _item.getVisible();
        _item.setVisible(visible);
        if (changed)
            _item.persist(_mapView.getMapEventDispatcher(), null, getClass());
        return true;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        if (_item == null)
            return null;
        String reterms = "*" + terms + "*";
        for (String field : SEARCH_FIELDS) {
            if (MapGroup.matchItemWithMetaString(_item, field, reterms))
                return new HashSet<>();
        }
        return null;
    }

    private void getLocation(GeoPoint g) {

        GeoPoint loc;
        if (this._item == null || this._item.getGroup() == null) {
            Log.w(TAG, "Skipping getLocation");
            loc = GeoPoint.ZERO_POINT;
        } else if (this._item instanceof Shape) {
            loc = ((Shape) this._item).getCenter().get();
        } else if (this._item instanceof PointMapItem) {
            loc = ((PointMapItem) this._item).getPoint();
        } else if (this._item instanceof AnchoredMapItem) {
            loc = ((AnchoredMapItem) this._item).getAnchorItem().getPoint();
        } else {
            throw new IllegalStateException("Bad type for "
                    + this.getTitle() + ": "
                    + this._item.getClass().getName());
        }

        // XXX -
        if (loc == null) {
            Log.w(TAG, "null location for " + _item + ", default to 0, 0");
            loc = GeoPoint.ZERO_POINT;
        }

        g.set(loc);
    }

    @Override
    public String getUID() {
        if (this._mapListItem == null) {
            Log.w(TAG, "Skipping invalid UID");
            return null;
        }

        return this._mapListItem.getUID();
    }

    @Override
    public boolean goTo(boolean select) {
        if (this._item == null) {
            if (_mapListItem.point != null) {
                CameraController.Programmatic.panTo(
                        _mapView.getRenderer3(), _mapListItem.point, true);
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        MapMenuReceiver.HIDE_MENU));
            } else
                Log.w(TAG, "Skipping invalid goto");
            return false;
        }

        if (_shape != null) {
            MapTouchController.goTo(_shape, select);
            return true;
        }

        if (!MapTouchController.goTo(this._item, select)) {
            GeoPoint zoomLoc = GeoPoint.createMutable();
            getLocation(zoomLoc);
            Intent intent = new Intent(
                    "com.atakmap.android.maps.ZOOM_TO_LAYER");
            intent.putExtra("point", zoomLoc.toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        return true;
    }

    @Override
    public MapItem getMapItem() {
        return this._item;
    }

    @Override
    public boolean delete() {
        if (_mapListItem != null) {
            _group.removeMapItem(_mapListItem);
            return true;
        }
        return false;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return _item instanceof Exportable && ((Exportable) _item)
                .isSupported(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        return _item instanceof Exportable ? ((Exportable) _item)
                .toObjectOf(target, filters) : null;
    }
}
