
package com.atakmap.android.hierarchy.action;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.List;

/**
 * A delete action that is comprised of multiple sub-deletion tasks
 * which can be obtained and executed individually.
 */
public interface GroupDelete extends Delete {

    /**
     * Get the list of delete actions for this group
     * @return List of delete actions
     */
    List<Delete> getDeleteActions();

    /**
     * Obtain all the delete actions for this item and execute them in order
     *
     * Note: In order to properly utilize this interface, it's not recommended
     * to extend this method. Extend {@link #getDeleteActions()} instead.
     *
     * @return True if all deletions were executed successfully
     */
    default boolean delete() {

        // Get the list of delete actions
        List<Delete> actions = getDeleteActions();

        // Nothing to execute
        if (FileSystemUtils.isEmpty(actions))
            return true;

        // Execute delete actions and track success
        boolean ret = true;
        for (Delete delete : actions)
            ret &= delete.delete();

        return ret;
    }
}
