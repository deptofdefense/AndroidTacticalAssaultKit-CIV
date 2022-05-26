
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.android.gui.ActionButton;
import com.atakmap.android.gui.Selector;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;

/**
 *
 *
 * HLZ view (part of the medline)
 */
public class HLZView extends LinearLayout
        implements PointMapItem.OnPointChangedListener {
    public static final String TAG = "HLZView";

    private final Context _context;
    private MapView _mapView;
    private MedLineView _mlView;

    private ImageButton closedArrow;
    private ImageButton openArrow;
    private LinearLayout view;
    private Selector zoneProtCoord;
    private ImageButton btnCustomZone;
    private ImageButton btnDeleteZone;
    private ActionButton markedBy;
    private ActionButton obstacles;
    private ActionButton winds;
    private ActionButton friendlies;
    private ActionButton enemy;
    private ActionButton remarks;

    private static final String[] locationTypeArray = new String[4];
    Marker zonePoint;

    private PointMapItem _marker;

    public HLZView(final Context context) {
        this(context, null);
    }

    public HLZView(final Context context,
            final AttributeSet attrSet) {
        super(context, attrSet);
        this._context = context;
    }

    public void init(final MapView mapView, MedLineView mlView) {
        _mapView = mapView;
        _mlView = mlView;

        closedArrow = findViewById(R.id.arrowClosed);
        openArrow = findViewById(R.id.arrowOpen);

        view = findViewById(R.id.hlz_view);
        view.setVisibility(GONE);

        //on click for the closed arrow
        closedArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closedArrow.setVisibility(GONE);
                openArrow.setVisibility(VISIBLE);
                view.setVisibility(VISIBLE);
            }
        });

        //on click for the open arrow
        openArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openArrow.setVisibility(GONE);
                closedArrow.setVisibility(VISIBLE);
                view.setVisibility(GONE);
            }
        });

        //click listener for setting a custom zone
        btnCustomZone = findViewById(R.id.btnCustomZone);
        btnCustomZone.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setBtnCustomZone(!v.isSelected());
            }
        });

        //click listener for deleting custom zone
        btnDeleteZone = findViewById(R.id.btnDeleteZone);
        btnDeleteZone.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (zonePoint != null) {
                    removeItem();
                }
            }
        });

        markedBy = new ActionButton(
                findViewById(R.id.markedByTxt));
        markedBy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldMarked = markedBy.getText();
                if (!oldMarked.equals(""))
                    input.setText(oldMarked);
                AlertDialog.Builder ad = new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.marked))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            markedBy.setText(input
                                                    .getText().toString());

                                            saveData("marked_by",
                                                    markedBy.getText());
                                        } else {
                                            markedBy.setText(_mlView
                                                    .getLineSevenText());
                                            if (_marker
                                                    .getMetaString("marked_by",
                                                            null) != null)
                                                _marker.removeMetaData(
                                                        "marked_by");
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null);

                AlertDialog dialog = ad.create();

                Window w = dialog.getWindow();
                if (w != null) {
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();

            }
        });

        obstacles = new ActionButton(
                findViewById(R.id.obstaclesTxt));
        obstacles.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldObs = obstacles.getText();
                if (!oldObs.equals(""))
                    input.setText(oldObs);
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.obstacles))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            obstacles.setText(input
                                                    .getText().toString());

                                            saveData("obstacles",
                                                    obstacles.getText());
                                        } else
                                            obstacles.setText(oldObs);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).create();

                Window w = dialog.getWindow();
                if (w != null) {
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();
            }
        });

        winds = new ActionButton(
                findViewById(R.id.windsTxt));
        winds.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldWinds = winds.getText();
                if (!oldWinds.equals(""))
                    input.setText(oldWinds);
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.winds_from))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            winds.setText(input
                                                    .getText().toString());

                                            saveData("winds_are_from",
                                                    winds.getText());
                                        } else
                                            winds.setText(oldWinds);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).create();

                Window w = dialog.getWindow();
                if (w != null) {
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();
            }
        });

        friendlies = new ActionButton(
                findViewById(R.id.friendliesTxt));
        friendlies.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldFriend = friendlies.getText();
                if (!oldFriend.equals(""))
                    input.setText(oldFriend);
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.friendlies))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            friendlies.setText(input
                                                    .getText().toString());

                                            saveData("friendlies",
                                                    friendlies.getText());
                                        } else
                                            friendlies.setText(oldFriend);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).create();

                Window w = dialog.getWindow();
                if (w != null) {

                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();
            }
        });

        enemy = new ActionButton(
                findViewById(R.id.enemyTxt));

        enemy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldEnemy = enemy.getText();
                if (!oldEnemy.equals(""))
                    input.setText(oldEnemy);
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setMessage(_context.getString(R.string.enemy))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            enemy.setText(input
                                                    .getText().toString());

                                            saveData("enemy", enemy.getText());
                                        } else {
                                            enemy.setText(_mlView
                                                    .getLineSixText()
                                                    .substring(4));
                                            if (_marker
                                                    .getMetaString("enemy",
                                                            null) != null)
                                                _marker.removeMetaData("enemy");
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).create();

                Window w = dialog.getWindow();
                if (w != null) {
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();
            }
        });

        remarks = new ActionButton(
                findViewById(R.id.hlzRemarksTxt));

        remarks.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(_context);
                final String oldRemarks = remarks.getText();
                if (!oldRemarks.equals(""))
                    input.setText(oldRemarks);
                AlertDialog dialog = new AlertDialog.Builder(_context)
                        .setMessage(
                                _context.getString(R.string.remarks_medline))
                        .setView(input)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        if (input.getText().length() > 0) {
                                            remarks.setText(input
                                                    .getText().toString());

                                            saveData("hlz_remarks",
                                                    remarks.getText());
                                        } else
                                            remarks.setText(oldRemarks);
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null).create();

                Window w = dialog.getWindow();

                if (w != null) {
                    w.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                    |
                                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                dialog.show();
            }
        });
    }

    private void setBtnCustomZone(boolean selected) {
        btnCustomZone.setSelected(selected);
        if (selected) {
            _addMapListener();
            // Display the text prompt for the user
            TextContainer.getTopInstance().displayPrompt(
                    _context.getString(R.string.select_zone));
        } else {
            _removeMapListener();
            TextContainer.getTopInstance().closePrompt();
        }
    }

    private void removeItem() {
        _mapView.getRootGroup().removeItem(zonePoint);
        _marker.removeMetaData("zone_prot_marker");
        _marker.removeMetaData("zone_protected_coord");
        zonePoint = null;
        getLocationData(_marker.getPoint());
    }

    /**
     * Initialize the selector with the Zone
     * Protected Point's coordinates and the
     * different formats
     * @param location - the location of the Zone
     *                 Protected point
     */
    public void getLocationData(final GeoPoint location) {
        locationTypeArray[CoordinateFormat.MGRS
                .getValue()] = CoordinateFormatUtilities
                        .formatToString(location, CoordinateFormat.MGRS);
        locationTypeArray[CoordinateFormat.DD
                .getValue()] = CoordinateFormatUtilities
                        .formatToString(location, CoordinateFormat.DD);
        locationTypeArray[CoordinateFormat.DMS
                .getValue()] = CoordinateFormatUtilities
                        .formatToString(location, CoordinateFormat.DMS);
        locationTypeArray[CoordinateFormat.DM
                .getValue()] = CoordinateFormatUtilities
                        .formatToString(location, CoordinateFormat.DM);

        zoneProtCoord = new Selector(_context,
                findViewById(R.id.zoneProtCoord),
                locationTypeArray);

        zoneProtCoord.setEnabled(true);

        zoneProtCoord
                .setOnSelectionChangedListener(
                        new Selector.OnSelectionChangedListener() {
                            @Override
                            public void onSelectionChanged(String selectionText,
                                    int selectedIndex) {
                                if (selectedIndex != 0) {
                                    selectionText = insertNewLine(
                                            selectionText);
                                    zoneProtCoord.setCustomText(selectionText);
                                    ViewGroup.LayoutParams lp = findViewById(
                                            R.id.zoneProtCoord)
                                                    .getLayoutParams();
                                    lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                                } else {
                                    ViewGroup.LayoutParams lp = findViewById(
                                            R.id.zoneProt).getLayoutParams();
                                    lp.height = (int) _context.getResources()
                                            .getDimension(
                                                    R.dimen.nineline_line_height);
                                }
                                _marker.setMetaString("zone_prot_selection",
                                        String.valueOf(selectedIndex));
                            }
                        });

        zoneProtCoord.setSelection(Integer.parseInt(
                _marker.getMetaString("zone_prot_selection", "0")));
    }

    /**
     * Helper function for format Lat/Lon text to be two lines
     * @param s the string in the format lat long that needs a newline inserted.
     * @return returns the lat lon string as lat\nlon
     */
    private String insertNewLine(final String s) {
        int w = s.indexOf("W");
        int e = s.indexOf("E");
        if (w != -1) {
            String backHalf = s.substring(w);
            String firstHalf = s.substring(0, w);
            return firstHalf + "\n" + backHalf;

        } else if (e != -1) {
            String backHalf = s.substring(e);
            String firstHalf = s.substring(0, e);
            return firstHalf + "\n" + backHalf;
        } else {
            return s;
        }
    }

    /**
     * Registers the appropriate map listeners used during one of the selection
     * states.   Care should be taken to call _removeMapListener() in order to
     * restore the previous state of the Map Interface.
     */

    private void _addMapListener() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        // Save the listener state
        dispatcher.pushListeners();
        // Clear off the listener state, so no other map clicks are possible while are mini tool is
        // up
        dispatcher.clearListeners(MapEvent.ITEM_CLICK);
        dispatcher.clearListeners(MapEvent.ITEM_CONFIRMED_CLICK);
        dispatcher.clearListeners(MapEvent.ITEM_DOUBLE_TAP);
        dispatcher.clearListeners(MapEvent.ITEM_LONG_PRESS);
        dispatcher.clearListeners(MapEvent.MAP_CLICK);
        dispatcher.clearListeners(MapEvent.MAP_CONFIRMED_CLICK);
        dispatcher.clearListeners(MapEvent.MAP_DOUBLE_TAP);
        dispatcher.clearListeners(MapEvent.MAP_LONG_PRESS);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, _mapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, _mapListener);
    }

    private void _removeMapListener() {
        MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();
        // Return the dispatcher to previous listener state
        dispatcher.popListeners();
    }

    private final MapEventDispatcher.MapEventDispatchListener _mapListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            String type = event.getType();
            if (MapEvent.MAP_CLICK.equals(type)) {
                if (zonePoint != null) {
                    _mapView.getRootGroup().removeItem(zonePoint);
                }
                GeoPointMetaData p = _mapView.inverse(event.getPointF().x,
                        event.getPointF().y, MapView.InverseMode.RayCast);
                setBtnCustomZone(false);
                makeZonePoint(p.get());
                getLocationData(p.get());
                saveData("zone_protected_coord",
                        zoneProtCoord.getSelectedItem());
                saveData("zone_prot_marker",
                        zonePoint.getPoint()
                                .toStringRepresentation());
            }
        }
    };

    /**
     * Creates the Zone Protected point
     * @param p - point to be made
     */
    private void makeZonePoint(GeoPoint p) {
        MapItem mi = _mapView.getMapItem(_marker.getUID() + "." + "zonepoint");
        if (mi instanceof Marker) {
            ((Marker) mi).setPoint(p);
            zonePoint = (Marker) mi;
            return;
        }

        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                p);
        mc.setUid(_marker.getUID() + "." + "zonepoint");
        mc.setIconPath("icons/redtriangle.png");
        mc.showCotDetails(false);
        mc.setNeverPersist(true);
        mc.setType("b-m-p-c-z");
        mc.setCallsign("ZoneProt");
        Marker m = mc.placePoint();
        m.setTitle("ZoneProt");

        // set the icon
        Icon.Builder builder = new Icon.Builder();
        builder.setImageUri(0, "asset:/icons/redtriangle.png");
        builder.setAnchor(18, 18);
        m.setIcon(builder.build());

        //Listener for when the user moves a point via long press or fine adjust
        m.addOnPointChangedListener(new PointMapItem.OnPointChangedListener() {
            @Override
            public void onPointChanged(PointMapItem item) {
                getLocationData(item.getPoint());
                saveData("zone_protected_coord",
                        zoneProtCoord.getSelectedItem());
                saveData("zone_prot_marker",
                        zonePoint.getPoint()
                                .toStringRepresentation());
            }
        });

        //Listener for when the group of the point gets changed
        //called either when the drop down is closed or the point is deleted
        m.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup group) {
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup group) {
                if (!item.getMetaBoolean("__groupTransfer", false)) {
                    if (group.getItems() != null) {
                        removeItem();
                    }

                }
            }
        });

        _mapView.getRootGroup().addItem(m);
        zonePoint = m;
        /*_mapView.getMapEventDispatcher().addMapItemEventListener(
                m, new ItemClickListener());*/
    }

    /**
     * Set the marker for the view
     * @param marker set the marker to use for the HLZ view.
     */
    public void setMarker(PointMapItem marker) {
        _marker = marker;
        _marker.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup group) {

            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup group) {
                if (item instanceof Marker) {
                    if (item.equals(_marker)) {
                        removeItem();
                    }
                }
            }
        });
        loadData();
    }

    public void updateEnemy(String selection) {
        if (_marker.getMetaString("enemy", null) == null) {
            String enemyText = selection.substring(4);
            enemy.setText(enemyText);
        }
    }

    public void updateMarkedBy(String text) {
        if (_marker.getMetaString("marked_by", null) == null)
            markedBy.setText(text);
    }

    public void updateObstacles(String text) {
        if (_marker.getMetaString("obstacles", null) == null)
            obstacles.setText(text);
    }

    /**
     * Save the data to the marker's
     * metadata
     */
    public void saveData(String metaString, String text) {

        _marker.setMetaString(metaString, text);

        _marker.persist(_mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    /**
     * Load the data from metadata
     */
    public void loadData() {
        getLocationData(_marker.getPoint());
        markedBy.setText(_mlView.getMarkedBy());
        obstacles.setText(_mlView.getHLZObstacles());
        winds.setText("");
        friendlies.setText("");

        final String line6 = _mlView.getLineSixText();

        String enemyText = "";
        if (line6 != null && line6.length() > 4)
            enemyText = _mlView.getLineSixText().substring(4);

        enemy.setText(enemyText);
        remarks.setText("");

        try {
            if (_marker.getMetaString("zone_prot_marker", null) != null) {
                String point = _marker.getMetaString("zone_prot_marker", null);
                GeoPoint gp = GeoPoint.parseGeoPoint(point);

                GeoPointMetaData newPoint = ElevationManager
                        .getElevationMetadata(gp);

                //if the zone point already exists just set it to visible
                //otherwise make the new point
                if (zonePoint == null
                        || !newPoint.equals(zonePoint.getGeoPointMetaData())) {
                    makeZonePoint(newPoint.get());
                    getLocationData(newPoint.get());
                } else {
                    getLocationData(zonePoint.getPoint());
                    zonePoint.setVisible(true);
                }
            }
            if (_marker.getMetaString("marked_by", null) != null)
                markedBy.setText(
                        _marker.getMetaString("marked_by", null));
            if (_marker.getMetaString("obstacles", null) != null)
                obstacles.setText(
                        _marker.getMetaString("obstacles", null));
            if (_marker.getMetaString("winds_are_from", null) != null)
                winds.setText(
                        _marker.getMetaString("winds_are_from", null));
            if (_marker.getMetaString("friendlies", null) != null)
                friendlies.setText(
                        _marker.getMetaString("friendlies", null));
            if (_marker.getMetaString("enemy", null) != null)
                enemy.setText(
                        _marker.getMetaString("enemy", null));
            if (_marker.getMetaString("hlz_remarks", null) != null)
                remarks.setText(
                        _marker.getMetaString("hlz_remarks", null));

        } catch (Exception e) {
            Log.d(TAG, "catch against bad call to loadData()", e);
        }
    }

    public void shutdown() {
        if (zonePoint != null)
            zonePoint.setVisible(false);
        if (zoneProtCoord != null)
            zoneProtCoord.setCustomText("");
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item != _marker) {
            item.removeOnPointChangedListener(this);
        } else {
            getLocationData(item.getPoint());
        }
    }
}
