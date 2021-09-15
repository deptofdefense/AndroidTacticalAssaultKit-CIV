
package com.atakmap.android.devtools;

import com.atakmap.map.MapSceneModel;

final class CameraModeHierarchyListItem extends DevToolGroup {
    public CameraModeHierarchyListItem() {
        super("Camera Mode", "MapRenderer.CameraMode");

        _children.add(new CameraModeItem("Ortho", false));
        _children.add(new CameraModeItem("Perspective", true));
    }

    final static class CameraModeItem extends DevToolToggle {
        private final boolean _perspective;

        public CameraModeItem(String title, boolean perspective) {
            super(title, "MapRenderer.CameraMode." + title);

            _perspective = perspective;
        }

        @Override
        protected void setEnabled(boolean visible) {
            MapSceneModel.setPerspectiveCameraEnabled(
                    _perspective ? visible : !visible);
        }

        @Override
        protected boolean isEnabled() {
            final boolean enabled = MapSceneModel.isPerspectiveCameraEnabled();
            return _perspective ? enabled : !enabled;
        }
    }
}
