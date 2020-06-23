
package com.atak.plugins.impl;

import android.content.Context;

public interface PluginContextProvider {
    /**
     * Implementors use the following interface to indicate that it can return the plugin context.
     * @return the plugin context.
     */
    Context getPluginContext();
}
