
package com.atakmap.android.routes.elevation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.app.R;

public class SeekerBarPanelView extends LinearLayout {

    private View _topLevelView;

    private TextView _mgrsText;
    private TextView _mslText;
    private TextView _gainText;
    private TextView _slopeText;
    private TextView _cpText;

    public SeekerBarPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(View parent) {
        if (parent != null)
            _topLevelView = parent;
        else
            _topLevelView = this;

        _mgrsText = _topLevelView.findViewById(R.id.MgrsText);
        _mslText = _topLevelView.findViewById(R.id.MslText);
        _gainText = _topLevelView.findViewById(R.id.GainText);
        _slopeText = _topLevelView.findViewById(R.id.SlopeText);
        _cpText = _topLevelView.findViewById(R.id.ControlPointText);
    }

    public TextView getMgrsText() {
        return _mgrsText;
    }

    public TextView getMslText() {
        return _mslText;
    }

    public TextView getGainText() {
        return _gainText;
    }

    public TextView getSlopeText() {
        return _slopeText;
    }

    public TextView getCpText() {
        return _cpText;
    }
}
