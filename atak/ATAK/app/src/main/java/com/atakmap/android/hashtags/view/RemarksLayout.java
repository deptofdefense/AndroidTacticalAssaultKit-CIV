
package com.atakmap.android.hashtags.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.android.imagecapture.CapturePrefs;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

/**
 * Layout that includes remarks and hashtags using the same visual style as
 * other elements in the CoT details views
 */
public class RemarksLayout extends LinearLayout {

    // Maximum number of allowed characters
    private static final int MAX_LENGTH = 5000;

    private final HashtagEditText _remarks;
    private String _rawText;

    public RemarksLayout(Context context) {
        this(context, null);
    }

    public RemarksLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RemarksLayout(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RemarksLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setOrientation(VERTICAL);
        LayoutInflater inf = LayoutInflater.from(context);
        LinearLayout ll = (LinearLayout) inf.inflate(R.layout.remarks_layout,
                this, false);
        addView(ll);

        _remarks = findViewById(R.id.remarks);
        ImageButton remarksBtn = findViewById(R.id.edit_remarks);
        remarksBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                promptEditText();
            }
        });
    }

    public void setText(String remarks) {
        if (remarks == null)
            remarks = "";
        if (remarks.length() > MAX_LENGTH)
            remarks = remarks.substring(0, MAX_LENGTH);
        _remarks.setText(_rawText = remarks);
    }

    public String getText() {
        return _rawText;
    }

    public void addTextChangedListener(TextWatcher watcher) {
        _remarks.addTextChangedListener(watcher);
    }

    private void promptEditText() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        final HashtagEditText input = new HashtagEditText(getContext());
        input.setMixedInput(true);
        input.setText(_rawText);
        input.setHint(_remarks.getHint());
        input.setInputType(_remarks.getInputType());
        input.setImeOptions(_remarks.getImeOptions());
        input.setSelection(_rawText.length());

        AlertDialog.Builder b = new AlertDialog.Builder(mv.getContext());

        // Hide the title bar when using a phone in landscape mode
        // Otherwise the hashtag suggestions are cut off by the giant soft keyboard
        if (getContext().getResources().getBoolean(R.bool.isTablet)
                || CapturePrefs.inPortraitMode())
            b.setTitle(_remarks.getContentDescription());

        b.setView(input);
        b.setPositiveButton(R.string.done,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setText(input.getTextString());
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog d = b.create();

        Window w = d.getWindow();
        if (w != null)
            w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        d.show();
    }
}
