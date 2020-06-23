
package com.atakmap.android.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.app.R;

import java.util.ArrayList;

/**
 * 
 */
public class AllToolsNavLayoutAdapter extends BaseAdapter {

    private ImageView imgIcon;
    private TextView txtTitle;
    private final ArrayList<AllToolsNavLayout> spinnerNavItem;
    private final Context context;

    public AllToolsNavLayoutAdapter(Context context,
            ArrayList<AllToolsNavLayout> spinnerNavItem) {
        this.spinnerNavItem = spinnerNavItem;
        this.context = context;
    }

    @Override
    public int getCount() {
        return spinnerNavItem.size();
    }

    @Override
    public Object getItem(int index) {
        return spinnerNavItem.get(index);
    }

    @Override
    public long getItemId(int position) {
        return ((AllToolsNavLayout) getItem(position)).getMenu().getItemId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater mInflater = LayoutInflater.from(context);
            convertView = mInflater
                    .inflate(R.layout.all_tools_layout_nav, null);
        }

        imgIcon = convertView.findViewById(R.id.imgIcon);
        txtTitle = convertView.findViewById(R.id.txtTitle);

        imgIcon.setImageResource(spinnerNavItem.get(position).getIcon());
        imgIcon.setVisibility(View.GONE);
        txtTitle.setText(spinnerNavItem.get(position).getMenu().getLabel());
        txtTitle.setTextColor(Color.WHITE);
        return convertView;
    }

    @Override
    public View getDropDownView(int position, View convertView,
            ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater
                    .inflate(R.layout.all_tools_layout_nav, null);
        }

        imgIcon = convertView.findViewById(R.id.imgIcon);
        txtTitle = convertView.findViewById(R.id.txtTitle);
        txtTitle.setTextColor(Color.WHITE);

        imgIcon.setImageResource(spinnerNavItem.get(position).getIcon());
        txtTitle.setText(spinnerNavItem.get(position).getMenu().getLabel());
        return convertView;
    }

}
