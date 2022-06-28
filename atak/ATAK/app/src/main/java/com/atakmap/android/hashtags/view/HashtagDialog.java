
package com.atakmap.android.hashtags.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Dialog for adding and remove hashtags
 */
public class HashtagDialog implements View.OnClickListener {

    public interface Callback {
        void onSetTags(Collection<String> tags);
    }

    private final MapView _mapView;
    private final Context _context;
    private final HashtagSet _tags = new HashtagSet();

    private String _title;
    private HashtagEditText _newTagET;
    private HashtagAdapter _adapter;
    private String _defaultTag;
    private Callback _callback;

    public HashtagDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    public HashtagDialog setTitle(String title) {
        _title = title;
        return this;
    }

    public HashtagDialog setDefaultTag(String defaultTag) {
        _defaultTag = defaultTag;
        return this;
    }

    public HashtagDialog setTags(Collection<String> tags) {
        _tags.clear();
        _tags.addAll(tags);
        return this;
    }

    public HashtagDialog setCallback(Callback cb) {
        _callback = cb;
        return this;
    }

    public void show() {
        View v = LayoutInflater.from(_context).inflate(
                R.layout.hashtags_dialog, _mapView, false);
        _newTagET = v.findViewById(R.id.tag_name);
        final ImageButton addTag = v.findViewById(R.id.add_tag);
        final ListView tagList = v.findViewById(R.id.tag_list);

        if (!FileSystemUtils.isEmpty(_defaultTag))
            _newTagET.setText(_defaultTag);

        addTag.setOnClickListener(this);

        tagList.setAdapter(_adapter = new HashtagAdapter());

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        if (_title != null)
            b.setTitle(_title);
        b.setView(v);
        b.setPositiveButton(R.string.done,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        // For ease of use, add whatever tag is sitting in the
                        // edit text in case the user forgets to tap the (+) button
                        String tag = _newTagET.getHashtag();
                        if (!FileSystemUtils.isEmpty(tag))
                            _tags.add(tag);

                        // Notify callback
                        if (_callback != null)
                            _callback.onSetTags(_tags);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();

        _adapter.refresh();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.add_tag) {
            String tag = _newTagET.getHashtag();
            if (FileSystemUtils.isEmpty(tag))
                return;
            _tags.add(tag);
        } else {
            String tag = String.valueOf(v.getTag());
            _tags.remove(tag);
        }
        _adapter.refresh();
    }

    private class HashtagAdapter extends BaseAdapter {

        private final List<String> _sorted = new ArrayList<>();

        public void refresh() {
            _sorted.clear();
            _sorted.addAll(_tags);
            Collections.sort(_sorted, HashtagManager.SORT_BY_NAME);
            _adapter.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return _sorted.size();
        }

        @Override
        public String getItem(int position) {
            return _sorted.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View row, ViewGroup parent) {

            ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
            if (h == null) {
                h = new ViewHolder();
                row = LayoutInflater.from(_context).inflate(
                        R.layout.hashtag_row, parent, false);
                h.tag = row.findViewById(R.id.hashtag);
                h.delete = row.findViewById(R.id.remove_tag);
                row.setTag(h);
            }

            String tag = getItem(position);

            h.tag.setText(tag);
            h.delete.setTag(tag);
            h.delete.setOnClickListener(HashtagDialog.this);

            return row;
        }
    }

    private static class ViewHolder {
        TextView tag;
        ImageButton delete;
    }
}
