
package com.atakmap.android.routes;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

public class RouteToolbarBroadcastReceiver extends DropDownReceiver
        implements View.OnClickListener, IToolbarExtension {
    private final ActionBarView _layout;

    private static final String TAG = "RouteToolbarBroadcastReceiver";

    protected TextWidget _text;
    private final List<Tool> _tools = new ArrayList<>();
    private final RouteMapReceiver _routeMapReceiver;
    final RouteEditTool _editTool;
    private final RouteConfirmationTool _confirmTool;

    public static final String TOOLBAR_IDENTIFIER = "com.atakmap.android.routes";

    public RouteToolbarBroadcastReceiver(MapView mapView,
            RouteMapReceiver RouteMapReceiver) {
        super(mapView);
        Context context = mapView.getContext();
        _routeMapReceiver = RouteMapReceiver;

        LayoutInflater inflater = LayoutInflater.from(context);
        _layout = (ActionBarView) inflater.inflate(R.layout.route_toolbar_view,
                mapView, false);
        _layout.setPosition(ActionBarView.TOP_RIGHT);
        _layout.setEmbedState(ActionBarView.FLOATING);

        Log.d(TAG, "setting embedded on: " + this);

        _layout.findViewById(R.id.endButton).setOnClickListener(this);

        Button drawButton = _layout.findViewById(R.id.drawButton);
        drawButton.setOnClickListener(this);

        Button undoButton = _layout.findViewById(R.id.undoButton);
        undoButton.setOnClickListener(this);

        _editTool = new RouteEditTool(getMapView(), undoButton, drawButton,
                _routeMapReceiver);
        _tools.add(_editTool);

        _confirmTool = new RouteConfirmationTool(getMapView(),
                _routeMapReceiver);
        _tools.add(_confirmTool);

        // register the toolbar
        ToolbarBroadcastReceiver.getInstance().registerToolbarComponent(
                TOOLBAR_IDENTIFIER, this);
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public void onClick(View v) {
        if (!_editTool.getActive())
            return;

        int id = v.getId();

        // Undo route edit change
        if (id == R.id.undoButton)
            _editTool.undo();

        // Toggle draw mode
        else if (id == R.id.drawButton)
            _editTool.toggleDrawMode();

        // End route edit tool
        else if (id == R.id.endButton)
            _editTool.requestEndTool();
    }

    @Override
    public List<Tool> getTools() {
        return _tools;
    }

    @Override
    public ActionBarView getToolbarView() {
        return _layout;
    }

    @Override
    public boolean hasToolbar() {
        return true;
    }

    @Override
    public void onToolbarVisible(final boolean vis) {
    }

    public static void openToolbar() {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ToolbarBroadcastReceiver.OPEN_TOOLBAR)
                        .putExtra("toolbar", TOOLBAR_IDENTIFIER));
    }
}
