
package com.atakmap.android.tools;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

//import com.atakmap.android.maps.CircleCreationTool;

public class CircleCreationButtonTool extends DrawingCircleCreationTool
        implements View.OnLongClickListener {

    private final ImageButton _button;

    private final MapView _mapView;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.CIRCLE_TOOL";

    public CircleCreationButtonTool(MapView mapView, ImageButton button,
            MapGroup drawingGroup) {
        super(mapView, drawingGroup);
        _button = button;
        _mapView = mapView;
        initButton();
    }

    /***
     * Performs button initialization, setting up onclick listeners etc. Overide if the default
     * behavior (start tool if it isn't started, stop if if it is) isn't sufficient.
     */
    protected void initButton() {
        _button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                // end if we're active, begin if we're not
                if (getActive())
                    requestEndTool();
                else
                    requestBeginTool();
            }
        });

        _button.setOnLongClickListener(this);
    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_mapView.getContext(), R.string.circle_tip,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    protected void setActive(boolean active) {
        _button.setSelected(active);
        super.setActive(active);
    }

}
