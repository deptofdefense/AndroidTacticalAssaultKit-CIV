
package com.atakmap.android.user.icon;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

public abstract class IconsetAdapterBase extends BaseAdapter {
    private static final String TAG = "UserIconsetAdapter";

    static class ViewHolder {
        ImageView image;
    }

    protected final Context mContext;
    protected List<UserIcon> mGroupIcons;

    public IconsetAdapterBase(Context c, List<UserIcon> icons) {
        mContext = c;
        mGroupIcons = icons;
    }

    public abstract void setIcons(List<UserIcon> icons);

    @Override
    public int getCount() {
        return mGroupIcons.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mGroupIcons.size())
            return mGroupIcons.get(position);

        return null;
    }

    @Override
    public long getItemId(int position) {
        if (position < mGroupIcons.size())
            return mGroupIcons.get(position).getId();

        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= mGroupIcons.size()) {
            Log.e(TAG, "Invalid position: " + position);
            LayoutInflater inflater = ((Activity) mContext)
                    .getLayoutInflater();
            return inflater.inflate(R.layout.empty, parent, false);
        }

        View row = convertView;
        ViewHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext)
                    .getLayoutInflater();
            row = inflater.inflate(R.layout.enter_location_user_icon_child,
                    parent, false);

            holder = new ViewHolder();
            holder.image = row
                    .findViewById(R.id.userIcon_childImage);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        UserIcon icon = mGroupIcons.get(position);

        try {
            // attempt to create image view for icon
            Bitmap bitmap = UserIconDatabase.instance(mContext)
                    .getIconBitmap(icon.getId());
            holder.image.setImageBitmap(bitmap);
            Drawable d = holder.image.getDrawable();
            if (bitmap != null && d != null) {
                holder.image.setImageDrawable(d);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon: " + icon.toString(), e);
            Drawable d = mContext.getResources().getDrawable(
                    R.drawable.close);
            holder.image.setImageDrawable(d);
        }
        return row;
    }

    public AdapterView.OnItemClickListener getOnItemClickListener() {
        return null;
    }

    public String getName() {
        return "";
    }

}
