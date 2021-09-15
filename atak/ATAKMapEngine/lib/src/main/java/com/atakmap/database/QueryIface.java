
package com.atakmap.database;

public interface QueryIface extends CursorIface, Bindable {

    /**
     * Prepares for a new query. Note that this method does NOT clear the
     * existing bindings; bindings must always be explicitly cleared via
     * {@link #clearBindings()}.
     */
    public void reset();

}
