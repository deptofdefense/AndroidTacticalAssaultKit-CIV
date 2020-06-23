
package com.atakmap.android.util;

public abstract class EditAction {
    public abstract boolean run();

    public abstract void undo();

    public abstract String getDescription();
}
