
package com.atakmap.android.layers;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;

import com.atakmap.app.R;

public class LayersManagerView extends AbstractLayersManagerView {

    private ImageButton _upButton;
    private ImageButton _downButton;
    private CompoundButton _autoSelectButton;

    public LayersManagerView(Context context, AttributeSet attrs) {
        super(context, attrs, R.id.layers_manager_list,
                R.id.layer_outline_toggle);
    }

    public void setOnUpLayerClickListener(OnClickListener listener) {
        ImageButton upButton = _getUpButton();
        upButton.setOnClickListener(listener);
    }

    public void setOnDownLayerClickListener(OnClickListener listener) {
        ImageButton downButton = _getDownButton();
        downButton.setOnClickListener(listener);
    }

    public void setAutoSelectListener(OnClickListener listener) {
        Button viewButton = getAutoSelectButton();
        viewButton.setOnClickListener(listener);
    }

    @Override
    public void setOnOutlineToggleListener(OnCheckedChangeListener listener) {
        super.setOnOutlineToggleListener(listener);
    }

    @Override
    public void setOutlineToggleState(boolean visible) {
        super.setOutlineToggleState(visible);
    }

    private ImageButton _getUpButton() {
        if (_upButton == null) {
            _upButton = findViewById(R.id.layers_mgr_layer_up);
        }
        return _upButton;
    }

    private ImageButton _getDownButton() {
        if (_downButton == null) {
            _downButton = findViewById(
                    R.id.layers_mgr_layer_down);
        }
        return _downButton;
    }

    public CompoundButton getAutoSelectButton() {
        if (_autoSelectButton == null) {
            _autoSelectButton = findViewById(
                    R.id.auto_map_select_btn);
        }
        return _autoSelectButton;
    }

}
