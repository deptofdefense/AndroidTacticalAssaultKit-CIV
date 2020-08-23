
package com.atakmap.android.hashtags.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.MultiAutoCompleteTextView;

import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;

/**
 * Editable text view that auto-completes based on the list of registered tags
 */
public class HashtagEditText extends MultiAutoCompleteTextView {

    private final HashtagAdapter _adapter;
    private boolean _clickableTags;
    private View.OnClickListener _clickListener;

    public HashtagEditText(Context context) {
        this(context, null);
    }

    public HashtagEditText(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.autoCompleteTextViewStyle);
    }

    public HashtagEditText(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HashtagEditText(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setAdapter(_adapter = new HashtagAdapter());
        setTokenizer(new HashtagTokenizer());

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.HashtagEditText, 0, 0);

        boolean mixedInput = a.getBoolean(
                R.styleable.HashtagEditText_mixedInput, false);

        boolean clickableTags = a.getBoolean(
                R.styleable.HashtagEditText_clickableTags, false);

        a.recycle();

        setMixedInput(mixedInput);

        if (clickableTags)
            setClickableTags(true);
    }

    /**
     * Set whether this text supports a mix of words and tags
     * @param b True to support words and tags
     *          False to only support a single hashtag
     */
    public void setMixedInput(boolean b) {
        if (b) {
            if (getTextString().equals("#"))
                setText("");
            setFilters(new InputFilter[0]);
        } else {
            String text = getText().toString();
            if (text.isEmpty()) {
                setText("#");
                setSelection(1);
            }
            setFilters(new InputFilter[] {
                    new HashtagInputFilter()
            });
        }
    }

    /**
     * Toggle whether the hashtags are clickable
     * @param b True if clickable
     */
    public void setClickableTags(boolean b) {
        _clickableTags = b;
        Editable txt = getText();
        if (txt != null && txt.length() > 0)
            setText(String.valueOf(txt));
        else
            super.setText("");
    }

    /**
     * Get the single hashtag from this text
     * Assumes mixed input is turned off
     * @return Hashtag
     */
    public String getHashtag() {
        return HashtagUtils.validateTag(getText().toString());
    }

    /**
     * Set text string
     * @param str String
     */
    public void setText(String str) {
        // Empty string exception
        if (FileSystemUtils.isEmpty(str)) {
            super.setText("");
            return;
        }

        if (!_clickableTags) {
            super.setText(str);
            return;
        }

        CharSequence text = HashtagUtils.getStylizedMessage(str);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        URLSpan[] urls = sb.getSpans(0, text.length(), URLSpan.class);
        for (URLSpan url : urls) {
            int start = sb.getSpanStart(url);
            int end = sb.getSpanEnd(url);
            int flags = sb.getSpanFlags(url);
            final String tag = text.subSequence(start, end).toString();
            ClickableSpan clickable = new ClickableSpan() {
                @Override
                public void onClick(View v) {
                    HashtagEditText.this.setOnClickListener(null);
                    showTag(tag);
                    HashtagEditText.this.setOnClickListener(_clickListener);
                }
            };
            sb.setSpan(clickable, start, end, flags);
            sb.removeSpan(url);
        }
        super.setText(sb);
        setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void setSelection(int index) {
        super.setSelection(MathUtils.clamp(index, 0, getText().length()));
    }

    public String getTextString() {
        return getText().toString();
    }

    private void showTag(String tag) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(getContext().getString(R.string.hashtags));
        paths.add(tag.toLowerCase(LocaleUtil.getCurrent()));
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.MANAGE_HIERARCHY)
                        .putStringArrayListExtra("list_item_paths", paths)
                        .putExtra("isRootList", true));
    }

    @Override
    public void setOnClickListener(View.OnClickListener l) {
        super.setOnClickListener(l);
        _clickListener = l;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        HashtagManager.getInstance().registerUpdateListener(_adapter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        HashtagManager.getInstance().unregisterUpdateListener(_adapter);
    }

    /**
     * Tokenizer specially tailored to finding hashtags in a string
     */
    private static class HashtagTokenizer implements Tokenizer {

        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;

            while (i > 0 && text.charAt(i - 1) != '#')
                i--;

            while (i < cursor && text.charAt(i) == ' ')
                i++;

            return i > 0 ? i - 1 : 0;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor + 1;
            int len = text.length();

            while (i < len) {
                char c = text.charAt(i);
                if (c == '#' || c == ' ')
                    return i;
                else
                    i++;
            }

            return len;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();

            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }

            if (i > 0 && text.charAt(i - 1) == '#') {
                return text;
            } else {
                if (text instanceof Spanned) {
                    SpannableString sp = new SpannableString(text + " ");
                    TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
                            Object.class, sp, 0);
                    return sp;
                } else
                    return text + " ";
            }
        }
    }

    private static class HashtagInputFilter implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            for (int k = start; k < end; k++) {
                char c = source.charAt(k);
                if (Character.isWhitespace(c))
                    return String.valueOf(source).replace(" ", "");
            }
            return null;
        }
    }
}
