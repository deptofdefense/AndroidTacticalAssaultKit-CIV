
package com.atakmap.android.toolbars;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.view.LayoutInflater;
import android.widget.ImageButton;

import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.user.SelectPointButtonTool;
import com.atakmap.android.user.SpecialPointButtonTool;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class RangeAndBearingToolbar implements IToolbarExtension {

    static public final String TAG = "RangeAndBearingToolbar";
    protected static final String TOOLBAR_NAME = "com.atakmap.android.toolbars.RangeAndBearingToolbar";

    protected static final String REDX_CLICK = "com.atakmap.android.user.REDX_CLICK";
    protected static final String REDX_LONG_CLICK = "com.atakmap.android.user.REDX_LONG_CLICK";
    protected static final String REDX_OFF = "com.atakmap.android.user.REDX_OFF";

    private static RangeAndBearingToolbar _instance;

    private final SelectPointButtonTool _spbt;
    private final ArrayList<Tool> _tools;
    private final ActionBarView _toolbarView;
    private final MapView _mapView;

    synchronized public static RangeAndBearingToolbar getInstance(
            MapView mapView) {
        if (_instance == null)
            _instance = new RangeAndBearingToolbar(mapView);
        return _instance;
    }

    protected RangeAndBearingToolbar(MapView mapView) {
        _mapView = mapView;
        _tools = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(mapView.getContext());
        _toolbarView = (ActionBarView) inflater.inflate(R.layout.rab_toolbar,
                _mapView,
                false);
        _toolbarView.setPosition(ActionBarView.TOP_LEFT);

        ImageButton buttonDynamicRangeBearing = _toolbarView
                .findViewById(R.id.buttonDynamicRangeAndBearing);
        ImageButton buttonRangeBearing = _toolbarView
                .findViewById(R.id.buttonRangeAndBearing);
        ImageButton buttonCircle = _toolbarView
                .findViewById(R.id.buttonCircle);
        ImageButton buttonBullseye = _toolbarView
                .findViewById(R.id.buttonBullseye);

        RangeAndBearingEndpointMoveTool rabepTool = new RangeAndBearingEndpointMoveTool(
                _mapView);
        RangeAndBearingEndpoint.setTool(rabepTool);
        _spbt = SelectPointButtonTool.getInstance(_mapView, null);
        _tools.add(DynamicRangeAndBearingTool.getInstance(_mapView,
                buttonDynamicRangeBearing));
        _tools.add(new RangeAndBearingTool(_mapView, buttonRangeBearing));
        _tools.add(rabepTool);
        _tools.add(_spbt);
        _tools.add(RangeAndBearingCompat.createBullseyeToolInstance(_mapView,
                buttonBullseye));
        _tools.add(RangeAndBearingCompat
                .createRangeCircleButtonToolInstance(_mapView, buttonCircle));

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(REDX_CLICK);
        filter.addAction(REDX_LONG_CLICK);
        filter.addAction(REDX_OFF);
        AtakBroadcast.getInstance().registerReceiver(redXListener, filter);

        ToolbarBroadcastReceiver.getInstance().registerToolbar(TOOLBAR_NAME,
                this);

    }

    public static synchronized void dispose() {
        if (_instance != null)
            _instance.disposeImpl();
        _instance = null;
    }

    private void disposeImpl() {

        try {
            AtakBroadcast.getInstance().unregisterReceiver(redXListener);
        } catch (Exception ignored) {
        }

        for (Tool t : _tools) {
            t.dispose();
        }

        _tools.clear();

        final ToolbarBroadcastReceiver tbr = ToolbarBroadcastReceiver
                .checkInstance();
        if (tbr != null)
            tbr.unregisterToolbarComponent(TOOLBAR_NAME);
    }

    private final BroadcastReceiver redXListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            SpecialPointButtonTool.States state = SpecialPointButtonTool.States.OFF;
            if (REDX_CLICK.equals(action)) {
                state = _spbt.executeClick();
            } else if (REDX_LONG_CLICK.equals(action)) {
                state = _spbt.exectuteLongClick();
            } else if (REDX_OFF.equals(action)) {
                state = _spbt.off();
            }
            boolean selected = false, enabled = false;
            switch (state) {
                case DISABLED:
                    selected = true;
                    enabled = false;
                    break;
                case ENABLED:
                    selected = enabled = true;
                    break;
            }
            NavButtonModel mdl = NavButtonManager.getInstance()
                    .getModelByReference("redx.xml");
            mdl.setSelected(selected);
            mdl.setSelectedImage(context.getDrawable(selected && !enabled
                    ? R.drawable.nav_redx_locked
                    : R.drawable.nav_redx));
            NavButtonManager.getInstance().notifyModelChanged(mdl);
        }
    };

    public void set() {
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.toolbar.SET_TOOLBAR");
        intent.putExtra("toolbar", TOOLBAR_NAME);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    public void show() {
        set();
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.toolbar.SHOW_TOOLBAR");
        intent.putExtra("toolbar", TOOLBAR_NAME);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    public void hide() {
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.toolbar.HIDE_TOOLBAR");
        intent.putExtra("toolbar", TOOLBAR_NAME);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    @Override
    public List<Tool> getTools() {
        return _tools;
    }

    @Override
    public ActionBarView getToolbarView() {
        return _toolbarView;
    }

    @Override
    public boolean hasToolbar() {
        return true;
    }

    @Override
    public void onToolbarVisible(final boolean vis) {
    }
}
