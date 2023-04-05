
package com.atakmap.android.gui;

import android.content.Context;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Preference} that provides checkbox widget functionality specifically
 * geared towards plugin developers.
 * <p>
 * This preference will store a boolean into the SharedPreferences.
 */
public class PanSwitchPreference extends SwitchPreference {

    public static final String TAG = "PanSwitchPreference";

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    public PanSwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanSwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanSwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);
        return v;
    }

}
