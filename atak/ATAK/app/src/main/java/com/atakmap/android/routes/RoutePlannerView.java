
package com.atakmap.android.routes;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.gui.ColorButton;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.NonEmptyEditTextDialog;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.routes.elevation.RouteElevationBroadcastReceiver;
import com.atakmap.android.routes.elevation.model.RouteCache;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.android.routes.elevation.service.AnalyticsElevationService;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * View used for the route planner
 */
public class RoutePlannerView extends LinearLayout implements
        View.OnClickListener, Route.OnRoutePointsChangedListener,
        Route.OnRouteMethodChangedListener, Route.OnEditableChangedListener,
        MapItem.OnGroupChangedListener, AdapterView.OnItemClickListener,
        DropDown.OnStateListener, MapEventDispatcher.MapEventDispatchListener,
        SharedPreferences.OnSharedPreferenceChangeListener, Undoable,
        HashtagManager.OnUpdateListener {

    public static final Comparator<String> ALPHA_SORT = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return lhs.compareTo(rhs);
        }
    };

    private static final String TAG = "RoutePlannerView";
    protected static final int LARGE_POINT_COUNT = 5000;

    protected MapView _mapView;
    protected Context _context;
    protected SharedPreferences _prefs;
    private RoutePlannerManager _routeManager;

    protected Route _route;
    private PointMapItem[] _cps = new PointMapItem[0];
    private RouteMapReceiver _receiver;
    private RouteData _routeData;
    private DropDownReceiver _dropDown;
    private View _listeningForMapClick;
    private boolean _active = false;
    private RoutePlannerInterface _planner;
    private RouteGenerationHandler _routeGenHandler;
    protected ColorClickListener colorOnClick = new ColorClickListener();

    // Undo functionality
    private final Stack<EditAction> _undoStack = new Stack<>();
    private ActionBarView _toolbar;
    private View _undoToolbar, _undoCP, _drawButton, _endButton;

    // Children views
    private EditText _routeName;
    private ImageButton _routePlannerBtn, _editRoute;
    protected ColorButton _colorButton;
    private ImageView _routeTypeIcon;
    private TextView _routeType;
    private TextView _distTotal, _elevTotal;
    private RemarksLayout _remarksLayout;
    private ContactPointAdapter _adapter;
    private ExtraDetailsLayout _extrasLayout;

    public RoutePlannerView(Context context) {
        super(context);
    }

    public RoutePlannerView(Context context, final AttributeSet inAtr) {
        super(context, inAtr);
    }

    public DropDownReceiver init(MapView mapView, Route route,
            RouteMapReceiver receiver, RoutePlannerInterface autoPlan) {
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _route = route;
        _receiver = receiver;
        _planner = autoPlan;

        // Get the RouteMapComponent handle
        MapComponent mc = ((MapActivity) mapView.getContext())
                .getMapComponent(RouteMapComponent.class);
        _routeManager = mc != null ? ((RouteMapComponent) mc)
                .getRoutePlannerManager() : null;

        // Make sure the route is visible
        _route.setVisible(true);

        // View init
        _colorButton = findViewById(R.id.route_color);
        _colorButton.setOnClickListener(colorOnClick);
        _routeName = findViewById(R.id.route_name);

        _routeName.setOnClickListener(new NonEmptyEditTextDialog());
        _routeName.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateName();
            }
        });

        _routeType = findViewById(R.id.route_type);
        _routeTypeIcon = findViewById(R.id.route_type_icon);
        findViewById(R.id.route_type_btn).setOnClickListener(this);
        findViewById(R.id.start_nav).setOnClickListener(this);
        _routePlannerBtn = findViewById(R.id.start_route_planner);
        _routePlannerBtn.setOnClickListener(this);

        // Total distances
        _distTotal = findViewById(R.id.distance_total);
        _elevTotal = findViewById(R.id.elevation_total);
        _undoCP = findViewById(R.id.undoButton);
        _undoCP.setOnClickListener(this);

        // List of control points
        ListView cpList = findViewById(R.id.control_points_list);
        _adapter = new ContactPointAdapter();
        cpList.setAdapter(_adapter);
        cpList.setOnItemClickListener(this);

        // Remarks
        _remarksLayout = findViewById(R.id.remarksLayout);

        // Extra views for tools and plugins
        _extrasLayout = findViewById(R.id.extrasLayout);

        // Route action buttons
        findViewById(R.id.share_route).setOnClickListener(this);
        _editRoute = findViewById(R.id.edit_route);
        _editRoute.setOnClickListener(this);
        findViewById(R.id.export_route).setOnClickListener(this);
        findViewById(R.id.route_elevation_profile).setOnClickListener(this);
        findViewById(R.id.route_settings).setOnClickListener(this);

        // Initialize undo toolbar
        // The undo stack is shared between edit and non-edit mode
        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.route_toolbar_view, _mapView, false);
        _toolbar.setEmbedState(ActionBarView.FLOATING);
        _toolbar.setPosition(ActionBarView.TOP_RIGHT);
        _toolbar.showCloseButton(false);
        _undoToolbar = _toolbar.findViewById(R.id.undoButton);
        _undoToolbar.setOnClickListener(this);
        _drawButton = _toolbar.findViewById(R.id.drawButton);
        _drawButton.setOnClickListener(this);
        _endButton = _toolbar.findViewById(R.id.endButton);
        _endButton.setOnClickListener(this);
        _undoStack.clear();
        _route.setUndoable(this);

        _route.addOnRoutePointsChangedListener(this);
        _route.addOnRouteMethodChangedListener(this);
        _route.addOnEditableChangedListener(this);
        _route.addOnGroupChangedListener(this);
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _receiver.dimRoutes(_route, true, false);
        _active = true;
        refresh();

        HashtagManager.getInstance().registerUpdateListener(this);

        if (autoPlan != null && route.getNumWaypoint() >= 2) {
            PointMapItem origin = route.getMarker(0);
            PointMapItem dest = route.getMarker(route.getNumPoints() - 1);
            if (origin != null && dest != null) {
                List<GeoPoint> byWayOf = new ArrayList<>();
                _routeGenHandler = new RouteGenerationHandler(_mapView,
                        origin, dest, _route);
                RouteGenerationPackage routeGen = new RouteGenerationPackage(
                        _prefs, origin.getPoint(), dest.getPoint(), byWayOf);
                autoPlan.getRouteGenerationTask(_routeGenHandler)
                        .execute(routeGen);
            }
        }

        _dropDown = new DropDownReceiver(_mapView) {
            @Override
            public void onReceive(Context context, Intent intent) {
            }

            @Override
            public void disposeImpl() {
            }

            @Override
            public boolean onBackButtonPressed() {
                if (_listeningForMapClick != null) {
                    listenForMapClick(_listeningForMapClick);
                    return true;
                }
                return endTool();
            }

            @Override
            public String getAssociationKey() {
                return "routePreference";
            }
        };
        _dropDown.showDropDown(this, DropDownReceiver.THREE_EIGHTHS_WIDTH,
                DropDownReceiver.FULL_HEIGHT,
                DropDownReceiver.FULL_WIDTH,
                DropDownReceiver.HALF_HEIGHT, this);
        _dropDown.setAssociationKey("routePreference");
        _dropDown.setRetain(true);
        return _dropDown;
    }

    public boolean isOpen() {
        return _dropDown != null && !_dropDown.isClosed();
    }

    public void close() {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_dropDown != null && !_dropDown.isClosed())
                    _dropDown.closeDropDown();
                onClose();
            }
        });
    }

    public Route getRoute() {
        return _route;
    }

    public void startEdit() {
        if (_receiver.isNavigating())
            return;
        Bundle bundle = new Bundle();
        bundle.putString("routeUID", _route.getUID());
        bundle.putString("uid", _route.getUID());
        bundle.putBoolean("ignoreToolbar", true);
        bundle.putBoolean("scaleToFit", false);
        bundle.putBoolean("hidePane", false);
        bundle.putBoolean("handleUndo", false);
        bundle.putBoolean("dimRoutes", false);
        ToolManagerBroadcastReceiver.getInstance().startTool(
                RouteEditTool.TOOL_IDENTIFIER,
                bundle);
        refresh();
    }

    public void processingDone() {
        _routeData = RouteCache.getInstance().retrieve(_route.getTitle());
        if (_routeData != null)
            refresh();
    }

    private void onClose() {
        if (_active) {
            updateName();
            _prefs.unregisterOnSharedPreferenceChangeListener(this);
            _route.removeOnRoutePointsChangedListener(this);
            _route.removeOnRouteMethodChangedListener(this);
            _route.removeOnEditableChangedListener(this);
            _route.removeOnGroupChangedListener(this);
            _route.setUndoable(null);
            if (!_receiver.isNavigating())
                _receiver.dimRoutes(false);

            // Unregister hashtags/remarks listener
            HashtagManager.getInstance().unregisterUpdateListener(this);

            // Update remarks
            String remarks = _remarksLayout.getText();
            if (remarks != null && !FileSystemUtils.isEquals(remarks,
                    _route.getRemarks()))
                _route.setRemarks(remarks);

            // Reset alpha to 255 temporarily so the route is persisted correctly
            if (_route.hasMetaValue("archive")) {
                int alpha = Color.alpha(_route.getColor());
                if (alpha < 255)
                    _route.resetAlpha();
                _route.persist(_mapView.getMapEventDispatcher(), null,
                        RoutePlannerView.class);
                if (alpha < 255)
                    _route.setAlpha(alpha);
            }

            RouteElevationBroadcastReceiver.getInstance().stopProcessing();

            // Close the undo toolbar
            ActionBarView toolView = ActionBarReceiver.getInstance()
                    .getToolView();
            if (toolView == _toolbar)
                ActionBarReceiver.getInstance().setToolView(null, false);
            _active = false;
        }
    }

    @Override
    public void onHashtagsUpdate(HashtagContent content) {
        if (content == _route)
            refresh();
    }

    @Override
    public boolean run(final EditAction action) {
        boolean success = action.run();
        if (success) {
            synchronized (_undoStack) {
                _undoStack.push(action);
            }
            refresh();
        }
        return success;
    }

    @Override
    public void undo() {
        EditAction act = null;
        synchronized (_undoStack) {
            if (_undoStack.size() > 0)
                act = _undoStack.pop();
        }
        if (act != null) {
            try {
                act.undo();
                refresh();
            } catch (Exception e) {
                Log.d(TAG, "error occurred attempting to undo.", e);
            }
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(MapMenuReceiver.HIDE_MENU));
        }
    }

    @Override
    public void onRoutePointsChanged(Route route) {
        if (route.isBulkOperation())
            return;
        refresh();
    }

    @Override
    public void onRouteMethodChanged(Route route) {
        refresh();
    }

    @Override
    public void onEditableChanged(EditablePolyline polyline) {
        refresh();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_route == item)
            close();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals("rab_rng_units_pref"))
            _adapter.refresh();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            _dropDown.getDropDown().setCloseBeforeTool(true);
        refresh();
    }

    @Override
    public void onDropDownSizeChanged(double w, double h) {
    }

    @Override
    public void onDropDownClose() {
        onClose();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();

        // Undo route edit
        if (i == R.id.undoButton)
            undo();

        // Toggle draw mode
        else if (i == R.id.drawButton) {
            RouteEditTool tool = getEditTool();
            if (tool != null) {
                tool.toggleDrawMode();
                refresh();
            }
        }

        // End edit tool
        else if (i == R.id.endButton)
            endTool();

        // Start route navigation
        else if (i == R.id.start_nav) {
            DropDownManager.getInstance().closeAllDropDowns();

            Intent routeNavigatorIntent = new Intent(
                    RouteMapReceiver.START_NAV);
            routeNavigatorIntent.putExtra("routeUID", _route.getUID());
            AtakBroadcast.getInstance().sendBroadcast(routeNavigatorIntent);
        }

        // Plan route using interface such as VNS
        else if (i == R.id.start_route_planner) {
            int nPts = _route.getNumPoints();
            if (nPts > 1)
                RouteMapReceiver.promptPlanRoute(_mapView,
                        _route.getPointMapItem(0),
                        _route.getPointMapItem(nPts - 1), _route, true);
        }

        // Toggle route edit mode
        else if (i == R.id.edit_route) {
            if (!isSelected())
                startEdit();
            else
                endTool();
        }

        // Change route attributes
        else if (i == R.id.route_type_btn) {
            AlertDialog.Builder b = RouteCreationDialog.getDetailsDialog(
                    _route, _context, false);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            refresh();
                            _route.refresh(_mapView.getMapEventDispatcher(),
                                    null, RoutePlannerView.class);
                        }
                    });
            AlertDialog ad = b.create();
            ad.show();
        }

        // Send route to contacts
        else if (i == R.id.share_route) {
            updateName();
            endTool();
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            RouteMapReceiver.SHARE_ACTION)
                                    .putExtra("routeUID", _route.getUID()));
                }
            });
        }

        // Post to make sure this happens after any name changes
        else if (i == R.id.export_route) {
            updateName();
            endTool();
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    AtakBroadcast.getInstance().sendBroadcast(new Intent(
                            RouteMapReceiver.ROUTE_EXPORT)
                                    .putExtra("routeUID", _route.getUID()));
                }
            });
        }

        // Show route elevation profile
        else if (i == R.id.route_elevation_profile) {
            int numPoints = _route.getNumPoints();
            if (numPoints < 2) {
                Toast.makeText(
                        _context,
                        "Elevation profile not available. Route contains too few points.",
                        Toast.LENGTH_SHORT).show();
                return;
            } else if (numPoints > LARGE_POINT_COUNT) {
                Toast.makeText(
                        _context,
                        "Elevation profile not available. Route contains too many points.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            updateName();
            RouteElevationBroadcastReceiver.getInstance().setRoute(_route);
            RouteElevationBroadcastReceiver.getInstance().setTitle(
                    _route.getTitle());
            RouteElevationBroadcastReceiver.getInstance().openDropDown();
        }

        // Bring up route preferences
        else if (i == R.id.route_settings) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    "com.atakmap.app.ADVANCED_SETTINGS")
                            .putExtra("toolkey", "routePreference"));
        }

        // End the tool
        else if (i == R.id.close)
            endTool();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
        if (pos < _cps.length)
            MapTouchController.goTo(_cps[pos], true);
    }

    protected RouteEditTool getEditTool() {
        Tool tool = ToolManagerBroadcastReceiver.getInstance().getActiveTool();
        return tool instanceof RouteEditTool ? (RouteEditTool) tool : null;
    }

    protected boolean endTool() {
        RouteEditTool tool = getEditTool();
        if (tool != null) {
            tool.requestEndTool();
            synchronized (_undoStack) {
                _undoStack.clear();
            }
            refresh();
            return true;
        }
        return false;
    }

    protected void refresh() {
        if (_routeName == null)
            return;

        // The below may be fired from a non-UI thread, so taking precautions here
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (!_active)
                    return;
                _routeName.setText(_route.getTitle());
                String remarks = _route.getRemarks();
                if (!FileSystemUtils.isEquals(_remarksLayout.getText(),
                        remarks))
                    _remarksLayout.setText(remarks);
                _editRoute.setSelected(_route.getEditable());
                findViewById(R.id.edit_route_spacer).setVisibility(
                        _editRoute.getVisibility());
                _routeType.setText(
                        Route.getLocalName(_route.getRouteType().resourceid)
                                + ", "
                                + Route.getLocalName(
                                        _route.getRouteDirection().resourceid));
                _routeTypeIcon.setImageBitmap(ATAKUtilities.getUriBitmap(
                        "android.resource://" + _context.getPackageName()
                                + "/" + _route.getRouteMethod().iconId));
                setupRoutePlannerButton(_routePlannerBtn);
                _colorButton.setColor(_route.getColor());
                _cps = _route.getContactPoints();
                _adapter.refresh();

                RouteEditTool tool = getEditTool();
                boolean editing = tool != null;
                _endButton.setVisibility(editing ? View.VISIBLE : View.GONE);
                _drawButton.setVisibility(editing ? View.VISIBLE : View.GONE);
                if (tool != null)
                    _drawButton.setSelected(tool.inDrawMode());

                // Toggle toolbar based on editing state
                ActionBarView toolbar = ActionBarReceiver.getInstance()
                        .getToolView();
                if (_toolbar != toolbar && editing)
                    ActionBarReceiver.getInstance().setToolView(_toolbar);
                else if (_toolbar == toolbar && !editing)
                    ActionBarReceiver.getInstance().setToolView(null, false);

                // Update the undo button and toolbar
                synchronized (_undoStack) {
                    _undoToolbar.setEnabled(!_undoStack.isEmpty());
                    _undoCP.setEnabled(!_undoStack.isEmpty());
                    _undoCP.setVisibility(editing ? View.INVISIBLE
                            : View.VISIBLE);
                }

                _extrasLayout.setItem(_route);
            }
        });

    }

    /**
     * Simplification method that will allow for the visibility of a button
     * to be set based on a boolean.
     */
    private void setupRoutePlannerButton(final ImageButton routePlannerBtn) {
        //routePlannerBtn.setEnabled(_routeManager.getCount() > 0);

        final boolean enabled = _routeManager != null
                && _routeManager.getCount() > 0;

        routePlannerBtn.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    protected void updateName() {
        String name;
        if (_routeName != null && _routeName.getText() != null
                && !FileSystemUtils.isEquals(_route.getTitle(),
                        (name = _routeName.getText().toString().trim())))
            _route.setTitle(name);
    }

    private void listenForMapClick(View v) {
        if (_listeningForMapClick == v) {
            // Deactivate listener
            _listeningForMapClick = null;
            _mapView.getMapEventDispatcher().popListeners();
            TextContainer.getInstance().closePrompt();
        } else if (_listeningForMapClick == null) {
            // Activate listener
            _listeningForMapClick = v;
            _mapView.getMapEventDispatcher().pushListeners();
            _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, this);
        } else {
            // Switch to other listener
            _listeningForMapClick = v;
        }
        refresh();
    }

    @Override
    public void onMapEvent(MapEvent event) {
        listenForMapClick(_listeningForMapClick);
    }

    protected class ColorClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.details_text28);
            ColorPalette palette = new ColorPalette(_context,
                    _route.getColor());
            b.setView(palette);
            final AlertDialog alert = b.create();
            OnColorSelectedListener l = new OnColorSelectedListener() {
                @Override
                public void onColorSelected(int color, String label) {
                    _route.setColor(color);
                    refresh();
                    alert.dismiss();
                    _prefs.edit().putInt("route_last_selected_color", color)
                            .apply();
                }
            };
            palette.setOnColorSelectedListener(l);
            alert.show();
        }
    }

    private class ContactPointAdapter extends BaseAdapter {

        private final List<String[]> _details = new ArrayList<>();
        private int _distFmt;

        ContactPointAdapter() {
            refresh();
        }

        void refresh() {
            List<String[]> details = _route.getRouteDetails();

            double dist = _route.getTotalDistance();
            _distFmt = Integer.parseInt(_prefs.getString("rab_rng_units_pref",
                    String.valueOf(Span.METRIC)));

            // Get miles/ft distance
            String distEngString = "--";
            if (!Double.isNaN(dist)) {
                int unit = Span.ENGLISH;
                if (_route.getRouteMethod().equals(Route.RouteMethod.Flying)
                        || _route.getRouteMethod().equals(
                                Route.RouteMethod.Watercraft))
                    unit = Span.NM;
                distEngString = SpanUtilities.formatType(unit, dist,
                        Span.METER);
            }

            // Get metrics distance
            String distMetString = "--";
            if (!Double.isNaN(dist))
                distMetString = SpanUtilities.formatType(Span.METRIC, dist,
                        Span.METER);

            // Get total elevation gain
            String gainString = "--";
            if (_routeData == null
                    && GeoPoint.isAltitudeValid(
                            _route.getMaxAltitude().get().getAltitude())
                    && GeoPoint.isAltitudeValid(
                            _route.getMinAltitude().get().getAltitude()))
                gainString = "+ " + SpanUtilities.format(
                        _route.getMaxAltitude().get().getAltitude()
                                - _route.getMinAltitude().get().getAltitude(),
                        Span.METER, Span.FOOT);
            else if (_routeData != null && _routeData.getTotalGain() != 0)
                gainString = "+ " + SpanUtilities.format(
                        _routeData.getTotalGain(), Span.FOOT, Span.FOOT);

            if (_routeData != null) {
                double[] gain = AnalyticsElevationService
                        .findContactPointElevationGain(_routeData
                                .getControlPointData().getIndices(),
                                _routeData.getGeoPoints());
                if (gain != null) {
                    // Update elevation gain
                    for (int i = 0; i < gain.length
                            && i < details.size(); i++) {
                        String g = "--";
                        if (gain[i] != 0)
                            g = "+ " + SpanUtilities.format(gain[i], Span.FOOT,
                                    Span.FOOT);
                        details.get(i)[3] = g;
                    }
                }
            }

            _distTotal.setText(_distFmt == Span.METRIC ? distMetString
                    : distEngString);
            _elevTotal.setText(gainString);

            _details.clear();
            if (details != null)
                _details.addAll(details);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return _details.size();
        }

        @Override
        public String[] getItem(int position) {
            return _details.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View row, ViewGroup parent) {
            ViewHolder holder = row != null ? (ViewHolder) row.getTag() : null;
            if (holder == null) {
                row = LayoutInflater.from(_context).inflate(
                        R.layout.route_planner_contact_point_row,
                        parent, false);
                holder = new ViewHolder();
                holder.name = row.findViewById(R.id.cp_name);
                holder.dist = row.findViewById(R.id.cp_dist);
                holder.distEl = row.findViewById(R.id.cp_elevation);
                holder.dir = row.findViewById(R.id.cp_dir);
                holder.routePlanner = row.findViewById(R.id.cp_plan_segment);
                holder.remove = row.findViewById(R.id.cp_remove);
                row.setTag(holder);
            }

            String[] data = getItem(position);

            holder.name.setText(data[0]);

            holder.dist.setText(_distFmt == Span.METRIC
                    ? data[2]
                    : data[1]);
            holder.distEl.setText(data[3]);

            setupRoutePlannerButton(holder.routePlanner);
            row.setBackgroundColor(0);

            NavigationCue cue = _route.getCueForPoint(data[4]);
            int cueRes = 0;
            if (cue != null) {
                String txt = cue.getTextCue();
                cueRes = getNavigationIcon(txt);
            }
            holder.dir.setImageResource(cueRes);

            if (position < _cps.length) {
                RowListener rListener = new RowListener(position);
                if (position > 0) {
                    setupRoutePlannerButton(holder.routePlanner);
                } else {
                    holder.routePlanner.setVisibility(View.INVISIBLE);
                }
                holder.name.setOnClickListener(rListener);
                holder.routePlanner.setOnClickListener(rListener);
                holder.dir.setOnClickListener(rListener);
                holder.remove.setOnClickListener(rListener);
            }

            return row;
        }

    }

    private class RowListener implements View.OnClickListener {
        final int index;
        final PointMapItem cp;
        final PointMapItem prevPoint;
        final PointMapItem nextPoint;
        final String name, nextName;

        RowListener(int index) {
            this.index = index;
            this.cp = _cps[index];
            this.name = ATAKUtilities.getDisplayName(cp);
            this.prevPoint = index > 0 ? _cps[index - 1] : null;
            this.nextPoint = index < _cps.length - 1 ? _cps[index + 1] : null;
            this.nextName = nextPoint != null ? ATAKUtilities
                    .getDisplayName(nextPoint) : null;
        }

        @Override
        public void onClick(View v) {
            AlertDialog.Builder b;
            int i = v.getId();
            if (i == R.id.cp_name) {
                final EditText et = new EditText(_context);
                et.setSingleLine(true);
                et.setText(name);
                et.setSelection(name.length());
                b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.rename);
                b.setView(et);
                b.setPositiveButton(R.string.done,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                int i = _route.getIndexOfMarker(cp);
                                if (i == -1)
                                    return;
                                String name = et.getText().toString();
                                if (FileSystemUtils.isEmpty(name)) {
                                    if (index == 0)
                                        name = _route.getFirstWaypointName();
                                    else if (index == _cps.length - 1)
                                        name = _route.getLastWaypointName();
                                    else
                                        name = _route.getPrefix() + index;
                                }
                                EditAction act = _route.new RouteSetPointName(
                                        i, name);
                                Undoable undo = _route.getUndoable();
                                if (undo != null)
                                    undo.run(act);
                                else
                                    act.run();
                                refresh();
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                final AlertDialog d = b.create();
                if (d.getWindow() != null) {
                    d.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }
                d.show();
                et.setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                            KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_DONE)
                            d.getButton(DialogInterface.BUTTON_POSITIVE)
                                    .performClick();
                        return false;
                    }
                });
            } else if (i == R.id.cp_plan_segment) {
                if (prevPoint == null)
                    return;
                RouteMapReceiver.promptPlanRoute(_mapView, prevPoint, cp,
                        _route, false);

            } else if (i == R.id.cp_dir) {
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(RouteMapReceiver.EDIT_CUES_ACTION)
                                .putExtra("routeUID", _route.getUID())
                                .putExtra("uid", cp.getUID()));

            } else if (i == R.id.cp_remove) {
                promptRemoveWaypoint();
            }
        }

        private void promptRemoveWaypoint() {
            View v = LayoutInflater.from(_context).inflate(
                    R.layout.route_remove_waypoint_dialog,
                    _mapView, false);
            final RadioButton delWaypoint = v.findViewById(
                    R.id.route_remove_waypoint);
            final RadioButton delVerts = v.findViewById(
                    R.id.route_remove_vertices);
            delWaypoint.setText(
                    _context.getString(R.string.route_remove_waypoint, name));
            if (nextName != null && nextPoint != null)
                delVerts.setText(_context.getString(
                        R.string.route_remove_vertices, name, nextName));
            else {
                delWaypoint.setChecked(true);
                delVerts.setVisibility(View.GONE);
            }
            if (_cps.length < 3) {
                delVerts.setChecked(true);
                delWaypoint.setVisibility(View.GONE);
            }
            if (delVerts.getVisibility() == View.GONE
                    && delWaypoint.getVisibility() == View.GONE) {
                Toast.makeText(_context, _context.getString(
                        R.string.route_cannot_remove_waypoint, name),
                        Toast.LENGTH_LONG).show();
                return;
            }

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setView(v);
            b.setPositiveButton(R.string.delete_no_space,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            EditAction act = null;
                            if (delVerts.isChecked()) {
                                // Remove all vertices between 2 way points
                                int start = _route.getIndexOfMarker(cp);
                                int end = _route.getIndexOfMarker(nextPoint);
                                if (start != -1 && end != -1 && start + 1 < end)
                                    act = new RemoveVerticesAction(_route,
                                            start + 1, end);
                            } else if (delWaypoint.isChecked()) {
                                if (prevPoint == null || nextPoint == null) {
                                    promptRemoveEndWaypoint(cp,
                                            prevPoint == null);
                                } else {
                                    // Remove a single waypoint, without removing underlying vertex
                                    act = _route.new RouteRemoveMarkerAction(
                                            cp);
                                }
                            }

                            if (act != null) {
                                // Insert action into available undo stack
                                Undoable undo = _route.getUndoable();
                                if (undo != null)
                                    undo.run(act);
                                else
                                    act.run();
                                refresh();
                            }
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        private void promptRemoveEndWaypoint(final PointMapItem cp,
                final boolean start) {
            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setMessage(
                    _context.getString(R.string.route_remove_end_waypoint_msg,
                            ATAKUtilities.getDisplayName(cp)));
            b.setPositiveButton(R.string.delete_no_space,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            if (_cps.length < 3)
                                return;
                            int s = start ? 0
                                    : _route.getIndexOfMarker(
                                            _cps[_cps.length - 2]) + 1;
                            int e = start ? _route.getIndexOfMarker(_cps[1])
                                    : _route.getNumPoints();
                            if (s < 0 || e < 0 | s >= e)
                                return;
                            EditAction act = new RemoveVerticesAction(_route, s,
                                    e);
                            Undoable undo = _route.getUndoable();
                            if (undo != null)
                                undo.run(act);
                            else
                                act.run();
                            _route.setTitle(_route.getTitle());
                            refresh();
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    private static class ViewHolder {
        Button name;
        TextView dist, distEl;
        ImageView dir;
        ImageButton routePlanner, remove;
    }

    /**
     * Determine the appropriate navigation icon to use based on nav text
     * @param txt Navigation text instruction
     * @return Icon resource ID
     */
    public static int getNavigationIcon(String txt) {
        if (!FileSystemUtils.isEmpty(txt)) {
            txt = txt.toLowerCase(LocaleUtil.getCurrent());
            if (txt.contains("stay left")
                    || txt.contains("keep left")
                    || txt.contains("exit left")
                    || txt.contains("slight left")
                    || txt.contains("on the left"))
                return R.drawable.navcue_btn_stayleft;
            else if (txt.contains("hard left")
                    || txt.contains("hard turn left")
                    || txt.contains("sharp left"))
                return R.drawable.navcue_btn_hardleft;
            else if (txt.contains("turn left")
                    || txt.contains("turns left")
                    || txt.contains("make a left"))
                return R.drawable.navcue_btn_turnleft;
            else if (txt.contains("stay right")
                    || txt.contains("keep right")
                    || txt.contains("exit right")
                    || txt.contains("slight right")
                    || txt.contains("take exit"))
                return R.drawable.navcue_btn_stayright;
            else if (txt.contains("hard right")
                    || txt.contains("hard turn right")
                    || txt.contains("sharp right"))
                return R.drawable.navcue_btn_hardright;
            else if (txt.contains("turn right")
                    || txt.contains("turns right")
                    || txt.contains("make a right"))
                return R.drawable.navcue_btn_turnright;
            else if (txt.contains("stay straight")
                    || txt.contains("keep straight")
                    || txt.contains("continue straight")
                    || txt.contains("continue onto"))
                return R.drawable.navcue_btn_straight;
            else if (txt.contains("speed up"))
                return R.drawable.navcue_btn_speedup;
            else if (txt.contains("slow down"))
                return R.drawable.navcue_btn_slowdown;
            else if (txt.contains("danger") || txt.contains("yield"))
                return R.drawable.navcue_btn_caution1;
            else if (txt.contains("stop"))
                return R.drawable.navcue_btn_stop1;
            else
                return R.drawable.navcue_btn_other;
        }
        return 0;
    }

    static class PlanRouteAction extends EditAction {

        private final Route _route;
        private final PointMapItem _origin, _dest;
        private final RoutePointPackage _routePointPackage;

        // Assigned during run
        private int _indexOfOrigin = -1;
        private List<PointMapItem> _removed;
        private Map<String, NavigationCue> _removedCues;

        PlanRouteAction(Route route, PointMapItem origin,
                PointMapItem dest, RoutePointPackage rpp) {
            _route = route;
            _origin = origin;
            _dest = dest;
            _routePointPackage = rpp;
        }

        @Override
        public boolean run() {
            _indexOfOrigin = _route.getIndexOfPoint(_origin);
            int indexOfDest = _route.getIndexOfPoint(_dest);

            if (_indexOfOrigin == -1 || indexOfDest == -1)
                return false;

            List<PointMapItem> points = _routePointPackage.getRoutePoints();
            Map<String, NavigationCue> cues = _routePointPackage.getPointCues();
            _removedCues = _route.getNavigationCues();
            _removed = replacePoints(_indexOfOrigin, indexOfDest, points, cues);
            return true;
        }

        @Override
        public void undo() {
            int indexOfDest = _route.getIndexOfPoint(_dest);
            if (_indexOfOrigin > -1 && indexOfDest != -1
                    && !FileSystemUtils.isEmpty(_removed))
                replacePoints(_indexOfOrigin, indexOfDest, _removed,
                        _removedCues);
        }

        @Override
        public String getDescription() {
            return null;
        }

        private List<PointMapItem> replacePoints(int indexOfOrigin,
                int indexOfDest, List<PointMapItem> points,
                Map<String, NavigationCue> cues) {

            _route.setMetaBoolean("__ignoreRefresh", true);

            // Remove point listeners while route is being processed
            long start = SystemClock.elapsedRealtime();
            List<PointMapItem> removed = _route.removePoints(
                    indexOfOrigin, indexOfDest);
            Log.d(TAG, "Finished removing points from " + _route.getTitle()
                    + " in " + (SystemClock.elapsedRealtime() - start) + "ms");

            start = SystemClock.elapsedRealtime();
            _route.addMarkers(indexOfOrigin, points);
            Log.d(TAG, "Finished adding sub-route to " + _route.getTitle()
                    + " in " + (SystemClock.elapsedRealtime() - start) + "ms");

            // Move cues to new route
            start = SystemClock.elapsedRealtime();
            Map<String, NavigationCue> existingCues = _route
                    .getNavigationCues();
            existingCues.putAll(cues);
            _route.setNavigationCues(existingCues);
            Log.d(TAG, "Finished moving cues to " + _route.getTitle()
                    + " in " + (SystemClock.elapsedRealtime() - start) + "ms");

            _route.setMetaBoolean("__ignoreRefresh", false);
            _route.fixSPandVDO(false);
            MapView mv = MapView.getMapView();
            if (mv != null)
                _route.refresh(mv.getMapEventDispatcher(), null, getClass());
            return removed;
        }
    }

    private static class RemoveVerticesAction extends EditAction {

        private final Route _route;
        private final int _start, _end;

        // Assigned during run
        private List<PointMapItem> _removed;
        private Map<String, NavigationCue> _cues;

        RemoveVerticesAction(Route route, int start, int end) {
            _route = route;
            _start = start;
            _end = end;
        }

        @Override
        public boolean run() {
            _cues = _route.getNavigationCues();
            _removed = _route.removePoints(_start, _end);
            return true;
        }

        @Override
        public void undo() {
            if (!FileSystemUtils.isEmpty(_removed)) {
                _route.addMarkers(_start, _removed);
                _route.setNavigationCues(_cues);
            }
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Log.d(TAG, "RoutePlannerView detached from window");

        if (_routeGenHandler != null) {
            _routeGenHandler.getDialog().dismiss();
        }
    }
}
