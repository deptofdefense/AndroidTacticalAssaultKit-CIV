
package com.atakmap.android.hashtags.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * String array adapter specifically made for hashtags
 */
public class HashtagAdapter extends BaseAdapter implements Filterable,
        HashtagManager.OnUpdateListener {

    private final Context _context;

    // Complete list of tags
    private final List<String> _tags = new ArrayList<>();

    // Filtered list of tags
    private final List<String> _filtered = new ArrayList<>();

    public HashtagAdapter() {
        MapView mv = MapView.getMapView();
        _context = mv != null ? mv.getContext() : null;
        onHashtagsUpdate(null);
    }

    @Override
    public void onHashtagsUpdate(HashtagContent content) {
        _tags.clear();
        _tags.addAll(HashtagManager.getInstance().getTags());
    }

    @Override
    public int getCount() {
        return _filtered.size();
    }

    @Override
    public String getItem(int position) {
        return _filtered.get(position);
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
            row = LayoutInflater.from(_context).inflate(R.layout.hashtag_item,
                    parent, false);
            h.hashtag = row.findViewById(R.id.hashtag);
            h.usageCount = row.findViewById(R.id.usage_count);
            row.setTag(h);
        }

        String tag = getItem(position);

        int usages = HashtagManager.getInstance().getUsageCount(tag);

        h.hashtag.setText(tag);
        h.usageCount.setText("(" + usages + ")");

        return row;
    }

    private static class ViewHolder {
        TextView hashtag, usageCount;
    }

    @Override
    public Filter getFilter() {
        return new ArrayFilter();
    }

    // Modified version of ArrayAdapter filter sub-class
    private class ArrayFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            final FilterResults results = new FilterResults();

            final ArrayList<String> values = new ArrayList<>(_tags);

            if (prefix == null || prefix.length() == 0) {
                results.values = values;
                results.count = values.size();
            } else {
                final String prefixString = prefix.toString()
                        .toLowerCase(LocaleUtil.getCurrent());

                final int count = values.size();
                final ArrayList<String> newValues = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    final String value = values.get(i);
                    final String valueText = value
                            .toLowerCase(LocaleUtil.getCurrent());

                    // First match against the whole, non-splitted value
                    if (valueText.startsWith(prefixString)) {
                        newValues.add(value);
                    } else {
                        final String[] words = valueText.split(" ");
                        for (String word : words) {
                            if (word.startsWith(prefixString)) {
                                newValues.add(value);
                                break;
                            }
                        }
                    }
                }

                results.values = newValues;
                results.count = newValues.size();
            }

            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint,
                FilterResults results) {
            _filtered.clear();
            _filtered.addAll((List<String>) results.values);
            if (results.count > 0)
                notifyDataSetChanged();
            else
                notifyDataSetInvalidated();
        }
    }
}
