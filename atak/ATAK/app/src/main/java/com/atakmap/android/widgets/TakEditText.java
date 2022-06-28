
package com.atakmap.android.widgets;

import com.atakmap.android.gui.EditText;
import com.atakmap.app.R;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.DragEvent;

public class TakEditText extends EditText implements TextWatcher {
    private static final int[] STATE_ERROR = {
            R.attr.state_error
    };
    private static final int[] STATE_ENTERED = {
            R.attr.state_entered
    };

    private boolean _errorState = false;
    private boolean _enteredState = false;
    private boolean _dragEnabled = true;

    public TakEditText(Context context) {
        this(context, null);
    }

    public TakEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TakEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(new ContextThemeWrapper(context, R.style.AtakEditTextNew), attrs,
                defStyleAttr);
        setFilters(new InputFilter[0]);

        addTextChangedListener(this);
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        InputFilter[] nFilters = new InputFilter[filters.length + 1];
        nFilters[0] = new TakEditText.EmojiExcludeFilter();
        System.arraycopy(filters, 0, nFilters, 1, filters.length);
        super.setFilters(nFilters);
    }

    public void setError(boolean error) {
        _errorState = error;
        refreshDrawableState();
    }

    /**
     * Set whether default drag-and-drop behavior is enabled
     * @param enabled True if enabled
     */
    public void setDragEnabled(boolean enabled) {
        _dragEnabled = enabled;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 2);

        if (_enteredState) {
            mergeDrawableStates(drawableState, STATE_ENTERED);
        }

        if (_errorState) {
            mergeDrawableStates(drawableState, STATE_ERROR);
        }

        return drawableState;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,
            int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        _enteredState = s.length() > 0;
        refreshDrawableState();
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return _dragEnabled && super.onDragEvent(event);
    }

    static class EmojiExcludeFilter implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start,
                int end, Spanned dest,
                int dstart, int dend) {
            if (source == null)
                return null;

            boolean modified = false;
            StringBuffer sb = new StringBuffer();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                int type = Character.getType(c);
                if (type == Character.SURROGATE
                        || type == Character.OTHER_SYMBOL) {
                    modified = true;
                } else {
                    sb.append(c);
                }
            }
            if (!modified)
                return null;

            if (source instanceof Spanned) {
                SpannableString sp = new SpannableString(sb);
                TextUtils.copySpansFrom((Spanned) source, start, sb.length(),
                        null, sp, 0);
                return sp;
            } else {
                return sb;
            }

        }
    }
}
