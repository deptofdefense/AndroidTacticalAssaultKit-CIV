
package com.atakmap.android.widgets;

import android.graphics.Color;
import android.os.SystemClock;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.concurrent.ConcurrentLinkedQueue;

public class LayoutWidget extends AbstractParentWidget {

    public interface OnBackingColorChangedListener {
        void onBackingColorChanged(LayoutWidget layout);
    }

    public interface OnDragEnabledChangedListener {
        void onDragEnabledChanged(LayoutWidget layout);
    }

    public static class Factory extends AbstractParentWidget.Factory {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            LayoutWidget widget = new LayoutWidget();
            configAttributes(widget, config, defNode.getAttributes());
            return widget;
        }

        protected void configAttributes(LayoutWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            // bgColor
            int bgColor = DataParser.parseColorText(
                    attrs.getNamedItem("bgColor"), Color.WHITE);
            widget.setBackingColor(bgColor);
        }
    }

    public LayoutWidget() {
    }

    /**
     * Set the background color of this widget
     * @param backingColor Widget background color
     */
    public void setBackingColor(int backingColor) {
        if (_backingColor != backingColor) {
            _backingColor = backingColor;
            onBackingColorChanged();
        }
    }

    public int getBackingColor() {
        return _backingColor;
    }

    /**
     * Set whether this layout should use a medium nine patch as its background
     * This is the same background used for text widgets
     * @param ninePatchBG True to enable nine patch background
     */
    public void setNinePatchBG(boolean ninePatchBG) {
        if (_ninePatchBG != ninePatchBG) {
            _ninePatchBG = ninePatchBG;
            onBackingColorChanged();
        }
    }

    public boolean getNinePatchBG() {
        return _ninePatchBG;
    }

    /**
     * Set layout alpha applied to this layout and its children
     * Calling this will cancel alpha fading
     * @param alpha Alpha value (0 - 255)
     */
    public void setAlpha(int alpha) {
        if (_alpha != alpha || _alphaFadeStartMS > 0) {
            _alpha = alpha;
            _alphaFadeStartMS = _alphaFadeDurationMS = 0;
            onBackingColorChanged();
        }
    }

    /**
     * Fade alpha of layout and its children
     * @param fromAlpha Start alpha value (0 - 255)
     * @param toAlpha End alpha value (0 - 255)
     * @param fadeTimeMS Duration of fade in milliseconds
     */
    public void fadeAlpha(int fromAlpha, int toAlpha, int fadeTimeMS) {
        _alpha = fromAlpha;
        _alphaFadeTo = toAlpha;
        _alphaFadeDurationMS = fadeTimeMS;
        _alphaFadeStartMS = SystemClock.elapsedRealtime();
        onBackingColorChanged();
    }

    public boolean isFadingAlpha() {
        return _alphaFadeStartMS > 0;
    }

    public float getAlpha() {
        if (isFadingAlpha()) {
            long curTime = SystemClock.elapsedRealtime();
            if (curTime >= _alphaFadeStartMS + _alphaFadeDurationMS) {
                _alpha = _alphaFadeTo;
                _alphaFadeStartMS = _alphaFadeDurationMS = 0;
            } else {
                float t = (float) (curTime - _alphaFadeStartMS)
                        / _alphaFadeDurationMS;
                return (_alpha * (1 - t)) + (_alphaFadeTo * t);
            }
        }
        return _alpha;
    }

    public void setDragEnabled(boolean dragEnabled) {
        if (_dragEnabled != dragEnabled) {
            _dragEnabled = dragEnabled;
            onDragEnabledChanged();
        }
    }

    public boolean getDragEnabled() {
        return _dragEnabled;
    }

    public void addOnBackingColorChangedListener(
            OnBackingColorChangedListener l) {
        _onBackingColorChanged.add(l);
    }

    public void removeOnBackingColorChangedListener(
            OnBackingColorChangedListener l) {
        _onBackingColorChanged.remove(l);
    }

    public void addOnDragEnabledChangedListener(
            OnDragEnabledChangedListener l) {
        _onDragEnabledChanged.add(l);
    }

    public void removeOnDragEnabledChangedListener(
            OnDragEnabledChangedListener l) {
        _onDragEnabledChanged.remove(l);
    }

    protected void onBackingColorChanged() {
        for (OnBackingColorChangedListener l : _onBackingColorChanged) {
            l.onBackingColorChanged(this);
        }
    }

    protected void onDragEnabledChanged() {
        for (OnDragEnabledChangedListener l : _onDragEnabledChanged) {
            l.onDragEnabledChanged(this);
        }
    }

    private int _alpha = 255;
    private int _alphaFadeTo = 255;
    private long _alphaFadeStartMS;
    private int _alphaFadeDurationMS;

    private int _backingColor = 0;
    private boolean _ninePatchBG = false;
    private boolean _dragEnabled = false;
    private final ConcurrentLinkedQueue<OnBackingColorChangedListener> _onBackingColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnDragEnabledChangedListener> _onDragEnabledChanged = new ConcurrentLinkedQueue<>();
}
