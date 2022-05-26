
package com.atakmap.android.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.app.R;

public class TakDialogMapItemVH extends MappingVH<TakDialogMapItemVM> {
    private final ImageView imageView;
    private final TextView textView;

    public TakDialogMapItemVH(ViewGroup parent) {
        super(parent, R.layout.adapter_element_map_item);
        this.textView = this.findViewById(R.id.map_item_title);
        this.imageView = this.findViewById(R.id.map_item_image);
    }

    @Override
    public void onBind(final TakDialogMapItemVM viewModel,
            final MappingAdapterEventReceiver receiver) {
        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                receiver.eventReceived(viewModel);
            }
        });

        this.imageView.setImageResource(viewModel.getImage());
        this.textView.setText(viewModel.getText());
    }
}
