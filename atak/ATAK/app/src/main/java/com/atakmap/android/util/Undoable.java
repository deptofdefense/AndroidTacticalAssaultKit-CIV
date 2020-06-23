
package com.atakmap.android.util;

public interface Undoable {
    boolean run(EditAction action);

    void undo();

}
