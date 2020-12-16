
package com.atakmap.android.rubbersheet;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.BitmapPyramid;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.android.rubbersheet.maps.RubberModelLayer;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.android.rubbersheet.ui.RubberSheetMapOverlay;

public class RubberSheetMapComponent extends DropDownMapComponent {

    public static final String TAG = "RubberSheetMapComponent";

    private RubberSheetMapGroup _group;
    private RubberSheetMapOverlay _overlay;
    private RubberSheetManager _manager;
    private RubberSheetReceiver _receiver;
    private RubberModelLayer _modelLayer;

    @Override
    public void onCreate(final Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        // Map group used for rubber sheets
        _group = new RubberSheetMapGroup(view);

        // Map overlay / map manager
        _overlay = new RubberSheetMapOverlay(view, _group);

        // Manages saving and loading
        _manager = new RubberSheetManager(view, _group);

        // Transparency tool
        _receiver = new RubberSheetReceiver(view);

        // 3D models layer
        _modelLayer = new RubberModelLayer(view, _group);

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        _modelLayer.dispose();
        _receiver.dispose();
        _overlay.dispose();
        _manager.shutdown();
        _group.dispose();
        BitmapPyramid.disposeStatic();
        super.onDestroyImpl(context, view);
    }

}
