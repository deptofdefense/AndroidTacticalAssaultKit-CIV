
package com.atakmap.android.layers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.gui.drawable.VisibilityDrawable;
import com.atakmap.app.R;

/**
 * Layer list item view holder
 */
public class LayerViewHolder {

    public View root, background;
    public View outlineBorder, titleLayout, resLayout, dlLayout;
    public LinearLayout rangeLayout, filesLayout;
    public TextView title, desc, dlSize, resTxt;
    public ImageView lockBtn, vizBtn, expandBtn;
    public VisibilityDrawable vizIcon;
    public ImageButton sendBtn;
    public CheckBox outlineBtn;
    public LayerSelection selection;

    public static LayerViewHolder get(Context ctx, View row, ViewGroup parent) {
        LayerViewHolder h = row != null ? (LayerViewHolder) row.getTag() : null;
        if (h == null) {
            h = new LayerViewHolder();
            h.root = row = LayoutInflater.from(ctx).inflate(
                    R.layout.layers_manager_list_item, parent, false);
            h.background = row
                    .findViewById(R.id.layers_manager_item_background);
            h.titleLayout = row
                    .findViewById(R.id.layers_manager_item_title_layout);
            h.title = row.findViewById(R.id.layers_manager_item_title);
            h.desc = row.findViewById(R.id.layers_manager_item_desc);
            h.outlineBorder = row
                    .findViewById(R.id.layers_manager_item_outline_layout);
            h.outlineBtn = row
                    .findViewById(R.id.layers_manager_item_outline_checkbox);
            h.lockBtn = row.findViewById(R.id.layers_manager_item_layer_lock);
            h.vizBtn = row.findViewById(R.id.layers_manager_item_toggle_image);
            h.sendBtn = row.findViewById(R.id.layers_manager_item_layer_send);
            h.expandBtn = row.findViewById(R.id.layers_manager_item_expand);
            h.resLayout = row.findViewById(R.id.resolutionLayout);
            h.dlLayout = row.findViewById(R.id.downloadLayout);
            h.rangeLayout = row.findViewById(R.id.rangeSeekLayout);
            h.filesLayout = row.findViewById(R.id.filesLayout);
            h.dlSize = row.findViewById(R.id.downloadSizeTV);
            h.resTxt = row.findViewById(R.id.rezTV);
            h.titleLayout.setTag(h);
            h.vizBtn.setTag(h);
            h.vizBtn.setImageDrawable(h.vizIcon = new VisibilityDrawable());
            h.outlineBtn.setTag(h);
            h.lockBtn.setTag(h);
            h.sendBtn.setTag(h);
            h.expandBtn.setTag(h);
            row.setTag(h);
        }
        return h;
    }
}
