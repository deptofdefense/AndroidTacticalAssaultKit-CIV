
package com.atakmap.android.fires;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.app.BuildConfig;

import com.atakmap.android.gui.FastMGRS;
import com.atakmap.android.data.DataMgmtReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.toolbars.DynamicRangeAndBearingTool;
import com.atakmap.android.toolbars.LaserBasketDisplayTool;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.user.PlaceHostileTool;
import com.atakmap.android.user.IntentLauncherTool;
import com.atakmap.android.user.SpecialPointButtonTool;
import com.atakmap.android.user.SpiButtonTool;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

public class FiresToolbar extends BroadcastReceiver implements
        IToolbarExtension, OnTouchListener, View.OnLongClickListener,
        OnSharedPreferenceChangeListener {

    public static final String TAG = "FiresToolbar";
    private static final String TOOLBAR_NAME = "com.atakmap.android.toolbars.FiresToolbar";

    private static FiresToolbar _instance;
    private final ArrayList<Tool> _tools;
    private ActionBarView _toolbarView;
    private final MapView _mapView;
    private final TextContainer _container;
    private final FastMGRS fMGRS;
    private boolean shouldClick = false;
    final Handler handler = new Handler();
    Runnable mLongPressed = null;

    private SharedPreferences prefs;

    /**
     * Get a new instance of the FiresToolbar if one doesn't exist.  If it does, return that
     * instance.
     * @param mapView MapView to pass to the constructor
     * @return FiresToolbar instance
     */

    synchronized public static FiresToolbar getInstance(final MapView mapView) {
        if (_instance == null)
            _instance = new FiresToolbar(mapView);
        return _instance;
    }

    private FiresToolbar(MapView mapView) {
        _mapView = mapView;
        _tools = new ArrayList<>();
        fMGRS = new FastMGRS(_mapView, true);

        ToolbarBroadcastReceiver.getInstance().registerToolbar(TOOLBAR_NAME,
                this);

        /* Register this toolbar as a listner for the zeroize functionality */
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(DataMgmtReceiver.ZEROIZE_CONFIRMED_ACTION);
        AtakBroadcast.getInstance().registerReceiver(this, filter);
        _container = TextContainer.getInstance();
    }

    public static synchronized void dispose() {
        if (_instance != null)
            _instance.disposeImpl();
        _instance = null;
    }

    private void disposeImpl() {
        if (prefs != null)
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        for (Tool t : _tools) {
            t.dispose();
        }

        final ToolbarBroadcastReceiver tbr = ToolbarBroadcastReceiver
                .checkInstance();
        if (tbr != null)
            tbr.unregisterToolbarComponent(TOOLBAR_NAME);

        AtakBroadcast.getInstance().unregisterReceiver(_instance);
    }

    @Override
    public List<Tool> getTools() {
        return _tools;
    }

    @Override
    public ActionBarView getToolbarView() {

        if (_toolbarView == null) {
            Log.e(TAG, "Creating Fires Overlay");
            LayoutInflater inflater = LayoutInflater
                    .from(_mapView.getContext());
            _toolbarView = (ActionBarView) inflater.inflate(
                    R.layout.cas_toolbar, _mapView,
                    false);
            ImageButton buttonSPI1 = _toolbarView
                    .findViewById(R.id.buttonSPI1);
            buttonSPI1.setOnTouchListener(this);
            ImageButton buttonSPI2 = _toolbarView
                    .findViewById(R.id.buttonSPI2);
            buttonSPI2.setOnTouchListener(this);
            ImageButton buttonSPI3 = _toolbarView
                    .findViewById(R.id.buttonSPI3);
            buttonSPI3.setOnTouchListener(this);
            ImageButton buttonLaser = _toolbarView
                    .findViewById(R.id.buttonLaser);
            ImageButton buttonDynamicRAB = _toolbarView
                    .findViewById(R.id.buttonCASDynamicRangeAndBearing);
            buttonDynamicRAB.setOnTouchListener(this);
            ImageButton buttonPlaceHostile = _toolbarView
                    .findViewById(R.id.buttonPlaceHostile);
            buttonPlaceHostile.setOnTouchListener(this);

            ImageButton hostileManager = _toolbarView
                    .findViewById(R.id.hostileManager);
            hostileManager.setOnTouchListener(this);

            ImageButton mgrsGoto = _toolbarView
                    .findViewById(R.id.mgrsGoto);
            mgrsGoto.setOnTouchListener(this);

            mgrsGoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (!BuildConfig.MIL_CAPABILITIES) {
                        Intent i = new Intent();
                        i.setAction("com.atakmap.android.user.GO_TO");
                        AtakBroadcast.getInstance().sendBroadcast(i);
                        //shb
                    } else {
                        fMGRS.show();
                    }
                }
            });
            mgrsGoto.setOnLongClickListener(this);

            _tools.add(new SpiButtonTool(_mapView, buttonSPI1, 1));
            _tools.add(new SpiButtonTool(_mapView, buttonSPI2, 2));
            _tools.add(new SpiButtonTool(_mapView, buttonSPI3, 3));
            _tools.add(new LaserBasketDisplayTool(_mapView, buttonLaser));
            _tools.add(DynamicRangeAndBearingTool.getInstance(_mapView,
                    buttonDynamicRAB));
            _tools.add(new PlaceHostileTool(_mapView, buttonPlaceHostile));
            _tools.add(new IntentLauncherTool(_mapView, hostileManager,
                    "hostileManager",
                    "com.atakmap.android.maps.MANAGE_HOSTILES"));

            prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                    .getContext());
            prefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(prefs, "legacyFiresToolbarMode");
            onSharedPreferenceChanged(prefs, "firesNumberOfSpis");

            // instead of having a second layout, just set the layout items to gone
            if (!BuildConfig.MIL_CAPABILITIES) {
                _toolbarView.findViewById(R.id.hostileManager).setVisibility(
                        View.GONE);
                _toolbarView.findViewById(R.id.buttonLaser).setVisibility(
                        View.GONE);
                _toolbarView.findViewById(R.id.buttonPlaceHostile)
                        .setVisibility(
                                View.GONE);
            }

        }
        return _toolbarView;
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {
        if (key.equals("legacyFiresToolbarMode")) {
            if (prefs.getBoolean(key, false)) {
                if (BuildConfig.MIL_CAPABILITIES) {
                    _toolbarView.findViewById(R.id.hostileManager)
                            .setVisibility(
                                    View.GONE);
                }
                _toolbarView.findViewById(R.id.buttonCASDynamicRangeAndBearing)
                        .setVisibility(View.VISIBLE);

                if (BuildConfig.MIL_CAPABILITIES) {
                    _toolbarView.findViewById(R.id.buttonLaser).setVisibility(
                            View.VISIBLE);
                }
            } else {
                if (BuildConfig.MIL_CAPABILITIES) {
                    _toolbarView.findViewById(R.id.hostileManager)
                            .setVisibility(
                                    View.VISIBLE);
                }
                _toolbarView.findViewById(R.id.buttonCASDynamicRangeAndBearing)
                        .setVisibility(View.GONE);
                if (BuildConfig.MIL_CAPABILITIES) {
                    _toolbarView.findViewById(R.id.buttonLaser).setVisibility(
                            View.GONE);
                }
            }
        } else if (key.equals("firesNumberOfSpis")) {
            String numStr = prefs.getString("firesNumberOfSpis", "1");
            int num = 1;
            try {
                num = Integer.parseInt(numStr);
            } catch (NumberFormatException ignore) {
            }
            if (num >= 2) {
                _toolbarView.findViewById(R.id.buttonSPI2).setVisibility(
                        View.VISIBLE);
            } else {
                _toolbarView.findViewById(R.id.buttonSPI2).setVisibility(
                        View.GONE);
            }

            if (num >= 3) {
                _toolbarView.findViewById(R.id.buttonSPI3).setVisibility(
                        View.VISIBLE);
            } else {
                _toolbarView.findViewById(R.id.buttonSPI3).setVisibility(
                        View.GONE);
            }
        }

    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_mapView.getContext(), R.string.mgrs_goto,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public boolean hasToolbar() {
        return true;
    }

    @Override
    public boolean onTouch(final View view, MotionEvent motionEvent) {

        _container.closePrompt();
        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                shouldClick = true;
                handler.postDelayed(mLongPressed = new Runnable() {
                    @Override
                    public void run() {
                        shouldClick = false;
                        view.performLongClick();
                    }
                }, android.view.ViewConfiguration.getLongPressTimeout());

                break;
            case MotionEvent.ACTION_UP:
                if (mLongPressed != null)
                    handler.removeCallbacks(mLongPressed);

                if (shouldClick) {
                    view.performClick();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
        }
        _toolbarView.invalidate();
        return true;
    }

    @Override
    public void onToolbarVisible(final boolean vis) {
        Log.d(TAG, "toolbar visible: " + vis);
        if (!vis) {
            _container.closePrompt();
        } else {
            _container.displayPrompt(_mapView.getContext().getString(
                    R.string.SPI_prompt));
        }
    }

    /**
     * Used to remove the markers from the map in case of a zeroize action.  Does not do so cleanly
     * but does remove the markers and their associated data.  Button state is not properly
     * restored, but in a zeroize state, that's the least of the worries.  Should cover the Red X
     * in the R+B toolbar as it is a tool for both toolbars.
     * @param context Current Context
     * @param intent Intent that was received
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        /* Zeroize all the stuff */
        if (DataMgmtReceiver.ZEROIZE_CONFIRMED_ACTION
                .equals(intent.getAction())) {
            Log.d(TAG, "Removing Fires Overlays");
            for (Tool tool : _tools) {
                if (tool instanceof SpecialPointButtonTool) {
                    Marker m = ((SpecialPointButtonTool) tool).getMarker();
                    if (m != null)
                        m.removeFromGroup();
                }
            }
            // Remove any Laser Basket that may currently be displayed
            Intent laserIntent = new Intent();
            laserIntent.setAction("com.atakmap.android.maps.DRAW_WEDGE");
            AtakBroadcast.getInstance().sendBroadcast(laserIntent);
        }
    }
}
