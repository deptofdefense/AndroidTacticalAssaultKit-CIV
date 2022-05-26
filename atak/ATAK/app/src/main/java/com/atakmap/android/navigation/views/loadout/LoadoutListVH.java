
package com.atakmap.android.navigation.views.loadout;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.android.util.MappingVH;
import com.atakmap.android.util.TakAlertDialog;
import com.atakmap.app.R;

/**
 * View handler for the list of available loadouts
 */
@SuppressLint("ViewConstructor")
public class LoadoutListVH extends MappingVH<LoadoutListVM> {
    private final TextView textView;
    private final ImageView selectImageView;
    private final ImageView deleteButton;
    private final ImageView editButton;

    public LoadoutListVH(ViewGroup parent) {
        super(parent, R.layout.loadout_list_item);
        this.textView = this.findViewById(R.id.toolbar_title_item_text);
        this.selectImageView = this.findViewById(R.id.toolbar_item_image);
        this.deleteButton = this.findViewById(R.id.toolbar_delete_button);
        this.editButton = this.findViewById(R.id.toolbar_edit_image);
    }

    @Override
    public void onBind(final LoadoutListVM viewModel,
            final MappingAdapterEventReceiver<LoadoutListVM> receiver) {

        textView.setText(viewModel.getTitle());

        boolean selected = viewModel.getLoadout() == LoadoutManager
                .getInstance()
                .getCurrentLoadout();
        selectImageView.setSelected(selected);

        if (viewModel.getLoadout().isDefault()) {
            deleteButton.setVisibility(INVISIBLE);
            editButton.setVisibility(INVISIBLE);
        } else {
            deleteButton.setVisibility(VISIBLE);
            editButton.setVisibility(VISIBLE);
        }

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.setMode(LoadoutMode.VIEW);
                receiver.eventReceived(viewModel);
            }
        });
        selectImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.setMode(LoadoutMode.SELECT);
                receiver.eventReceived(viewModel);
            }
        });

        deleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final TakAlertDialog td = new TakAlertDialog();
                td.setTitle(R.string.delete_toolbar_title);
                td.setTitle(String.format(
                        getResources().getString(R.string.delete_toolbar_title),
                        viewModel.getTitle()));
                td.setMessage(R.string.delete_toolbar_text);
                td.setPositiveButton(R.string.confirm,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                td.dismiss();
                                viewModel.setMode(LoadoutMode.DELETE);
                                receiver.eventReceived(viewModel);

                            }
                        });

                td.setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                td.dismiss();
                            }
                        });
                td.show();
            }
        });

        editButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.setMode(LoadoutMode.EDIT);
                receiver.eventReceived(viewModel);
            }
        });
    }

}
