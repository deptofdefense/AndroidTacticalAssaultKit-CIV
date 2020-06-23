
package com.atakmap.android.gui;

import android.content.Context;
import android.text.*;

import android.util.AttributeSet;

/**
 * An implementation of EditText that rejects Emoji characters.   Otherwise acts just
 * like a standard edit text.
 */
public class EditText extends android.widget.EditText {

    public EditText(Context context) {
        super(context);
        setFilters(new InputFilter[0]);
    }

    public EditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFilters(new InputFilter[0]);
    }

    public EditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFilters(new InputFilter[0]);
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        InputFilter[] nFilters = new InputFilter[filters.length + 1];
        nFilters[0] = new EmojiExcludeFilter();
        System.arraycopy(filters, 0, nFilters, 1, filters.length);
        super.setFilters(nFilters);
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
