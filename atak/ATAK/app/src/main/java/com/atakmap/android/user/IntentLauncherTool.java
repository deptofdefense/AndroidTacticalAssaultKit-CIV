
package com.atakmap.android.user;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ImageButton;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;

public class IntentLauncherTool extends ButtonTool {
    public static final String TAG = "IntentLauncherTool";

    private final TextContainer _container;
    private final SharedPreferences prefs;
    private final String intentString;

    public IntentLauncherTool(MapView mapView, ImageButton button,
            String identifier, String intentString) {
        super(mapView, button, identifier);
        _container = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                identifier, this);
        prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        this.intentString = intentString;
    }

    @Override
    public boolean onToolBegin(Bundle bundle) {
        Intent intent = new Intent(intentString);
        AtakBroadcast.getInstance().sendBroadcast(intent);
        return false;
        //super.onToolBegin(bundle);
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();
    }
}
