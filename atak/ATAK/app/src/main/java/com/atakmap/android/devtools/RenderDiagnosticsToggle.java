
package com.atakmap.android.devtools;

import com.atakmap.map.opengl.GLMapView;

final class RenderDiagnosticsToggle extends DevToolToggle {
    final GLMapView _view;

    public RenderDiagnosticsToggle(GLMapView view) {
        super("Renderer Diagnostics", "GLMapView.RenderDiagnostics");
        _view = view;
    }

    @Override
    protected boolean isEnabled() {
        return _view.isRenderDiagnosticsEnabled();
    }

    @Override
    protected void setEnabled(boolean v) {
        _view.setRenderDiagnosticsEnabled(v);
    }
}
