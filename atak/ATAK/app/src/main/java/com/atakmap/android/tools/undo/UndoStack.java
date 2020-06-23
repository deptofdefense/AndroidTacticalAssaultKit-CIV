
package com.atakmap.android.tools.undo;

import com.atakmap.coremap.log.Log;

import java.util.Stack;

/**
 * Based on EditAction e.g. ShapeCreationTool.undoStack
 * 
 * 
 */
public class UndoStack {

    private static final int MAX_UNDO = 10;

    private static final String TAG = "UndoStack";

    // protected FixedStack<UndoAction> undoStack = new FixedStack<UndoAction>(new
    // UndoAction[MAX_UNDO]);
    protected Stack<UndoAction> undoStack = new Stack<>();

    public UndoStack() {
    }

    public void dispose() {
        if (undoStack != null) {
            undoStack.clear();
            undoStack = null;
        }
    }

    public void push(UndoAction action) {
        if (action == null)
            return;

        if (undoStack.size() >= MAX_UNDO) {
            UndoAction discard = undoStack.remove(0);
            if (discard != null) {
                Log.d(TAG, "Discarding " + discard.getDescription());
            }
        }

        Log.d(TAG, "Push " + action.getDescription());
        undoStack.push(action);
    }

    public boolean hasAction() {
        // return !undoStack.poppedAll();
        return undoStack.size() > 0;
    }

    /**
     * @return undone operation, otherwise null
     */
    public UndoActionResult undo() {
        if (undoStack.size() > 0) {
            UndoAction action = undoStack.pop();
            if (action != null) {
                Log.d(TAG, "Undo " + action.getDescription());
                return new UndoActionResult(action, action.undo());
            }
        }

        return null;
    }

    public static class UndoActionResult {
        private final UndoAction action;
        private final boolean result;

        public UndoActionResult(UndoAction action, boolean result) {
            super();
            this.action = action;
            this.result = result;
        }

        public UndoAction getAction() {
            return action;
        }

        public boolean isResult() {
            return result;
        }

        public boolean isValid() {
            return action != null;
        }
    }

}
