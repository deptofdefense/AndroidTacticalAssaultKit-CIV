
package com.atakmap.android.util;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.ColorInt;

public class SpanUtils {
    public static Spannable applyForegroundColorSpan(String text,
            @ColorInt int color) {
        if (text == null) {
            return null;
        }

        final SpannableStringBuilder prompt = new SpannableStringBuilder(text);
        applyForegroundColorSpanToRange(prompt,
                color,
                0,
                text.length());

        return prompt;
    }

    public static void applyForegroundColorSpanToRange(Spannable text,
            @ColorInt int color,
            int start,
            int end) {
        if (text == null || start < 0 || start > text.length() || end < start
                || end > text.length()) {
            return;
        }

        text.setSpan(
                new ForegroundColorSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
