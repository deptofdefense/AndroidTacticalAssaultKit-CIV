
package com.atakmap.android.user;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.app.system.SystemComponentLoader;

/**
 *
 */
public class PlaceHostileTool extends ButtonTool {
    public static final String TAG = "PlaceHostileButtonTool";
    public static final String IDENTIFIER = "com.atakmap.android.user.PLACEHOSTILE";

    private final TextContainer _container;
    private final SharedPreferences prefs;

    public PlaceHostileTool(MapView mapView, ImageButton button) {
        super(mapView, button, IDENTIFIER);
        _container = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                IDENTIFIER, this);

        prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());

        // check to see what type is being built - capabilities wise
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null && fp.hasMilCapabilities())
            button.setOnLongClickListener(_buttonLongClickListener);
    }

    private final View.OnLongClickListener _buttonLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Intent intent = new Intent(
                    "com.atakmap.android.maps.MANAGE_HOSTILES");
            AtakBroadcast.getInstance().sendBroadcast(intent);
            return false;
        }
    };

    @Override
    public boolean onToolBegin(Bundle bundle) {
        this._imageButton.setImageResource(R.drawable.ic_hostile_selected);
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, _onClick);

        _container.displayPrompt(_mapView.getContext().getString(
                R.string.point_dropper_text39));
        return super.onToolBegin(bundle);
    }

    private final MapEventDispatcher.MapEventDispatchListener _onClick = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (_container != null) {
                _container.closePrompt();
            }
            PlacePointTool.MarkerCreator markerCreator = new PlacePointTool.MarkerCreator(
                    _mapView.inverseWithElevation(event.getPointF().x,
                            event.getPointF().y));

            markerCreator.setType("a-h-G");
            markerCreator.setHow("h-g-i-g-o");
            markerCreator.showCotDetails(false);
            markerCreator.setPrefix(
                    ResourceUtil.getString(_mapView.getContext(),
                            R.string.civ_tgtPrefix, R.string.tgtPrefix) + ".");

            boolean launchNineLine = prefs.getBoolean("autostart_nineline",
                    false);

            if (launchNineLine)
                markerCreator.setShowNineLine(true);

            markerCreator.placePoint();
        }
    };

    @Override
    public void onToolEnd() {
        if (_container != null) {
            _container.closePrompt();
        }
        this._imageButton.setImageResource(R.drawable.ic_hostile_unselected);
        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        super.onToolEnd();
    }
}
