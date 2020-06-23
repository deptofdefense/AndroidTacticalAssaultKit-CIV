/**
 * 
 */

package com.atakmap.android.gui;

import android.R.attr;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.StringTokenizer;

/**
 * TextView that automatically sets the size of the text to fill the available space. This is
 * loosely based on the class found here:
 * http://stackoverflow.com/questions/5033012/auto-scale-textview
 * -text-to-fit-within-bounds/5535672#5535672
 */
public class AutoSizeTextView extends TextView {

    // Width, in pixels, available for the text
    private int availableWidth = 0;

    // Height, in pixels, available for the text
    private int availableHeight = 0;

    // Text will never go smaller than this size, in raw pixels
    private static final float MIN_SIZE = 8f;

    // Text will never go bigger than this size, in raw pixels.
    private float maxSize = Float.MAX_VALUE;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public AutoSizeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // Force normal line spacing
        setLineSpacing(0f, 1f);

        // If a text size is specified, make it the max size
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                attr.textSize
        });
        maxSize = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
        a.recycle();
    }

    /**
     * @param context
     * @param attrs
     */
    public AutoSizeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Force normal line spacing
        setLineSpacing(0f, 1f);

        // If a text size is specified, make it the max size
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                attr.textSize
        });
        maxSize = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
        a.recycle();
    }

    /**
     * @param context the context to use when constructing the view.
     */
    public AutoSizeTextView(Context context) {
        super(context);
        // Force normal line spacing
        setLineSpacing(0f, 1f);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start,
            int lengthBefore, int lengthAfter) {

        if (lengthBefore != lengthAfter)
            resizeText();
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {

        availableWidth = right - left - getCompoundPaddingLeft()
                - getCompoundPaddingRight();
        availableHeight = bottom - top - getCompoundPaddingBottom()
                - getCompoundPaddingTop();
        resizeText();

        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        availableHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        resizeText();

        super.onSizeChanged(w, h, oldw, oldh);
    }

    /**
     * Set the text size for this view based on the dimensions.
     */
    private void resizeText() {
        // The full height is always too big for text size because letters like
        // 'g' go below the baseline. This factor (hopefully) makes the initial
        // size guess more accurate.
        final float FACTOR = 0.9f;

        if (availableHeight < 1)
            return;

        // Force normal line spacing
        setLineSpacing(0f, 1f);

        // First guess for text size is height of available space, unless it's greater than the max
        // size
        float firstGuess = (float) Math.floor(availableHeight * FACTOR);
        if (firstGuess > maxSize)
            firstGuess = maxSize;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, firstGuess);

        Paint.FontMetrics metrics = getPaint().getFontMetrics();

        // Calculate the width of the text when drawn when using the available
        // height
        float width = getTextWidth();

        // Loop until a text size that fits is found or minimum size is reached
        while (getTextSize() > MIN_SIZE
                && ((metrics.bottom - metrics.top) > availableHeight
                        || width > availableWidth)) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) Math.floor(getTextSize() - 1));
            metrics = getPaint().getFontMetrics(); // old FontMetrics object is not valid after
                                                   // setTextSize() call
            width = getTextWidth();
        }
    }

    private float getTextWidth() {
        if (getText().toString().contains("\n")) {
            StringTokenizer toks = new StringTokenizer(getText().toString(),
                    "\n");
            float longest = 0;
            while (toks.hasMoreTokens()) {
                float length = getPaint().measureText(toks.nextToken());
                if (length > longest)
                    longest = length;
            }
            return longest;
        } else {
            return getPaint().measureText(getText().toString());
        }
    }
}
