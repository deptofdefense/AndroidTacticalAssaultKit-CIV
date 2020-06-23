
package com.atakmap.android.hashtags.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.view.HashtagDialog;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import java.util.Collection;

/**
 * List entry that represents content under a hashtag listing
 */
public class HashtagContentListItem extends AbstractChildlessListItem
        implements HashtagContent, Visibility, GoTo, Delete, MapItemUser,
        View.OnClickListener {

    private final HashtagMapOverlay _overlay;
    private final HashtagContent _content;

    public HashtagContentListItem(HashtagMapOverlay overlay,
            HashtagContent content) {
        _overlay = overlay;
        _content = content;
    }

    @Override
    public String getTitle() {
        return _content.getTitle();
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        for (String tag : _content.getHashtags())
            sb.append(tag).append(" ");
        return sb.toString().trim();
    }

    @Override
    public String getUID() {
        return _content instanceof MapItem
                ? ((MapItem) _content).getUID()
                : _content.getURI();
    }

    @Override
    public Drawable getIconDrawable() {
        return _content.getIconDrawable();
    }

    @Override
    public int getIconColor() {
        return _content.getIconColor();
    }

    @Override
    public Object getUserObject() {
        return _content;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ImageButton edit = v instanceof ImageButton ? (ImageButton) v : null;
        if (edit == null) {
            Context ctx = _overlay.getMapView().getContext();
            edit = (ImageButton) LayoutInflater.from(ctx)
                    .inflate(R.layout.hashtag_edit, parent, false);
        }
        edit.setOnClickListener(this);
        return edit;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.edit_tag) {
            MapView mv = _overlay.getMapView();
            Context ctx = mv.getContext();
            new HashtagDialog(mv)
                    .setTitle(ctx.getString(R.string.hashtags))
                    .setTags(_content.getHashtags())
                    .setCallback(new HashtagDialog.Callback() {
                        @Override
                        public void onSetTags(Collection<String> tags) {
                            _content.setHashtags(tags);
                        }
                    }).show();
        }
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (_content instanceof MapItem)
            return super.getAction(clazz);

        return clazz.isInstance(_content) ? clazz.cast(_content) : null;
    }

    // XXX - MapItem has a different method signature for setVisible
    // so we can't cast it directly to Visibility...
    // Also getVisible() vs. isVisible()

    @Override
    public boolean setVisible(boolean visible) {
        if (!(_content instanceof MapItem))
            return false;
        MapItem item = (MapItem) _content;
        boolean viz = item.getVisible();
        if (viz != visible) {
            item.setVisible(visible);
            return true;
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        return _content instanceof MapItem
                && ((MapItem) _content).getVisible();
    }

    @Override
    public boolean delete() {
        if (!(_content instanceof MapItem))
            return false;

        MapItem item = (MapItem) _content;

        if (item.getMetaBoolean("removable", true)) {
            // Remove from map group
            return item.removeFromGroup();
        } else if (item.hasMetaValue("deleteAction")) {
            // Special delete action
            Intent delete = new Intent(item.getMetaString("deleteAction", ""));
            delete.putExtra("targetUID", item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(delete);
            return true;
        }
        return false;
    }

    @Override
    public boolean goTo(boolean select) {
        if (!(_content instanceof MapItem))
            return false;

        MapItem item = (MapItem) _content;

        // Mark this item as selected by overlay manager
        item.setMetaBoolean("overlay_manager_select", true);
        return MapTouchController.goTo(item, select);
    }

    @Override
    public String getURI() {
        return _content.getURI();
    }

    @Override
    public MapItem getMapItem() {
        return _content instanceof MapItem ? (MapItem) _content : null;
    }

    @Override
    public void setHashtags(Collection<String> tags) {
        _content.setHashtags(tags);
    }

    @Override
    public Collection<String> getHashtags() {
        return _content.getHashtags();
    }
}
