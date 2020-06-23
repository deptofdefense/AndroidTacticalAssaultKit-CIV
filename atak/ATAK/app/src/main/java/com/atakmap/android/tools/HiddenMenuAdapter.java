
package com.atakmap.android.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

/**
 * Adapter for Hidden Menu
 * 
 * 
 */
public class HiddenMenuAdapter extends ActionMenuAdapter {
    private static final String TAG = "HiddenMenuAdapter";

    HiddenMenuAdapter(Context c, List<ActionMenuData> actions,
            OnClickListener onClick,
            OnLongClickListener onLongClick, OnDragListener onDrag) {
        super(c, actions, onClick, onLongClick, onDrag);
    }

    @Override
    public boolean add(ActionMenuData action) {
        if (mActions != null) {
            action.setPreferredMenu(PreferredMenu.hidden);
            return mActions.add(action);
        }

        return false;
    }

    @Override
    public boolean add(int index, ActionMenuData action) {
        if (mActions != null) {
            action.setPreferredMenu(PreferredMenu.hidden);
            mActions.add(index, action);
            return true;
        }

        return false;
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
        ViewHolder holder;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(R.layout.all_tools_config_hidden, parent,
                    false);
            if (onLongClickListener != null)
                row.setOnLongClickListener(onLongClickListener);
            if (onClickListener != null)
                row.setOnClickListener(onClickListener);
            if (onDragListener != null)
                row.setOnDragListener(onDragListener);

            holder = new ViewHolder();
            holder.imageTitle = row
                    .findViewById(R.id.allToolsConfig_hiddenLabel);
            holder.image = row
                    .findViewById(R.id.allToolsConfig_hiddenImage);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        ActionMenuData item = mActions.get(position);
        holder.action = item;
        holder.imageTitle.setText(item.getTitle());

        try {
            // attempt to create image view for tool icon
            Drawable icon = item.getIcon(mContext);
            holder.image.setImageDrawable(icon);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon: " + item.getIcon(), e);
            Drawable icon = mContext.getResources().getDrawable(
                    R.drawable.ic_menu_tools);
            holder.image.setImageDrawable(icon);
        }

        return row;
    }
}
