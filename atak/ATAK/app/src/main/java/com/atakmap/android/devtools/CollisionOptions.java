
package com.atakmap.android.devtools;

import com.atakmap.map.layer.control.SurfaceRendererControl;

public class CollisionOptions extends DevToolGroup {
    final SurfaceRendererControl _ctrl;

    public CollisionOptions(SurfaceRendererControl ctrl) {
        super("Camera Collision", "SurfaceRendererControl.CameraCollision");
        _ctrl = ctrl;

        _children.add(new CollisionDistance(0d));
        _children.add(new CollisionDistance(1d));
        _children.add(new CollisionDistance(5d));
        _children.add(new CollisionDistance(10d));
    }

    final class CollisionDistance extends DevToolToggle {
        final double _radius;

        CollisionDistance(double radius) {
            super((radius > 0d) ? String.format("%.1fm", radius) : "Disabled",
                    CollisionOptions.this.getUID() + "."
                            + String.format("%.1fm", radius));
            _radius = radius;
        }

        @Override
        protected boolean isEnabled() {
            return (_ctrl.getCameraCollisionRadius() == _radius);
        }

        @Override
        protected void setEnabled(boolean v) {
            if (v)
                _ctrl.setCameraCollisionRadius(_radius);
        }
    }
}
