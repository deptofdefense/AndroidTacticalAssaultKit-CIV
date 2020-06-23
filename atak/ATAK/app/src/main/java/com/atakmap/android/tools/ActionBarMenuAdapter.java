
package com.atakmap.android.tools;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import android.widget.AbsListView;

import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

/**
 * Adapter for Action Bar Menu Assumes icons for all actions are the same size (as the "Placeholder"
 * icon)
 * 
 * 
 */
public class ActionBarMenuAdapter extends ActionMenuAdapter {

    private static final String TAG = "ActionBarMenuAdapter";

    /**
     * Max number of actions that can fit in the action bar for this device
     */
    private final int mMax;
    private final int mIconSize;

    private PlaceholderLongClickListener mPlaceholderLongClick;

    private final class PlaceholderLongClickListener implements
            OnLongClickListener {

        @Override
        public boolean onLongClick(View view) {
            if (view != null && view.getTag() != null
                    && view.getTag() instanceof ViewHolder) {
                ActionMenuData action = ((ViewHolder) view
                        .getTag()).action;
                if (action != null && action.isValid()) {
                    Toast.makeText(mContext, action.getTitle(),
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            Log.w(TAG, "Invalid view click");
            return false;
        }
    }

    public ActionBarMenuAdapter(Context c, List<ActionMenuData> actions,
            int iconSize, int max, OnClickListener onClick,
            OnLongClickListener onLongClick, OnDragListener onDrag) {
        super(c, actions, onClick, onLongClick, onDrag);
        mIconSize = iconSize;
        mMax = max;
        fill();
    }

    /**
     * If room, add specified action Also fill any empty space with placeholders
     */
    @Override
    public boolean add(ActionMenuData action) {
        if (mActions == null)
            return false;

        if (mActions.size() < mMax) {
            Log.d(TAG, "Adding and filling action: " + action.toString());
            // if action bar is empty, place this action and fill with placeholders
            if (mActions.add(action)) {
                action.setPreferredMenu(PreferredMenu.actionBar);
                fill();
                return true;
            } else {
                Log.w(TAG,
                        "Failed to add action to empty toolbar: "
                                + action.toString());
                return false;
            }
        } else if (hasRoom(action)) {
            // e.g. dropped on gridview (in between actions), so swap out
            // the first placeholder
            // TODO could instead find closest placeholder
            for (int i = 0; i < mActions.size(); i++) {
                if (mActions.get(i).isPlaceholder()) {
                    Log.d(TAG, "Swapping " + i + " "
                            + mActions.get(i).toString()
                            + " - next placeholder for " + action.toString());
                    mActions.add(i, action);
                    mActions.remove(i + 1);
                    action.setPreferredMenu(PreferredMenu.actionBar);
                    return true;
                }
            }

            Log.d(TAG, "No room for another action: " + action.toString());
            Toast.makeText(mContext, "No room", Toast.LENGTH_LONG).show();
            return false;
        } else {
            Log.w(TAG, "No room in non empty adapter");
            return false;
        }
    }

    /**
     * Fill the action bar with Placeholders
     *
     */
    private void fill() {
        int toAddCount = mMax - mActions.size();

        Log.d(TAG, "filling with " + toAddCount + " placeholders");
        for (int i = 0; i < toAddCount; i++) {
            mActions.add(ActionMenuData.createPlaceholder());
        }
    }

    /**
     * Actions from other adapters may only be placed in "Placeholder" slots. Actions within this
     * adapter may be rearranged
     */
    @Override
    public boolean add(int index, ActionMenuData action) {
        if (mActions != null) {
            if (index >= mActions.size()) {
                Log.w(TAG, "Cannot add action at index: " + index);
                return false;
            }

            ActionMenuData existing = (ActionMenuData) getItem(index);
            if (existing == null) {
                Log.w(TAG, "Cannot add action (2) at index: " + index);
                return false;
            }

            // see if this is a new action for this adapter, or if we are just rearranging
            int oldActionIndex = mActions.indexOf(action);
            if (oldActionIndex >= 0) {
                // rearrange within this adapter, see if dropping on placeholder
                if (existing.isPlaceholder()) {
                    // drop in placeholder, put placeholder in old location
                    Log.d(TAG, "Swapping " + index + " " + existing + ", for "
                            + action.toString());
                    mActions.remove(index); // remove placeholder from action's new spot
                    mActions.add(index, action); // add dropped action in that slot
                    mActions.remove(oldActionIndex); // remove action from old spot
                    mActions.add(oldActionIndex, existing); // put placeholder in old location
                    return true;
                } else {
                    // drop in new location, remove from old location
                    Log.d(TAG, "Moving " + action.toString() + " from "
                            + oldActionIndex + " to "
                            + index);
                    mActions.add(index, action);
                    int removeIndex = oldActionIndex;
                    if (index < oldActionIndex)
                        removeIndex++; // bump index by 1 since we added before the old location
                    mActions.remove(removeIndex);
                    return true;
                }
            } else {
                // action being dropped from some other adapter
                // see if dropped on a placeholder
                if (existing.isPlaceholder()) {
                    Log.d(TAG, "Swapping " + index + " " + existing + ", for "
                            + action.toString());
                    mActions.add(index, action);
                    mActions.remove(index + 1);
                    action.setPreferredMenu(PreferredMenu.actionBar);
                    return true;
                } else {
                    // find first placeholder
                    // TODO could instead find closest placeholder
                    for (int i = 0; i < mActions.size(); i++) {
                        if (mActions.get(i).isPlaceholder()) {
                            Log.d(TAG,
                                    "Swapping " + i + " "
                                            + mActions.get(i).toString()
                                            + " - next placeholder for "
                                            + action.toString());
                            mActions.add(i, action);
                            mActions.remove(i + 1);
                            action.setPreferredMenu(PreferredMenu.actionBar);
                            return true;
                        }
                    }

                    Log.d(TAG,
                            "No room for another action: " + action.toString());
                    Toast.makeText(mContext, "No room", Toast.LENGTH_LONG)
                            .show();
                    return false;
                }
            }
        }

        return false;
    }

    @Override
    public void remove(ActionMenuData action) {
        if (mActions != null) {
            int index = mActions.indexOf(action);
            if (index < 0) {
                Log.w(TAG, "Unable to remove action: " + action.toString());
                return;
            }

            Log.d(TAG, "Removed " + action.toString() + " from " + index);
            mActions.remove(index);
            mActions.add(index, ActionMenuData.createPlaceholder());
        }
    }

    /**
     * True there is an open slot (not yet at mMax) or there is at least one Placeholder action.
     * Note, we currently assume all actions are the same size (as Placeholder), so we don't measure
     * here
     */
    @Override
    public boolean hasRoom(ActionMenuData action) {
        if (mActions.size() < mMax)
            return true;

        for (ActionMenuData curAction : mActions) {
            if (curAction.isPlaceholder())
                return true;
        }

        return false;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (position >= mActions.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return new View(mContext);
        }
        ViewHolder holder = row != null ? (ViewHolder) row.getTag() : null;
        if (holder == null) {
            holder = new ViewHolder();
            row = holder.image = new ImageView(mContext);
            row.setLayoutParams(new AbsListView.LayoutParams(mIconSize,
                    mIconSize));
            row.setPadding(ActionBarReceiver.ActionItemPaddingLR,
                    ActionBarReceiver.ActionItemPaddingLR,
                    ActionBarReceiver.ActionItemPaddingLR,
                    ActionBarReceiver.ActionItemPaddingLR);
            row.setTag(holder);
        }

        if (onLongClickListener != null)
            row.setOnLongClickListener(onLongClickListener);
        if (onClickListener != null)
            row.setOnClickListener(onClickListener);
        if (onDragListener != null)
            row.setOnDragListener(onDragListener);

        ActionMenuData item = mActions.get(position);
        if (item.isPlaceholder()) {
            // placeholders are not draggable to other lists
            row.setOnLongClickListener(mPlaceholderLongClick);
            // Log.d(TAG, "resetting onLongClick for: " + item.toString());
        }

        holder.action = item;

        int iconColor = ActionBarReceiver.getUserIconColor();
        try {
            // attempt to create image view for tool icon
            Drawable icon = item.getIcon(mContext);
            holder.image.setImageDrawable(icon);
            holder.image.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon: " + item.getIcon(), e);
            Drawable icon = mContext.getResources().getDrawable(
                    R.drawable.ic_menu_tools);
            holder.image.setImageDrawable(icon);
        }

        return row;
    }
}
