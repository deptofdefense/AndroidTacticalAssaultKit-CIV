
package com.atakmap.android.navigation;

import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayReceiver;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.selfcoordoverlay.SelfCoordText;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Self coordinate information displayed in a bar on the bottom of the screen
 */
public class SelfCoordBottomBar extends FrameLayout implements
        SelfCoordOverlayReceiver,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener {

    private static final String TAG = "SelfCoordBottomBar";

    private final AtakPreferences _prefs;
    private SelfCoordText _lastText;

    // Views
    private View layout;
    private ViewGroup gpsInfo;
    private TextView source;
    private TextView callsign;
    private TextView location;
    private TextView altitude;
    private TextView heading;
    private TextView speed;
    private TextView accuracy;
    private TextView noGps;

    public SelfCoordBottomBar(Context context) {
        this(context, null);
    }

    public SelfCoordBottomBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SelfCoordBottomBar(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        _prefs = new AtakPreferences(context);
    }

    /**
     * Inflate and initialize the layout used for displaying GPS info
     * @param portrait True if the device is in portrait mode
     */
    private void initLayout(boolean portrait) {
        layout = LayoutInflater.from(getContext()).inflate(portrait
                ? R.layout.self_coord_bar_portrait
                : R.layout.self_coord_bar_landscape, this, false);
        layout.setTag(portrait);
        gpsInfo = layout.findViewById(R.id.gps_info);
        source = layout.findViewById(R.id.source);
        callsign = layout.findViewById(R.id.callsign);
        location = layout.findViewById(R.id.location);
        altitude = layout.findViewById(R.id.altitude);
        heading = layout.findViewById(R.id.heading);
        speed = layout.findViewById(R.id.speed);
        accuracy = layout.findViewById(R.id.accuracy);
        noGps = layout.findViewById(R.id.no_gps);
        removeAllViews();
        addView(layout);

        // Hide the nav GPS source text if it's visible
        TextView navSrc = getNavGpsSource();
        if (navSrc != null) {
            navSrc.setOnClickListener(this);
            navSrc.setVisibility(GONE);
        }
    }

    /**
     * Get the GPS source text that's part of the {@link NavView}
     * @return GPS source text
     */
    private TextView getNavGpsSource() {
        NavView nv = NavView.getInstance();
        return nv != null ? nv.findViewById(R.id.gps_source) : null;
    }

    @Override
    public void onSelfCoordinateChanged(SelfCoordText text) {
        if (text == null)
            return;

        _lastText = text;

        // Bar isn't displayed - ignore update
        boolean display = _prefs.get(SelfCoordOverlayUpdater.PREF_DISPLAY,
                SelfCoordOverlayUpdater.DISPLAY_BOTTOM_RIGHT)
                .equals(SelfCoordOverlayUpdater.DISPLAY_BOTTOM_BAR);
        setVisibility(display ? VISIBLE : GONE);
        if (!display)
            return;

        Boolean portraitMode = getResources()
                .getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        // Initialize the layout if needed
        if (layout == null || layout.getTag() != portraitMode)
            initLayout(portraitMode);

        // Check if we have no GPS
        if (FileSystemUtils.isEmpty(text.location)) {
            gpsInfo.setVisibility(GONE);
            noGps.setVisibility(VISIBLE);
            return;
        }

        // GPS source
        if (source == null)
            source = getNavGpsSource();
        if (source != null) {
            source.setOnClickListener(this);
            source.setVisibility(VISIBLE);
            if (!FileSystemUtils.isEmpty(text.source)) {
                // Custom GPS source
                source.setText(text.source);
                source.setTextColor(text.sourceColor != 0 ? text.sourceColor
                        : getResources().getColor(R.color.pale_silver));
            } else if (FileSystemUtils.isEmpty(text.location)) {
                // Default to NO GPS text
                source.setText("NO GPS");
                source.setTextColor(Color.RED);
            } else
                source.setVisibility(GONE);
        }

        // Make the callsign bold
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(text.callsign,
                new StyleSpan(android.graphics.Typeface.BOLD),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        callsign.setText(sb);

        location.setText(text.location);
        altitude.setText(text.altitude);
        heading.setText(text.heading);
        speed.setText(text.speed);
        accuracy.setText(text.accuracy);

        gpsInfo.setVisibility(VISIBLE);
        noGps.setVisibility(GONE);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (source != null)
            source.setVisibility(visibility);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (SelfCoordOverlayUpdater.PREF_DISPLAY.equals(key))
            onSelfCoordinateChanged(_lastText);
    }

    @Override
    public void onClick(View v) {
        // Redirect clicks on the detached GPS source to this view
        if (v == getNavGpsSource())
            performClick();
    }
}
