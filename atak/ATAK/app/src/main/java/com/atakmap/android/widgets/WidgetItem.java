
package com.atakmap.android.widgets;

import android.graphics.Point;

import com.atakmap.android.maps.MapDataRef;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Defines icon reference, color, and text under different states for a element of a widget.
 * 
 * 
 */
public class WidgetItem {

    /**
     * Create a widget item given default criteria.
     * 
     * @param iconRef the default icon image reference
     * @param iconWidth the on-screen icon width in pixels (-1) if unknown
     * @param iconHeight the on-screen icon height in pixels (-1) if unknown
     * @param iconAnchor the pixel location of the icon anchor point (in on-screen space)
     * @param labelText the label text for the menu item
     * @param backingColor the background color for the menu item (alpha supported)
     */
    public WidgetItem(MapDataRef iconRef, int iconWidth, int iconHeight,
            Point iconAnchor,
            String labelText, int backingColor) {
        State s = _ensureState(0);
        s._icon = iconRef;
        s._color = backingColor;
        _anchorx = iconAnchor.x;
        _anchory = iconAnchor.y;
        _label = labelText;
        _iconWidth = 32;
        _iconHeight = 32;
    }

    /**
     * @param state
     * @return
     */
    public MapDataRef getIconRef(int state) {
        State s = _states.get(state);
        if (s == null || s._icon == null) {
            s = _states.get(0);
        }
        return s != null ? s._icon : null;
    }

    /**
     * @param state
     * @return
     */
    public int getBackingColor(int state) {
        State s = _states.get(state);
        if (s == null) {
            s = _states.get(0);
        }
        return s != null ? s._color : 0;
    }

    /**
     * @return
     */
    public String getLabelText() {
        return _label;
    }

    /**
     * @return
     */
    public Point getIconAnchor() {
        return new Point(_anchorx, _anchory);
    }

    /**
     * @return
     */
    public int getIconWidth() {
        return _iconWidth;
    }

    /**
     * @return
     */
    public int getIconHeight() {
        return _iconHeight;
    }

    /**
     * 
     */
    public static class Builder {

        /**
         * @param labelText
         * @return
         */
        public Builder setLabel(String labelText) {
            _label = labelText;
            return this;
        }

        /**
         * @param anchor
         * @return
         */
        public Builder setIconAnchor(Point anchor) {
            _anchorx = anchor.x;
            _anchory = anchor.y;
            return this;
        }

        /**
         * @param width
         * @param height
         * @return
         */
        public Builder setIconSize(int width, int height) {
            _iconWidth = width;
            _iconHeight = height;
            return this;
        }

        /**
         * @param state
         * @param color
         * @return
         */
        public Builder setBackingColor(int state, int color) {
            State s = _ensureState(state);
            s._color = color;
            return this;
        }

        /**
         * @param color
         * @return
         */
        public Builder setBackingColor(int color) {
            return setBackingColor(0, color);
        }

        /**
         * @param state
         * @param iconRef
         * @return
         */
        public Builder setIconRef(int state, MapDataRef iconRef) {
            State s = _ensureState(state);
            s._icon = iconRef;
            return this;
        }

        /**
         * @param iconRef
         * @return
         */
        public Builder setIconRef(MapDataRef iconRef) {
            return setIconRef(0, iconRef);
        }

        /**
         * @return
         */
        public WidgetItem build() {
            WidgetItem item = new WidgetItem();
            item._label = _label;
            item._anchorx = _anchorx;
            item._anchory = _anchory;
            item._iconHeight = _iconHeight;
            item._iconWidth = _iconWidth;
            item._states = _cloneStates();
            return item;
        }

        public Builder fork() {
            Builder b = new Builder();
            b._label = _label;
            b._anchorx = _anchorx;
            b._anchory = _anchory;
            b._iconWidth = _iconWidth;
            b._iconHeight = _iconHeight;
            b._states = _cloneStates();
            return b;
        }

        private TreeMap<Integer, State> _cloneStates() {
            TreeMap<Integer, State> r = new TreeMap<>();
            Set<Entry<Integer, State>> entries = _states.entrySet();
            for (Entry<Integer, State> e : entries) {
                r.put(e.getKey(), e.getValue().copy());
            }
            return r;
        }

        private State _ensureState(int state) {
            State s = _states.get(state);
            if (s == null) {
                s = new State();
                s._key = state;
                _states.put(state, s);
            }
            return s;
        }

        private String _label;
        private int _anchorx;
        private int _anchory;
        private int _iconWidth = -1;
        private int _iconHeight = -1;
        private TreeMap<Integer, State> _states = new TreeMap<>();
    }

    /**
     * @return
     */
    public State[] getStates() {
        Collection<State> values = _states.values();
        return values.toArray(new State[_states.size()]);
    }

    private WidgetItem() {

    }

    private State _ensureState(int state) {
        State s = _states.get(state);
        if (s == null) {
            s = new State();
            s._key = state;
            _states.put(state, s);
        }
        return s;
    }

    public static class State {
        public int getBackingColor() {
            return _color;
        }

        public MapDataRef getIconRef() {
            return _icon;
        }

        public int getStateKey() {
            return _key;
        }

        public String toString() {
            return "{icon=" + _icon + ", color=" + _color + "}";
        }

        public State copy() {
            State s = new State();
            s._color = _color;
            s._key = _key;
            s._icon = _icon;
            return s;
        }

        private int _color;
        private MapDataRef _icon;
        private int _key;
    }

    private String _label;
    private int _anchorx;
    private int _anchory;
    private int _iconWidth = -1;
    private int _iconHeight = -1;
    private TreeMap<Integer, State> _states = new TreeMap<>();
}
