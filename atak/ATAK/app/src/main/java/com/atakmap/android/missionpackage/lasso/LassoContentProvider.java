
package com.atakmap.android.missionpackage.lasso;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.layers.RegionShapeTool;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.app.R;

import java.util.List;

/**
 * Content provider using the lasso tool
 */
public class LassoContentProvider extends BroadcastReceiver
        implements URIContentProvider {

    public static final String CALLBACK_ACTION = "com.atakmap.android.missionpackage.lasso.CALLBACK";
    private static final String NAV_REF = "lasso.xml";

    private final MapView _mapView;
    private final Context _context;

    private Callback _callback;

    public LassoContentProvider(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        DocumentedIntentFilter f = new DocumentedIntentFilter(CALLBACK_ACTION,
                "Intent callback for selecting content via lasso shape",
                new DocumentedExtra[] {
                        new DocumentedExtra("DataPackageUID",
                                "The UID of the Data Package to add content to",
                                false, String.class),
                        new DocumentedExtra("uid",
                                "The UID of newly created lasso shape",
                                true, String.class)
                });
        AtakBroadcast.getInstance().registerReceiver(this, f);
        URIContentManager.getInstance().registerProvider(this);
    }

    public void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
        URIContentManager.getInstance().unregisterProvider(this);
    }

    @Override
    public String getName() {
        return _context.getString(R.string.lasso);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_lasso);
    }

    @Override
    public boolean isSupported(String requestTool) {
        // Do not show the lasso tool when prompting how to import files
        return !"Import Manager".equals(requestTool);
    }

    @Override
    public boolean addContent(String requestTool, Bundle extras,
            Callback callback) {
        if (callback == null)
            return false;
        Bundle b = new Bundle();
        b.putSerializable("mode", RegionShapeTool.Mode.LASSO);
        Intent cb = new Intent(CALLBACK_ACTION);
        if (extras != null)
            cb.putExtras(extras);
        b.putParcelable("callback", cb);
        ToolManagerBroadcastReceiver recv = ToolManagerBroadcastReceiver
                .getInstance();
        recv.startTool(RegionShapeTool.TOOL_ID, b);
        if (recv.getActiveTool() instanceof RegionShapeTool) {
            _callback = callback;
            NavView.getInstance().setButtonSelected(NAV_REF, true);
        }
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (_callback == null)
            return;

        NavView.getInstance().setButtonSelected(NAV_REF, false);

        final Callback cb = _callback;
        _callback = null;

        String shapeUID = intent.getStringExtra("uid");
        MapItem mi = _mapView.getMapItem(shapeUID);
        if (!(mi instanceof DrawingShape))
            return;

        LassoSelectionDialog d = new LassoSelectionDialog(_mapView);
        d.setLassoShape((DrawingShape) mi);
        d.setCallback(new LassoSelectionDialog.Callback() {
            @Override
            public void onContentSelected(List<String> uris) {
                cb.onAddContent(LassoContentProvider.this, uris);
            }
        });
        d.show();
    }
}
