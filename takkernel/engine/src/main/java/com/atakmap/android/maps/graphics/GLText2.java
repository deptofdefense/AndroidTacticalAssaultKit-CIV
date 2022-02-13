
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.opengl.GLText;

public class GLText2 {

    private MapTextFormat textFormat;
    private GLText impl;

    public GLText2(MapTextFormat textFormat, String text) {
        this.textFormat = textFormat;
        this.impl = GLText.getInstance(null, textFormat);

        setText(text);
    }

    public void setText(String text) {
        if (text == null)
            text = "";
        _text = GLText.localize(text);

        _width = this.textFormat.measureTextWidth(text);
        _height = this.textFormat.measureTextHeight(text);
        _lineCount = text.split("\n").length;
        _length = text.length();
    }

    public int getLength() {
        return _length;
    }

    public float getWidth() {
        return _width;
    }

    public float getHeight() {
        return _height;
    }

    public float getBaselineSpacing() {
        return this.textFormat.getBaselineSpacing();
    }

    public int getLineCount() {
        return _lineCount;
    }

    public void draw(int[] colors) {
        this.impl.drawSplitString(_text, colors);
    }

    public void draw(int color) {
        if (_lineCount > 1) {
            this.impl.drawSplitString(_text,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    Color.alpha(color) / 255f);
        } else {
            this.impl.draw(_text,
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    Color.alpha(color) / 255f);
        }
    }

    private String _text;
    private int _lineCount;
    private int _length;
    private float _width;
    private float _height;
}
