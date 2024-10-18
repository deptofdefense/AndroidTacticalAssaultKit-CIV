
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

import gov.tak.api.annotation.ModifierApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class RecentlyAddedDropDownReceiver extends DropDownReceiver
        implements MapEventDispatcher.OnMapEventListener,
        OnStateListener,
        OnPointChangedListener {

    private static final String TAG = "RecentlyAddedDropDownReceiver";

    public static final String START = "com.atakmap.android.user.RECENTLY_ADDED_DROP_DOWN";

    private final Context _context;
    private ListView _recentlyAddedListView;
    private View _layout;

    private CoordinateFormat _currFormat = CoordinateFormat.MGRS;

    private Marker _myDevice;
    private RecentlyAdded _swapped;
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected final List<RecentlyAdded> _recentlyAddedList = new ArrayList<>();
    private final RecentlyAddedAdapter _recentlyAddedAdapter = new RecentlyAddedAdapter();

    private final SharedPreferences _prefs;
    protected static RecentlyAddedDropDownReceiver instance;

    // *********************************** Constructors ***********************************//
    synchronized public static RecentlyAddedDropDownReceiver getInstance() {
        if (instance == null) {
            instance = new RecentlyAddedDropDownReceiver(MapView.getMapView());
        }
        return instance;
    }

    protected RecentlyAddedDropDownReceiver(MapView mapView) {
        super(mapView);
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(START)) {
            String coordValue = _prefs.getString("coord_display_pref",
                    context.getString(R.string.coord_display_pref_default));
            CoordinateFormat cf = CoordinateFormat.find(coordValue);
            if (cf != null) {
                _currFormat = cf;
            }
            _initView();

            //refresh the list
            refreshLayout();

            setRetain(true);
            this.showDropDown(_layout, THIRD_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, this);
        } else if (action
                .equals("com.atakmap.android.cotdetails.COTINFO_SETTYPE")) {
            String uid = intent.getStringExtra("uid");
            String newType = intent.getStringExtra("type");
            RecentlyAdded ra = haveRecentlyAddedInstance(uid);

            // This was recently added so let's see if it will soon go through a mapgroup swap
            if (ra != null && newType != null) {
                String currentType = ra.item.getType();
                // Make sure both are atom CoT types
                if (currentType.startsWith("a-") && newType.startsWith("a-")) {
                    if (currentType.trim().charAt(2) != newType.trim()
                            .charAt(2)) {
                        _swapped = ra;
                    }
                }
            }
        }
    }

    public void close() {
        this.closeDropDown();
    }

    private void _removeFromList(RecentlyAdded ra, boolean removeFromGroup) {
        getMapView().getMapEventDispatcher().removeMapItemEventListener(
                ra.item, this);
        ra.anchor.removeOnPointChangedListener(this);
        _recentlyAddedList.remove(ra);
        ra.delete(removeFromGroup);
        refreshLayout();
    }

    public void addToRecentList(MapItem item) {

        if (_myDevice == null) {
            _myDevice = getMapView().getSelfMarker();
        }

        GeoPointMetaData currPoint = getMapView().inverse(
                getMapView().getWidth() / 3f,
                getMapView().getHeight() / 2f, MapView.InverseMode.RayCast);
        if (_myDevice != null) {
            currPoint = _myDevice.getGeoPointMetaData();
        }

        if (currPoint == null) {
            Log.e(TAG, "Failed to find current self point");
            return;
        }

        PointMapItem anchor = null;
        if (item instanceof PointMapItem)
            anchor = (PointMapItem) item;
        else if (item instanceof AnchoredMapItem)
            anchor = ((AnchoredMapItem) item).getAnchorItem();

        if (anchor == null) {
            Log.e(TAG, "Failed to find item anchor: " + item);
            return;
        }

        RecentlyAdded recent = new RecentlyAdded(item, anchor,
                currPoint.get());
        _recentlyAddedList.add(0, recent);
        refreshLayout();

        getMapView().getMapEventDispatcher().addMapItemEventListener(
                recent.item, this);
        recent.anchor.addOnPointChangedListener(this);
    }

    public boolean isRecentlyAdded(MapItem item) {
        if (item != null) {
            for (RecentlyAdded recent : _recentlyAddedList) {
                if (recent.item == item || recent.anchor == item)
                    return true;
            }
        }
        return false;
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        final RecentlyAdded ra = haveRecentlyAddedInstance(item);
        // Listen to if an item is removed, if so we can remove it from our recent list
        if (ra == null) {
            return;
        }
        if (MapEvent.ITEM_REFRESH.equals(event.getType())
                || MapEvent.ITEM_PERSIST.equals(event.getType())) {
            ra.refresh();
            refreshLayout();
        } else if (MapEvent.ITEM_REMOVED.equals(event.getType())) {
            // Listen to if an item is removed, if so we can remove it from our recent list
            if (ra == _swapped) {
                _swapped = null;
                return;
            }
            _removeFromList(ra, false);
            refreshLayout();
        }
    }

    @Override
    public void onPointChanged(PointMapItem item) {
    }

    private void refreshLayout() {
        if (_layout == null)
            return;
        _layout.post(new Runnable() {
            @Override
            public void run() {
                _recentlyAddedAdapter.notifyDataSetChanged();
            }
        });
    }

    private void _initView() {
        LayoutInflater inflater = LayoutInflater
                .from(getMapView().getContext());
        _layout = inflater
                .inflate(R.layout.enter_location_recently_added, null);

        _recentlyAddedListView = _layout
                .findViewById(R.id.enterLocationRecentlyAddedListView);
        _recentlyAddedListView
                .setOnItemClickListener(_recentlyAddedItemClickListener);
        _recentlyAddedListView.setAdapter(_recentlyAddedAdapter);
    }

    @Override
    public void disposeImpl() {
        _recentlyAddedListView = null;
        _layout = null;
    }

    private final OnItemClickListener _recentlyAddedItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, int position,
                long id) {
            GeoPoint center = ((RecentlyAdded) _recentlyAddedAdapter
                    .getItem(position)).getPoint();
            CameraController.Programmatic.panTo(
                    getMapView().getRenderer3(), center, false);
        }
    };

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownClose() {
        Intent myIntent = new Intent();
        myIntent.setAction("com.atakmap.android.maps.toolbar.END_TOOL");
        myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
        AtakBroadcast.getInstance().sendBroadcast(myIntent);
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    static protected class RecentlyAdded {

        double distance;
        double bearing;
        public boolean open;
        public String title;
        public final String uid;
        public Drawable icon;
        public final MapItem item;
        public final PointMapItem anchor;
        public int color;

        public RecentlyAdded(MapItem item, PointMapItem anchor,
                GeoPoint focusPoint) {
            this.item = item;
            this.anchor = anchor;

            this.distance = GeoCalculations.distanceTo(focusPoint, getPoint());
            this.bearing = GeoCalculations.bearingTo(focusPoint, getPoint());
            this.uid = item.getUID();
            this.open = false;
            refresh();
        }

        public GeoPoint getPoint() {
            return anchor.getPoint();
        }

        public void refresh() {
            if (this.item == null)
                return;
            this.title = ATAKUtilities.getDisplayName(this.item);
            MapItem item = this.item;
            if (item.hasMetaValue("shapeUID")
                    || item.hasMetaValue("assocSetUID"))
                // Use shape icon and color
                item = ATAKUtilities.findAssocShape(item);
            this.color = ATAKUtilities.getIconColor(item);
            this.icon = item.getIconDrawable();
        }

        public void delete() {
            this.delete(true);
        }

        private void delete(boolean removeFromGroup) {
            if (this.item == null)
                return;
            MapGroup g = item.getGroup();
            String shapeUID = item.getMetaString("shapeUID", null);
            if (g != null && removeFromGroup) {
                g.removeItem(item);
                // Remove shape if needed
                if (shapeUID != null) {
                    MapItem shape = MapView.getMapView().getRootGroup()
                            .deepFindUID(shapeUID);
                    if (shape != null)
                        shape.removeFromGroup();
                }
            }
        }

        /**
         * Get the map item w/ conversion to shape if necessary
         * @return Map item
         */
        protected MapItem getMapItem() {
            if (item != null && item.hasMetaValue("shapeUID")
                    && item.getGroup() != null) {
                MapItem shape = item.getGroup().findItem("uid",
                        item.getMetaString("shapeUID", uid));
                if (shape != null)
                    return shape;
            }
            return item;
        }
    }

    private RecentlyAdded haveRecentlyAddedInstance(MapItem instance) {
        String iUID = instance.getUID();
        return haveRecentlyAddedInstance(iUID);
    }

    private RecentlyAdded haveRecentlyAddedInstance(String instanceUID) {
        if (instanceUID == null)
            return null;
        for (RecentlyAdded ra : _recentlyAddedList) {
            if (instanceUID.equals(ra.uid)) {
                return ra;
            }
        }
        return null;
    }

    private class RecentlyAddedAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return _recentlyAddedList.size();
        }

        @Override
        public Object getItem(int position) {
            return _recentlyAddedList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return _recentlyAddedList.get(position).item.getSerialId();
        }

        @Override
        public View getView(int position, View convertView,
                final ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(parent
                        .getContext());
                convertView = inflater.inflate(
                        R.layout.enter_location_recently_added_item, null);
            }

            convertView.setVisibility(View.VISIBLE);

            final RecentlyAdded ra = _recentlyAddedList.get(position);
            final Context ctx = getMapView().getContext();

            if (ra == null)
                return convertView;

            ImageView icon = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemIcon);
            final TextView title = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemTitle);
            TextView desc = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemDesc);
            TextView dirTextDeg = convertView
                    .findViewById(
                            R.id.enterLocationRecentlyAddedItemDirTextDeg);
            TextView dirText = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemDirText);
            TextView elText = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemElText);
            final View optionsView = convertView
                    .findViewById(
                            R.id.enterLocationRecentlyAddedItemOptionsView);
            final View toggleClosed = convertView
                    .findViewById(
                            R.id.enterLocationRecentlyAddedItemToggleClosed);
            View toggleOpen = convertView
                    .findViewById(
                            R.id.enterLocationRecentlyAddedItemToggleOpen);

            ra.refresh();
            if (ra.open) {
                optionsView.setVisibility(View.VISIBLE);
                toggleClosed.setVisibility(View.GONE);
            } else {
                optionsView.setVisibility(View.GONE);
                toggleClosed.setVisibility(View.VISIBLE);
            }

            toggleClosed.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleClosed.setVisibility(View.GONE);
                    optionsView.setVisibility(View.VISIBLE);
                    ra.open = true;
                }
            });

            optionsView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    optionsView.setVisibility(View.GONE);
                    toggleClosed.setVisibility(View.VISIBLE);
                    ra.open = false;
                }
            });

            //Instead of == 1, check != 0.
            //Don't think position can ever be -, but if it is this will
            //still give the correct result.
            if ((position % 2) != 0) {
                convertView.setBackgroundColor(0x66AAAAAA);
                toggleOpen.setBackgroundColor(0xFF7A7A7A);
            } else {
                convertView.setBackgroundColor(0x66DDDDDD);
                toggleOpen.setBackgroundColor(0xFF909090);
            }

            if (_myDevice == null) {
                _myDevice = getMapView().getSelfMarker();
            }

            GeoPointMetaData currPoint;
            if (_myDevice != null) {
                currPoint = _myDevice.getGeoPointMetaData();
            } else {
                currPoint = getMapView().inverse(
                        getMapView().getWidth() / 3f, getMapView()
                                .getHeight() / 2f,
                        MapView.InverseMode.RayCast);
            }

            final double distance = GeoCalculations.distanceTo(currPoint.get(),
                    ra.getPoint());
            final double bearing = GeoCalculations.bearingTo(currPoint.get(),
                    ra.getPoint());

            ra.distance = distance;
            ra.bearing = ATAKUtilities.convertFromTrueToMagnetic(
                    currPoint.get(),
                    bearing);

            String t = ra.title;
            if (t == null || t.trim().equals("")) {
                t = ctx.getString(R.string.untitled_item);
            }
            title.setText(t);
            desc.setText("");

            // ICON
            icon.setImageDrawable(ra.icon);
            icon.setColorFilter(ra.color, Mode.MULTIPLY);

            dirTextDeg.setVisibility(View.VISIBLE);
            dirText.setVisibility(View.VISIBLE);
            elText.setVisibility(View.VISIBLE);

            dirText.setText(ctx.getString(R.string.rb_text1)
                    + SpanUtilities.formatType(Span.METRIC, ra.distance,
                            Span.METER));
            dirTextDeg
                    .setText(ctx.getString(R.string.rb_text2)
                            + AngleUtilities.format(ra.bearing,
                                    Angle.DEGREE)
                            + "M");
            elText.setText(EGM96.formatMSL(ra.getPoint()));
            desc.setText(CoordinateFormatUtilities.formatToShortString(
                    ra.getPoint(),
                    _currFormat));

            Button del = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemDelete);
            del.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                    b.setTitle(R.string.confirmation_dialogue)
                            .setMessage(
                                    ctx.getString(R.string.remove)
                                            + ra.title
                                            + ctx.getString(
                                                    R.string.question_mark_symbol))
                            .setPositiveButton(R.string.yes,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface arg0,
                                                int arg1) {
                                            _removeFromList(ra, true);
                                        }
                                    })
                            .setNegativeButton(R.string.cancel, null);
                    b.show();
                }
            });

            del.getBackground().setColorFilter(Color.RED, Mode.MULTIPLY);

            Button send = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemSend);
            send.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    MapItem actual = ra.getMapItem();
                    if (actual == null)
                        return;

                    // Prompt user to include marker attachments
                    final String uid = actual.getUID();
                    CoTInfoBroadcastReceiver.promptSendAttachments(actual,
                            null, null, new Runnable() {
                                @Override
                                public void run() {
                                    Intent contactList = new Intent(
                                            ContactPresenceDropdown.SEND_LIST);
                                    contactList.putExtra("targetUID", uid);

                                    AtakBroadcast.getInstance().sendBroadcast(
                                            contactList);
                                }
                            });
                }
            });

            send.getBackground().setColorFilter(Color.GREEN, Mode.MULTIPLY);

            Button rename = convertView
                    .findViewById(R.id.enterLocationRecentlyAddedItemRename);

            rename.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    final EditText input = new EditText(_context);
                    input.setSingleLine(true);
                    input.setText(ra.title);
                    input.selectAll();

                    AlertDialog.Builder b = new AlertDialog.Builder(_context);
                    b.setTitle(R.string.rename);
                    b.setView(input);
                    b.setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    String newName = input.getText().toString()
                                            .trim();
                                    _updateName(ra.getMapItem(), newName);
                                    title.setText(newName);
                                    ra.refresh();
                                }
                            });
                    b.setNegativeButton(R.string.cancel, null);
                    AlertDialog d = b.create();
                    Window w = d.getWindow();
                    if (w != null)
                        w.setSoftInputMode(
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    d.show();
                }
            });

            rename.getBackground()
                    .setColorFilter(Color.BLUE, Mode.MULTIPLY);

            return convertView;
        }
    }

    private void _updateName(MapItem item, String name) {
        if (item == null) {
            Log.w(TAG, "Cannot set name on marker");
            return;
        }

        // If the name changed then update it
        item.setTitle(name);
        item.persist(getMapView().getMapEventDispatcher(), null,
                this.getClass());
    }

}
