
package com.atakmap.android.toolbars;

import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.RangeCircleCreationTool;
import com.atakmap.app.R;

/**
 * Button Tool to place a RangeCircle in the Range & Bearing Group (direct child of Root Group). The
 * user workflow follows that of the R&B Arrow Tool. If the user long presses the user marker will
 * automatically be followed as the center point. Also if selecting a PointMapItem the center or
 * radius will follow that PointMapItem's center point.<br>
 * <br>
 * Heavily modified this class with the circle refactor.  Most functionality shifted to other
 * classes.  AS.
 * <br>
 * 
 * 
 * 
 */
public class RangeCircleButtonTool extends RangeCircleCreationTool implements
        View.OnLongClickListener, View.OnClickListener {
    private final ImageButton _button;

    public RangeCircleButtonTool(MapView mapView, ImageButton button) {
        super(mapView, RangeAndBearingMapComponent.getGroup());
        _button = button;
        initButton();
    }

    /***
     * Performs button initialization, setting up onclick listeners etc. Overide if the default
     * behavior (start tool if it isn't started, stop if if it is) isn't sufficient.
     */
    protected void initButton() {
        _button.setOnClickListener(this);

        _button.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View v) { // end if we're active, begin if we're not
        if (getActive()) {
            requestEndTool();
        } else {
            requestBeginTool();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_mapView.getContext(), R.string.rb_circle_tip,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    protected void setActive(boolean active) {
        _button.setSelected(active);
        //        }

        super.setActive(active);
    }
}
