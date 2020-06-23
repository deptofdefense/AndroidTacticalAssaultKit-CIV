
package com.atakmap.android.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Base Adapter for Action Menu, used for "All Tools" menu
 * 
 * 
 */
public class ActionMenuAdapter extends BaseAdapter {
    private static final String TAG = "ActionMenuAdapter";

    public static class ViewHolder {
        TextView imageTitle;
        ImageView image;
        ActionMenuData action;
    }

    protected final Context mContext;
    protected List<ActionMenuData> mActions;

    protected final OnLongClickListener onLongClickListener;
    protected final OnClickListener onClickListener;
    protected final OnDragListener onDragListener;

    public ActionMenuAdapter(Context c, List<ActionMenuData> actions,
            OnClickListener onClick,
            OnLongClickListener onLongClick, OnDragListener onDrag) {
        mContext = c;
        mActions = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(actions)) {
            for (ActionMenuData action : actions) {
                mActions.add(new ActionMenuData(action));
            }
        }

        onClickListener = onClick;
        onLongClickListener = onLongClick;
        onDragListener = onDrag;
    }

    public void clear() {
        getActions().clear();
        notifyDataSetChanged();
    }

    public void add(List<ActionMenuData> actions, PreferredMenu menu) {
        if (!FileSystemUtils.isEmpty(actions)) {
            for (ActionMenuData action : actions) {
                ActionMenuData na = new ActionMenuData(action);
                na.setPreferredMenu(menu);
                getActions().add(na);
            }
        }
    }

    public List<ActionMenuData> getActions() {
        if (mActions == null)
            mActions = new ArrayList<>();

        return mActions;
    }

    @Override
    public int getCount() {
        return mActions.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mActions.size())
            return mActions.get(position);

        return null;
    }

    @Override
    public long getItemId(int position) {
        if (position < mActions.size())
            return mActions.get(position).getTitle().hashCode();

        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= mActions.size()) {
            Log.e(TAG, "Invalid position: " + position);
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            return inflater.inflate(R.layout.empty, parent, false);
        }

        View row = convertView;
        ViewHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(R.layout.all_tools_child, parent, false);
            if (onLongClickListener != null)
                row.setOnLongClickListener(onLongClickListener);
            if (onClickListener != null)
                row.setOnClickListener(onClickListener);
            if (onDragListener != null)
                row.setOnDragListener(onDragListener);

            holder = new ViewHolder();
            holder.imageTitle = row
                    .findViewById(R.id.allTools_childLabel);
            holder.image = row
                    .findViewById(R.id.allTools_childImage);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        ActionMenuData item = mActions.get(position);
        holder.action = item;
        holder.imageTitle.setText(item.getTitle());

        int iconColor = ActionBarReceiver.getUserIconColor();
        try {
            // attempt to create image view for tool icon
            Drawable icon = mActions.get(position).getIcon(mContext);
            holder.image.setImageDrawable(icon);
            holder.image.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon: "
                    + mActions.get(position).getIcon(), e);
            Drawable icon = mContext.getResources().getDrawable(
                    R.drawable.ic_menu_tools);
            holder.image.setImageDrawable(icon);
        }

        return row;
    }

    public boolean add(ActionMenuData action) {
        if (mActions != null)
            return mActions.add(action);

        return false;
    }

    public boolean add(int index, ActionMenuData action) {
        if (mActions != null) {
            mActions.add(index, action);
            Log.d(TAG, "Added " + action.toString());
            return true;
        }

        return false;
    }

    public void remove(ActionMenuData action) {
        if (mActions != null) {
            Log.d(TAG, "Removed " + action.toString());
            mActions.remove(action);
        }
    }

    public boolean hasRoom(ActionMenuData droppedAction) {
        return true;
    }

    public boolean contains(ActionMenuData action) {
        if (mActions != null) {
            for (ActionMenuData curAction : mActions) {
                if (curAction.equals(action))
                    return true;
            }
        }

        return false;
    }
}
