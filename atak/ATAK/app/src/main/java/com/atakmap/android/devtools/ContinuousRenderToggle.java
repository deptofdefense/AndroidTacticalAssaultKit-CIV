
package com.atakmap.android.devtools;

import com.atakmap.map.RenderContext;

final class ContinuousRenderToggle extends DevToolToggle {
    final RenderContext _context;

    ContinuousRenderToggle(RenderContext ctx) {
        super("Continuous Rendering", "RenderContext.ContinuousRender.Enabled");

        _context = ctx;
    }

    @Override
    protected boolean isEnabled() {
        return _context.isContinuousRenderEnabled();
    }

    @Override
    protected void setEnabled(boolean v) {
        _context.setContinuousRenderEnabled(v);
    }
}
