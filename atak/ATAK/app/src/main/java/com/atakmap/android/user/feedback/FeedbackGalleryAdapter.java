
package com.atakmap.android.user.feedback;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.util.ImageThumbnailCache;
import com.atakmap.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FeedbackGalleryAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeedbackFile> contents = new ArrayList<>();
    private final Set<FeedbackFile> selected = new HashSet<>();
    private boolean _multiSelect;

    /**
     * Construct an adapter for associated files to be used with the current feedback
     * @param context the context to be used during the creation of the experience
     */
    public FeedbackGalleryAdapter(final Context context) {
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    /**
     * Set the associated feedback files that are currently used by the adapter.
     * @param files the feedback files
     */
    public void setFiles(List<FeedbackFile> files) {
        contents.clear();
        if (files != null) {
            contents.addAll(files);
        }
        notifyDataSetChanged();
    }

    /**
     * enable multiselect for the gallery items
     * @param multiSelect true if multiselect checkboxes should be enabled.
     */
    public void setMultiSelect(boolean multiSelect) {
        if (_multiSelect != multiSelect) {
            _multiSelect = multiSelect;
            selected.clear();
            notifyDataSetChanged();
        }
    }

    /**
     * Adds or removes the FeedbackFile to the selection list.
     * @param file the feedback file representing the feedback.
     */
    public void toggleSelect(FeedbackFile file) {
        if (file == null)
            return;
        if (selected.contains(file))
            selected.remove(file);
        else
            selected.add(file);
        notifyDataSetChanged();
    }

    /**
     * Gets the selected files
     * @return the list of feedback files.
     */
    public List<FeedbackFile> getSelectedFiles() {
        return new ArrayList<>(selected);
    }

    @Override
    public int getCount() {
        return contents.size();
    }

    @Override
    public FeedbackFile getItem(int position) {
        return contents.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View item, ViewGroup parent) {

        ViewHolder h = item != null && item.getTag() instanceof ViewHolder
                ? (ViewHolder) item.getTag()
                : null;
        if (h == null) {
            item = inflater.inflate(R.layout.user_feedback_gallery_item, parent,
                    false);
            h = new ViewHolder();

            h.largeIcon = item.findViewById(R.id.largeIcon);
            h.name = item.findViewById(R.id.name);
            h.selector = item.findViewById(R.id.selector);
            item.setTag(h);
        }

        final FeedbackFile content = getItem(position);
        if (content == null) {
            item.setVisibility(View.INVISIBLE);
            return item;
        }

        item.setVisibility(View.VISIBLE);
        h.name.setText(content.getName());
        h.largeIcon.setVisibility(View.VISIBLE);

        final ImageView icon = h.largeIcon;

        icon.setImageBitmap(
                content.getIcon(context, new ImageThumbnailCache.Callback() {
                    @Override
                    public void onGetThumbnail(File file, Bitmap thumb) {
                        icon.post(new Runnable() {
                            @Override
                            public void run() {
                                icon.setImageBitmap(thumb);
                                notifyDataSetChanged();
                            }
                        });
                    }
                }));

        h.selector.setVisibility(_multiSelect ? View.VISIBLE : View.INVISIBLE);
        h.selector.setChecked(selected.contains(content));

        return item;
    }

    private static class ViewHolder {
        ImageView largeIcon;
        CheckBox selector;
        TextView name;
    }

}
