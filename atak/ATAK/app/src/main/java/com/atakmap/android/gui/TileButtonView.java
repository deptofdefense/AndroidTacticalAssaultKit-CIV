
package com.atakmap.android.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * View used for the tile buttons
 * The icon and text dynamically resize and wrap to fit within the button
 * See ATAK-10467
 */
public class TileButtonView extends View {

    private final Paint _paint;
    private final Matrix _mat;
    private final float _dp;
    private Drawable _icon;
    private final int _iconSize;
    private String _text = "";
    private final int _maxSize;
    private final int _minSize;
    private final int _textColor;

    private final List<String> scrList = new ArrayList<>();
    private final StringBuilder scrBuilder = new StringBuilder();

    public TileButtonView(Context context) {
        this(context, null);
    }

    public TileButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);

        _mat = new Matrix();
        _paint = new Paint();
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _dp = context.getResources().getDisplayMetrics().density;

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.TileButtonView, 0, 0);

        int iconID = a.getResourceId(R.styleable.TileButtonView_icon, 0);
        CharSequence text = a.getText(R.styleable.TileButtonView_text);

        _iconSize = a.getDimensionPixelSize(R.styleable.TileButtonView_iconSize,
                -1);
        _maxSize = a.getDimensionPixelSize(
                R.styleable.TileButtonView_maxTextSize, (int) (14 * _dp));
        _minSize = a.getDimensionPixelSize(
                R.styleable.TileButtonView_minTextSize, (int) (12 * _dp));
        _textColor = a.getColor(R.styleable.TileButtonView_textColor,
                Color.LTGRAY);

        a.recycle();

        if (iconID != 0) {
            setIcon(context.getDrawable(iconID));
        }
        if (text != null)
            _text = text.toString();
    }

    public void setIcon(Drawable icon) {
        _icon = icon;
        if (_icon != null) {
            _icon = _icon.mutate();
            _icon.setBounds(0, 0, _icon.getIntrinsicWidth(),
                    _icon.getIntrinsicHeight());
        }
        invalidate();
    }

    public void setText(String text) {
        _text = text;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        int fullWidth = getWidth() - getPaddingStart() - getPaddingEnd();
        int fullHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int x = getPaddingStart(), y = getPaddingTop();

        _paint.setColor(_textColor);
        if (_text != null && !_text.isEmpty()) {

            // Check if the text can fit within the button at max size
            float textWidth = setTextSize(_maxSize, _text);

            // Shrink down to min size and check again
            if (textWidth > fullWidth)
                textWidth = setTextSize(_minSize, _text);

            // Start line wrapping
            List<String> lines = this.scrList;
            lines.clear();
            if (textWidth > fullWidth) {
                StringBuilder sb = this.scrBuilder;
                sb.delete(0, sb.length());
                String[] words = _text.split(" ");
                float lSize = 0f;
                for (int i = 0; i < words.length; ++i) {
                    float width = _paint
                            .measureText((i > 0 ? " " : "") + words[i]);
                    if (lSize + width < fullWidth) {
                        // word does fit with the current line, keep going 
                        if (sb.length() > 0)
                            sb.append(" ");
                        sb.append(words[i]);
                        lSize += width;
                    } else {
                        if (sb.length() > 0) {
                            lines.add(sb.toString());
                            sb.delete(0, sb.length());
                            lSize = 0;
                        }
                        if (width < fullWidth) {
                            sb.append(words[i]);
                            lSize = width;
                        } else {
                            // Word does not fit on the current line
                            lines.add(words[i]);
                        }
                    }
                }
                if (sb.length() > 0)
                    lines.add(sb.toString());
            } else
                lines.add(_text);

            float textHeight = _paint.getTextSize();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                float lineWidth = _paint.measureText(line);
                c.drawText(line, x + (fullWidth - lineWidth) / 2,
                        getBottom() - getPaddingBottom()
                                - (lines.size() - i - 1) * textHeight,
                        _paint);
            }

            fullHeight -= textHeight * lines.size();
        }

        // Draw the icon
        if (_icon != null && _iconSize != 0) {
            int s = c.save();
            float width = _icon.getIntrinsicWidth();
            float height = _icon.getIntrinsicHeight();
            int allowWidth, allowHeight;
            if (_iconSize != -1) {
                allowWidth = Math.min(fullWidth, _iconSize);
                allowHeight = Math.min(fullHeight, _iconSize);
            } else {
                allowWidth = fullWidth;
                allowHeight = fullHeight;
            }
            float scaleX = allowWidth / width;
            float scaleY = allowHeight / height;
            float scale = Math.min(scaleX, scaleY);
            width *= scale;
            height *= scale;
            _mat.reset();
            _mat.postScale(scale, scale);
            _mat.postTranslate(x + (fullWidth - width) / 2,
                    y + (fullHeight - height) / 2);
            c.concat(_mat);
            _icon.draw(c);
            c.restoreToCount(s);
        }
    }

    private float setTextSize(int size, String text) {
        _paint.setTextSize(size);
        return _paint.measureText(text);
    }
}
