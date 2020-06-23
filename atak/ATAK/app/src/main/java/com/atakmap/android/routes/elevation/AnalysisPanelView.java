
package com.atakmap.android.routes.elevation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.app.R;

public class AnalysisPanelView extends LinearLayout {

    private View _topLevelView;
    private TextView _totalDistText;
    private TextView _maxAltText;
    private TextView _minAltText;
    private TextView _totalGainText;
    private TextView _totalLossText;
    private TextView _maxSlopeText;
    private View _toggleView;
    private ImageView _toggleImage;
    private View _viewshedView;

    public AnalysisPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(View parent) {
        if (parent != null)
            _topLevelView = parent;
        else
            _topLevelView = this;

        _totalDistText = _topLevelView
                .findViewById(R.id.TotalDistText);
        _maxAltText = _topLevelView.findViewById(R.id.MaxAltText);
        _minAltText = _topLevelView.findViewById(R.id.MinAltText);
        _totalGainText = _topLevelView
                .findViewById(R.id.TotalGainText);
        _totalLossText = _topLevelView
                .findViewById(R.id.TotalLossText);
        _maxSlopeText = _topLevelView
                .findViewById(R.id.MaxSlopeText);
        _toggleView = _topLevelView
                .findViewById(R.id.elevationProfileArrowToggle);
        _toggleImage = _topLevelView
                .findViewById(R.id.elevationProfileArrowImage);
        _viewshedView = _topLevelView
                .findViewById(R.id.viewshedLayout);
    }

    public TextView getTotalDistText() {
        return _totalDistText;
    }

    public TextView getMaxAltText() {
        return _maxAltText;
    }

    public TextView getMinAltText() {
        return _minAltText;
    }

    public TextView getTotalGainText() {
        return _totalGainText;
    }

    public TextView getTotalLossText() {
        return _totalLossText;
    }

    public TextView getMaxSlopeText() {
        return _maxSlopeText;
    }

    public View getToggleView() {
        return _toggleView;
    }

    public ImageView getToggleImage() {
        return _toggleImage;
    }

    public View getViewshedView() {
        return _viewshedView;
    }
}
