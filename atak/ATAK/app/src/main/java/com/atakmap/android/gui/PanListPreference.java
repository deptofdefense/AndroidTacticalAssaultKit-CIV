
package com.atakmap.android.gui;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.R;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link android.preference.Preference} that displays a list of entries as
 * a dialog geared toward Plugin Developers.
 * <p>
 * This preference will store a string into the SharedPreferences. This string will be the value
 * from the {@link #setEntryValues(CharSequence[])} array.
 *
 * This has been modified to allow for a pluginIcon attribute which will allow for icons
 * to be used in the entity.
 * </p>
 */
public class PanListPreference extends ListPreference {

    public static final String TAG = "PanListPreference";
    private ImageView imageView;

    private final Map<String, Integer> otherAttributes = new HashMap<>();

    private static Context appContext;

    public static void setContext(Context c) {
        appContext = c;
    }

    public PanListPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, PanPreference.filter(attrs), defStyleAttr, defStyleRes);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanListPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, PanPreference.filter(attrs), defStyleAttr);
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanListPreference(Context context, AttributeSet attrs) {
        super(context, PanPreference.filter(attrs));
        PanPreference.setup(attrs, context, this, otherAttributes);
    }

    public PanListPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        if (!isEnabled())
            v.setEnabled(false);

        if (v instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) v;
            MapView view = MapView.getMapView();
            ImageView iv = new ImageView(view.getContext());
            if (BuildConfig.FLAVOR == "civUIMods") {
                iv.setImageDrawable(view.getContext()
                        .getDrawable(
                                R.drawable.ic_baseline_keyboard_arrow_right_24));
            } else {
                iv.setImageDrawable(
                        view.getContext().getDrawable(R.drawable.arrow_right));
            }
            layout.addView(iv);
            imageView = iv;
        }
        setRightSideIconVisibility(isEnabled());
        return v;
    }

    private void setRightSideIconVisibility(boolean enabled) {
        try {
            imageView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {

        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        setRightSideIconVisibility(isEnabled());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setRightSideIconVisibility(isEnabled());
    }

    boolean pluginContext = true;

    @Override
    public Context getContext() {
        if (pluginContext) {
            return super.getContext();
        } else {
            return appContext;
        }
    }

    /**
     * Shows the dialog associated with this Preference. This is normally initiated
     * automatically on clicking on the preference. Call this method if you need to
     * show the dialog on some other event.
     * 
     * @param state Optional instance state to restore on the dialog
     */
    @Override
    protected void showDialog(Bundle state) {
        pluginContext = false;
        super.showDialog(state);
        pluginContext = true;
    }

}
