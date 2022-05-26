
package com.atakmap.android.hashtags.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.imagecapture.CapturePrefs;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Layout that includes remarks and hashtags using the same visual style as
 * other elements in the CoT details views
 */
public class RemarksLayout extends LinearLayout implements ActionMode.Callback {

    private static final String TAG = "RemarksLayout";

    // Maximum number of allowed characters
    private static final int MAX_LENGTH = 5000;

    protected TextView _label;
    protected HashtagEditText _remarks;
    protected ImageButton _editBtn;
    protected String _rawText = "";
    private String _dialogHint;
    private final ConcurrentLinkedQueue<TextWatcher> _textChangedListeners = new ConcurrentLinkedQueue<>();

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

        createLayout(context);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.RemarksLayout, 0, 0);

        int minLines = a.getInt(R.styleable.RemarksLayout_android_minLines, -1);
        if (minLines >= 0)
            _remarks.setMinLines(minLines);

        int maxLines = a.getInt(R.styleable.RemarksLayout_android_maxLines, -1);
        if (maxLines >= 0)
            _remarks.setMaxLines(maxLines);

        int ellipsize = a.getInt(R.styleable.RemarksLayout_android_ellipsize,
                -1);
        switch (ellipsize) {
            case 1:
                _remarks.setEllipsize(TruncateAt.START);
                break;
            case 2:
                _remarks.setEllipsize(TruncateAt.MIDDLE);
                break;
            case 3:
                _remarks.setEllipsize(TruncateAt.END);
                break;
            case 4:
                _remarks.setEllipsize(TruncateAt.MARQUEE);
                break;
        }

        int gravity = a.getInt(R.styleable.RemarksLayout_android_gravity, -1);
        if (gravity >= 0)
            _remarks.setGravity(gravity);

        String hint = a.getString(R.styleable.RemarksLayout_android_hint);
        String contentDesc = a.getString(
                R.styleable.RemarksLayout_android_contentDescription);
        _dialogHint = a.getString(R.styleable.RemarksLayout_dialogHint);

        _remarks.setHint(hint);
        _remarks.setContentDescription(contentDesc);

        String viz = a.getString(R.styleable.RemarksLayout_labelVisibility);
        if (viz != null) {
            switch (viz) {
                case "visible":
                    _label.setVisibility(View.VISIBLE);
                    break;
                case "invisible":
                    _label.setVisibility(View.INVISIBLE);
                    break;
                case "gone":
                    _label.setVisibility(View.GONE);
                    break;
            }
        }

        a.recycle();
    }

    protected void createLayout(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater inf = LayoutInflater.from(context);
        LinearLayout ll = (LinearLayout) inf.inflate(R.layout.remarks_layout,
                this, false);
        addView(ll);

        _label = findViewById(R.id.label);

        _remarks = findViewById(R.id.remarks);
        _remarks.setCustomSelectionActionModeCallback(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            _remarks.setCustomInsertionActionModeCallback(this);

        _editBtn = findViewById(R.id.edit_remarks);
        _editBtn.setOnClickListener(new OnClickListener() {
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

    public void setHint(String hint) {
        _remarks.setHint(hint);
    }

    /**
     * Add text changed listener that's called whenever the user changes the
     * remarks text (this is NOT triggered by automatic changes)
     * @param watcher Text watcher
     */
    public void addTextChangedListener(TextWatcher watcher) {
        _textChangedListeners.add(watcher);
    }

    /**
     * Remove text changed listener
     * @param watcher Text watcher
     */
    public void removeTextChangedListener(TextWatcher watcher) {
        _textChangedListeners.remove(watcher);
    }

    protected void promptEditText() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        final HashtagEditText input = new HashtagEditText(getContext());
        input.setMixedInput(true);
        input.setText(_rawText);
        input.setHint(_dialogHint != null ? _dialogHint : _remarks.getHint());
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
                        Editable newText = _remarks.getText();
                        for (TextWatcher tw : _textChangedListeners)
                            tw.afterTextChanged(newText);
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
        input.requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (_remarks != null)
            _remarks.setEnabled(enabled);
        if (_editBtn != null)
            _editBtn.setEnabled(enabled);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        Toast.makeText(getContext(), R.string.remarks_select_edit_button,
                Toast.LENGTH_LONG).show();
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return true;
    }
}
