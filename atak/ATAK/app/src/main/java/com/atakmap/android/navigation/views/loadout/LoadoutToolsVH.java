
package com.atakmap.android.navigation.views.loadout;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;

import com.atakmap.android.gui.drawable.VisibilityDrawable;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.navigation.views.buttons.NavButtonDrawable;
import com.atakmap.android.navigation.views.buttons.NavButtonShadowBuilder;
import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.android.util.MappingVH;
import com.atakmap.app.R;

/**
 * Abstract view handler for the loadout tools
 */
abstract class LoadoutToolsVH<T extends LoadoutToolsVM> extends MappingVH<T> {

    protected final ImageView imageView;
    protected final TextView textView;
    protected final ImageView deleteView;
    protected final ImageView hideView;
    protected final VisibilityDrawable hideIcon;

    public LoadoutToolsVH(ViewGroup parent, @LayoutRes int layoutResId) {
        super(parent, layoutResId);
        this.imageView = this.findViewById(R.id.tak_nav_item_image);
        this.textView = this.findViewById(R.id.tak_nav_item_text);
        this.deleteView = this.findViewById(R.id.tak_nav_delete_tool);
        this.hideView = this.findViewById(R.id.tak_nav_hide_tool);
        this.hideView.setImageDrawable(hideIcon = new VisibilityDrawable());
    }

    @Override
    public void onBind(final T viewModel,
            final MappingAdapterEventReceiver<T> receiver) {

        boolean inUse = viewModel.isInUse();
        boolean editMode = viewModel.isEditMode();
        boolean hidden = viewModel.isHidden();

        // Get the button icon
        final NavButtonModel btn = viewModel.getButton();
        Drawable icon = btn.getImage();
        int color = NavView.getInstance().getUserIconColor();
        if (btn.isSelected()) {
            icon = btn.getSelectedImage();
            color = getResources().getColor(R.color.maize);
        }

        // Update the nav button drawable
        NavButtonDrawable navDr;
        Drawable dr = imageView.getDrawable();
        if (dr instanceof NavButtonDrawable && ((NavButtonDrawable) dr)
                .getBaseDrawable() == icon)
            navDr = (NavButtonDrawable) dr;
        else {
            navDr = new NavButtonDrawable(getContext(), icon);
            navDr.setShadowRadius(0);
            imageView.setImageDrawable(navDr);
        }
        navDr.setColor(color);
        navDr.setBadgeCount(btn.getBadgeCount());
        navDr.setBadgeImage(btn.getBadgeImage());

        this.textView.setText(viewModel.getName());
        this.deleteView.setVisibility(inUse && editMode
                ? View.VISIBLE
                : View.GONE);
        this.hideView.setVisibility(!inUse && editMode
                ? View.VISIBLE
                : View.GONE);
        this.imageView.setAlpha(hidden || editMode && inUse ? 0.5f : 1f);
        this.textView.setAlpha(hidden || editMode && inUse ? 0.5f : 1f);

        this.deleteView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.setInUse(false);
                Intent intent = new Intent(NavView.DELETE_TOOL);
                intent.putExtra("name", viewModel.getName());
                intent.putExtra("reference", btn.getReference());
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        });

        this.hideView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.setHidden(!viewModel.isHidden());
                Intent intent = new Intent(viewModel.isHidden()
                        ? NavView.HIDE_TOOL
                        : NavView.SHOW_TOOL);
                intent.putExtra("name", viewModel.getName());
                intent.putExtra("reference", btn.getReference());
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        });

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!viewModel.isEditMode()) {
                    viewModel.action = btn.getAction();
                    receiver.eventReceived(viewModel);
                }
            }
        });

        this.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (viewModel.isEditMode())
                    startDrag(view, viewModel);
                else {
                    viewModel.action = btn.getActionLong();
                    receiver.eventReceived(viewModel);
                }
                return true;
            }
        });
    }

    private void startDrag(View view, T viewModel) {
        final LoadoutItemModel currentLoadout = LoadoutManager.getInstance()
                .getCurrentLoadout();
        NavButtonModel btn = viewModel.getButton();
        if (!currentLoadout.containsButton(btn)) {
            ClipData dragData = btn.createClipboardData(NavView.DRAG_ADD_TOOL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                view.startDragAndDrop(dragData,
                        new NavButtonShadowBuilder(imageView, btn), null, 0);
            else
                view.startDrag(dragData,
                        new NavButtonShadowBuilder(imageView, btn), null, 0);
        }
    }
}
