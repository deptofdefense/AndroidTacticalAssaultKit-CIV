
package com.atakmap.android.gui;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Preference} that provides checkbox widget functionality specifically
 * geared towards plugin developers.
 * <p>
 * This preference will store a boolean into the SharedPreferences.
 */
public class PanPreferenceCategory extends PreferenceCategory {

    public static final String TAG = "PanPreferenceCategory";
    private final Map<String, Integer> otherAttributes = new HashMap<>();

    public PanPreferenceCategory(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanPreferenceCategory(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanPreferenceCategory(Context context) {
        super(context);
    }

}
