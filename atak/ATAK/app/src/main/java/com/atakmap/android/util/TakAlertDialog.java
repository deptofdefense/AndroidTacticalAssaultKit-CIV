
package com.atakmap.android.util;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.TakButton;
import com.atakmap.app.R;

import android.app.Dialog;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public class TakAlertDialog extends Dialog {
    protected Resources _resources;
    protected TextView _titleView;
    protected TextView _messageView;
    protected FrameLayout _customView;
    protected TakButton _positiveButton;
    protected TakButton _negativeButton;

    public TakAlertDialog() {
        super(MapView.getMapView().getContext(), R.style.newAlertDialog);
        _resources = getContext().getResources();

        initializeView();
    }

    protected void initializeView() {
        View v = createView();

        setContentView(v);
    }

    protected View createView() {
        View v = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_alert, null, false);

        _titleView = v.findViewById(android.R.id.title);
        _messageView = v.findViewById(android.R.id.message);
        _customView = v.findViewById(android.R.id.custom);

        _positiveButton = v.findViewById(android.R.id.button1);
        _negativeButton = v.findViewById(android.R.id.button2);

        setPositiveButton(R.string.confirm, null);
        setNegativeButton(R.string.cancel, null);

        return v;
    }

    @Override
    public void setTitle(CharSequence title) {
        _titleView.setText(title);
        _titleView.setVisibility(
                TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
    }

    @Override
    public void setTitle(@StringRes int titleId) {
        _titleView.setText(titleId);
        _titleView.setVisibility(View.VISIBLE);
    }

    //    @Override
    public void setMessage(CharSequence message) {
        _messageView.setText(message);
        _messageView.setVisibility(
                TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    public void setMessage(@StringRes int msgId) {
        _messageView.setText(msgId);
        _messageView.setVisibility(View.VISIBLE);
    }

    public void setPositiveButton(@StringRes int resId, @Nullable
    final OnClickListener listener) {
        setPositiveButton(_resources.getString(resId), listener);
    }

    public void setPositiveButton(String text, @Nullable
    final OnClickListener listener) {
        _positiveButton.setText(text);
        _positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(TakAlertDialog.this, BUTTON_POSITIVE);
                }
                dismiss();
            }
        });
    }

    public void setNegativeButton(@StringRes int resId, @Nullable
    final OnClickListener listener) {
        setNegativeButton(_resources.getString(resId), listener);
    }

    public void setNegativeButton(String text, @Nullable
    final OnClickListener listener) {
        _negativeButton.setText(text);
        _negativeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(TakAlertDialog.this, BUTTON_NEGATIVE);
                }
                dismiss();
            }
        });
    }

    public TakButton getPositiveButton() {
        return _positiveButton;
    }

    public TakButton getNegativeButton() {
        return _negativeButton;
    }
}
