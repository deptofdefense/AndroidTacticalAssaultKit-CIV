
package com.atakmap.android.tools.menu;

import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Represents a list of action menus
 *
 * 
 */
@Root(name = "ActionMenuList")
public class ActionMenuListData {

    private static final String TAG = "ActionMenuList";

    /**
     * These tools are loosely anchored to the bottom of the overflow actions. Not strictly
     * enforced but if these tools are at the end of the overflow action, prior to the hidden
     * actions (which is our default XML layouts), then then any plugins will be added above these
     * Note, Layout Manager item gets added auto-magically at render time, not in this list
     */
    private static final List<String> BOTTOM_ANCHORED;

    static {
        BOTTOM_ANCHORED = new ArrayList<>();
        BOTTOM_ANCHORED.add(ActionBarReceiver.QUIT_TOOL);
        BOTTOM_ANCHORED.add(ActionBarReceiver.SETTINGS_TOOL);
        BOTTOM_ANCHORED.add(ActionBarReceiver.LAYOUT_MGR);
    }

    @ElementList(entry = "ActionMenu", inline = true, required = true)
    protected List<ActionMenuData> actions;

    public ActionMenuListData() {
    }

    public ActionMenuListData(ActionMenuListData copy) {
        if (copy != null && copy.isValid()) {
            actions = new ArrayList<>();
            for (ActionMenuData action : copy.getActions()) {
                actions.add(new ActionMenuData(action));
            }
        }
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(actions);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ActionMenuListData) {
            ActionMenuListData c = (ActionMenuListData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ActionMenuListData c) {

        return FileSystemUtils.isEquals(getActions(), c.getActions());
    }

    @Override
    public int hashCode() {
        return 31 * ((getActions() == null ? 0 : getActions().hashCode()));
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%d",
                (getActions() == null ? 0
                        : getActions().size()));
    }

    synchronized public List<ActionMenuData> getActions() {
        if (actions == null)
            actions = new ArrayList<>();

        return actions;
    }

    public List<ActionMenuData> getActions(PreferredMenu pm) {
        List<ActionMenuData> matching = new ArrayList<>();
        if (!isValid())
            return matching;

        for (ActionMenuData action : actions) {
            if (action.getPreferredMenu() == pm)
                matching.add(action);
        }
        return matching;
    }

    public List<ActionMenuData> getPlaceholderActions(boolean placeholder) {
        List<ActionMenuData> matching = new ArrayList<>();
        if (!isValid())
            return matching;

        for (ActionMenuData action : actions) {
            if (action.isPlaceholder() == placeholder)
                matching.add(action);
        }
        return matching;
    }

    public ActionMenuData getAction(String title) {
        if (!isValid())
            return null;

        for (ActionMenuData action : getActions()) {
            if (action != null && action.isValid()
                    && action.getTitle().equalsIgnoreCase(title))
                return action;
        }

        return null;
    }

    public ActionMenuData getAction(int id) {
        if (!isValid()) {
            return null;
        }

        for (ActionMenuData action : getActions()) {
            if (action != null && action.isValid() && action.getId() == id)
                return action;
        }

        return null;
    }

    /**
     * Note, does not do a full .equals comparison Returns true if a tool exists
     * with the same title
     *
     * @param action
     * @return
     */
    public boolean hasAction(ActionMenuData action) {
        if (action == null || !action.isValid()) {
            return false;
        }

        return getAction(action.getId()) != null;
    }

    /**
     * Adds an action to the end of the action list.   Since this is usually a
     * plugin menu item, the overflow is the appropriate place for it.
     */
    public boolean add(final ActionMenuData action) {
        return getActions().add(action);
    }

    /**
     * Inserts a menu at a specified location.
     */
    public boolean add(final ActionMenuData action, final int location) {
        if (location < 0 || location > actions.size()) {
            return add(action);
        }
        getActions().add(location, action);
        return true;
    }

    /**
     * Add at end of visible (action/overflow) menus, prior to hidden items and prior
     * to BOTTOM_ANCHORED items
     *
     * @param menu
     */
    public void addLast(ActionMenuData menu) {
        int index = -1;

        //see if we can find a better spot
        for (int i = 0; i < actions.size(); i++) {
            ActionMenuData cur = actions.get(i);
            if (cur != null &&
                    cur.getPreferredMenu().equals(PreferredMenu.hidden)) {
                //place it above first hidden menu
                index = Math.max(0, i);
                //Log.d(TAG, "add last above hidden: " + index);
                break;
            }
        }

        //move one up from the last hidden (by default XML it will be Quit item)
        int tmpI = index - 1;

        if (index < 0) {
            //no hidden items found, lets start at the back
            index = actions.size() - 1;
            //by default XML last one is Quit item
            tmpI = index;
            //Log.d(TAG, "add last: " + index);
        }

        //See if we can walk up above anchored items
        while (tmpI > 0) {
            ActionMenuData tempM = actions.get(tmpI);
            if (tempM != null && BOTTOM_ANCHORED.contains(tempM.getTitle())) {
                index = Math.max(0, tmpI);
                //Log.d(TAG, "add last above anchored item: " + tempM.getTitle() + ", " + index);
            } else {
                //item not bottom anchored
                break;
            }

            tmpI -= 1;
        }

        //Log.d(TAG, "add last final: " + index);
        add(menu, index);
    }

    public boolean add(List<ActionMenuData> actions) {
        return getActions().addAll(actions);
    }

    public int size() {
        return getActions().size();
    }

    public boolean remove(ActionMenuData action) {
        return getActions().remove(action);
    }

    public void clear() {
        getActions().clear();
    }

}
