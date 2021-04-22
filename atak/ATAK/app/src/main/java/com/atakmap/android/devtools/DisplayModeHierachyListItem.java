
package com.atakmap.android.devtools;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.map.MapRenderer2;

import java.util.ArrayList;

final class DisplayModeHierachyListItem extends DevToolGroup {
    private final MapRenderer2 _renderer;

    public DisplayModeHierachyListItem(MapRenderer2 renderer) {
        super("Display Mode", "MapRenderer2.DisplayMode");

        _renderer = renderer;
        _children.add(new DisplayModeHierachyListItem.DisplayModelToggleImpl(
                _renderer, MapRenderer2.DisplayMode.Flat));
        _children.add(new DisplayModeHierachyListItem.DisplayModelToggleImpl(
                _renderer, MapRenderer2.DisplayMode.Globe));
    }

    final static class DisplayModelToggleImpl extends DevToolToggle {
        private final MapRenderer2 _renderer;
        private final MapRenderer2.DisplayMode _mode;

        public DisplayModelToggleImpl(MapRenderer2 renderer,
                MapRenderer2.DisplayMode mode) {
            super(mode.name(), "MapRenderer2.DisplayMode." + mode.name());

            _renderer = renderer;
            _mode = mode;
        }

        @Override
        protected void setEnabled(boolean visible) {
            if (visible)
                _renderer.setDisplayMode(_mode);
        }

        @Override
        protected boolean isEnabled() {
            return _renderer.getDisplayMode() == _mode;
        }
    }
}
