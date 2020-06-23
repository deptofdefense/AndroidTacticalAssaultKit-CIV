
package com.atakmap.android.tools.undo;

/**
 * Based on com.atakmap.android.util.EditAction
 * 
 * 
 */
public interface UndoAction {
    boolean undo();

    String getDescription();
}
