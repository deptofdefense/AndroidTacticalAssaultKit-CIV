
package com.atakmap.android.devtools;

import com.atakmap.map.layer.control.AtmosphereControl;

public class AtmosphereToggle extends DevToolToggle {
    final AtmosphereControl _control;

    AtmosphereToggle(AtmosphereControl control) {
        super("Atmosphere Enabled", "AtmosphereControl.Enabled");
        _control = control;
    }

    @Override
    protected boolean isEnabled() {
        return _control.isAtmosphereEnabled();
    }

    @Override
    protected void setEnabled(boolean v) {
        _control.setAtmosphereEnabled(v);
    }
}
