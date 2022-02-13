
package com.atakmap.android.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

import com.atakmap.app.R;

public class TakImageButton extends ImageButton {
    private static final int[] STATE_EXPANDED = {
            R.attr.state_expanded
    };
    private static final int[] STATE_COLLAPSED = {
            -R.attr.state_expanded
    };

    private boolean _expanded = false;

    public TakImageButton(Context context) {
        this(context, null);
    }

    public TakImageButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TakImageButton(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TakImageButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public boolean isExpanded() {
        return _expanded;
    }

    public void setExpanded(boolean expanded) {
        _expanded = expanded;
        if (expanded) {
            setImageState(STATE_EXPANDED, true);
        } else {
            setImageState(STATE_COLLAPSED, true);
        }

    }
}
