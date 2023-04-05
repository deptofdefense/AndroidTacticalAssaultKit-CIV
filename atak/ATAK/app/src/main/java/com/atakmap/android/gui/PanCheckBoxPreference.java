
package com.atakmap.android.gui;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
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
 * This has been modified to allow for a pluginIcon attribute which will allow for icons
 * to be used in the entity.

 */
public class PanCheckBoxPreference extends CheckBoxPreference {

    public static final String TAG = "PanCheckBoxPreference";

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    public PanCheckBoxPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanCheckBoxPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanCheckBoxPreference(Context context) {
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
