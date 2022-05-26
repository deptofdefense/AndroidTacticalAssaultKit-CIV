
package com.atakmap.android.widgets;

import com.atakmap.app.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

public class TakButton extends LinearLayout {
    private static final int[] STATE_ERROR = {
            R.attr.state_error
    };

    private boolean _errorState = false;
    private TextView btnTextView;
    private ImageView btnImageView;
    private final String buttonText;
    private final Drawable buttonImage;
    private final int buttonTextAppearance;
    private final int imageTint;

    public TakButton(Context context) {
        this(context, null);
    }

    public TakButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TakButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.AtakButton);
    }

    public TakButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        int[] attrIds = {
                android.R.attr.textAppearance, android.R.attr.tint
        };

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.TakButton, defStyleAttr,
                defStyleRes);

        try {
            buttonText = a.getString(R.styleable.TakButton_buttonText);
            buttonImage = a.getDrawable(R.styleable.TakButton_buttonImage);
            imageTint = a.getColor(R.styleable.TakButton_buttonImageTint,
                    ContextCompat.getColor(context, R.color.black));
        } finally {
            a.recycle();
        }

        try {
            a = context.obtainStyledAttributes(attrs, attrIds, defStyleAttr,
                    defStyleRes);

            buttonTextAppearance = a.getResourceId(0,
                    R.style.ATAKTextAppearance_Button);
        } finally {
            a.recycle();
        }

        initView();
    }

    protected void initView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        View view = inflater.inflate(R.layout.tak_button, this);

        btnTextView = view.findViewById(R.id.button_text);
        btnTextView.setText(buttonText);
        btnTextView.setTextAppearance(getContext(), buttonTextAppearance);

        btnImageView = view.findViewById(R.id.button_image);
        btnImageView.setImageDrawable(buttonImage);
        ImageViewCompat.setImageTintList(btnImageView,
                ColorStateList.valueOf(imageTint));
    }

    public void setError(boolean error) {
        _errorState = error;
        refreshDrawableState();
    }

    public void setText(CharSequence text) {
        btnTextView.setText(text);
    }

    public void setText(@StringRes int resId) {
        btnTextView.setText(resId);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (_errorState) {
            mergeDrawableStates(drawableState, STATE_ERROR);
        }
        return drawableState;
    }
}
