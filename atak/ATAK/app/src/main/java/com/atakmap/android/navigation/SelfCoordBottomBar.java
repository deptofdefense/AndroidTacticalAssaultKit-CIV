
package com.atakmap.android.navigation;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.ScreenUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class SelfCoordBottomBar extends TextView {
    final public static String TAG = "SelfCoordBottomBar";
    final public static String UPDATE_SELF_COORD = "com.atakmap.android.navigation.UPDATE_SELF_COORD";
    private static final String BOTTOM_SECTION_SPACING = "        ";
    private static final String PORTRAIT_BOTTOM_SECTION_SPACING = "      ";

    public SelfCoordBottomBar(Context context) {
        super(context);
        init();
    }

    public SelfCoordBottomBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SelfCoordBottomBar(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setLayoutHeight();
    }

    private void init() {
        setVisibility(GONE);
        AtakBroadcast.DocumentedIntentFilter navSettingsDropDownFilter = new AtakBroadcast.DocumentedIntentFilter();
        navSettingsDropDownFilter
                .addAction(UPDATE_SELF_COORD);
        AtakBroadcast.getInstance().registerReceiver(new SelfCoordReceiver(),
                navSettingsDropDownFilter);
    }

    private void setLayoutHeight() {
        boolean portraitMode = getContext().getResources()
                .getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        ViewGroup.LayoutParams params = getLayoutParams();

        if (portraitMode) {
            params.height = (int) getContext().getResources()
                    .getDimension(R.dimen.callsign_portrait_height);
        } else {
            params.height = (int) getContext().getResources()
                    .getDimension(R.dimen.callsign_landscape_height);
        }
        setLayoutParams(params);
    }

    private class SelfCoordReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            SelfCoordBottomBar.this.setVisibility(VISIBLE);

            SpannableStringBuilder displayString = new SpannableStringBuilder(
                    "");

            final String gpsText = intent.getStringExtra("gpsText");
            if (!FileSystemUtils.isEmpty(gpsText)) {
                displayString.append(gpsText).append(BOTTOM_SECTION_SPACING);
            } else {
                displayString.append("NO GPS" + BOTTOM_SECTION_SPACING);
            }

            displayString.append("Callsign: ");
            displayString.append(intent.getStringExtra("callsignText"),
                    new StyleSpan(android.graphics.Typeface.BOLD),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            boolean portraitMode = getContext().getResources()
                    .getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

            ViewGroup.LayoutParams params = getLayoutParams();
            if (portraitMode) {
                params.height = (int) ScreenUtils.convertDpToPixels(66,
                        getContext());
                setLayoutParams(params);
                displayString.append("\n");
                displayString.append(intent.getStringExtra("locationText"))
                        .append(PORTRAIT_BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("altText"))
                        .append(PORTRAIT_BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("orientationText"))
                        .append(PORTRAIT_BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("speedText"));
                displayString.append("\n");
                displayString.append(intent.getStringExtra("accuracyText"));
            } else {
                displayString.append(BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("locationText"))
                        .append(BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("altText"))
                        .append(BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("orientationText"));
                displayString.append(intent.getStringExtra("speedText"))
                        .append(BOTTOM_SECTION_SPACING);
                displayString.append(intent.getStringExtra("accuracyText"));
                params.height = (int) ScreenUtils.convertDpToPixels(23,
                        getContext());
                setLayoutParams(params);
            }
            SelfCoordBottomBar.this.setText(displayString);
        }
    }
}
