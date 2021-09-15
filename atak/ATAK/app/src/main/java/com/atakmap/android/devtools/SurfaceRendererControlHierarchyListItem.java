
package com.atakmap.android.devtools;

import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.model.Mesh;

final class SurfaceRendererControlHierarchyListItem extends DevToolGroup {
    private final SurfaceRendererControl _ctrl;

    public SurfaceRendererControlHierarchyListItem(
            SurfaceRendererControl ctrl) {
        super("Surface Renderer Control", "SurfaceRendererControl");

        _ctrl = ctrl;
        _children.add(
                new DrawModeHierarchyListItem(_ctrl, Mesh.DrawMode.Triangles));
        _children
                .add(new DrawModeHierarchyListItem(_ctrl, Mesh.DrawMode.Lines));
        _children.add(
                new DrawModeHierarchyListItem(_ctrl, Mesh.DrawMode.Points));
    }

    final static class DrawModeHierarchyListItem extends DevToolToggle {
        private final SurfaceRendererControl _ctrl;
        private final Mesh.DrawMode _mode;

        public DrawModeHierarchyListItem(SurfaceRendererControl ctrl,
                Mesh.DrawMode mode) {
            super(mode.name(),
                    "SurfaceRendererControl.DrawMode." + mode.name());

            _ctrl = ctrl;
            _mode = mode;
        }

        @Override
        protected void setEnabled(boolean visible) {
            if (visible)
                _ctrl.enableDrawMode(_mode);
            else
                _ctrl.disableDrawMode(_mode);
        }

        @Override
        protected boolean isEnabled() {
            return _ctrl.isDrawModeEnabled(_mode);
        }
    }
}
