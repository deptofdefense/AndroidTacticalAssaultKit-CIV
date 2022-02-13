
package com.atakmap.android.drawing.tools;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.editableShapes.CircleEditTool;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;

public class DrawingCircleEditTool extends CircleEditTool {

    /************************ CONSTRUCTOR ****************************/

    public DrawingCircleEditTool(MapView mapView, Button button,
            Button undoButton,
            DrawingToolsToolbar drawingToolsToolbar) {
        super(mapView, button, undoButton);
        _drawingToolsToolbar = drawingToolsToolbar;
        _button.setVisibility(Button.GONE);
    }

    /************************ INHERITED METHODS ****************************/

    @Override
    public boolean onToolBegin(Bundle extras) {
        String uid;
        String shapeUID = extras.getString("shapeUID");
        if (shapeUID == null || shapeUID.equals(""))
            uid = extras.getString("uid");
        else
            uid = extras.getString("shapeUID");

        MapItem found = null;
        if (uid != null)
            found = _mapView.getMapItem(uid);

        if (uid != null && found instanceof DrawingCircle) {
            _circle = (DrawingCircle) found;
            _drawingToolsToolbar.toggleEditButtons(true);
            return super.onToolBegin(extras);
        } else {
            return false;
        }
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));
        _drawingToolsToolbar.toggleEditButtons(false);

        _circle.persist(_mapView.getMapEventDispatcher(), null,
                this.getClass());

        _button.setVisibility(Button.GONE);
    }

    /************************ FIELDS ****************************/

    private final DrawingToolsToolbar _drawingToolsToolbar;
}
