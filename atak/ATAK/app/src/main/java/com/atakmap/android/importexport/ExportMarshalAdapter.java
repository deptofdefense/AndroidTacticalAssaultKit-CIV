
package com.atakmap.android.importexport;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.importexport.ExporterManager.ExportMarshalMetadata;
import com.atakmap.app.R;

/**
 * Display a list of Exporters
 * 
 * 
 */
public class ExportMarshalAdapter extends ArrayAdapter<ExportMarshalMetadata> {

    private final Context context;
    private final int layoutResourceId;
    private ExportMarshalMetadata[] data = null;

    public ExportMarshalAdapter(Context context, int layoutResourceId,
            ExportMarshalMetadata[] data) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView,
            @NonNull ViewGroup parent) {
        View row = convertView;
        ExportMarshalHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new ExportMarshalHolder();
            holder.imgIcon = row
                    .findViewById(R.id.exportDataItemIcon);
            holder.txtTitle = row
                    .findViewById(R.id.exportDataItemTitle);

            row.setTag(holder);
        } else {
            holder = (ExportMarshalHolder) row.getTag();
        }

        ExportMarshalMetadata meta = data[position];
        holder.txtTitle.setText(meta.getType());
        holder.imgIcon.setImageDrawable(meta.getIcon());

        return row;
    }

    static class ExportMarshalHolder {
        ImageView imgIcon;
        TextView txtTitle;
    }
}
