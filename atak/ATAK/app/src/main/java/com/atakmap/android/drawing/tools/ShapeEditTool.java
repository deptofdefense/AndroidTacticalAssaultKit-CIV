
package com.atakmap.android.drawing.tools;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast;

import com.atakmap.android.drawing.DrawingToolsMapReceiver;
import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.editableShapes.EditablePolylineEditTool;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.app.R;

public class ShapeEditTool extends EditablePolylineEditTool {

    private final DrawingToolsToolbar _drawingToolsToolbar;
    final DrawingToolsMapReceiver _drawingToolsReceiver;
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.EDIT_SHAPE";

    public ShapeEditTool(MapView mapView, Button button, Button undoButton,
            DrawingToolsToolbar drawingToolsToolbar,
            DrawingToolsMapReceiver drawingToolsReceiver) {
        super(mapView, button, undoButton, TOOL_IDENTIFIER);

        _drawingToolsReceiver = drawingToolsReceiver;
        _button.setVisibility(Button.GONE);

        // Change prompt for dragging capabilities
        MAIN_PROMPT = _mapView.getResources().getString(
                R.string.drawing_edit_prompt);
        TAP_PROMPT = _mapView.getResources().getString(
                R.string.drawing_tap_prompt);

        _drawingToolsToolbar = drawingToolsToolbar;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {

        Intent intent = new Intent();
        intent.setAction(CamLockerReceiver.UNLOCK_CAM);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        String uid;
        String shapeUID = extras.getString("shapeUID");
        if (shapeUID == null || shapeUID.equals(""))
            uid = extras.getString("uid");
        else
            uid = extras.getString("shapeUID");

        MapItem found = null;
        if (uid != null)
            found = _mapView.getMapItem(uid);
        if (uid != null && found instanceof MultiPolyline) {
            return false;
        } else if (uid != null && found instanceof EditablePolyline) {
            _poly = (EditablePolyline) found;

            _drawingToolsToolbar.toggleEditButtons(true);

            return super.onToolBegin(extras);
        } else {
            Toast.makeText(_mapView.getContext(),
                    _mapView.getResources().getString(R.string.drawing_tip),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();
        _drawingToolsToolbar.toggleEditButtons(false);

        _poly.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

}
