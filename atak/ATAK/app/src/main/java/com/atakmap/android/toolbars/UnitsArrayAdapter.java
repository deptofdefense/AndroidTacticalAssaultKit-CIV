
package com.atakmap.android.toolbars;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.atakmap.coremap.conversions.Span;

/**
 * Adapter for R&B units
 */
public class UnitsArrayAdapter extends ArrayAdapter<Span> {

    public UnitsArrayAdapter(Context context, int textViewResourceId,
            Span[] objects) {
        super(context, textViewResourceId, objects);
    }

    private static class ViewHolder {
        private TextView itemView;
    }

    @Override
    @NonNull
    public View getView(int position, View row, @NonNull ViewGroup parent) {
        ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
        if (h == null) {
            row = LayoutInflater.from(getContext()).inflate(
                    android.R.layout.simple_list_item_1, parent, false);
            h = new ViewHolder();
            h.itemView = row.findViewById(android.R.id.text1);
            row.setTag(h);
        }
        Span unit = getItem(position);
        if (unit != null)
            h.itemView.setText(unit.getAbbrev());
        return row;
    }
}
