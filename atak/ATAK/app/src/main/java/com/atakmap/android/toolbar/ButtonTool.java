
package com.atakmap.android.toolbar;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;

import com.atakmap.android.maps.MapView;

/***
 * Handles button selected state, enabled state, and sends begin_tool/end_tool intents when you
 * press the button.
 * 
 * 
 */
public abstract class ButtonTool extends Tool {

    protected Button _button;
    protected ImageButton _imageButton;

    OnClickListener onClickListener;

    public ButtonTool(MapView mapView, Button button, String identifier) {
        super(mapView, identifier);
        _button = button;
        _imageButton = null;
        initButton();
    }

    public ButtonTool(MapView mapView, ImageButton button, String identifier) {
        super(mapView, identifier);
        _imageButton = button;
        _button = null;
        initButton();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        // Set this tool (the active tool) as the button user
        initButton();
        return super.onToolBegin(extras);
    }

    @Override
    public void dispose() {
        if (_button != null) {
            _button.setOnClickListener(null);
            _button.setOnLongClickListener(null);
        }
        if (_imageButton != null) {
            _imageButton.setOnClickListener(null);
            _imageButton.setOnLongClickListener(null);
        }
        _button = null;
        _imageButton = null;
        onClickListener = null;
    }

    // allow for an implementor of the button tool to know precisely when a button is 
    // clicked.
    public void onButtonClicked() {
    }

    /***
     * Performs button initialization, setting up onclick listeners etc. Overide if the default
     * behavior (start tool if it isn't started, stop if if it is) isn't sufficient.
     */
    protected void initButton() {
        onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {

                onButtonClicked();

                // end if we're active, begin if we're not
                if (getActive())
                    requestEndTool();
                else
                    requestBeginTool();

            }
        };

        if (_button != null) {
            _button.setOnClickListener(onClickListener);
        } else if (_imageButton != null) {
            _imageButton.setOnClickListener(onClickListener);
        }
    }

    @Override
    protected void setActive(boolean active) {
        if (_button != null) {
            if (_button instanceof CompoundButton)
                ((CompoundButton) _button).setChecked(active);
            else {
                _button.setSelected(active);
            }
        } else if (_imageButton != null) {
            _imageButton.setSelected(active);

        }

        super.setActive(active);
    }

}
