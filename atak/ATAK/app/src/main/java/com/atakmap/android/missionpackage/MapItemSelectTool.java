
package com.atakmap.android.missionpackage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.toolbars.RangeAndBearingEndpoint;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapItemSelectTool extends Tool implements
        View.OnClickListener,
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TOOL_NAME = "MapItemSelectTool";

    private final Context _context;
    private final TextContainer _container;
    private final Map<String, MapItem> _itemMap = new HashMap<>();
    private final Map<String, MapItem> _selectors = new HashMap<>();
    private final List<MapItem> _mapItems = new ArrayList<>();
    private final MapGroup _selectorGroup;
    private final Icon _selectorIcon;
    private boolean _doneSelected = false;
    private boolean _multiSelect = true;
    private final Set<String> _allowKeys = new HashSet<>();
    private final Set<String> _allowTypes = new HashSet<>();
    private final Set<String> _allowUIDs = new HashSet<>();
    private final Set<String> _disallowKeys = new HashSet<>();
    private final Set<String> _disallowTypes = new HashSet<>();
    private final Set<String> _disallowUIDs = new HashSet<>();
    private final View _view;
    private final MapSelectDropDown _dropDown;
    private final ItemListAdapter _adapter;
    private Intent _callbackIntent;

    MapItemSelectTool(MapView mapView) {
        super(mapView, TOOL_NAME);
        _context = mapView.getContext();
        _container = TextContainer.getInstance();
        _selectorGroup = mapView.getRootGroup().addGroup(
                TOOL_NAME + "_selectors");
        _selectorGroup.setMetaBoolean("addToObjList", false);
        _selectorGroup.setVisible(true);
        _selectorIcon = new Icon.Builder()
                .setImageUri(0, "asset:/icons/outline.png")
                .setAnchor(24, 24).build();

        _view = LayoutInflater.from(_context)
                .inflate(R.layout.map_item_select_tool_view, mapView, false);
        _view.findViewById(R.id.done).setOnClickListener(this);
        _view.findViewById(R.id.clear).setOnClickListener(this);
        _view.findViewById(R.id.end_tool).setOnClickListener(this);
        ListView lv = _view.findViewById(R.id.item_list);
        lv.setAdapter(_adapter = new ItemListAdapter(_context, this));

        _dropDown = new MapSelectDropDown(mapView, _view, this);

        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _callbackIntent = extras.getParcelable("callback");
        if (_callbackIntent == null) {
            requestEndTool();
            return false;
        }

        getListExtra(_allowKeys, extras, "allowKeys");
        getListExtra(_allowTypes, extras, "allowTypes");
        getListExtra(_allowUIDs, extras, "allowUIDs");
        getListExtra(_disallowKeys, extras, "disallowKeys");
        getListExtra(_disallowTypes, extras, "disallowTypes");
        getListExtra(_disallowUIDs, extras, "disallowUIDs");

        _multiSelect = extras.getBoolean("multiSelect", true);
        if (_multiSelect) {
            String title = extras.getString("title", _context.getString(
                    R.string.select_items));
            TextView titleTV = _view.findViewById(R.id.title);
            titleTV.setText(title);
            _dropDown.show();
        }

        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);

        String prompt = extras.getString("prompt");
        if (FileSystemUtils.isEmpty(prompt))
            prompt = _context.getString(_multiSelect
                    ? R.string.map_item_multiselect_prompt
                    : R.string.map_item_select_prompt);
        _container.displayPrompt(prompt);
        _mapView.getRootGroup().addGroup(_selectorGroup);
        _mapView.getMapTouchController().setToolActive(true);
        return true;
    }

    private void getListExtra(Set<String> list, Bundle extras, String key) {
        String[] arr = extras.getStringArray(key);
        list.clear();
        if (arr != null)
            list.addAll(Arrays.asList(arr));
    }

    @Override
    public void onToolEnd() {
        _container.closePrompt();

        if (_multiSelect && !_dropDown.isClosed())
            _dropDown.closeDropDown();

        if (_callbackIntent != null) {
            final Intent i = new Intent(_callbackIntent);
            if (!_itemMap.isEmpty()) {
                if (_multiSelect && _doneSelected)
                    i.putExtra("itemUIDs", _itemMap.keySet()
                            .toArray(new String[0]));
                else
                    i.putExtra("itemUID", _itemMap.keySet().iterator().next());
            }
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }
            });
        }

        _itemMap.clear();
        _mapItems.clear();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        _selectors.clear();
        _selectorGroup.clearItems();
        _mapView.getRootGroup().removeGroup(_selectorGroup);
        _adapter.refresh(_mapItems);
        _doneSelected = false;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            MapItem pt = event.getItem();
            if (pt != null) {
                // TODO: Add support for selecting vector feature data (KMLs, SHPs, etc.)
                // Need to figure out way to trace which file this feature was generated from
                if (pt.getUID().startsWith("spatialdb::")
                        && !pt.hasMetaValue("file")) {
                    showRejectToast();
                    return;
                }

                GeoPointMetaData gp = findPoint(event);

                // Find shape so we don't add the center point as a separate item
                if (pt.hasMetaValue("shapeUID")
                        || pt.hasMetaValue("assocSetUID")) {
                    MapItem shape = _mapView.getRootGroup().deepFindUID(
                            pt.getMetaString("shapeUID",
                                    pt.getMetaString("assocSetUID", null)));
                    if (shape != null)
                        pt = shape;
                }

                // Find R&B line so we don't add the endpoint
                if (pt instanceof RangeAndBearingEndpoint
                        && pt.hasMetaValue("rabUUID")) {
                    MapItem rab = _mapView.getRootGroup().deepFindUID(
                            pt.getMetaString("rabUUID", null));
                    if (rab instanceof RangeAndBearingMapItem)
                        pt = rab;
                }

                // UID/type/key blacklist
                if (_disallowUIDs.contains(pt.getUID())
                        || _disallowTypes.contains(pt.getType())) {
                    showRejectToast();
                    return;
                }
                for (String k : _disallowKeys) {
                    if (pt.hasMetaValue(k)) {
                        showRejectToast();
                        return;
                    }
                }

                // UID/type/key whitelist
                if (!_allowUIDs.isEmpty()
                        && !_allowUIDs.contains(pt.getUID())
                        || !_allowTypes.isEmpty()
                                && !_allowTypes.contains(pt.getType())) {
                    showRejectToast();
                    return;
                }
                boolean passesWhitelist = _allowKeys.isEmpty();
                for (String k : _allowKeys) {
                    if (pt.hasMetaValue(k)) {
                        passesWhitelist = true;
                        break;
                    }
                }
                if (!passesWhitelist) {
                    showRejectToast();
                    return;
                }

                String uid = pt.getUID();
                if (!_multiSelect) {
                    _itemMap.put(uid, pt);
                    _mapItems.add(pt);
                    requestEndTool();
                    return;
                }

                if (_itemMap.containsKey(uid)) {
                    // Toggle off
                    removeFromList(pt);
                } else {
                    // Toggle on
                    _itemMap.put(uid, pt);
                    _mapItems.add(pt);

                    if (gp == null)
                        return;

                    // Create selection highlight marker
                    Marker selector = new Marker(TOOL_NAME + "_selector"
                            + _selectors.size());
                    selector.setClickable(false);
                    selector.setIcon(_selectorIcon);
                    selector.setZOrder(Double.NEGATIVE_INFINITY);
                    selector.setMetaBoolean("ignoreFocus", false);
                    selector.setMetaBoolean("toggleDetails", true);
                    selector.setMetaBoolean("ignoreMenu", true);
                    selector.setMetaString("entry", "user");
                    selector.setMetaBoolean("ignoreOffscreen", true);
                    selector.setMetaBoolean("addToObjList", false);
                    selector.setMetaBoolean("preciseMove", true);
                    selector.setClickable(false);
                    selector.setPoint(gp);
                    selector.setVisible(true);
                    _selectorGroup.addItem(selector);
                    _selectors.put(uid, selector);
                }
                _adapter.refresh(_mapItems);
            }
        }
    }

    private void showRejectToast() {
        Toast.makeText(_context, R.string.cannot_select_item,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.clear) {
            _itemMap.clear();
            _mapItems.clear();
            _selectors.clear();
            _selectorGroup.clearItems();
            _adapter.refresh(_mapItems);

        } else if (i == R.id.done) {
            _doneSelected = true;
            // fall thru to allow for requestEndTool to be called.

            requestEndTool();

        } else if (i == R.id.end_tool) {
            requestEndTool();

        }
    }

    @Override
    public boolean onKey(final View v, final int keyCode,
            final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN)
                return true;
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!onBackButtonPressed())
                    requestEndTool();
                return true;
            }
        }
        return true;
    }

    private boolean onBackButtonPressed() {
        if (!_mapItems.isEmpty()) {
            removeFromList(_mapItems.get(_mapItems.size() - 1));
            _adapter.refresh(_mapItems);
            return true;
        }
        return false;
    }

    private void removeFromList(MapItem mi) {
        if (mi != null) {
            _itemMap.remove(mi.getUID());
            _mapItems.remove(mi);
            MapItem selector = _selectors.get(mi.getUID());
            if (selector != null)
                selector.removeFromGroup();
            _selectors.remove(mi.getUID());
        }
        _adapter.refresh(_mapItems);
    }

    private static class ItemListAdapter extends BaseAdapter {

        private final Context _context;
        private final MapItemSelectTool _tool;
        private final List<MapItem> _items = new ArrayList<>();

        private ItemListAdapter(Context context,
                MapItemSelectTool tool) {
            _context = context;
            _tool = tool;
        }

        public void refresh(Collection<MapItem> mapItems) {
            _items.clear();
            _items.addAll(mapItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return _items.size();
        }

        @Override
        public Object getItem(int position) {
            return _items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            ViewHolder holder = row != null ? (ViewHolder) row.getTag() : null;
            if (holder == null) {
                row = LayoutInflater.from(_context).inflate(
                        R.layout.map_item_select_tool_row, parent, false);
                holder = new ViewHolder();
                holder.icon = row.findViewById(R.id.icon);
                holder.title = row.findViewById(R.id.msg);
                holder.delete = row.findViewById(R.id.delete);
                row.setTag(holder);
            }

            holder.icon.setVisibility(View.GONE);
            holder.title.setVisibility(View.GONE);
            holder.delete.setVisibility(View.GONE);
            holder.delete.setOnClickListener(null);

            final MapItem mi = (MapItem) getItem(position);
            if (mi == null)
                return row;

            Bitmap icon = ATAKUtilities.getIconBitmap(mi);
            holder.icon.setImageBitmap(icon);
            holder.icon.setColorFilter(ATAKUtilities.getIconColor(mi),
                    PorterDuff.Mode.MULTIPLY);
            holder.icon.setVisibility(View.VISIBLE);

            holder.title.setText(ATAKUtilities.getDisplayName(mi));
            holder.title.setVisibility(View.VISIBLE);

            holder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    _tool.removeFromList(mi);
                }
            });
            holder.delete.setVisibility(View.VISIBLE);

            return row;
        }

        private static class ViewHolder {
            ImageView icon;
            TextView title;
            ImageButton delete;
        }
    }

    private static class MapSelectDropDown extends DropDownReceiver
            implements DropDown.OnStateListener {

        private final MapItemSelectTool _tool;
        private final View _view;

        private MapSelectDropDown(MapView mapView, View view,
                MapItemSelectTool tool) {
            super(mapView);
            _tool = tool;
            _view = view;
        }

        public void show() {
            Context ctx = getMapView().getContext();
            double landscapeW = DropDownReceiver.HALF_WIDTH;
            double portraitH = DropDownReceiver.HALF_HEIGHT;
            if (ctx.getResources()
                    .getBoolean(com.atakmap.app.R.bool.isTablet)) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(ctx);
                if (prefs.getString("overlay_manager_width_height", "33")
                        .equals("33"))
                    landscapeW = portraitH = DropDownReceiver.THIRD_WIDTH;
                else if (prefs.getString("overlay_manager_width_height", "33")
                        .equals("25"))
                    landscapeW = portraitH = DropDownReceiver.QUARTER_SCREEN;
            }
            if (_view.getParent() != null)
                ((ViewGroup) _view.getParent()).removeView(_view);
            setRetain(true);
            showDropDown(_view, landscapeW, FULL_HEIGHT, FULL_WIDTH,
                    portraitH, this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
        }

        @Override
        public void disposeImpl() {
        }

        @Override
        public String getAssociationKey() {
            return TOOL_NAME;
        }

        @Override
        public boolean onBackButtonPressed() {
            return _tool.onBackButtonPressed();
        }

        @Override
        public void onDropDownSelectionRemoved() {
        }

        @Override
        public void onDropDownClose() {
            if (ToolManagerBroadcastReceiver.getInstance()
                    .getActiveTool() == _tool)
                _tool.requestEndTool();
        }

        @Override
        public void onDropDownSizeChanged(double width, double height) {
        }

        @Override
        public void onDropDownVisible(boolean v) {
        }
    }
}
